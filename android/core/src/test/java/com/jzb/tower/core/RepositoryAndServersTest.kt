package com.jzb.tower.core

import com.jzb.tower.core.contract.LanServiceHooks
import com.jzb.tower.core.engine.TowerRepositoryImpl
import com.jzb.tower.core.model.*
import com.jzb.tower.core.service.*
import com.jzb.tower.core.store.PersistenceStore
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RepositoryAndServersTest {
    @get:Rule val temp = TemporaryFolder()
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

    @Test fun refreshCarriesExclusionsAndOverridesAcrossUniqueRemarkChanges() = runBlocking {
        val first = AtomicReference("first")
        val server = fixture { _, _ -> NodeExport.uri(ss(first.get()))!! }
        val repo = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder())
        try {
            val initial = repo.addSubscription("http://127.0.0.1:${server.address.port}/nodes", null, null)
            val oldNode = repo.state.value.snapshot.nodes.single()
            repo.updateNode(oldNode.id, null, "GB", false, true)
            first.set("new remark")
            repo.refreshSubscription(initial.subscription.id)
            val newNode = repo.state.value.snapshot.nodes.single()
            assertNotEquals(oldNode.id, newNode.id)
            assertEquals("new remark", newNode.name)
            assertEquals("GB", newNode.countryOverride)
            assertEquals(listOf(newNode.id), repo.state.value.snapshot.excludedNodeIDs)
            assertEquals(0, repo.generateConfiguration(ClientTarget.FLCLASH, ExportContentMode.NODES_ONLY).supportedCount)

            val a = ss("A", id = "a").copy(countryOverride = "SG")
            val b = ss("B", id = "b").copy(countryOverride = "US")
            val identical = a.copy(id = "a2", countryOverride = null)
            val ambiguous = a.copy(id = "a3", name = "C", countryOverride = null)
            val carried = TowerRepositoryImpl.carriedOverExclusions(listOf(a, b), setOf("a"), listOf(ambiguous, identical))
            assertNull(carried.nodes.first().countryOverride)
            assertEquals("SG", carried.nodes.last().countryOverride)
            assertEquals(setOf("a2"), carried.excludedNodeIDs)
        } finally {
            repo.shutdown()
            server.stop(0)
        }
    }

    @Test fun refreshSerializesSameHostAndRunsDifferentHostsInParallel() = runBlocking {
        val same = AtomicInteger()
        val maxSame = AtomicInteger()
        val global = AtomicInteger()
        val maxGlobal = AtomicInteger()
        val server = fixture { host, _ ->
            val localhost = host.startsWith("127.0.0.1")
            if (localhost) maxSame.accumulateAndGet(same.incrementAndGet(), ::maxOf)
            maxGlobal.accumulateAndGet(global.incrementAndGet(), ::maxOf)
            try {
                Thread.sleep(120)
                NodeExport.uri(ss())!!
            } finally {
                if (localhost) same.decrementAndGet()
                global.decrementAndGet()
            }
        }
        val repo = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder())
        try {
            val port = server.address.port
            repo.addSubscription("http://127.0.0.1:$port/one", null, null)
            repo.addSubscription("http://127.0.0.1:$port/two", null, null)
            repo.addSubscription("http://127.0.0.2:$port/other", null, null)
            assertEquals(3, repo.refreshAllSubscriptions().size)
            assertEquals(1, maxSame.get())
            assertTrue(maxGlobal.get() > 1)
        } finally {
            repo.shutdown()
            server.stop(0)
        }
    }

    @Test fun persistenceAndBackupKeepTimestampAndNeverStoreAuthKey() = runBlocking {
        val dir = temp.newFolder()
        val seeds = PersistenceStore(dir)
        seeds.write(AppSnapshot(updatedAtEpochMs = 123456L, nodes = listOf(ss())))
        val store = TestSecrets()
        val repo = TowerRepositoryImpl(TestResources, store, dir)
        val key = "tskey-auth-fakeTestKey"
        try {
            repo.initialize()
            assertEquals(123456L, repo.state.value.snapshot.updatedAtEpochMs)
            val connection = repo.createTailnet("Home", null, null, emptyList(), null, key, true)
            assertEquals(key, store.getAuthKey(connection.id))
            assertTrue(repo.state.value.snapshot.tailnets.single().hasAuthKey)
            val timestamp = repo.state.value.snapshot.updatedAtEpochMs
            val saved = dir.resolve("state.json")
            val exported = repo.exportBackupJson()
            assertFalse(exported.contains(key))
            assertFalse(saved.readText().contains(key))
            assertFalse(PersistenceStore.decode(exported).tailnets.single().hasAuthKey)
            assertEquals(timestamp, seeds.read()?.updatedAtEpochMs)
            assertEquals(listOf("state.json"), dir.list()!!.sorted())
            if (Files.getFileStore(saved.toPath()).supportsFileAttributeView("posix")) {
                assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(saved.toPath()))
            }
            val direct = repo.prepareDirectImport(ClientTarget.CLASH, ExportContentMode.FULL_CONFIGURATION)
            client.newCall(Request.Builder().url(direct.localUrl).build()).execute().use {
                assertEquals(200, it.code)
                assertTrue(it.body!!.string().contains(key))
            }
            val foreign = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder())
            try {
                foreign.importBackupJson(exported)
                assertEquals(timestamp, foreign.state.value.snapshot.updatedAtEpochMs)
                assertFalse(foreign.state.value.snapshot.tailnets.single().hasAuthKey)
            } finally {
                foreign.shutdown()
            }
            repo.resetAllData()
            assertNull(store.getAuthKey(connection.id))
            assertTrue(repo.state.value.snapshot.nodes.isEmpty())
        } finally {
            repo.shutdown()
        }
    }

    @Test fun directImportUsesLoopbackAndNegotiatesAnywhereAjson() = runBlocking {
        val repo = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder())
        try {
            repo.importNodesFromText("vless://$sampleUuid@example.net:443?security=tls&allowInsecure=1#AJSON")
            val handle = repo.prepareDirectImport(ClientTarget.ANYWHERE, ExportContentMode.NODES_ONLY)
            assertTrue(handle.localUrl.startsWith("http://127.0.0.1:"))
            assertTrue(handle.expiresAtEpochMs - System.currentTimeMillis() in 170_000..180_000)
            assertTrue(handle.schemeUrls.single().startsWith("anywhere://"))
            client.newCall(Request.Builder().url(handle.localUrl).header("X-Anywhere-Format", "ajson/1").build()).execute().use { response ->
                assertEquals(200, response.code)
                assertTrue(response.header("Content-Type")!!.startsWith("application/json"))
                assertTrue(response.header("Content-Disposition")!!.contains(".json"))
                assertNotNull(response.header("profile-title"))
                assertNotNull(response.header("subscription-userinfo"))
                assertTrue(response.body!!.string().contains("\"insecure\": true"))
            }
            client.newCall(Request.Builder().url(handle.localUrl).header("Host", "example.net").build()).execute().use {
                assertEquals(404, it.code)
            }
            try {
                repo.prepareDirectImport(ClientTarget.QUANX, ExportContentMode.FULL_CONFIGURATION)
                fail("QuanX full configurations must not create import URLs")
            } catch (_: IllegalArgumentException) { }
        } finally {
            repo.shutdown()
        }
    }

    @Test fun temporaryImportsExpireAndLanUsesTokenUserAgentAndPortFallback() = runBlocking {
        val generator = ConfigurationGenerator(TestResources, TestSecrets())
        val snapshot = AppSnapshot(nodes = listOf(ss()))
        DirectImportServer(lifetimeMs = 1000).use { server ->
            val handle = server.issue(generator.generate(snapshot, ClientTarget.FLCLASH, ExportContentMode.FULL_CONFIGURATION))
            client.newCall(Request.Builder().url(handle.localUrl).head().build()).execute().use {
                assertEquals(200, it.code)
                assertTrue(it.header("Content-Length")!!.toInt() > 0)
                assertEquals("", it.body!!.string())
            }
            Thread.sleep(1100)
            assertTrue(runCatching { client.newCall(Request.Builder().url(handle.localUrl).build()).execute().close() }
                .exceptionOrNull() is java.io.IOException)
        }
        val selected = AtomicReference<ClientTarget>()
        ServerSocket(0).use { occupied ->
            LanSubscriptionServer({ target, mode ->
                selected.set(target)
                generator.generate(snapshot, target, mode)
            }, { subscriptionHeaders(snapshot) }, occupied.localPort).use { server ->
                val port = server.start()
                assertNotEquals(occupied.localPort, port)
                val old = server.token
                val base = "http://127.0.0.1:$port/sub/"
                fun get(path: String, ua: String = "FlClash/0.9") =
                    client.newCall(Request.Builder().url(base + path).header("User-Agent", ua).build()).execute()
                get("invalid").use { assertEquals(403, it.code) }
                get(old + "?content=nodesOnly", "FlClash/0.9").use {
                    assertEquals(200, it.code)
                    assertTrue(it.body!!.string().contains("proxies"))
                }
                assertEquals(ClientTarget.FLCLASH, selected.get())
                get(old, "v2rayNG/2.0").use {
                    assertEquals(200, it.code)
                    assertTrue(String(java.util.Base64.getDecoder().decode(it.body!!.string())).startsWith("ss://"))
                    assertNotNull(it.header("profile-title"))
                }
                assertEquals(ClientTarget.V2BOX, selected.get())
                get(old + "?target=surfboard").use {
                    assertEquals(200, it.code)
                    assertTrue(it.body!!.string().contains("[Proxy]"))
                }
                assertEquals(ClientTarget.SURFBOARD, selected.get())
                server.rotateToken()
                get(old).use { assertEquals(403, it.code) }
                get(server.token, "SFA/1.14").use { assertEquals(200, it.code) }
                assertEquals(ClientTarget.SING_BOX, selected.get())
            }
        }
    }

    @Test fun repositoryLanHooksAndQrFollowSharingState() = runBlocking {
        val active = AtomicReference<String>()
        val repo = TowerRepositoryImpl(TestResources, TestSecrets(), temp.newFolder(), object : LanServiceHooks {
            override fun getLocalLanAddresses() = listOf("192.168.1.10")
            override fun onLanSharingStarted(primaryUrl: String, port: Int) { active.set(primaryUrl) }
            override fun onLanSharingStopped() { active.set(null) }
        })
        try {
            val first = repo.toggleLanSharing(true)
            assertEquals(first.primaryURL, active.get())
            val url = repo.buildLanSubscriptionUrl(LanSubscriptionFormat.STASH, ExportContentMode.NODES_ONLY)
            assertTrue(url.contains("target=stash&content=nodesOnly"))
            val qr = repo.generateQrMatrix(url)!!
            assertTrue(qr.size > 21)
            assertTrue(qr.get(0, 0))
            val rotated = repo.rotateLanToken()
            assertNotEquals(first.token, rotated.token)
            assertEquals(rotated.primaryURL, active.get())
            assertFalse(repo.toggleLanSharing(false).enabled)
            assertNull(active.get())
        } finally {
            repo.shutdown()
        }
    }

    private fun fixture(body: (host: String, path: String) -> String): HttpServer {
        val server = HttpServer.create(InetSocketAddress("0.0.0.0", 0), 8)
        server.executor = Executors.newCachedThreadPool { task -> Thread(task, "Tower subscription fixture").apply { isDaemon = true } }
        server.createContext("/") { exchange ->
            exchange.use {
                val result = body(it.requestHeaders.getFirst("Host"), it.requestURI.path).toByteArray()
                it.responseHeaders.add("subscription-userinfo", "total=100; download=3")
                it.sendResponseHeaders(200, result.size.toLong())
                it.responseBody.write(result)
            }
        }
        server.start()
        return server
    }
}
