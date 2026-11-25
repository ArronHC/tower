package com.jzb.tower.core.service

import com.jzb.tower.core.contract.DirectImportHandle
import com.jzb.tower.core.model.*
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private data class Request(val method: String, val uri: URI, val headers: Map<String, String>) {
    val acceptsAjson get() = headers["x-anywhere-format"]?.split(',')?.any { it.trim().equals("ajson/1", true) } == true
}
private data class Response(val status: Int, val headers: Map<String, String> = emptyMap(), val body: ByteArray = byteArrayOf())

private class LocalHttpServer(address: String, requestedPort: Int, private val handler: (Request) -> Response) : Closeable {
    private val socket = ServerSocket().apply {
        try {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName(address), requestedPort), 32)
        } catch (e: IOException) {
            close()
            throw e
        }
    }
    val port = socket.localPort
    private val connections = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, ArrayBlockingQueue(24)) { task ->
        Thread(task, "Tower HTTP request").apply { isDaemon = true }
    }
    private val listener = Thread(::listen, "Tower HTTP listener").apply { isDaemon = true; start() }

    private fun listen() {
        while (!socket.isClosed) {
            val connection = try { socket.accept() } catch (_: IOException) { break }
            connections.add(connection)
            try {
                workers.execute { serve(connection) }
            } catch (_: RejectedExecutionException) {
                connections.remove(connection)
                runCatching { connection.close() }
            }
        }
    }

    private fun serve(socket: Socket) {
        try {
            socket.use { connection ->
                connection.soTimeout = 4_000
                val request = try { readRequest(BufferedInputStream(connection.getInputStream())) } catch (_: IllegalArgumentException) { null }
                val response = try {
                    when (request?.method) {
                        null -> Response(400)
                        "GET", "HEAD" -> handler(request)
                        else -> Response(405, mapOf("Allow" to "GET, HEAD"))
                    }
                } catch (_: Exception) {
                    Response(500)
                }
                val headers = linkedMapOf("Connection" to "close", "Cache-Control" to "no-store",
                    "X-Content-Type-Options" to "nosniff", "Content-Length" to response.body.size.toString()) + response.headers
                val status = when (response.status) {
                    200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
                    else -> "Internal Server Error"
                }
                val output = connection.getOutputStream().buffered()
                output.write(buildString {
                    append("HTTP/1.1 ${response.status} $status\r\n")
                    headers.forEach { (key, value) -> append("$key: $value\r\n") }
                    append("\r\n")
                }.toByteArray(Charsets.US_ASCII))
                if (request?.method != "HEAD") output.write(response.body)
                output.flush()
            }
        } catch (_: IOException) {
            // The connection may be canceled by the importing client or by close().
        } finally {
            connections.remove(socket)
        }
    }

    private fun readRequest(input: BufferedInputStream): Request? {
        var remaining = 16_384
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        fun line(): String? {
            val result = StringBuilder()
            while (remaining-- > 0 && System.nanoTime() < deadline) {
                val byte = input.read()
                if (byte < 0) return null
                if (byte == 10) return result.toString().removeSuffix("\r")
                if (byte !in 32..126 && byte != 9 && byte != 13) return null
                result.append(byte.toChar())
            }
            return null
        }
        val request = line()?.split(' ') ?: return null
        if (request.size != 3 || request[2] !in setOf("HTTP/1.0", "HTTP/1.1") || !request[1].startsWith('/')) return null
        val uri = runCatching { URI(request[1]) }.getOrNull() ?: return null
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawFragment != null) return null
        val headers = linkedMapOf<String, String>()
        while (true) {
            val row = line() ?: return null
            if (row.isEmpty()) break
            val key = row.substringBefore(':').lowercase(Locale.ROOT)
            val value = row.substringAfter(':', "").trim()
            if (!key.matches(Regex("[a-z0-9!#$%&'*+.^_`|~-]+")) || ':' !in row || '\r' in row) return null
            if (key == "host" && key in headers) return null
            headers.merge(key, value) { first, second -> "$first,$second" }
        }
        return Request(request[0], uri, headers)
    }

    override fun close() {
        runCatching { socket.close() }
        workers.shutdownNow()
        connections.forEach { runCatching { it.close() } }
        connections.clear()
    }
}

private val random = SecureRandom()
internal fun accessToken(): String {
    val bytes = ByteArray(24).apply(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

internal fun subscriptionHeaders(snapshot: AppSnapshot): Map<String, String> {
    val usage = snapshot.subscriptions.filter { it.isEnabled }.mapNotNull { it.usage }
    fun total(selector: (SubscriptionUsage) -> Long?) = usage.fold(0L) { sum, item ->
        val value = (selector(item) ?: 0L).coerceAtLeast(0L)
        if (value > Long.MAX_VALUE - sum) Long.MAX_VALUE else sum + value
    }
    val title = cleanLine(snapshot.settings.configurationName).ifEmpty { "Tower" }
    val expiry = usage.mapNotNull { it.expiresAtEpochMs?.takeIf { date -> date > 0 } }.minOrNull()?.div(1000) ?: 0
    return mapOf("subscription-userinfo" to "upload=${total { it.uploadBytes }}; download=${total { it.downloadBytes }}; total=${total { it.totalBytes }}; expire=$expiry",
        "profile-title" to ("base64:" + Base64.getEncoder().encodeToString(title.toByteArray())), "profile-update-interval" to "24")
}

private fun response(config: GeneratedConfiguration, ajson: Boolean, headers: Map<String, String>): Response {
    val alternate = config.anywhereAJSON?.takeIf { ajson }
    val name = if (alternate != null) config.fileName.substringBeforeLast('.') + ".json" else config.fileName
    val mime = if (alternate != null) "application/json" else config.mimeType
    val ascii = name.replace(Regex("[^ -~]|[\"\\\\]"), "_")
    return Response(200, headers + mapOf("Content-Type" to "$mime; charset=utf-8",
        "Content-Disposition" to "attachment; filename=\"$ascii\"; filename*=UTF-8''${percentEncode(name)}"),
        (alternate ?: config.content).toByteArray(Charsets.UTF_8))
}

class DirectImportServer(private val lifetimeMs: Long = 180_000) : Closeable {
    private data class Entry(val configuration: GeneratedConfiguration, val deadlineNanos: Long, val headers: Map<String, String>)
    private val entries = linkedMapOf<String, Entry>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "Tower import expiry").apply { isDaemon = true } }
    private var listener: LocalHttpServer? = null
    private var timer: ScheduledFuture<*>? = null

    @Synchronized
    fun issue(config: GeneratedConfiguration, headers: Map<String, String> = emptyMap()): DirectImportHandle {
        check(!scheduler.isShutdown)
        val meta = ClientCatalog.meta(config.target)
        require(if (config.contentMode == ExportContentMode.NODES_ONLY) meta.supportsNodesOnlyImport else meta.supportsDirectConfigurationImport) {
            "此客户端需要通过文件或链接导入"
        }
        val server = listener ?: LocalHttpServer("127.0.0.1", 0, ::handle).also { listener = it }
        val token = accessToken()
        val expiration = System.currentTimeMillis() + lifetimeMs
        val url = "http://127.0.0.1:${server.port}/import/$token"
        val schemes = schemes(config, url, headers["profile-title"])
        require(schemes.isNotEmpty()) { "此客户端需要通过文件或链接导入" }
        val now = System.nanoTime()
        entries.entries.removeIf { it.value.deadlineNanos <= now }
        if (entries.size >= 24) entries.remove(entries.keys.first())
        val defaults = subscriptionHeaders(AppSnapshot(settings = AppSettings(configurationName = config.fileName.substringBeforeLast('.'))))
        entries[token] = Entry(config, now + TimeUnit.MILLISECONDS.toNanos(lifetimeMs), defaults + headers)
        scheduleExpiry()
        return DirectImportHandle(url, schemes, expiration, config.fileName, config.mimeType)
    }

    @Synchronized
    private fun handle(request: Request): Response {
        val port = listener?.port ?: return Response(404)
        val token = Regex("/import/([A-Za-z0-9_-]+)").matchEntire(request.uri.rawPath)?.groupValues?.get(1)
        val entry = token?.let(entries::get)?.takeIf { it.deadlineNanos > System.nanoTime() }
        if (entry == null || request.headers["host"] != "127.0.0.1:$port") return Response(404)
        return response(entry.configuration, request.acceptsAjson, entry.headers)
    }

    private fun scheduleExpiry() {
        timer?.cancel(false)
        val next = entries.values.minOfOrNull { it.deadlineNanos }
        if (next == null) {
            listener?.close()
            listener = null
            return
        }
        timer = scheduler.schedule(::expire, (next - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
    }

    @Synchronized
    private fun expire() {
        val now = System.nanoTime()
        entries.entries.removeIf { it.value.deadlineNanos <= now }
        scheduleExpiry()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        timer?.cancel(false)
        listener?.close()
        listener = null
    }

    @Synchronized
    override fun close() {
        clear()
        scheduler.shutdownNow()
    }

    private fun schemes(config: GeneratedConfiguration, url: String, profileTitle: String?): List<String> {
        val encoded = percentEncode(url)
        val title = percentEncode(profileTitle?.removePrefix("base64:")?.let(::base64Decode) ?: "Tower")
        val nodes = config.contentMode == ExportContentMode.NODES_ONLY
        val specific = when (config.target) {
            ClientTarget.SURGE_MAC -> listOf("surgeconfig:///install-config?url=$encoded")
            ClientTarget.CLASH_APPLE -> listOf("clashmeta://install-config?url=$encoded")
            ClientTarget.CLASH_VERGE -> listOf("clash-verge://install-config?url=$encoded")
            ClientTarget.MIHOMO_PARTY -> listOf("mihomo://install-config?url=$encoded&name=$title")
            ClientTarget.CLASH_MI -> listOf("clashmi://install-config?url=$encoded&name=$title")
            ClientTarget.KARING -> listOf("karing://install-config?url=$encoded&name=$title")
            ClientTarget.SHADOWROCKET -> listOf(if (nodes) "shadowrocket://add/$url#$title" else "shadowrocket://config/add/$url")
            ClientTarget.LOON -> listOf(if (nodes) "loon://import?nodelist=$encoded" else "loon://import?sub=$encoded")
            ClientTarget.EGERN -> listOf("egern:/profiles/new?url=$encoded&name=$title")
            ClientTarget.HIDDIFY -> listOf("hiddify://import/$url")
            ClientTarget.SING_BOX -> listOf("sing-box://import-remote-profile?url=$encoded#$title")
            ClientTarget.ANYWHERE -> listOf("anywhere://add-proxy?link=$encoded")
            ClientTarget.QUANX -> if (!nodes) emptyList() else listOf("quantumult-x:///add-resource?remote-resource=" +
                percentEncode(mapOf("server_remote" to listOf("$url, tag=Tower, as-policy=static")).jsonValue().toString()))
            else -> emptyList()
        }
        return (specific + ClientCatalog.meta(config.target).urlSchemeTemplates.filter {
            config.target in setOf(ClientTarget.FLCLASH, ClientTarget.V2BOX, ClientTarget.SURFBOARD, ClientTarget.CLASH, ClientTarget.SURGE) ||
                (config.target == ClientTarget.CLASH_MI || config.target == ClientTarget.CLASH_APPLE) && !nodes
        }.map { it.replace("{url}", encoded) }).distinct()
    }
}

class LanSubscriptionServer(
    private val generate: (ClientTarget, ExportContentMode) -> GeneratedConfiguration,
    private val headers: () -> Map<String, String> = { emptyMap() },
    private val preferredPort: Int = 38420,
) : Closeable {
    @Volatile
    var token: String = accessToken()
        private set
    private var server: LocalHttpServer? = null

    @Synchronized
    fun start(): Int {
        val active = server ?: try {
            LocalHttpServer("0.0.0.0", preferredPort, ::handle)
        } catch (_: java.net.BindException) {
            LocalHttpServer("0.0.0.0", 0, ::handle)
        }.also { server = it }
        return active.port
    }

    @Synchronized
    fun rotateToken(): String = accessToken().also { token = it }

    private fun handle(request: Request): Response {
        val expected = token
        val supplied = Regex("/sub/([A-Za-z0-9_-]+)").matchEntire(request.uri.rawPath)?.groupValues?.get(1)
            ?: return Response(404)
        if (!MessageDigest.isEqual(expected.toByteArray(), supplied.toByteArray())) return Response(403)
        val query = try { queryFields(request.uri.rawQuery) } catch (_: IllegalArgumentException) { return Response(400) }
        val selected = target(query.string("target"), request.headers["user-agent"].orEmpty(), request.acceptsAjson)
            ?: return Response(400)
        val mode = query.string("content")?.let { value -> ExportContentMode.entries.firstOrNull { it.rawValue.equals(value, true) }
            ?: return Response(400) } ?: ExportContentMode.FULL_CONFIGURATION
        return response(generate(selected, mode), request.acceptsAjson, headers())
    }

    @Synchronized
    override fun close() {
        server?.close()
        server = null
        rotateToken()
    }

    companion object {
        internal fun target(choice: String?, userAgent: String, ajson: Boolean = false): ClientTarget? {
            if (choice != null && !choice.equals("auto", true)) {
                return when (choice.lowercase(Locale.ROOT)) {
                    "clash" -> ClientTarget.FLCLASH
                    "stash" -> ClientTarget.CLASH
                    "v2ray", "v2rayng", "v2rayn" -> ClientTarget.V2BOX
                    "clashmeta", "clash-meta" -> ClientTarget.CLASH_APPLE
                    else -> ClientTarget.fromRaw(choice)
                }
            }
            if (ajson) return ClientTarget.ANYWHERE
            val agents = listOf(
                "anywhere" to ClientTarget.ANYWHERE, "shadowrocket" to ClientTarget.SHADOWROCKET,
                "quantumult|quanx" to ClientTarget.QUANX, "hiddify" to ClientTarget.HIDDIFY,
                "sing-?box|\\bsfa\\b" to ClientTarget.SING_BOX, "stash" to ClientTarget.CLASH, "karing" to ClientTarget.KARING,
                "surfboard" to ClientTarget.SURFBOARD, "surge[ /_-]*mac" to ClientTarget.SURGE_MAC, "surge" to ClientTarget.SURGE,
                "loon" to ClientTarget.LOON, "egern" to ClientTarget.EGERN, "v2rayn|v2box" to ClientTarget.V2BOX,
                "clashmac" to ClientTarget.CLASHMAC, "clash.?verge" to ClientTarget.CLASH_VERGE, "flclash" to ClientTarget.FLCLASH,
                "clash.?party|mihomo.?party" to ClientTarget.MIHOMO_PARTY, "clash.?meta|nekobox" to ClientTarget.CLASH_APPLE,
                "clash|mihomo|nikki" to ClientTarget.CLASH_MI,
            )
            return agents.firstOrNull { Regex(it.first, RegexOption.IGNORE_CASE).containsMatchIn(userAgent) }?.second
        }
    }
}
