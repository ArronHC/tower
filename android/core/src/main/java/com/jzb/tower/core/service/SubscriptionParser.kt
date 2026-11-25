package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.util.regex.Pattern
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import java.util.UUID

internal typealias Fields = Map<String, Any?>
internal fun Any?.fields(): Fields = (this as? Map<*, *>)?.entries
    ?.mapNotNull { (key, value) -> (key as? String)?.let { it.lowercase(Locale.ROOT) to value } }?.toMap().orEmpty()
internal fun Any?.text(): String? = when (this) {
    is String -> takeUnless(String::isEmpty)
    is Number, is Boolean -> toString()
    else -> null
}
internal fun Any?.strings(): List<String> = if (this is List<*>) mapNotNull { it.text() }
    else text()?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
internal fun Any?.integer(): Int? = text()?.toIntOrNull()
internal fun Any?.truth(): Boolean = text()?.lowercase(Locale.ROOT) in setOf("1", "true", "yes", "on")
internal fun Fields.value(vararg keys: String): Any? = keys.firstNotNullOfOrNull { this[it] }
internal fun Fields.string(vararg keys: String): String? = value(*keys).text()
internal fun Fields.int(vararg keys: String): Int? = value(*keys).integer()
internal fun Fields.flag(vararg keys: String): Boolean = value(*keys).truth()
internal fun Fields.list(vararg keys: String): List<String> = value(*keys).strings()
internal fun cleanLine(value: String): String = value.replace(Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]"), " ").trim()
internal fun percentDecode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
internal fun percentEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
internal fun base64Decode(value: String): String? {
    val input = value.filterNot(Char::isWhitespace).replace('-', '+').replace('_', '/')
    if (input.isEmpty()) return null
    return runCatching {
        Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(input))).toString()
    }.getOrNull()
}
internal fun base64Url(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
internal fun queryFields(query: String?): Fields = query.orEmpty().split('&').filter(String::isNotEmpty).associate {
    percentDecode(it.substringBefore('=')).lowercase(Locale.ROOT) to percentDecode(it.substringAfter('=', ""))
}

internal fun splitFields(line: String, separator: Char = ','): List<String> = buildList {
    var start = 0
    var depth = 0
    var quote: Char? = null
    var escaped = false
    line.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            quote != null -> if (char == quote) quote = null
            (char == '"' || char == '\'') && (index == start || line[index - 1] == '=' || line[index - 1].isWhitespace()) -> quote = char
            char == '(' -> depth++
            char == ')' -> depth--
            char == separator && depth == 0 -> {
                add(line.substring(start, index).trim())
                start = index + 1
            }
        }
    }
    add(line.substring(start).trim())
}

internal fun unquote(value: String): String {
    if (value.length < 2) return value
    return when {
        value.first() == '"' && value.last() == '"' -> runCatching {
            kotlinx.serialization.json.Json.decodeFromString<String>(value)
        }.getOrDefault(value.removeSurrounding("\""))
        value.first() == '\'' && value.last() == '\'' -> value.removeSurrounding("'")
        else -> value
    }
}

internal fun keyValues(lines: Iterable<String>): Fields = lines.filter { '=' in it }.associate {
    it.substringBefore('=').trim().lowercase(Locale.ROOT) to unquote(it.substringAfter('=').trim())
}

internal fun iniSections(text: String): Map<String, List<String>> {
    val sections = linkedMapOf<String, MutableList<String>>()
    var section = ""
    text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') &&
        !it.startsWith(';') && !it.startsWith("//") }.forEach { line ->
        if (line.startsWith('[') && line.endsWith(']') && ']' !in line.dropLast(1)) {
            section = line.removeSurrounding("[", "]").trim().lowercase(Locale.ROOT)
        } else sections.getOrPut(section) { mutableListOf() }.add(line)
    }
    return sections
}

internal fun yamlObject(text: String): Fields {
    val options = LoaderOptions().apply {
        isAllowDuplicateKeys = false
        maxAliasesForCollections = 32
        codePointLimit = 16 * 1024 * 1024
        nestingDepthLimit = 48
    }
    val dumping = DumperOptions()
    return Yaml(SafeConstructor(options), Representer(dumping), dumping, options, ProfileYamlResolver()).load<Any?>(text).fields()
}

private class ProfileYamlResolver : Resolver() {
    override fun addImplicitResolvers() {
        addImplicitResolver(Tag.BOOL, Pattern.compile("true|false|True|False|TRUE|FALSE"), "tTfF")
        addImplicitResolver(Tag.INT, Pattern.compile("[-+]?(?:0|[1-9][0-9]*)"), "-+0123456789")
        addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.")
        addImplicitResolver(Tag.NULL, NULL, "~nN\u0000")
        addImplicitResolver(Tag.NULL, EMPTY, null)
        addImplicitResolver(Tag.MERGE, MERGE, "<")
    }
}

internal fun proxyKind(value: String): ProxyKind = ProxyKind.fromRaw(when (val type = value.lowercase(Locale.ROOT)) {
    "shadowsocks" -> "ss"
    "shadowsocksr" -> "ssr"
    "hy" -> "hysteria"
    "hy2" -> "hysteria2"
    "wg" -> "wireguard"
    "socks", "socks5-tls" -> "socks5"
    "https" -> "http"
    "tuic-v5" -> "tuic"
    "trust-tunnel" -> "trusttunnel"
    else -> type
})

internal fun transport(value: String?): String? = when (val type = value?.lowercase(Locale.ROOT)) {
    null, "", "none", "tcp", "raw" -> null
    "websocket" -> "ws"
    "http2" -> "h2"
    "http-upgrade" -> "httpupgrade"
    "splithttp" -> "xhttp"
    else -> type
}

data class ParsedSubscription(
    val nodes: List<ProxyNode>,
    val skippedReasons: List<SkippedReasonCount>,
    val structuredUsage: SubscriptionUsage?,
    val noticeUsage: SubscriptionUsage?,
) {
    val skippedCount: Int get() = skippedReasons.sumOf { it.count }
    val usage: SubscriptionUsage? get() = structuredUsage.merge(noticeUsage)
}

internal fun SubscriptionUsage?.merge(lower: SubscriptionUsage?): SubscriptionUsage? {
    if (this == null) return lower
    if (lower == null) return this
    return SubscriptionUsage(uploadBytes ?: lower.uploadBytes, downloadBytes ?: lower.downloadBytes,
        totalBytes ?: lower.totalBytes, expiresAtEpochMs ?: lower.expiresAtEpochMs, (notices + lower.notices).distinct())
}

object SubscriptionParser {
    private val uriLine = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)
    private val clash = Regex("(?m)^\\s*proxies\\s*:")
    private val notice = Regex("剩余流量|已用流量|总流量|流量重置|距离下次重置|重置剩余|套餐到期|到期时间|过期时间|有效期至|账户余额|官网|续费|客服|订阅地址|机场地址|\\b(?:expires?|traffic|remaining|reset)\\b", RegexOption.IGNORE_CASE)
    private val placeholderHosts = setOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "223.5.5.5",
        "127.0.0.1", "0.0.0.0", "localhost", "example.com")
    private val alwaysTLS = setOf(ProxyKind.TROJAN, ProxyKind.HYSTERIA, ProxyKind.HYSTERIA2, ProxyKind.TUIC,
        ProxyKind.MASQUE, ProxyKind.ANYTLS, ProxyKind.TRUSTTUNNEL)
    private val amnezia = Regex("(?i)^(?:jc|jmin|jmax|s[1-4]|h[1-4]|i[1-5]|amnezia(?:-wg)?|awg)$")
    private class InvalidNode(message: String) : IllegalArgumentException(message)
    private fun invalid(message: String): Nothing = throw InvalidNode(message)

    fun isMetadata(node: ProxyNode): Boolean = node.isSubscriptionMetadata == true || notice.containsMatchIn(node.name) ||
        node.name.startsWith("STATUS=", true) || (node.sourceID != null && node.server.lowercase(Locale.ROOT) in placeholderHosts)

    internal fun bodyText(input: String): String {
        val text = input.trim().removePrefix("\uFEFF")
        val decoded = base64Decode(text)?.trim()?.removePrefix("\uFEFF")
        return decoded?.takeIf { body ->
            clash.containsMatchIn(body) || body.lineSequence().any { uriLine.containsMatchIn(it.trim()) ||
                it.trim().startsWith("STATUS=", true) || it.trim().equals("[Proxy]", true) }
        } ?: text
    }

    fun parse(input: String, sourceID: String? = null): ParsedSubscription {
        val content = bodyText(input)
        val reasons = linkedMapOf<String, Int>()
        val nodes = mutableListOf<ProxyNode>()
        val notices = mutableListOf<String>()
        var structured: SubscriptionUsage? = null
        fun add(parse: () -> ProxyNode?) {
            try {
                val node = parse() ?: return
                val metadata = isMetadata(node)
                if (metadata) {
                    notices.add(node.name)
                    if (node.name.startsWith("STATUS=", true)) structured = structured ?: quotaFromStatus(node.name.substringAfter('='))
                }
                nodes.add(validate(node).copy(isSubscriptionMetadata = if (metadata) true else null))
            } catch (e: InvalidNode) {
                val reason = e.message.orEmpty()
                reasons[reason] = reasons.getOrDefault(reason, 0) + 1
            } catch (_: Exception) {
                val reason = "无效的节点参数"
                reasons[reason] = reasons.getOrDefault(reason, 0) + 1
            }
        }
        content.lineSequence().map(String::trim).forEach { line ->
            if (line.startsWith("STATUS=", true)) structured = structured ?: quotaFromStatus(line.substringAfter('='))
        }
        val sections = iniSections(content)
        when {
            clash.containsMatchIn(content) -> {
                val cleaned = content.lineSequence().filterNot { it.trim().startsWith("STATUS=", true) }.joinToString("\n")
                val proxies = yamlObject(cleaned)["proxies"] as? List<*> ?: emptyList<Any>()
                proxies.forEach { proxy -> add { fromFields(proxy.fields(), "", sourceID) } }
            }
            "proxy" in sections || Regex("(?im)^\\s*\\[proxy]\\s*$").containsMatchIn(content) ->
                sections["proxy"].orEmpty().forEach { line -> add { fromSurge(line, sections, sourceID) } }
            else -> content.lineSequence().map(String::trim).forEach { line ->
                when {
                    uriLine.containsMatchIn(line) -> add { fromUri(line, sourceID) }
                    line.isNotEmpty() && '=' in line && ',' in line && !line.startsWith("STATUS=", true) &&
                        !line.startsWith('#') && !line.startsWith(';') && !line.startsWith("//") ->
                        add { fromSurge(line, sections, sourceID) }
                    notice.containsMatchIn(line) && !line.startsWith("STATUS=", true) -> notices.add(cleanLine(line))
                }
            }
        }
        return ParsedSubscription(nodes, reasons.map { SkippedReasonCount(it.key, it.value) }, structured,
            quotaFromNotices(notices.distinct()))
    }

    fun parseProxyURI(raw: String, sourceID: String? = null): ProxyNode? = runCatching {
        val text = raw.trim()
        val node = if (uriLine.containsMatchIn(text)) fromUri(text, sourceID) else fromSurge(text, emptyMap(), sourceID)
        node?.let(::validate)
    }.getOrNull()

    private fun node(kind: ProxyKind, host: String?, port: Int?, name: String?, raw: String, source: String?): ProxyNode {
        val server = host.orEmpty().trim().removeSurrounding("[", "]")
        val title = cleanLine(name.orEmpty()).ifEmpty { server }
        return ProxyNode(UUID.randomUUID().toString(), source, kind, name = title, server = server, port = port ?: 0, rawURI = raw)
    }

    internal fun validate(node: ProxyNode): ProxyNode {
        if (node.kind == ProxyKind.UNKNOWN) invalid("不支持的代理协议")
        if (node.port !in 1..65535 || node.server.startsWith('-') ||
            (!node.server.matches(Regex("[\\p{L}\\p{N}_.-]+")) && literalIP(node.server) == null)) invalid("无效的服务器地址或端口")
        if (node.kind in setOf(ProxyKind.SS, ProxyKind.SSR) && (node.cipher.isNullOrBlank() || node.password.isNullOrEmpty()))
            invalid("缺少加密方式或密码")
        if (node.kind == ProxyKind.SSR && (node.protocolName.isNullOrBlank() || node.obfs.isNullOrBlank())) invalid("缺少 SSR 协议或混淆")
        if (node.kind in setOf(ProxyKind.VMESS, ProxyKind.VLESS, ProxyKind.TUIC)) {
            val id = node.uuid.orEmpty()
            val fullUuid = Regex("(?i)[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}").matches(id)
            if (!fullUuid && (node.kind == ProxyKind.TUIC || id.isBlank() || id.toByteArray(Charsets.UTF_8).size >= 32))
                invalid("无效或缺少代理 ID")
        }
        if (node.realityPublicKey != null && !node.tls) invalid("REALITY 需要 TLS")
        if (node.kind in setOf(ProxyKind.TROJAN, ProxyKind.HYSTERIA2, ProxyKind.TUIC, ProxyKind.ANYTLS, ProxyKind.SNELL,
                ProxyKind.TRUSTTUNNEL) && node.password.isNullOrEmpty()) invalid("缺少代理密码")
        if (node.kind == ProxyKind.SNELL && (node.version ?: 4) !in 1..6) invalid("无效的 Snell 版本")
        if (node.transport != null && node.transport !in setOf("ws", "http", "h2", "grpc", "httpupgrade", "xhttp", "quic", "kcp"))
            invalid("不支持的代理传输")
        if (node.kind == ProxyKind.WIREGUARD && (node.wireGuardPrivateKey.isNullOrEmpty() || node.wireGuardPublicKey.isNullOrEmpty() ||
                (node.wireGuardIPv4.isNullOrEmpty() && node.wireGuardIPv6.isNullOrEmpty()))) invalid("缺少 WireGuard 密钥或地址")
        if (node.kind == ProxyKind.MASQUE) {
            val options = node.masque ?: invalid("缺少 MASQUE 参数")
            if (options.mode == "keyConnectIP") {
                if (options.privateKey.isNullOrEmpty() || options.publicKey.isNullOrEmpty() ||
                    (options.ipv4.isNullOrEmpty() && options.ipv6.isNullOrEmpty())) invalid("缺少 MASQUE 密钥或地址")
            } else if (node.username.isNullOrEmpty() || node.password.isNullOrEmpty()) invalid("缺少 MASQUE 登录凭据")
        }
        if (node.kind == ProxyKind.SSH && (node.username.isNullOrBlank() ||
                (node.password.isNullOrEmpty() && node.ssh?.privateKey.isNullOrEmpty()))) invalid("缺少 SSH 凭据")
        if (node.kind == ProxyKind.TRUSTTUNNEL && node.username.isNullOrBlank()) invalid("缺少 TrustTunnel 用户名")
        if (listOfNotNull(node.alterID, node.idleSessionCheckInterval, node.idleSessionTimeout, node.minIdleSession,
                node.wireGuardMTU, node.wireGuardPersistentKeepalive, node.masque?.mtu, node.masque?.httpVersion).any { it < 0 })
            invalid("无效的节点数字参数")
        return node
    }

    private fun withPlugin(node: ProxyNode, type: String, fields: Fields): ProxyNode {
        if (node.kind != ProxyKind.SS && !(node.kind == ProxyKind.SNELL && type == "shadow-tls"))
            invalid("不支持的 SIP003 插件")
        val normalized = type.lowercase(Locale.ROOT)
        val host = fields.string("obfs-host", "host", "sni")
        val skip = fields.flag("skip-cert-verify")
        return when (normalized) {
            "obfs", "obfs-local", "simple-obfs" -> {
                val mode = fields.string("obfs", "mode")
                if (mode !in setOf("http", "tls") || fields.keys.any { it !in setOf("obfs", "mode", "obfs-host", "host") })
                    invalid("不支持的 Shadowsocks SIP003 插件选项")
                node.copy(plugin = "obfs", obfs = mode, hostHeader = host)
            }
            "v2ray-plugin" -> {
                if (fields.string("mode") !in setOf(null, "websocket") ||
                    fields.keys.any { it !in setOf("mode", "host", "path", "tls", "mux", "skip-cert-verify") })
                    invalid("不支持的 Shadowsocks SIP003 插件选项")
                node.copy(plugin = normalized, transport = "ws", tls = fields.flag("tls"), hostHeader = host,
                    path = fields.string("path"), pluginMux = fields["mux"]?.truth(), skipCertificateVerification = skip)
            }
            "shadow-tls" -> {
                val version = fields.int("version") ?: 2
                val password = fields.string("password")
                if (version !in 1..3 || host.isNullOrEmpty() || password.isNullOrEmpty() ||
                    fields.keys.any { it !in setOf("version", "host", "sni", "password", "skip-cert-verify") })
                    invalid("无效的 ShadowTLS 参数")
                node.copy(plugin = normalized, shadowTLS = ShadowTlsOptions(version, host, password, skip))
            }
            else -> invalid("不支持的 Shadowsocks SIP003 插件")
        }
    }

    private fun fromUri(raw: String, source: String?): ProxyNode {
        val type = raw.substringBefore("://").lowercase(Locale.ROOT)
        val kind = proxyKind(type)
        if (kind == ProxyKind.UNKNOWN) invalid("不支持的代理协议")
        val body = raw.substringAfter("://")
        val link = body.substringBefore('#')
        val label = body.substringAfter('#', "").takeIf(String::isNotBlank)?.let(::percentDecode)
        if (kind == ProxyKind.SS) {
            val encoded = link.substringBefore('?').trimEnd('/')
            val authority = if ('@' in encoded) encoded else base64Decode(percentDecode(encoded)) ?: invalid("无效的 Shadowsocks 链接")
            val user = percentDecode(authority.substringBeforeLast('@'))
            val credentials = if (':' in user) user else base64Decode(user) ?: invalid("无效的 Shadowsocks 凭据")
            if ('@' !in authority || ':' !in credentials) invalid("无效的 Shadowsocks 链接")
            val endpoint = authority.substringAfterLast('@').trimEnd('/')
            val result = node(kind, endpoint.substringBeforeLast(':'), endpoint.substringAfterLast(':').toIntOrNull(), label, raw, source)
                .copy(cipher = credentials.substringBefore(':').lowercase(Locale.ROOT), password = credentials.substringAfter(':'))
            val plugin = queryFields(link.substringAfter('?', "")).string("plugin") ?: return result
            val parts = Regex("(?<!\\\\);").split(plugin)
            val fields = parts.drop(1).associate { part ->
                part.substringBefore('=') to part.substringAfter('=', "true").replace(Regex("\\\\([;=\\\\])"), "$1")
            }
            return withPlugin(result, parts.first(), fields)
        }
        if (kind == ProxyKind.SSR) {
            val value = base64Decode(link) ?: invalid("无效的 ShadowsocksR 链接")
            val match = Regex("^(.+):(\\d+):([^:]+):([^:]+):([^:]+):([^/?]+)(?:/\\?(.*))?$").matchEntire(value)
                ?: invalid("无效的 ShadowsocksR 链接")
            val (host, port, protocol, cipher, obfs, password, query) = match.destructured
            val fields = queryFields(query)
            fun decoded(key: String) = fields.string(key)?.let(::base64Decode)
            return node(kind, host, port.toIntOrNull(), decoded("remarks") ?: label, raw, source).copy(
                protocolName = protocol, cipher = cipher, obfs = obfs, password = base64Decode(password),
                protocolParam = decoded("protoparam"), obfsParam = decoded("obfsparam"))
        }
        if (kind == ProxyKind.VMESS && '@' !in link.substringBefore('?')) {
            val decoded = base64Decode(link.substringBefore('?'))
            if (decoded?.trimStart()?.startsWith('{') == true) {
                val fields = yamlObject(decoded)
                return node(kind, fields.string("add"), fields.int("port"), fields.string("ps") ?: label, raw, source).copy(
                    uuid = fields.string("id"), cipher = fields.string("scy"), alterID = fields.int("aid"),
                    transport = transport(if (fields.string("type") == "http" && fields.string("net") == "tcp") "http" else fields.string("net")),
                    tls = fields.string("tls") == "tls" || fields.flag("tls"), sni = fields.string("sni"), hostHeader = fields.string("host"),
                    path = fields.string("path"), alpn = fields.string("alpn"), fingerprint = fields.string("fp"),
                    skipCertificateVerification = fields.flag("allowinsecure"))
            }
        }
        val hoppingPattern = Regex(":(\\d+(?:[-,]\\d+)+)(?=[/?]|$)")
        val hopping = if (kind == ProxyKind.HYSTERIA2) hoppingPattern.find(link)?.groupValues?.get(1) else null
        var authority = if (hopping != null) link.replaceFirst(hoppingPattern, ":" + hopping.substringBefore('-').substringBefore(',')) else link
        if (kind in setOf(ProxyKind.VMESS, ProxyKind.VLESS) && '@' !in authority.substringBefore('?')) {
            val decoded = base64Decode(authority.substringBefore('?'))
            if (decoded != null && '@' in decoded) {
                val credentials = decoded.substringBeforeLast('@')
                val cipher = credentials.substringBefore(':', "")
                if (kind == ProxyKind.VLESS && cipher.lowercase(Locale.ROOT) !in setOf("", "none", "auto")) invalid("不支持的 VLESS 加密")
                val legacy = queryFields(authority.substringAfter('?', "")).toMutableMap()
                val obfs = legacy.string("obfs")
                if (obfs != null) legacy.putIfAbsent("type", if (obfs == "wss") "ws" else obfs)
                if (obfs == "wss") legacy.putIfAbsent("tls", "1")
                legacy.string("obfsparam")?.let { legacy.putIfAbsent("host", it) }
                if (cipher.isNotEmpty()) legacy.putIfAbsent("cipher", cipher)
                authority = percentEncode(credentials.substringAfter(':')) + "@" + decoded.substringAfterLast('@') +
                    "?" + legacy.entries.joinToString("&") { percentEncode(it.key) + "=" + percentEncode(it.value.toString()) }
            }
        }
        val url = URI("$type://$authority")
        val query = queryFields(url.rawQuery)
        if (query.string("plugin") != null) invalid("不支持的 URI 插件")
        val rawAuthority = url.rawAuthority ?: invalid("无效的服务器地址")
        val userInfo = rawAuthority.substringBeforeLast('@', "")
        val user = percentDecode(userInfo.substringBefore(':')).takeIf(String::isNotEmpty)
        val secret = percentDecode(userInfo.substringAfter(':', "")).takeIf(String::isNotEmpty)
        val endpoint = rawAuthority.substringAfterLast('@')
        val address = Regex("(\\[[^]\\s]+]|[^:\\s]+)(?::([0-9]+))?").matchEntire(endpoint)
            ?: invalid("无效的服务器地址或端口")
        val host = percentDecode(address.groupValues[1])
        val explicitPort = address.groupValues[2]
        val port = if (explicitPort.isNotEmpty()) explicitPort.toIntOrNull() ?: 0 else when (kind) {
            ProxyKind.HTTP -> if (type == "https") 443 else 80
            ProxyKind.SOCKS5 -> 1080
            ProxyKind.WIREGUARD -> 51820
            ProxyKind.SSH -> 22
            else -> null
        }
        val security = query.string("security", "tls")?.lowercase(Locale.ROOT)
        val reality = query.string("pbk", "reality-public-key")
        if (security == "reality" && reality == null) invalid("缺少 REALITY 公钥")
        val network = transport(if (query.string("type") == "tcp" && query.string("headertype") == "http") "http" else query.string("type", "network"))
        if (network == "xhttp" && query.string("extra")?.trim() !in setOf(null, "", "{}", "null")) invalid("不支持的 XHTTP 扩展")
        val uuidKinds = setOf(ProxyKind.VMESS, ProxyKind.VLESS, ProxyKind.TUIC)
        val password = when (kind) {
            ProxyKind.TROJAN, ProxyKind.HYSTERIA2, ProxyKind.ANYTLS, ProxyKind.SNELL -> user ?: query.string("password", "psk")
            ProxyKind.HYSTERIA -> user ?: query.string("auth", "auth_str", "authstr")
            else -> secret ?: query.string("password")
        }
        val addresses = query.list("address", "addresses")
        val base = node(kind, host, port, label ?: query.string("remarks", "remark", "name", "tag"), raw, source).copy(
            username = if (kind in setOf(ProxyKind.SOCKS5, ProxyKind.HTTP, ProxyKind.SSH, ProxyKind.MASQUE, ProxyKind.TRUSTTUNNEL))
                user ?: query.string("username") else null,
            password = password, uuid = if (kind in uuidKinds) user ?: query.string("uuid") else null,
            cipher = query.string("cipher", "scy"), alterID = query.int("alterid", "aid"), flow = query.string("flow"),
            vlessEncryption = query.string("encryption")?.takeUnless { it == "none" }, transport = if (kind == ProxyKind.MASQUE) null else network,
            transportMode = if (network == "xhttp") query.string("mode") else null,
            tls = reality != null || type == "https" || type == "socks5-tls" || kind in alwaysTLS || security in setOf("tls", "true", "1", "xtls"),
            sni = query.string("sni", "servername", "peer"), hostHeader = query.string("host", "authority"),
            path = if (network == "grpc") query.string("servicename", "service_name", "path") else query.string("path"),
            alpn = query.filterKeys { it == "alpn" || it.startsWith("alpn[") }.values.flatMap { it.strings() }.joinToString(",").ifEmpty { null },
            realityPublicKey = reality, realityShortID = query.string("sid", "reality-short-id"),
            realitySupportsX25519MLKEM768 = query["support-x25519mlkem768"]?.truth(),
            fingerprint = query.string("fp", "client-fingerprint", "client_fingerprint"),
            certificateFingerprint = query.string("pinsha256", "pin-sha256", "tls-fingerprint", "pcs"),
            skipCertificateVerification = query.flag("allowinsecure", "allow_insecure", "insecure", "skip-cert-verify"),
            udpRelayMode = query.string("udp_relay_mode", "udp-relay-mode"), udpRelayEnabled = query.value("udp", "udp-relay")?.truth(),
            congestionControl = query.string("congestion_control", "congestion-controller"),
            portHopping = hopping ?: query.string("mport", "ports", "server-ports", "port-hopping"),
            upMbps = query.int("upmbps", "up"), downMbps = query.int("downmbps", "down"),
            protocolName = query.string("protocol"), obfs = if (kind == ProxyKind.HYSTERIA) query.string("obfsparam", "obfs-password", "obfs") else query.string("obfs"),
            obfsParam = query.string("obfs-password", "obfspassword", "obfs_password", "obfs-host"),
            idleSessionCheckInterval = query.int("idle-session-check-interval", "idlesessioncheckinterval", "ici"),
            idleSessionTimeout = query.int("idle-session-timeout", "idlesessiontimeout", "it"),
            minIdleSession = query.int("min-idle-session", "minidlesession", "mis"),
            version = query.int("version"),
        )
        return when (kind) {
            ProxyKind.WIREGUARD -> {
                if (query.keys.any(amnezia::matches)) invalid("不支持的 AmneziaWG 参数")
                base.copy(wireGuardPrivateKey = user ?: query.string("private-key"), wireGuardPublicKey = query.string("publickey", "public-key"),
                    wireGuardPreSharedKey = query.string("presharedkey", "pre-shared-key", "preshared-key"),
                    wireGuardIPv4 = addresses.firstOrNull { ':' !in it } ?: query.string("ip", "ipv4"),
                    wireGuardIPv6 = addresses.firstOrNull { ':' in it } ?: query.string("ipv6"),
                    wireGuardAllowedIPs = query.string("allowedips", "allowed-ips"), wireGuardReserved = query.string("reserved"),
                    wireGuardMTU = query.int("mtu"), wireGuardPersistentKeepalive = query.int("keepalive", "persistent-keepalive"),
                    wireGuardDNS = query.string("dns", "dns-server"))
            }
            ProxyKind.SNELL -> {
                checkSnellMode(query.string("mode"))
                base.copy(version = base.version ?: 4)
            }
            ProxyKind.SSH -> base.copy(ssh = ssh(query))
            ProxyKind.TRUSTTUNNEL -> base.copy(trustTunnel = trustTunnel(query))
            ProxyKind.MASQUE -> base.copy(masque = masque(query))
            else -> base
        }
    }

    private fun checkSnellMode(mode: String?) {
        if (!mode.isNullOrBlank() && !mode.trim().equals("default", true)) invalid("不支持的 Snell 模式")
    }

    private fun ssh(fields: Fields) = SshOptions(fields.string("private-key"), fields.string("private-key-passphrase"),
        fields.list("host-key"), fields.list("host-key-algorithms"))
    private fun trustTunnel(fields: Fields) = TrustTunnelOptions(fields.flag("quic", "h3"), fields.string("client-random-auth-key"))
    private fun masque(fields: Fields) = MasqueOptions(
        mode = if (fields.string("private-key") != null) "keyConnectIP" else "basicConnectIP",
        privateKey = fields.string("private-key"), publicKey = fields.string("public-key"),
        ipv4 = fields.string("ip", "ipv4"), ipv6 = fields.string("ipv6"), network = fields.string("network"),
        mtu = fields.int("mtu"), dns = fields.list("dns", "dns-server").ifEmpty { null },
        remoteDNSResolve = fields["remote-dns-resolve"]?.truth(), httpVersion = fields.int("http-version"),
        disableVersionFallback = fields["disable-version-fallback"]?.truth(),
        headers = fields["headers"].fields().mapValues { it.value.toString() }.ifEmpty { null },
        certificate = fields.list("certificate").ifEmpty { null },
    )

    private fun fromFields(p: Fields, raw: String, source: String?): ProxyNode {
        val type = p.string("type").orEmpty().lowercase(Locale.ROOT)
        val kind = proxyKind(type)
        if (p.string("dialer-proxy", "underlying-proxy", "proxy-chain") != null ||
            p["smux"].fields().flag("enabled") || p["ech-opts"].fields().flag("enable") || p.flag("udp-over-tcp"))
            invalid("不支持的代理链或传输扩展")
        val ws = p["ws-opts"].fields()
        val reality = p["reality-opts"].fields()
        val xhttp = p["xhttp-opts"].fields()
        val grpc = p["grpc-opts"].fields()
        val http = p["http-opts"].fields()
        val h2 = p["h2-opts"].fields()
        val upgrade = p.value("http-upgrade-opts", "httpupgrade-opts").fields()
        val snellObfs = p["obfs-opts"].fields()
        val headers = listOf(ws, http, upgrade, xhttp).flatMap { it["headers"].fields().entries }.associate { it.key to it.value }
        if (headers.keys.any { it != "host" }) invalid("不支持的传输请求头")
        val network = if (ws.flag("v2ray-http-upgrade")) "httpupgrade" else transport(p.string("network"))
        if (network == "xhttp" && xhttp.keys.any { it !in setOf("host", "path", "mode", "headers") })
            invalid("不支持的 XHTTP 扩展")
        if (kind == ProxyKind.SNELL) checkSnellMode(p.string("mode"))
        val path = listOf(grpc.value("grpc-service-name"), ws.value("path"), http.value("path"), h2.value("path"),
            xhttp.value("path"), upgrade.value("path")).firstNotNullOfOrNull { it.strings().firstOrNull() }
        val early = ws.int("max-early-data")
        if (early != null && early > 0 && ws.string("early-data-header-name")?.lowercase(Locale.ROOT) !in
                setOf(null, "sec-websocket-protocol")) invalid("不支持的 WebSocket early-data 请求头")
        var result = node(kind, p.string("server"), p.int("port"), p.string("name"), raw, source).copy(
            cipher = p.string("cipher", "method", "encrypt-method"), password = p.string("password", "psk", "auth-str", "auth"),
            uuid = p.string("uuid"), username = p.string("username", "user"),
            transport = if (kind == ProxyKind.MASQUE) null else network, transportMode = xhttp.string("mode"),
            tls = kind in alwaysTLS || type in setOf("https", "socks5-tls") || p.flag("tls") || reality.string("public-key") != null,
            sni = p.string("servername", "sni"),
            hostHeader = listOf(headers["host"], ws["host"], h2["host"], xhttp["host"], grpc["authority"])
                .firstNotNullOfOrNull { it.strings().firstOrNull() },
            path = if (early != null && early > 0) (path ?: "/") + (if (path?.contains('?') == true) "&" else "?") + "ed=$early" else path,
            alpn = p.list("alpn").joinToString(",").ifEmpty { null }, realityPublicKey = reality.string("public-key"),
            realityShortID = reality.string("short-id"), realitySupportsX25519MLKEM768 = reality["support-x25519mlkem768"]?.truth(),
            certificateFingerprint = p.string("fingerprint", "tls-fingerprint"), fingerprint = p.string("client-fingerprint"),
            flow = p.string("flow"), vlessEncryption = p.string("encryption")?.takeUnless { it == "none" },
            skipCertificateVerification = p.flag("skip-cert-verify"), alterID = p.int("alterid"),
            protocolName = p.string("protocol"), protocolParam = p.string("protocol-param"), obfs = p.string("obfs") ?: snellObfs.string("mode"),
            obfsParam = p.string("obfs-param", "obfs-password", "obfs-host") ?: snellObfs.string("host"),
            idleSessionCheckInterval = p.int("idle-session-check-interval"), idleSessionTimeout = p.int("idle-session-timeout"),
            minIdleSession = p.int("min-idle-session"), version = p.int("version"),
            congestionControl = p.string("congestion-controller"), udpRelayMode = p.string("udp-relay-mode"),
            udpRelayEnabled = p.value("udp", "udp-relay")?.truth(), portHopping = p.list("ports", "server-ports").joinToString(",").ifEmpty { null },
            upMbps = p.int("up", "up-speed"), downMbps = p.int("down", "down-speed"),
        )
        p.string("plugin")?.let { result = withPlugin(result, it, p["plugin-opts"].fields()) }
        return when (kind) {
            ProxyKind.WIREGUARD -> {
                if (p.keys.any(amnezia::matches)) invalid("不支持的 AmneziaWG 参数")
                val peers = p["peers"] as? List<*>
                if ((peers?.size ?: 0) > 1) invalid("不支持多个 WireGuard 对端")
                val peer = p + peers?.firstOrNull().fields()
                result.copy(server = peer.string("server")?.removeSurrounding("[", "]") ?: result.server, port = peer.int("port") ?: result.port,
                    wireGuardPrivateKey = p.string("private-key"), wireGuardPublicKey = peer.string("public-key"),
                    wireGuardPreSharedKey = peer.string("pre-shared-key"), wireGuardIPv4 = p.string("ip"), wireGuardIPv6 = p.string("ipv6"),
                    wireGuardAllowedIPs = peer.list("allowed-ips").joinToString(",").ifEmpty { null },
                    wireGuardReserved = peer.list("reserved").joinToString(",").ifEmpty { null }, wireGuardMTU = p.int("mtu"),
                    wireGuardPersistentKeepalive = peer.int("persistent-keepalive"), wireGuardDNS = p.list("dns").joinToString(",").ifEmpty { null })
            }
            ProxyKind.SSH -> result.copy(ssh = ssh(p))
            ProxyKind.TRUSTTUNNEL -> result.copy(trustTunnel = trustTunnel(p))
            ProxyKind.MASQUE -> result.copy(masque = masque(p))
            else -> result
        }
    }

    private fun fromSurge(line: String, sections: Map<String, List<String>>, source: String?): ProxyNode? {
        if ('=' !in line) invalid("无效的 Surge 节点行")
        val fields = splitFields(line.substringAfter('='))
        val type = fields.first().lowercase(Locale.ROOT)
        if (type in setOf("direct", "reject", "reject-drop", "reject-no-drop", "reject-tinygif", "tailscale")) return null
        val kind = proxyKind(type)
        val name = line.substringBefore('=').trim()
        val rawOptions = keyValues(fields.drop(1).filter { it.matches(Regex("[A-Za-z0-9_-]+\\s*=.*")) })
        val credentialOptions = setOf("password", "psk", "username", "shadow-tls-password", "shadow-tls-sni")
        val options = rawOptions.mapValues { (key, value) ->
            if (key !in credentialOptions || kind == ProxyKind.MASQUE) value else value.text()?.let { text ->
                runCatching { percentDecode(text) }.getOrDefault(text)
            }
        }
        if (kind == ProxyKind.WIREGUARD && options.string("section-name") != null) {
            val entries = sections["wireguard " + options.string("section-name")!!.lowercase(Locale.ROOT)].orEmpty()
            if (entries.count { it.substringBefore('=').trim().equals("peer", true) } != 1) invalid("WireGuard 需要一个对端")
            val settings = keyValues(entries)
            val peer = keyValues(splitFields(settings.string("peer").orEmpty().removeSurrounding("(", ")")))
            val endpoint = peer.string("endpoint").orEmpty()
            return fromFields(mapOf("type" to type, "name" to name, "server" to endpoint.substringBeforeLast(':'),
                "port" to endpoint.substringAfterLast(':'), "private-key" to settings["private-key"], "public-key" to peer["public-key"],
                "pre-shared-key" to peer["preshared-key"], "ip" to settings["self-ip"], "ipv6" to settings["self-ip-v6"],
                "dns" to settings["dns-server"], "mtu" to settings["mtu"], "allowed-ips" to peer["allowed-ips"],
                "reserved" to peer.string("client-id")?.replace('/', ','), "persistent-keepalive" to peer["keepalive"]), line, source)
        }
        val positional = fields.drop(3).filter { !it.matches(Regex("[A-Za-z0-9_-]+\\s*=.*")) }.map(::unquote)
        val network = when {
            options.flag("ws-http-upgrade") -> "httpupgrade"
            options.flag("ws") -> "ws"
            options.flag("grpc") -> "grpc"
            options.flag("xhttp") -> "xhttp"
            else -> options.string("transport")
        }
        val wsHeader = options.string("ws-headers")
        if (wsHeader != null && !wsHeader.startsWith("Host:", true)) invalid("不支持的传输请求头")
        val normalized = options + mapOf(
            "type" to type, "name" to name, "server" to fields.getOrNull(1), "port" to fields.getOrNull(2),
            "uuid" to (options.string("uuid") ?: if (kind in setOf(ProxyKind.VMESS, ProxyKind.VLESS)) options["username"] else null),
            "username" to (options.string("username") ?: positional.getOrNull(0)),
            "password" to (options.value("password", "psk") ?: positional.getOrNull(1)),
            "network" to network, "ws-opts" to mapOf("path" to options["ws-path"], "host" to wsHeader?.substringAfter(':')?.trim()),
            "grpc-opts" to mapOf("grpc-service-name" to options["grpc-service-name"], "authority" to options["grpc-authority"]),
            "xhttp-opts" to mapOf("path" to options["xhttp-path"], "host" to options["xhttp-host"], "mode" to options["xhttp-mode"]),
            "reality-opts" to mapOf("public-key" to options["reality-public-key"], "short-id" to options["reality-short-id"],
                "support-x25519mlkem768" to options["support-x25519mlkem768"]),
            "server-ports" to options["port-hopping"], "client-fingerprint" to options["tls-fingerprint"],
            "tls-fingerprint" to options["server-cert-fingerprint-sha256"], "flow" to options.value("vless-flow", "flow"),
            "obfs" to if (options["salamander-password"] != null) "salamander" else options["obfs"],
            "obfs-password" to options.value("salamander-password", "obfs-password"),
        )
        var node = fromFields(normalized, line, source)
        if (kind in setOf(ProxyKind.VMESS, ProxyKind.VLESS)) node = node.copy(username = null)
        if (options.keys.any { it.startsWith("shadow-tls-") }) {
            val shadow = options.filterKeys { it.startsWith("shadow-tls-") }.mapKeys { it.key.removePrefix("shadow-tls-") }
            node = withPlugin(node, "shadow-tls", shadow)
        }
        if (kind == ProxyKind.SS && node.obfs != null) {
            if (node.obfs !in setOf("http", "tls", "ws", "wss")) invalid("不支持的 Shadowsocks 混淆")
            node = if (node.obfs in setOf("ws", "wss")) node.copy(transport = "ws", tls = node.obfs == "wss",
                obfs = null, hostHeader = options.string("obfs-host"), path = options.string("obfs-uri"))
            else node.copy(hostHeader = options.string("obfs-host"))
        }
        return node
    }

    fun quotaFromHeader(text: String): SubscriptionUsage? {
        val values = Regex("(?i)(?:^|[\\s,;&])([a-z_]+)\\s*=\\s*(\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)(?=$|[\\s,;&])")
            .findAll(text).associate { it.groupValues[1].lowercase(Locale.ROOT) to
                it.groupValues[2].toDoubleOrNull()?.takeIf(Double::isFinite)?.toLong() }
        val expiry = values["expire"]?.takeIf { it in 1..(Long.MAX_VALUE / 1000) }?.times(1000)
        val total = values["total"]
        if (total == 1L && expiry == null) return null
        return if (values["upload"] != null || values["download"] != null || total != null || expiry != null)
            SubscriptionUsage(values["upload"], values["download"], total, expiry) else null
    }

    private fun bytes(text: String): Long? {
        val result = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*([KMGTPE]i?B|B)\\b").find(text) ?: return null
        val power = "BKMGTPE".indexOf(result.groupValues[2].first().uppercaseChar())
        return (result.groupValues[1].toDouble() * Math.pow(1024.0, power.toDouble())).toLong()
    }

    private fun expiry(text: String): Long? {
        val date = Regex("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})(?:[ T](\\d{2}):(\\d{2})(?::(\\d{2}))?)?").find(text) ?: return null
        val parts = date.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
        return runCatching { LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
            .toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun quotaFromStatus(value: String): SubscriptionUsage? {
        quotaFromHeader(value)?.let { return it }
        fun field(label: String) = Regex("(?i)$label\\s*[:：]\\s*([\\d.]+\\s*[A-Za-z]+)").find(value)
            ?.groupValues?.get(1)?.let(::bytes)
        val result = SubscriptionUsage(field("↑"), field("↓"), field("TOT"), expiry(value))
        return result.takeIf { it.hasPlanDetail || it.usedBytes != null }
    }

    private fun quotaFromNotices(notices: List<String>): SubscriptionUsage? {
        if (notices.isEmpty()) return null
        fun first(regex: String) = notices.firstNotNullOfOrNull { line -> bytes(line).takeIf { Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(line) } }
        val total = first("总流量|total")
        val remaining = first("剩余流量|remaining")
        val download = first("已用流量|used") ?: if (total != null && remaining != null) (total - remaining).coerceAtLeast(0) else null
        return SubscriptionUsage(downloadBytes = download, totalBytes = total,
            expiresAtEpochMs = notices.firstNotNullOfOrNull { if (Regex("到期|过期|有效期至|expir", RegexOption.IGNORE_CASE)
                    .containsMatchIn(it)) expiry(it) else null }, notices = notices)
    }
}
