package com.jzb.tower.core

import com.jzb.tower.core.model.*
import com.jzb.tower.core.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.Base64
import kotlin.concurrent.thread

class SubscriptionParserTest {
    @Test fun decodesBase64AndEverySupportedLinkFamily() {
        val vmess = base64Url("""{"add":"example.net","port":8443,"id":"$sampleUuid","ps":"VMess","net":"ws","path":"/ws","tls":"tls"}""")
        val ssr = base64Url("example.net:443:origin:aes-128-cfb:plain:${base64Url("pass")}/?remarks=${base64Url("SSR")}")
        val links = listOf(
            "ss://${base64Url("aes-128-gcm:p:ss+word")}@example.net:443#%E6%9D%B1%E4%BA%AC",
            "ssr://$ssr", "vmess://$vmess",
            "vless://$sampleUuid@example.net:443?security=reality&pbk=abc&sid=1&type=grpc&serviceName=tunnel&support-x25519mlkem768=1#VLESS",
            "trojan://password@example.net:443#Trojan",
            "hysteria://password@example.net:443?up=25&down=50#Hysteria",
            "hy2://password@example.net:443,8443-8445?obfs=salamander&obfs-password=p#HY2",
            "tuic://$sampleUuid:password@example.net:443?congestion_control=bbr#TUIC",
            "wg://private@example.net:51820?publickey=public&address=10.0.0.1/32,fd00::1234/128&mtu=1280#WG",
            "anytls://password@example.net:443?min-idle-session=2#AnyTLS",
            "snell://long-enough-psk@example.net:443?version=6&mode=default#Snell",
            "socks5://user:password@example.net:1080#SOCKS",
            "https://user:password@example.net:8443#HTTP",
            "ssh://user:password@example.net:22?host-key=ssh-rsa%20test#SSH",
            "trusttunnel://user:password@example.net:443?quic=true&client-random-auth-key=key#TrustTunnel",
            "masque://user:password@example.net:443#MASQUE",
        )
        val encoded = Base64.getMimeEncoder().encodeToString(links.joinToString("\n").toByteArray())
        val parsed = SubscriptionParser.parse(encoded, "source")
        assertEquals(parsed.skippedReasons.toString(), 0, parsed.skippedCount)
        assertEquals(ClientCatalog.ALL_PROXY_KINDS.toSet(), parsed.nodes.map { it.kind }.toSet())
        assertTrue(parsed.nodes.all { it.sourceID == "source" })
        assertEquals("p:ss+word", parsed.nodes[0].password)
        assertEquals("東京", parsed.nodes[0].name)
        assertEquals(true, parsed.nodes[3].realitySupportsX25519MLKEM768)
        assertEquals("443,8443-8445", parsed.nodes[6].portHopping)
        assertEquals("fd00::1234/128", parsed.nodes[8].wireGuardIPv6)
        val roundTrip = parsed.nodes.map { original -> NodeExport.uri(original)?.let(SubscriptionParser::parseProxyURI) }
        assertEquals(parsed.nodes.map { it.kind }, roundTrip.map { it?.kind })
        assertEquals(DetectedSourceKind.PROXY_NODES, SourceInputDetector.detect(encoded).kind)
    }

    @Test fun clashYamlAndUnsupportedOptionsHaveSpecificCounts() {
        val text = """
            proxies:
              - {name: Good, type: ss, server: proxy.example.net, port: 443, cipher: aes-128-gcm, password: test}
              - {name: Unsupported, type: ss, server: proxy.example.net, port: 443, cipher: aes-128-gcm, password: test, plugin: kcptun}
              - {name: Invalid port, type: http, server: proxy.example.net, port: 65536}
              - {name: Nondefault Snell, type: snell, server: proxy.example.net, port: 443, version: 6, mode: unsafe-raw, psk: long-password}
              - {name: Tokyo, type: vless, server: proxy.example.net, port: 443, uuid: $sampleUuid, tls: true, network: ws, ws-opts: {path: /vless, headers: {Host: front.example.net}}}
            rules:
              - MATCH,DIRECT
        """.trimIndent()
        val result = SubscriptionParser.parse(text)
        assertEquals(listOf(ProxyKind.SS, ProxyKind.VLESS), result.nodes.map { it.kind })
        assertEquals(3, result.skippedCount)
        assertTrue(result.skippedReasons.any { it.reason.contains("SIP003") })
        assertTrue(result.skippedReasons.any { it.reason.contains("Snell") })
        assertTrue(result.skippedReasons.any { it.reason.contains("端口") })
        assertEquals("front.example.net", result.nodes.last().hostHeader)
        assertEquals(DetectedSourceKind.CLASH_YAML, SourceInputDetector.detect(text).kind)
        val invalidLink = "ss://${base64Url("aes-128-gcm:pass")}@example.net:443?plugin=kcptun"
        assertEquals(1, SubscriptionParser.parse(invalidLink).skippedCount)
    }

    @Test fun surgeProxiesStatusAndNotices() {
        val text = """
            STATUS=↑: 1 GiB, ↓: 2 GiB, TOT: 10 GiB, EXPIRES: 2030-01-02
            [General]
            loglevel = notify
            [Proxy]
            SS = ss, proxy.example.net, 443, encrypt-method=aes-128-gcm, password="secret,with,commas"
            Snell = snell, proxy.example.net, 443, psk=long-secret-test, version=6, mode=default, shadow-tls-sni=front.example.net, shadow-tls-password=shadow, shadow-tls-version=3
            MASQUE = masque, proxy.example.net, 443, username=user, password=secret
            剩余流量 8 GB = https, proxy.example.net, 443
            [Rule]
            FINAL,DIRECT
        """.trimIndent()
        val parsed = SubscriptionParser.parse(text)
        assertEquals(parsed.skippedReasons.toString(), 0, parsed.skippedCount)
        assertEquals(4, parsed.nodes.size)
        assertEquals("secret,with,commas", parsed.nodes[0].password)
        assertEquals(ShadowTlsOptions(3, "front.example.net", "shadow"), parsed.nodes[1].shadowTLS)
        assertEquals("basicConnectIP", parsed.nodes[2].masque?.mode)
        assertEquals(true, parsed.nodes.last().isSubscriptionMetadata)
        val gib = 1024L * 1024 * 1024
        assertEquals(gib, parsed.usage?.uploadBytes)
        assertEquals(2 * gib, parsed.usage?.downloadBytes)
        assertEquals(10 * gib, parsed.usage?.totalBytes)
        assertNotNull(parsed.usage?.expiresAtEpochMs)
        assertEquals(listOf("剩余流量 8 GB"), parsed.usage?.notices)
        assertEquals(100L, SubscriptionParser.quotaFromHeader("upload=10; download=20; total=100; expire=1700000000")?.totalBytes)
        assertEquals(1_700_000_000_000L, SubscriptionParser.quotaFromHeader("expire=1700000000")?.expiresAtEpochMs)
        assertEquals(DetectedSourceKind.SURGE_CONFIG, SourceInputDetector.detect(text).kind)
        assertEquals(DetectedSourceKind.RULE_SCHEME, SourceInputDetector.detect("[Rule]\nFINAL,DIRECT").kind)
        assertEquals(DetectedSourceKind.SUBSCRIPTION_URL, SourceInputDetector.detect("https://example.net/sub?token=example").kind)
    }

    @Test fun quotaProbeReadsHeadersAndPreservesOriginalNodes() = runBlocking {
        withSubscriptionResponses(listOf(
            """Profile-Title: base64:${Base64.getEncoder().encodeToString("订阅测试".toByteArray())}""" to
                "anytls://password@example.net:443#Original",
            "subscription-userinfo: total=100; download=20" to "ss://${base64Url("aes-128-gcm:pw")}@example.net:443#Converted",
        )) { url, requests ->
            val result = SubscriptionFetcher().fetch(url, SubscriptionRequestOptions(userAgent = "Tower Unit Test"))
            assertEquals(listOf(ProxyKind.ANYTLS), result.parsed.nodes.map { it.kind })
            assertEquals("Original", result.parsed.nodes.single().name)
            assertEquals("订阅测试", result.title)
            assertEquals(100L, result.usage?.totalBytes)
            assertEquals(20L, result.usage?.downloadBytes)
            assertTrue(requests[0].contains("User-Agent: Tower Unit Test", true))
            assertTrue(requests[1].startsWith("GET /sub?flag=clash "))
        }
    }

    @Test fun originalHeaderOverridesStatusWithoutAnExtraRequest() = runBlocking {
        withSubscriptionResponses(listOf(
            "subscription-userinfo: total=100; download=20" to
                "STATUS=total=999; download=10; upload=5\nanytls://password@example.net:443#Original",
        )) { url, requests ->
            val result = SubscriptionFetcher().fetch(url)
            assertEquals(100L, result.usage?.totalBytes)
            assertEquals(20L, result.usage?.downloadBytes)
            assertEquals(5L, result.usage?.uploadBytes)
            assertEquals(1, requests.size)
        }
    }

    private suspend fun withSubscriptionResponses(
        responses: List<Pair<String, String>>,
        test: suspend (String, List<String>) -> Unit,
    ) {
        ServerSocket(0).use { listener ->
            val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
            val worker = thread(isDaemon = true) {
                try {
                    responses.forEach { (header, content) ->
                        listener.accept().use { socket ->
                            val reader = socket.getInputStream().bufferedReader()
                            val incoming = generateSequence { reader.readLine()?.takeIf(String::isNotEmpty) }.joinToString("\n")
                            requests.add(incoming)
                            val bytes = content.toByteArray(Charsets.UTF_8)
                            val prefix = "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n$header\r\n\r\n"
                            socket.getOutputStream().write(prefix.toByteArray() + bytes)
                        }
                    }
                } catch (_: java.net.SocketException) {
                    // Closing the fixture unblocks a pending accept on a failed assertion.
                }
            }
            try { test("http://127.0.0.1:${listener.localPort}/sub", requests) }
            finally { listener.close(); worker.join(1000) }
        }
    }
}
