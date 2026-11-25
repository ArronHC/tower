package com.jzb.tower.core.service

import com.jzb.tower.core.model.SubscriptionRequestOptions
import com.jzb.tower.core.model.SubscriptionUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.TimeUnit

internal fun webURL(input: String, httpsOnly: Boolean = false): HttpUrl {
    val url = input.trim().toHttpUrlOrNull()
    require(url != null && url.username.isEmpty() && url.password.isEmpty() && (!httpsOnly || url.isHttps)) {
        if (httpsOnly) "请输入 HTTPS 链接" else "请输入 HTTP 或 HTTPS 链接"
    }
    return url
}

data class FetchedSubscription(val parsed: ParsedSubscription, val usage: SubscriptionUsage?, val title: String?)

class SubscriptionFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
) {
    private val network = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val dohNetwork = network.newBuilder().callTimeout(7, TimeUnit.SECONDS).dns(Dns.SYSTEM).build()

    suspend fun fetch(urlString: String, options: SubscriptionRequestOptions? = null, sourceID: String? = null): FetchedSubscription =
        runInterruptible(Dispatchers.IO) {
            val url = webURL(urlString)
            val http = options?.dnsOverHTTPSURL?.takeIf(String::isNotBlank)?.let {
                val endpoint = webURL(it, true)
                network.newBuilder().dns(object : Dns { override fun lookup(hostname: String) = this@SubscriptionFetcher.lookup(endpoint, hostname) }).build()
            } ?: network
            val userAgent = options?.userAgent?.let(::cleanLine)?.takeIf(String::isNotEmpty) ?: "Tower/1.0"
            val original = load(http, url, userAgent)
            val parsed = SubscriptionParser.parse(original.body, sourceID)
            val originalQuota = original.headers["subscription-userinfo"]?.let(SubscriptionParser::quotaFromHeader)
            var structured = originalQuota.merge(parsed.structuredUsage)
            val probe = if (structured?.hasPlanDetail == true) null else quotaProbe(url)
            if (probe != null) {
                val extra = try {
                    load(http, probe, userAgent, headersOnly = true).headers["subscription-userinfo"]
                        ?.let(SubscriptionParser::quotaFromHeader)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
                structured = structured.merge(extra)
            }
            val title = (original.headers["profile-title"] ?: original.headers["subscription-title"])?.trim()?.let {
                val decoded = if (it.startsWith("base64:", true)) base64Decode(it.substring(7)) else it
                decoded?.let { value -> runCatching { percentDecode(value) }.getOrDefault(value) }
            }?.let(::cleanLine)?.take(256)?.ifEmpty { null }
            FetchedSubscription(parsed, structured.merge(parsed.noticeUsage), title)
        }

    private fun quotaProbe(original: HttpUrl): HttpUrl? {
        val params = original.queryParameterNames.associateBy { it.lowercase(java.util.Locale.ROOT) }
        val wrapped = params["url"]?.takeIf { "target" in params }?.let(original::queryParameter)
            ?.takeIf { '|' !in it }?.let { runCatching { webURL(it) }.getOrNull() }
        val url = wrapped ?: original
        if (url.queryParameterNames.any { it.equals("flag", true) }) return wrapped
        return url.newBuilder().addQueryParameter("flag", "clash").build()
    }

    suspend fun fetchRuleText(url: String): String = runInterruptible(Dispatchers.IO) {
        load(network, webURL(url, true), "Tower/1.0", httpsOnly = true).body
    }

    private data class Document(val headers: Headers, val body: String)
    private fun load(http: OkHttpClient, start: HttpUrl, agent: String, headersOnly: Boolean = false, httpsOnly: Boolean = false): Document {
        var url = start
        repeat(6) {
            val request = Request.Builder().url(url).header("User-Agent", agent).build()
            http.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (response.code in setOf(301, 302, 303, 307, 308) && location != null) {
                    url = webURL(url.resolve(location)?.toString() ?: throw IOException("无效的重定向"), httpsOnly)
                } else {
                    if (!response.isSuccessful) throw IOException("服务器返回 HTTP ${response.code}")
                    val body = if (headersOnly) "" else String(readLimited(response.body, 16 * 1024 * 1024), Charsets.UTF_8)
                    return Document(response.headers, body)
                }
            }
        }
        throw IOException("重定向次数过多")
    }

    private fun readLimited(body: ResponseBody?, max: Int): ByteArray {
        if (body == null) return byteArrayOf()
        if (body.contentLength() > max) throw IOException("响应体过大")
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        body.byteStream().use { stream ->
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                if (output.size() > max - count) throw IOException("响应体过大")
                output.write(chunk, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun lookup(endpoint: HttpUrl, host: String): List<InetAddress> {
        val addresses = listOf(1, 28).flatMap { type ->
            try {
                val query = ByteArrayOutputStream()
                query.write(byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                for (label in host.trimEnd('.').split('.')) {
                    val bytes = label.toByteArray(Charsets.US_ASCII)
                    require(bytes.size in 1..63)
                    query.write(bytes.size)
                    query.write(bytes)
                }
                query.write(byteArrayOf(0, 0, type.toByte(), 0, 1))
                val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(query.toByteArray())
                val url = endpoint.newBuilder().setQueryParameter("dns", encoded).build()
                val request = Request.Builder().url(url).header("Accept", "application/dns-message").build()
                dohNetwork.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("DNS over HTTPS failed")
                    decodeDns(readLimited(response.body, 65535), type)
                }
            } catch (_: IOException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            } catch (_: java.nio.BufferUnderflowException) {
                emptyList()
            }
        }
        if (addresses.isEmpty()) throw UnknownHostException("DNS over HTTPS returned no address")
        return addresses
    }

    private fun decodeDns(bytes: ByteArray, type: Int): List<InetAddress> = buildList {
        val buffer = ByteBuffer.wrap(bytes)
        fun short() = buffer.short.toInt() and 65535
        fun skipName() {
            while (true) {
                val length = buffer.get().toInt() and 255
                if (length == 0) return
                if (length and 192 == 192) { buffer.get(); return }
                require(length <= 63)
                buffer.position(buffer.position() + length)
            }
        }
        require(short() == 0 && short() and 0x820f == 0x8000)
        val questions = short()
        val answers = short()
        short()
        short()
        repeat(questions) { skipName(); short(); short() }
        repeat(answers) {
            skipName()
            val recordType = short()
            val recordClass = short()
            buffer.int
            val length = short()
            if (recordClass == 1 && recordType == type && length == if (type == 1) 4 else 16) {
                val address = ByteArray(length)
                buffer.get(address)
                add(InetAddress.getByAddress(address))
            } else buffer.position(buffer.position() + length)
        }
    }
}
