package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID

internal val surgeTargets = setOf(ClientTarget.SURGE, ClientTarget.SURGE_MAC, ClientTarget.SURFBOARD)
internal val mihomoTargets = setOf(ClientTarget.FLCLASH, ClientTarget.CLASH_APPLE, ClientTarget.CLASH_MI,
    ClientTarget.CLASH_VERGE, ClientTarget.MIHOMO_PARTY, ClientTarget.CLASHMAC)
internal fun confName(value: String): String = cleanLine(value)
    .replace('=', '-').replace(',', '，').replace('#', '＃').replace(';', '；')
    .replace("//", "／／").replace('[', '［').replace(']', '］')
internal fun quoted(value: String) = JsonPrimitive(value).toString()
internal fun compact(fields: Fields): Fields = fields.filterValues { it != null && it != "" }
internal fun fieldMap(vararg pairs: Pair<String, Any?>): Fields = compact(linkedMapOf(*pairs))
internal fun Any?.jsonValue(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.filter { it.value != null }.associate { it.key.toString() to it.value.jsonValue() })
    is Iterable<*> -> JsonArray(map { it.jsonValue() })
    else -> JsonPrimitive(toString())
}

internal fun endpoint(node: ProxyNode) = (if (':' in node.server) "[${node.server}]" else node.server) + ":" + node.port
private val uuidPattern = Regex("(?i)[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
private fun uuid(value: String): String {
    if (uuidPattern.matches(value)) return value.lowercase(Locale.ROOT)
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(ByteArray(16))
    val bytes = digest.digest(value.toByteArray())
    bytes[6] = ((bytes[6].toInt() and 15) or 80).toByte()
    bytes[8] = ((bytes[8].toInt() and 63) or 128).toByte()
    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long).toString()
}
internal fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun validCidr(text: String): Boolean {
    val address = literalIP(text.substringBefore('/')) ?: return false
    return (text.substringAfter('/', "").toIntOrNull() ?: return false) in 0..address.size * 8
}
private val v2Kinds = setOf(ProxyKind.VMESS, ProxyKind.VLESS, ProxyKind.TROJAN)
private val wsKinds = setOf("ws", "http", "h2", "httpupgrade")
private fun absolute(path: String?) = path?.let { if (it.startsWith('/')) it else "/$it" }
private val earlyPattern = Regex("([?&])ed=(\\d+)(&|$)")
private fun wsPath(path: String?) = absolute(path)?.replace(earlyPattern, "$1")?.trimEnd('?', '&')
private fun earlyData(path: String?) = path?.let { earlyPattern.find(it)?.groupValues?.get(2)?.toIntOrNull() }?.takeIf { it > 0 }
private fun sni(node: ProxyNode) = node.sni ?: node.hostHeader?.takeIf { node.tls && node.kind in v2Kinds && node.transport in wsKinds }
private fun simpleObfs(node: ProxyNode) = node.obfs?.takeIf { it in setOf("http", "tls") }
private fun wgAddresses(node: ProxyNode) = listOfNotNull(node.wireGuardIPv4, node.wireGuardIPv6)
    .map { if ('/' in it) it else it + if (':' in it) "/128" else "/32" }
private fun wgAllowed(node: ProxyNode) = (node.wireGuardAllowedIPs ?: "0.0.0.0/0,::/0").strings()
private fun reserved(node: ProxyNode) = node.wireGuardReserved?.removeSurrounding("[", "]")?.strings()
    ?.map { it.toIntOrNull() ?: -1 }
private fun confValue(value: Any?) = cleanLine(value.toString()).replace("%", "%25").replace(",", "%2C")
    .replace("#", "%23").replace(";", "%3B").replace("//", "%2F%2F")
private fun kv(fields: Fields) = compact(fields).map { (key, value) -> "$key=${confValue(value)}" }
private fun v2Plugin(n: ProxyNode) = n.plugin?.substringBefore(';') == "v2ray-plugin"

object NodeExport {
    private val ssLegacy = "rc4 rc4-md5 aes-128-cfb aes-192-cfb aes-256-cfb aes-128-ctr aes-192-ctr aes-256-ctr salsa20 chacha20 chacha20-ietf"
    private val ssExtra = "bf-cfb camellia-128-cfb camellia-192-cfb camellia-256-cfb cast5-cfb des-cfb idea-cfb rc2-cfb seed-cfb"
    private val ssAead = "aes-128-gcm aes-192-gcm aes-256-gcm chacha20-ietf-poly1305 xchacha20-ietf-poly1305"
    private val ss2022 = "2022-blake3-aes-128-gcm 2022-blake3-aes-256-gcm"
    private val restrictedSS = mapOf(
        ClientTarget.SURGE to "$ssLegacy $ssAead $ss2022 none", ClientTarget.SURGE_MAC to "$ssLegacy $ssAead $ss2022 none",
        ClientTarget.SURFBOARD to "$ssLegacy $ssAead $ss2022 none bf-cfb camellia-128-cfb camellia-192-cfb camellia-256-cfb",
        ClientTarget.LOON to "$ssLegacy $ssExtra $ssAead $ss2022", ClientTarget.QUANX to "$ssLegacy $ssAead $ss2022",
        ClientTarget.CLASH to "$ssLegacy $ssAead $ss2022 none",
        ClientTarget.EGERN to "$ssAead $ss2022 2022-blake3-chacha20-poly1305 none",
        ClientTarget.ANYWHERE to "aes-128-gcm aes-256-gcm chacha20-ietf-poly1305 chacha20-poly1305 $ss2022 2022-blake3-chacha20-poly1305 none",
    ).mapValues { it.value.split(' ').toSet() }

    fun rejection(node: ProxyNode, target: ClientTarget, mode: ExportContentMode): String? {
        try { SubscriptionParser.validate(node) } catch (e: IllegalArgumentException) { return e.message ?: "无效的节点" }
        val meta = ClientCatalog.meta(target)
        val kind = node.kind
        val network = transport(node.transport)
        val surge = target in surgeTargets
        val sing = meta.usesSingBoxFormat
        val clash = meta.usesClashFormat || target == ClientTarget.SHADOWROCKET
        val shadow = node.shadowTLS
        val reality = node.realityPublicKey != null
        if (kind !in meta.supportedKinds) return "${meta.name} 不支持 ${ClientCatalog.PROXY_KIND_LABELS[kind]}"
        if (mode == ExportContentMode.NODES_ONLY &&
            ((kind == ProxyKind.WIREGUARD && (surge || target == ClientTarget.SHADOWROCKET)) ||
                (kind == ProxyKind.SNELL && target == ClientTarget.SHADOWROCKET))) return "节点需要完整配置"
        if (node.certificateFingerprint != null && (sing || target in setOf(ClientTarget.V2BOX, ClientTarget.ANYWHERE)))
            return "客户端不支持证书指纹"
        if (kind == ProxyKind.TUIC && !uuidPattern.matches(node.uuid.orEmpty())) return "无效的 TUIC UUID"
        if (kind == ProxyKind.SS) {
            if (restrictedSS[target]?.contains(node.cipher) == false) return "不支持的 Shadowsocks 加密方式"
            if (node.plugin?.substringBefore(';') !in setOf(null, "obfs", "obfs-local", "simple-obfs", "v2ray-plugin", "shadow-tls"))
                return "不支持的 Shadowsocks SIP003 插件"
            if (node.tls && !v2Plugin(node) && network != "ws" && target !in setOf(ClientTarget.SHADOWROCKET, ClientTarget.QUANX))
                return "不支持原生 Shadowsocks TLS"
            if (v2Plugin(node) && !(clash || sing || target == ClientTarget.QUANX)) return "不支持 Shadowsocks WebSocket 插件"
            if (v2Plugin(node) && target == ClientTarget.QUANX && node.pluginMux != false) return "QuanX 需要关闭 WebSocket 插件复用"
            if (target == ClientTarget.ANYWHERE && (node.plugin != null || shadow != null || node.tls || simpleObfs(node) != null))
                return "Anywhere 不支持该 Shadowsocks 选项"
        }
        if (target == ClientTarget.SURFBOARD) {
            if (shadow != null) return "Surfboard 不支持 ShadowTLS"
            if (kind in setOf(ProxyKind.VMESS, ProxyKind.TROJAN) && network == "httpupgrade")
                return "Surfboard 不支持该协议的 HTTPUpgrade"
            if (kind == ProxyKind.SNELL && (node.version ?: 4) > 5) return "Surfboard 仅支持 Snell v1–v5"
            if (kind == ProxyKind.WIREGUARD && (node.wireGuardDNS.isNullOrBlank() || (node.wireGuardMTU ?: 0) <= 0 || node.wireGuardReserved != null))
                return "Surfboard WireGuard 需要 DNS 和 MTU，且不支持 reserved"
        }
        if (kind == ProxyKind.SNELL) {
            val version = node.version ?: if (target == ClientTarget.CLASH) 1 else 4
            if (target == ClientTarget.CLASH && version > 3) return "Stash 仅支持 Snell v1–v3"
            if (sing && (version !in 4..5 || node.obfs !in setOf(null, "none", "http"))) return "不支持该 Snell 版本或混淆方式"
            if (version == 6 && !surge && (target !in setOf(ClientTarget.CLASH_APPLE, ClientTarget.FLCLASH, ClientTarget.MIHOMO_PARTY) ||
                    node.obfs !in setOf(null, "none") || node.password.orEmpty().toByteArray().size !in 12..255)) return "不支持该 Snell v6 参数"
        }
        if (shadow != null) {
            if (kind == ProxyKind.SNELL && !surge) return "不支持 Snell ShadowTLS"
            if (!(surge || clash || sing || target in setOf(ClientTarget.LOON, ClientTarget.EGERN)) || target == ClientTarget.KARING)
                return "不支持 ShadowTLS"
            if ((surge || target in setOf(ClientTarget.CLASH, ClientTarget.LOON)) && shadow.version < 2)
                return "不支持此 ShadowTLS 版本"
            if (target in setOf(ClientTarget.EGERN, ClientTarget.SHADOWROCKET) && shadow.version != 3)
                return "客户端需要 ShadowTLS v3"
            if (shadow.skipCertificateVerification && !sing && target != ClientTarget.CLASH) return "不支持关闭 ShadowTLS 证书验证"
            if (sing && node.udpRelayEnabled == true) return "ShadowTLS 只支持 TCP"
            if (!sing && node.fingerprint != null) return "不支持 ShadowTLS 客户端指纹"
        }
        if (kind == ProxyKind.WIREGUARD) {
            if (reserved(node)?.let { it.size != 3 || it.any { byte -> byte !in 0..255 } } == true ||
                (wgAddresses(node) + wgAllowed(node)).any { !validCidr(it) }) return "无效的 WireGuard 参数"
            if (target == ClientTarget.HIDDIFY && (node.wireGuardPersistentKeepalive != null || node.wireGuardDNS != null))
                return "Hiddify 不支持该 WireGuard keepalive 或 DNS 参数"
        }
        if (target == ClientTarget.EGERN && kind == ProxyKind.SSR &&
            (node.protocolName !in setOf("origin", "auth_sha1_v4", "auth_aes128_md5", "auth_aes128_sha1", "auth_chain_a", "auth_chain_b") ||
                node.obfs !in setOf("plain", "http_simple", "http_post", "random_head", "tls1.2_ticket_auth", "tls1.2_ticket_fastauth")))
            return "Egern 不支持该 ShadowsocksR 协议或混淆"
        if (kind == ProxyKind.SSH && ((surge || target == ClientTarget.SHADOWROCKET) && node.ssh?.privateKey != null ||
                target == ClientTarget.CLASH && !node.ssh?.hostKeys.isNullOrEmpty() ||
                target == ClientTarget.EGERN && node.ssh?.privateKeyPassphrase != null)) return "不支持该 SSH 身份验证方式"
        if (kind == ProxyKind.TRUSTTUNNEL && ((target == ClientTarget.CLASH && node.trustTunnel?.quic != true) ||
                (!surge && node.trustTunnel?.clientRandomAuthKey != null))) return "不支持该 TrustTunnel 参数"
        if (kind == ProxyKind.MASQUE) {
            val options = node.masque ?: return "缺少 MASQUE 参数"
            if ((options.mode == "keyConnectIP") == surge) return "不支持该 MASQUE 认证模式"
            if (surge && (!options.headers.isNullOrEmpty() || !options.certificate.isNullOrEmpty() ||
                    options.httpVersion != null || options.disableVersionFallback != null || options.mtu != null))
                return "不支持该 MASQUE 参数"
        }
        if (kind == ProxyKind.SOCKS5 && node.tls && (sing || target in setOf(ClientTarget.KARING, ClientTarget.ANYWHERE, ClientTarget.CLASH) ||
                target in mihomoTargets && !reality)) return "不支持 SOCKS5 TLS"
        if (kind == ProxyKind.HYSTERIA && sing && node.protocolName !in setOf(null, "udp")) return "不支持该 Hysteria 传输"
        if (kind == ProxyKind.HYSTERIA2 && node.obfs !in setOf(null, "none") &&
            (node.obfsParam.isNullOrEmpty() || (node.obfs != "salamander" &&
                (sing || surge || target in setOf(ClientTarget.LOON, ClientTarget.SHADOWROCKET, ClientTarget.EGERN)))))
            return "不支持的 Hysteria 2 混淆"
        if (reality && ((!meta.expressesReality && !(surge && kind == ProxyKind.VLESS)) ||
                (target == ClientTarget.CLASH && (kind != ProxyKind.VLESS || network != null)) ||
                (target == ClientTarget.KARING && kind == ProxyKind.TROJAN) ||
                (target == ClientTarget.EGERN && network !in setOf(null, "grpc")) ||
                (target == ClientTarget.ANYWHERE && kind != ProxyKind.VLESS))) return "不支持该节点的 REALITY 参数"
        if (node.realitySupportsX25519MLKEM768 == true && !surge && target !in mihomoTargets)
            return "不支持 REALITY 混合密钥交换"
        if (node.vlessEncryption != null && target !in mihomoTargets && target != ClientTarget.ANYWHERE &&
            !(surge && Regex("(?i)mlkem768x25519plus\\.(?:native|xorpub|random)\\.(?:1rtt|0rtt)(?:\\.[\\w=-]+)+")
                .matches(node.vlessEncryption))) return "不支持 VLESS Encryption"
        if (surge && kind == ProxyKind.VLESS && !surgeVless(node, network)) return "Surge 方言无法表达该 VLESS 传输或安全参数"
        if (target == ClientTarget.ANYWHERE && (node.portHopping != null || node.udpRelayEnabled == false ||
                (node.fingerprint != null && anywhereFingerprint(node.fingerprint) !in anywhereNativeFingerprints &&
                    node.fingerprint.lowercase(Locale.ROOT) !in setOf("random", "randomized")) ||
                (kind == ProxyKind.VLESS && !uuidPattern.matches(node.uuid.orEmpty())) ||
                (kind == ProxyKind.HYSTERIA2 && (node.alpn != null || node.obfs !in setOf(null, "none", "salamander", "gecko"))) ||
                (node.skipCertificateVerification && (!node.tls || reality ||
                    kind !in setOf(ProxyKind.VLESS, ProxyKind.TROJAN, ProxyKind.ANYTLS) ||
                    (kind == ProxyKind.VLESS && network !in setOf(null, "httpupgrade")))))) return "Anywhere 不支持该节点的参数"
        if (network != null && !(kind == ProxyKind.SS && (v2Plugin(node) ||
                    (network == "ws" && (surge || target == ClientTarget.SHADOWROCKET || target == ClientTarget.QUANX))))) {
            val allowed = when {
                kind !in v2Kinds -> emptySet()
                surge && kind == ProxyKind.VLESS -> setOf("ws", "httpupgrade", "grpc", "xhttp")
                surge -> setOf("ws", "httpupgrade")
                target == ClientTarget.LOON -> if (kind == ProxyKind.TROJAN) setOf("ws") else setOf("ws", "http")
                target == ClientTarget.QUANX -> if (!node.tls && kind in setOf(ProxyKind.VMESS, ProxyKind.VLESS)) setOf("ws", "http") else setOf("ws")
                target == ClientTarget.EGERN -> if (kind == ProxyKind.TROJAN) setOf("ws") else setOf("ws", "http", "h2", "grpc")
                target == ClientTarget.CLASH -> if (kind == ProxyKind.TROJAN) setOf("ws", "grpc") else setOf("ws", "http", "h2", "grpc")
                target == ClientTarget.KARING -> setOf("ws", "http", "grpc", "xhttp")
                sing -> setOf("ws", "grpc", "httpupgrade") + if (node.tls) "h2" else "http"
                target == ClientTarget.ANYWHERE -> if (kind == ProxyKind.VLESS) setOf("ws", "grpc", "httpupgrade", "xhttp") else emptySet()
                else -> setOf("ws", "http", "grpc", "httpupgrade") +
                    listOfNotNull("h2".takeUnless { target == ClientTarget.V2BOX }, "xhttp".takeIf { kind == ProxyKind.VLESS })
            }
            if (network !in allowed || (sing && network == "grpc" && node.hostHeader != null) ||
                (target == ClientTarget.EGERN && ((network in setOf("h2", "grpc") && !node.tls) ||
                    (network == "http" && node.tls)))) return "不支持的传输参数 $network"
        }
        if (target in surgeTargets || target in setOf(ClientTarget.LOON, ClientTarget.QUANX)) {
            val values = listOfNotNull(node.server, node.cipher, node.uuid, node.password, node.username, node.sni, node.hostHeader,
                node.path, node.alpn, node.flow, node.obfs, node.obfsParam, node.realityPublicKey, node.realityShortID,
                node.fingerprint, node.certificateFingerprint, node.vlessEncryption, node.portHopping, node.congestionControl, node.udpRelayMode,
                node.shadowTLS?.password, node.shadowTLS?.host, node.wireGuardPrivateKey, node.wireGuardPublicKey, node.wireGuardPreSharedKey,
                node.wireGuardDNS, node.trustTunnel?.clientRandomAuthKey) + node.ssh?.hostKeys.orEmpty()
            if (values.any { it.any { c -> c.code < 32 || c in "\u0085\u2028\u2029" } }) return "INI 客户端不支持多行节点参数"
            if (kind == ProxyKind.WIREGUARD && surge && listOfNotNull(node.wireGuardPrivateKey, node.wireGuardPublicKey,
                    node.wireGuardPreSharedKey).any { it.any { char -> char in ",#;()\"'" } || " //" in it })
                return "WireGuard 密钥包含 INI 保留字符"
        }
        return null
    }

    private fun surgeVless(node: ProxyNode, network: String?): Boolean {
        val reality = node.realityPublicKey != null
        if (reality && (network !in setOf(null, "grpc", "xhttp") ||
                !node.realityShortID.orEmpty().matches(Regex("[a-fA-F0-9]{0,16}")))) return false
        val secured = node.tls || reality
        val flow = node.flow?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (flow.isNotEmpty() && (flow !in setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443") ||
                !(network == null && secured || node.vlessEncryption != null && network in setOf(null, "ws", "httpupgrade", "xhttp"))))
            return false
        if (network == "xhttp") return node.transportMode.orEmpty().lowercase(Locale.ROOT) in
            setOf("", "auto", "packet-up", "stream-up", "stream-one")
        return network in setOf(null, "ws", "httpupgrade") || network == "grpc" && secured
    }

    internal fun clash(n: ProxyNode, target: ClientTarget): Fields {
        val p = linkedMapOf<String, Any?>("name" to n.name, "type" to n.kind.rawValue, "server" to n.server, "port" to n.port)
        val stash = target == ClientTarget.CLASH
        val network = transport(n.transport)
        val hostname = sni(n)
        when (n.kind) {
            ProxyKind.SS -> {
                p.putAll(fieldMap("cipher" to n.cipher, "password" to n.password, "udp" to (n.udpRelayEnabled ?: true)))
                val shadow = n.shadowTLS
                when {
                    shadow != null -> p.putAll(fieldMap("plugin" to "shadow-tls", "plugin-opts" to fieldMap("version" to shadow.version,
                        "host" to shadow.host, "password" to shadow.password, "skip-cert-verify" to shadow.skipCertificateVerification)))
                    v2Plugin(n) -> p.putAll(fieldMap("plugin" to "v2ray-plugin", "plugin-opts" to fieldMap("mode" to "websocket", "tls" to n.tls,
                        "host" to n.hostHeader, "path" to absolute(n.path), "mux" to n.pluginMux, "skip-cert-verify" to n.skipCertificateVerification)))
                    simpleObfs(n) != null -> p.putAll(fieldMap("plugin" to "obfs", "plugin-opts" to fieldMap("mode" to simpleObfs(n),
                        "host" to (n.hostHeader ?: n.obfsParam))))
                    network == "ws" -> p.putAll(fieldMap("network" to "ws", "ws-opts" to fieldMap("path" to absolute(n.path),
                        "headers" to n.hostHeader?.let { mapOf("Host" to it) }), "tls" to n.tls, "sni" to hostname))
                    n.tls -> p.putAll(fieldMap("tls" to true, "sni" to hostname, "skip-cert-verify" to n.skipCertificateVerification))
                }
            }
            ProxyKind.SSR -> p.putAll(fieldMap("cipher" to n.cipher, "password" to n.password, "protocol" to n.protocolName,
                "obfs" to n.obfs, "protocol-param" to n.protocolParam, "obfs-param" to n.obfsParam, "udp" to (n.udpRelayEnabled ?: true)))
            ProxyKind.VMESS, ProxyKind.VLESS, ProxyKind.TROJAN -> {
                p.putAll(fieldMap("uuid" to n.uuid?.let(::uuid), "password" to n.password, "flow" to n.flow, "encryption" to n.vlessEncryption,
                    "udp" to (n.udpRelayEnabled ?: true), "tls" to (n.tls || n.kind == ProxyKind.TROJAN),
                    (if (n.kind == ProxyKind.TROJAN) "sni" else "servername") to hostname, "network" to network))
                if (n.kind == ProxyKind.VMESS) p.putAll(fieldMap("alterId" to (n.alterID ?: 0), "cipher" to (n.cipher ?: "auto")))
                when (network) {
                    "ws", "httpupgrade" -> p[if (network == "ws") "ws-opts" else "http-upgrade-opts"] = fieldMap(
                        "path" to if (stash) wsPath(n.path) else absolute(n.path),
                        "headers" to n.hostHeader?.let { mapOf("Host" to it) },
                        "max-early-data" to earlyData(n.path)?.takeIf { stash },
                        "early-data-header-name" to "Sec-WebSocket-Protocol".takeIf { stash && earlyData(n.path) != null })
                    "grpc" -> p["grpc-opts"] = fieldMap("grpc-service-name" to n.path?.trimStart('/'), "authority" to n.hostHeader)
                    "http", "h2", "xhttp" -> p["$network-opts"] = fieldMap(
                        "path" to if (network == "http") listOf(absolute(n.path) ?: "/") else absolute(n.path),
                        "host" to n.hostHeader?.let { if (network == "h2") listOf(it) else if (network == "xhttp") it else null },
                        "headers" to n.hostHeader?.takeIf { network == "http" }?.let { mapOf("Host" to listOf(it)) },
                        "mode" to n.transportMode)
                }
            }
            ProxyKind.HYSTERIA -> p.putAll(fieldMap("auth-str" to (n.password ?: ""), (if (stash) "up-speed" else "up") to (n.upMbps ?: 50),
                (if (stash) "down-speed" else "down") to (n.downMbps ?: 100), "protocol" to n.protocolName, "obfs" to n.obfs, "sni" to hostname))
            ProxyKind.HYSTERIA2 -> p.putAll(fieldMap((if (stash) "auth" else "password") to n.password, "sni" to hostname,
                "ports" to n.portHopping, "obfs" to n.obfs?.takeUnless { it == "none" }, "obfs-password" to n.obfsParam,
                "udp" to true, "up" to n.upMbps, "down" to n.downMbps))
            ProxyKind.TUIC -> p.putAll(fieldMap("uuid" to n.uuid?.let(::uuid), "password" to n.password, "sni" to hostname,
                "version" to 5.takeIf { stash }, "congestion-controller" to n.congestionControl, "udp-relay-mode" to n.udpRelayMode, "udp" to true))
            ProxyKind.WIREGUARD -> p.putAll(fieldMap("private-key" to n.wireGuardPrivateKey, "public-key" to n.wireGuardPublicKey,
                "pre-shared-key" to n.wireGuardPreSharedKey, "ip" to n.wireGuardIPv4, "ipv6" to n.wireGuardIPv6, "allowed-ips" to wgAllowed(n),
                "reserved" to reserved(n), "mtu" to n.wireGuardMTU, "persistent-keepalive" to n.wireGuardPersistentKeepalive,
                "dns" to n.wireGuardDNS?.strings(), "udp" to true))
            ProxyKind.ANYTLS -> p.putAll(fieldMap("password" to n.password, "sni" to hostname, "udp" to (n.udpRelayEnabled ?: true),
                "tls" to true.takeIf { target in setOf(ClientTarget.SHADOWROCKET, ClientTarget.KARING) },
                "idle-session-check-interval" to n.idleSessionCheckInterval, "idle-session-timeout" to n.idleSessionTimeout,
                "min-idle-session" to n.minIdleSession))
            ProxyKind.SNELL -> {
                val version = n.version ?: if (stash) 1 else 4
                p.putAll(fieldMap("psk" to n.password, "version" to version, "udp" to (n.udpRelayEnabled ?: true).takeIf { version >= 3 }))
                if (simpleObfs(n) != null) p["obfs-opts"] = fieldMap("mode" to n.obfs, "host" to n.obfsParam)
            }
            ProxyKind.SSH -> p.putAll(fieldMap((if (stash) "user" else "username") to n.username, "password" to n.password,
                "private-key" to n.ssh?.privateKey, "private-key-passphrase" to n.ssh?.privateKeyPassphrase,
                "host-key" to n.ssh?.hostKeys?.ifEmpty { null }, "host-key-algorithms" to n.ssh?.hostKeyAlgorithms?.ifEmpty { null }))
            ProxyKind.TRUSTTUNNEL -> p.putAll(fieldMap("username" to n.username, "password" to n.password,
                "sni" to hostname, "quic" to n.trustTunnel?.quic, "udp" to n.udpRelayEnabled))
            ProxyKind.MASQUE -> {
                val m = n.masque
                p.putAll(fieldMap("private-key" to m?.privateKey, "public-key" to m?.publicKey, "ip" to m?.ipv4, "ipv6" to m?.ipv6,
                    "mtu" to m?.mtu, "network" to (m?.network?.let { if (it == "h3") "quic" else it } ?: "quic"),
                    "remote-dns-resolve" to (m?.remoteDNSResolve ?: true), "dns" to (m?.dns ?: listOf("1.1.1.1", "8.8.8.8")),
                    "udp" to (n.udpRelayEnabled ?: false), "sni" to hostname, "headers" to m?.headers, "certificate" to m?.certificate,
                    "http-version" to m?.httpVersion, "disable-version-fallback" to m?.disableVersionFallback))
            }
            ProxyKind.HTTP, ProxyKind.SOCKS5 -> p.putAll(fieldMap("username" to n.username, "password" to n.password,
                "tls" to n.tls, "sni" to hostname, "udp" to (n.udpRelayEnabled ?: true).takeIf { n.kind == ProxyKind.SOCKS5 }))
            ProxyKind.UNKNOWN -> error("Unsupported proxy type")
        }
        if (n.tls && n.kind != ProxyKind.SS) p.putAll(fieldMap("alpn" to n.alpn?.strings(), "fingerprint" to n.certificateFingerprint,
            "client-fingerprint" to n.fingerprint, "skip-cert-verify" to n.skipCertificateVerification))
        if (n.realityPublicKey != null) p["reality-opts"] = fieldMap("public-key" to n.realityPublicKey,
            "short-id" to n.realityShortID, "support-x25519mlkem768" to n.realitySupportsX25519MLKEM768)
        return p
    }

    private fun wgSection(n: ProxyNode) = "tower-wg-" + sha256(n.id).take(12)
    internal fun surgeWireguard(n: ProxyNode): String {
        val peer = fieldMap("public-key" to n.wireGuardPublicKey, "preshared-key" to n.wireGuardPreSharedKey,
            "endpoint" to endpoint(n), "keepalive" to n.wireGuardPersistentKeepalive, "allowed-ips" to quoted(wgAllowed(n).joinToString(",")),
            "client-id" to reserved(n)?.joinToString("/")).entries.joinToString(", ") { "${it.key}=${it.value}" }
        return (listOf("[WireGuard ${wgSection(n)}]") + fieldMap("private-key" to n.wireGuardPrivateKey,
            "self-ip" to n.wireGuardIPv4, "self-ip-v6" to n.wireGuardIPv6, "mtu" to n.wireGuardMTU,
            "dns-server" to n.wireGuardDNS, "peer" to "($peer)").map { "${it.key} = ${it.value}" }).joinToString("\n")
    }

    internal fun surge(n: ProxyNode, surfboard: Boolean = false): String {
        var kind = n.kind.rawValue
        var parts = listOf(n.server, n.port.toString())
        val fields = linkedMapOf<String, Any?>()
        fun add(vararg options: Pair<String, Any?>) { fields.putAll(fieldMap(*options)) }
        when (n.kind) {
            ProxyKind.SS -> {
                add("encrypt-method" to n.cipher, "password" to n.password, "obfs" to
                    if (n.transport == "ws") { if (n.tls) "wss" else "ws" } else simpleObfs(n),
                    "obfs-host" to (n.hostHeader ?: n.obfsParam), "obfs-uri" to wsPath(n.path), "udp-relay" to (n.udpRelayEnabled ?: true))
            }
            ProxyKind.VMESS, ProxyKind.VLESS, ProxyKind.TROJAN -> {
                add("username" to n.uuid?.let(::uuid), "password" to n.password, "vmess-aead" to ((n.alterID ?: 0) == 0).takeIf { n.kind == ProxyKind.VMESS },
                    "tls" to n.tls.takeUnless { n.kind == ProxyKind.TROJAN }, "udp-relay" to (n.udpRelayEnabled ?: true),
                    "vless-flow" to n.flow, "encryption" to n.vlessEncryption)
                when (n.transport) {
                    "ws", "httpupgrade" -> add("ws" to true, "ws-path" to wsPath(n.path), "ws-headers" to n.hostHeader?.let { "Host: $it" },
                        "ws-http-upgrade" to true.takeIf { n.transport == "httpupgrade" })
                    "grpc" -> add("grpc" to true, "grpc-service-name" to n.path?.trimStart('/'), "grpc-authority" to n.hostHeader)
                    "xhttp" -> add("xhttp" to true, "xhttp-path" to absolute(n.path), "xhttp-host" to n.hostHeader, "xhttp-mode" to n.transportMode)
                }
                if (n.realityPublicKey != null) add("reality-public-key" to n.realityPublicKey, "reality-short-id" to n.realityShortID,
                    "support-x25519mlkem768" to n.realitySupportsX25519MLKEM768, "tls-fingerprint" to n.fingerprint)
            }
            ProxyKind.HYSTERIA2 -> add("password" to n.password, "salamander-password" to n.obfsParam, "port-hopping" to n.portHopping,
                "udp-relay" to true, "download-bandwidth" to n.downMbps)
            ProxyKind.TUIC -> { kind = "tuic-v5"; add("uuid" to n.uuid?.let(::uuid), "password" to n.password, "udp-relay" to true,
                "congestion-controller" to n.congestionControl, "udp-relay-mode" to n.udpRelayMode) }
            ProxyKind.WIREGUARD -> { parts = emptyList(); add("section-name" to wgSection(n)) }
            ProxyKind.ANYTLS -> add("password" to n.password, "udp-relay" to (n.udpRelayEnabled ?: true),
                "idle-session-check-interval" to n.idleSessionCheckInterval, "idle-session-timeout" to n.idleSessionTimeout,
                "min-idle-session" to n.minIdleSession)
            ProxyKind.SNELL -> {
                val version = n.version ?: 4
                add("psk" to n.password, "version" to if (surfboard && version == 5) 4 else version, "obfs" to simpleObfs(n),
                    "obfs-host" to n.obfsParam, "udp-relay" to (n.udpRelayEnabled ?: (n.shadowTLS == null)).takeIf { version >= 3 })
            }
            ProxyKind.HTTP, ProxyKind.SOCKS5 -> {
                if (n.tls) kind = if (n.kind == ProxyKind.HTTP) "https" else "socks5-tls"
                if (n.username != null || n.password != null) parts = parts + listOf(confValue(n.username.orEmpty()), confValue(n.password.orEmpty()))
                if (n.kind == ProxyKind.SOCKS5) add("udp-relay" to (n.udpRelayEnabled ?: true))
            }
            ProxyKind.SSH -> add("username" to n.username, "password" to n.password, "server-fingerprint" to n.ssh?.hostKeys?.joinToString(","))
            ProxyKind.TRUSTTUNNEL -> {
                kind = "trust-tunnel"
                add("username" to n.username, "password" to n.password, "h3" to n.trustTunnel?.quic,
                    "client-random-auth-key" to n.trustTunnel?.clientRandomAuthKey, "udp-relay" to n.udpRelayEnabled)
            }
            ProxyKind.MASQUE -> add("username" to n.username, "password" to n.password)
            else -> error("Unsupported Surge proxy type")
        }
        if (n.tls) add("sni" to sni(n), "skip-cert-verify" to true.takeIf { n.skipCertificateVerification },
            "alpn" to n.alpn, "server-cert-fingerprint-sha256" to n.certificateFingerprint)
        n.shadowTLS?.let { add("shadow-tls-password" to it.password, "shadow-tls-sni" to it.host, "shadow-tls-version" to it.version) }
        return confName(n.name) + " = " + (listOf(kind) + parts + kv(fields)).joinToString(", ")
    }

    internal fun loon(n: ProxyNode): String {
        val kind = n.kind
        val type = when (kind) {
            ProxyKind.SS -> "Shadowsocks"
            ProxyKind.SSR -> "ShadowsocksR"
            ProxyKind.SOCKS5 -> "Socks5"
            ProxyKind.HTTP -> if (n.tls) "https" else "http"
            else -> kind.rawValue
        }
        val parts = mutableListOf(n.server, n.port.toString())
        val fields = linkedMapOf<String, Any?>()
        fun add(vararg options: Pair<String, Any?>) { fields.putAll(fieldMap(*options)) }
        when (kind) {
            ProxyKind.SS, ProxyKind.SSR -> {
                parts.add(n.cipher.orEmpty()); parts.add(quoted(n.password.orEmpty()))
                if (kind == ProxyKind.SSR) add("protocol" to n.protocolName, "obfs" to n.obfs, "protocol-param" to n.protocolParam, "obfs-param" to n.obfsParam)
                else add("obfs-name" to simpleObfs(n), "obfs-host" to (n.hostHeader ?: n.obfsParam), "shadow-tls-password" to n.shadowTLS?.password,
                    "shadow-tls-sni" to n.shadowTLS?.host, "shadow-tls-version" to n.shadowTLS?.version)
            }
            ProxyKind.VMESS, ProxyKind.VLESS -> {
                if (kind == ProxyKind.VMESS) { parts.add(n.cipher ?: "auto"); add("alterId" to (n.alterID ?: 0)) }
                parts.add(quoted(uuid(n.uuid.orEmpty())))
                add("transport" to (n.transport ?: "tcp"), "overTLS" to n.tls, "flow" to n.flow, "path" to wsPath(n.path), "host" to n.hostHeader)
            }
            ProxyKind.TROJAN, ProxyKind.HYSTERIA2, ProxyKind.ANYTLS -> {
                parts.add(quoted(n.password.orEmpty()))
                if (kind == ProxyKind.TROJAN) add("transport" to n.transport, "path" to wsPath(n.path), "host" to n.hostHeader)
                if (kind == ProxyKind.HYSTERIA2) {
                    add("salamander-password" to n.obfsParam, "fast-open" to true)
                    n.portHopping?.let { parts.add("server-ports=" + quoted(it.replace('-', ':'))) }
                }
            }
            ProxyKind.HTTP, ProxyKind.SOCKS5 -> {
                if (n.username != null || n.password != null) {
                    parts.add(quoted(n.username.orEmpty())); parts.add(quoted(n.password.orEmpty()))
                }
                if (kind == ProxyKind.SOCKS5 && n.tls) add("over-tls" to true)
            }
            ProxyKind.WIREGUARD -> {
                parts.clear()
                parts.addAll(kv(fieldMap("interface-ip" to n.wireGuardIPv4, "interface-ipv6" to n.wireGuardIPv6,
                    "mtu" to n.wireGuardMTU, "keepalive" to n.wireGuardPersistentKeepalive)))
                parts.add("private-key=" + quoted(n.wireGuardPrivateKey.orEmpty()))
                val peer = mutableListOf("public-key=" + quoted(n.wireGuardPublicKey.orEmpty()),
                    "allowed-ips=" + quoted(wgAllowed(n).joinToString(",")), "endpoint=" + endpoint(n))
                n.wireGuardPreSharedKey?.let { peer.add("preshared-key=" + quoted(it)) }
                reserved(n)?.let { peer.add("reserved=[" + it.joinToString(",") + "]") }
                parts.add("peers=[{${peer.joinToString(",")}}]")
                n.wireGuardDNS?.let { parts.add("dns=" + quoted(it)) }
            }
            else -> error("Unsupported Loon proxy type")
        }
        if (kind != ProxyKind.HTTP) add("udp" to (n.udpRelayEnabled ?: true))
        if (n.tls) add((if (kind in setOf(ProxyKind.HTTP, ProxyKind.SOCKS5, ProxyKind.VMESS, ProxyKind.VLESS) ||
            n.realityPublicKey != null) "sni" else "tls-name") to sni(n), "skip-cert-verify" to true.takeIf { n.skipCertificateVerification },
            "alpn" to n.alpn, "tls-cert-sha256" to n.certificateFingerprint, "public-key" to n.realityPublicKey,
            "short-id" to n.realityShortID, "tls-profile" to n.fingerprint)
        return confName(n.name) + " = " + (listOf(type) + parts + kv(fields)).joinToString(",")
    }

    internal fun quanx(n: ProxyNode): String {
        val kind = n.kind
        val type = if (kind in setOf(ProxyKind.SS, ProxyKind.SSR)) "shadowsocks" else kind.rawValue
        val fields = linkedMapOf<String, Any?>()
        fun add(vararg options: Pair<String, Any?>) { fields.putAll(fieldMap(*options)) }
        when (kind) {
            ProxyKind.SS, ProxyKind.SSR -> {
                add("method" to n.cipher, "password" to n.password, "ssr-protocol" to n.protocolName, "ssr-protocol-param" to n.protocolParam)
                when {
                    n.transport == "ws" -> add("obfs" to if (n.tls) "wss" else "ws", "obfs-host" to n.hostHeader, "obfs-uri" to wsPath(n.path))
                    n.tls -> add("obfs" to "over-tls", "obfs-host" to sni(n))
                    else -> add("obfs" to n.obfs, "obfs-host" to (n.obfsParam ?: n.hostHeader))
                }
            }
            ProxyKind.VMESS, ProxyKind.VLESS -> add("method" to if (kind == ProxyKind.VLESS || n.cipher == "none") "none" else "aes-128-gcm",
                "password" to n.uuid?.let(::uuid), "aead" to ((n.alterID ?: 0) == 0).takeIf { kind == ProxyKind.VMESS },
                "obfs" to when (n.transport) { "ws" -> if (n.tls) "wss" else "ws"; "http" -> "http"; else -> "over-tls".takeIf { n.tls } },
                "obfs-host" to (n.hostHeader ?: sni(n)), "obfs-uri" to wsPath(n.path), "vless-flow" to n.flow)
            else -> {
                add("username" to n.username, "password" to n.password)
                if (n.transport == "ws") add("obfs" to "wss", "obfs-host" to n.hostHeader, "obfs-uri" to wsPath(n.path))
                else if (n.tls) add("over-tls" to true, "tls-host" to sni(n))
            }
        }
        if (n.tls) add("tls-verification" to !n.skipCertificateVerification, "tls-cert-sha256" to n.certificateFingerprint,
            "tls-alpn" to n.alpn, "tls-pubkey" to n.realityPublicKey, "tls-short-id" to n.realityShortID, "tls-fingerprint" to n.fingerprint)
        if (kind != ProxyKind.HTTP) add("udp-relay" to (n.udpRelayEnabled ?: true))
        return "$type=" + (listOf(endpoint(n)) + kv(fields) + ("tag=" + confName(n.name))).joinToString(", ")
    }

    fun uri(n: ProxyNode, anywhere: Boolean = false): String? {
        try { SubscriptionParser.validate(n) } catch (_: IllegalArgumentException) { return null }
        val remark = "#" + percentEncode(n.name)
        when (n.kind) {
            ProxyKind.SS -> {
                if (n.shadowTLS != null || (n.tls && !v2Plugin(n))) return null
                val plugin = when {
                    v2Plugin(n) -> listOfNotNull("v2ray-plugin", "mode=websocket", "tls".takeIf { n.tls },
                        n.hostHeader?.let { "host=$it" }, n.path?.let { "path=$it" }, n.pluginMux?.let { "mux=$it" },
                        "skip-cert-verify=true".takeIf { n.skipCertificateVerification }).joinToString(";")
                    simpleObfs(n) != null -> "obfs-local;obfs=${n.obfs}" +
                        (n.hostHeader ?: n.obfsParam)?.let { ";obfs-host=$it" }.orEmpty()
                    else -> null
                }
                return "ss://" + base64Url("${n.cipher}:${n.password}") + "@" + endpoint(n) +
                    plugin?.let { "?plugin=" + percentEncode(it) }.orEmpty() + remark
            }
            ProxyKind.SSR -> {
                val fields = fieldMap("remarks" to base64Url(n.name), "obfsparam" to n.obfsParam?.let(::base64Url),
                    "protoparam" to n.protocolParam?.let(::base64Url)).entries.joinToString("&") { "${it.key}=${it.value}" }
                return "ssr://" + base64Url("${endpoint(n)}:${n.protocolName}:${n.cipher}:${n.obfs}:${base64Url(n.password.orEmpty())}/?$fields")
            }
            ProxyKind.VMESS -> {
                if (n.realityPublicKey != null || n.certificateFingerprint != null || n.transport == "xhttp") return null
                val payload = fieldMap("v" to "2", "ps" to n.name, "add" to n.server, "port" to n.port,
                    "id" to n.uuid?.let(::uuid), "aid" to (n.alterID ?: 0), "scy" to (n.cipher ?: "auto"),
                    "net" to if (n.transport == "http") "tcp" else (n.transport ?: "tcp"), "type" to if (n.transport == "http") "http" else "none",
                    "host" to n.hostHeader, "path" to absolute(n.path), "sni" to sni(n), "tls" to "tls".takeIf { n.tls },
                    "alpn" to n.alpn, "fp" to n.fingerprint, "allowInsecure" to "1".takeIf { n.skipCertificateVerification })
                return "vmess://" + Base64.getEncoder().encodeToString(payload.jsonValue().toString().toByteArray())
            }
            ProxyKind.SNELL -> return surge(n)
            else -> Unit
        }
        val kind = n.kind
        val single = kind in setOf(ProxyKind.TROJAN, ProxyKind.HYSTERIA, ProxyKind.HYSTERIA2, ProxyKind.WIREGUARD,
            ProxyKind.VLESS, ProxyKind.ANYTLS, ProxyKind.SNELL)
        val user = when (kind) {
            ProxyKind.VLESS, ProxyKind.TUIC -> n.uuid?.let(::uuid)
            ProxyKind.WIREGUARD -> n.wireGuardPrivateKey
            else -> if (single) n.password else n.username
        }
        val password = n.password.takeUnless { single }
        val login = if (user != null || password != null) percentEncode(user.orEmpty()) +
            password?.let { ":" + percentEncode(it) }.orEmpty() + "@" else ""
        val fields = fieldMap("type" to n.transport, "mode" to n.transportMode,
            "security" to if (n.realityPublicKey != null) "reality" else "tls".takeIf { n.tls }, "sni" to sni(n),
            (if (anywhere && n.transport == "grpc") "authority" else "host") to n.hostHeader,
            (if (n.transport == "grpc") "serviceName" else "path") to (if (n.transport == "grpc") n.path?.trimStart('/') else absolute(n.path)),
            "alpn" to n.alpn, "pbk" to n.realityPublicKey, "sid" to n.realityShortID, "flow" to n.flow, "encryption" to n.vlessEncryption,
            "support-x25519mlkem768" to "1".takeIf { n.realitySupportsX25519MLKEM768 == true },
            "fp" to if (anywhere) anywhereFingerprint(n.fingerprint) else n.fingerprint,
            "allowInsecure" to "1".takeIf { !anywhere && n.skipCertificateVerification },
            "pinSHA256" to n.certificateFingerprint, "udp" to n.udpRelayEnabled).toMutableMap()
        when (kind) {
            ProxyKind.WIREGUARD -> fields.putAll(fieldMap("publickey" to n.wireGuardPublicKey, "presharedkey" to n.wireGuardPreSharedKey,
                "address" to wgAddresses(n).joinToString(","), "allowedips" to wgAllowed(n).joinToString(","), "mtu" to n.wireGuardMTU,
                "reserved" to n.wireGuardReserved, "keepalive" to n.wireGuardPersistentKeepalive, "dns" to n.wireGuardDNS))
            ProxyKind.HYSTERIA, ProxyKind.HYSTERIA2 -> fields.putAll(fieldMap("obfs" to n.obfs, "obfs-password" to n.obfsParam,
                "mport" to n.portHopping, "upmbps" to n.upMbps, "downmbps" to n.downMbps, "protocol" to n.protocolName))
            ProxyKind.TUIC -> fields.putAll(fieldMap("congestion_control" to n.congestionControl, "udp_relay_mode" to n.udpRelayMode))
            ProxyKind.ANYTLS -> fields.putAll(fieldMap((if (anywhere) "ici" else "idle-session-check-interval") to n.idleSessionCheckInterval,
                (if (anywhere) "it" else "idle-session-timeout") to n.idleSessionTimeout,
                (if (anywhere) "mis" else "min-idle-session") to n.minIdleSession))
            ProxyKind.SNELL -> fields.putAll(fieldMap("version" to n.version, "obfs" to n.obfs, "obfs-host" to n.obfsParam))
            ProxyKind.SSH -> fields.putAll(fieldMap("private-key" to n.ssh?.privateKey, "private-key-passphrase" to n.ssh?.privateKeyPassphrase,
                "host-key" to n.ssh?.hostKeys?.joinToString(","), "host-key-algorithms" to n.ssh?.hostKeyAlgorithms?.joinToString(",")))
            ProxyKind.TRUSTTUNNEL -> fields.putAll(fieldMap("quic" to true.takeIf { n.trustTunnel?.quic == true },
                "client-random-auth-key" to n.trustTunnel?.clientRandomAuthKey))
            ProxyKind.MASQUE -> fields.putAll(fieldMap("private-key" to n.masque?.privateKey, "public-key" to n.masque?.publicKey,
                "ip" to n.masque?.ipv4, "ipv6" to n.masque?.ipv6, "network" to n.masque?.network, "mtu" to n.masque?.mtu,
                "dns" to n.masque?.dns?.joinToString(","), "remote-dns-resolve" to n.masque?.remoteDNSResolve,
                "http-version" to n.masque?.httpVersion, "disable-version-fallback" to n.masque?.disableVersionFallback))
            else -> Unit
        }
        val query = if (anywhere && kind == ProxyKind.SOCKS5) "" else fields.entries.joinToString("&") {
            percentEncode(it.key) + "=" + percentEncode(it.value.toString())
        }.let { if (it.isEmpty()) "" else "?$it" }
        val name = if (kind == ProxyKind.HTTP && n.tls) "https" else kind.rawValue
        return "$name://$login${endpoint(n)}$query$remark"
    }

    private val anywhereNativeFingerprints = setOf("chrome_133", "chrome_120", "chrome_106", "firefox_148",
        "firefox_120", "safari_26", "edge_106", "non_browser")

    private fun anywhereFingerprint(value: String?): String? = when (value?.lowercase(Locale.ROOT)) {
        "chrome" -> "chrome_133"
        "firefox" -> "firefox_148"
        "safari" -> "safari_26"
        "ios" -> "chrome_120"
        "edge" -> "edge_106"
        "random", "randomized" -> null
        else -> value
    }

    internal fun anywhere(n: ProxyNode): Any? {
        if (!n.skipCertificateVerification) return uri(n, true)
        return fieldMap("name" to n.name, "protocol" to n.kind.rawValue, "server" to n.server, "port" to n.port,
            "uuid" to n.uuid, "password" to n.password, "encryption" to n.vlessEncryption, "flow" to n.flow,
            "transport" to transport(n.transport)?.let { fieldMap("type" to it, "path" to absolute(n.path), "host" to n.hostHeader,
                "serviceName" to n.path?.trimStart('/')?.takeIf { _ -> it == "grpc" }, "mode" to n.transportMode) },
            "security" to fieldMap("type" to "tls", "insecure" to true, "sni" to sni(n),
                "fingerprint" to (anywhereFingerprint(n.fingerprint) ?: "plain"), "alpn" to n.alpn?.strings()),
            "idleCheckInterval" to n.idleSessionCheckInterval, "idleTimeout" to n.idleSessionTimeout, "minIdleSession" to n.minIdleSession)
    }

    internal data class SingNode(val outbound: Fields? = null, val endpoint: Fields? = null, val helper: Fields? = null)

    internal fun sing(n: ProxyNode, target: ClientTarget, helperName: String): SingNode {
        val kind = n.kind
        if (kind == ProxyKind.WIREGUARD && target == ClientTarget.SING_BOX) {
            return SingNode(endpoint = fieldMap("type" to "wireguard", "tag" to n.name,
                "address" to wgAddresses(n), "private_key" to n.wireGuardPrivateKey, "mtu" to n.wireGuardMTU,
                "peers" to listOf(fieldMap("address" to n.server, "port" to n.port, "public_key" to n.wireGuardPublicKey,
                    "pre_shared_key" to n.wireGuardPreSharedKey, "allowed_ips" to wgAllowed(n), "reserved" to reserved(n),
                    "persistent_keepalive_interval" to n.wireGuardPersistentKeepalive))))
        }
        val p = linkedMapOf<String, Any?>("type" to kind.rawValue, "tag" to n.name, "server" to n.server, "server_port" to n.port)
        if (n.tls && kind !in setOf(ProxyKind.SS, ProxyKind.SNELL, ProxyKind.SSH, ProxyKind.WIREGUARD)) {
            p["tls"] = fieldMap("enabled" to true, "server_name" to (sni(n) ?: n.server), "insecure" to n.skipCertificateVerification,
                "alpn" to n.alpn?.strings(), "utls" to (n.fingerprint ?: "chrome".takeIf { n.realityPublicKey != null })?.let {
                    fieldMap("enabled" to true, "fingerprint" to it) },
                "reality" to n.realityPublicKey?.let { fieldMap("enabled" to true, "public_key" to it, "short_id" to n.realityShortID) })
        }
        val options = when (kind) {
            ProxyKind.SS -> {
                val plugin = when { v2Plugin(n) -> "v2ray-plugin"; simpleObfs(n) != null -> "obfs-local"; else -> null }
                val pluginOpts = when (plugin) {
                    "v2ray-plugin" -> listOfNotNull("mode=websocket", "tls".takeIf { n.tls }, n.hostHeader?.let { "host=$it" },
                        n.path?.let { "path=" + absolute(it) }, n.pluginMux?.let { "mux=" + if (it) "1" else "0" },
                        "skip-cert-verify=true".takeIf { n.skipCertificateVerification }).joinToString(";")
                    "obfs-local" -> "obfs=${n.obfs}" + (n.hostHeader ?: n.obfsParam)?.let { ";obfs-host=$it" }.orEmpty()
                    else -> null
                }
                fieldMap("type" to "shadowsocks", "method" to n.cipher, "password" to n.password, "plugin" to plugin, "plugin_opts" to pluginOpts,
                    "network" to "tcp".takeIf { n.udpRelayEnabled == false || n.shadowTLS != null }, "detour" to helperName.takeIf { n.shadowTLS != null })
            }
            ProxyKind.VMESS -> fieldMap("uuid" to n.uuid?.let(::uuid), "security" to (n.cipher?.takeUnless { it == "auto" } ?: "aes-128-gcm"),
                "alter_id" to (n.alterID ?: 0))
            ProxyKind.VLESS -> fieldMap("uuid" to n.uuid?.let(::uuid), "flow" to n.flow)
            ProxyKind.TROJAN -> fieldMap("password" to n.password)
            ProxyKind.HYSTERIA -> fieldMap("auth_str" to n.password, "up_mbps" to (n.upMbps ?: 50), "down_mbps" to (n.downMbps ?: 100), "obfs" to n.obfs)
            ProxyKind.HYSTERIA2 -> fieldMap("password" to n.password, "up_mbps" to n.upMbps, "down_mbps" to n.downMbps,
                "obfs" to n.obfsParam?.let { fieldMap("type" to n.obfs, "password" to it) },
                "server_ports" to n.portHopping?.strings()?.map { if (':' in it || '-' in it) it.replace('-', ':') else "$it:$it" })
            ProxyKind.TUIC -> fieldMap("uuid" to n.uuid?.let(::uuid), "password" to n.password,
                "congestion_control" to n.congestionControl, "udp_relay_mode" to n.udpRelayMode)
            ProxyKind.WIREGUARD -> fieldMap("local_address" to wgAddresses(n), "private_key" to n.wireGuardPrivateKey,
                "peer_public_key" to n.wireGuardPublicKey, "pre_shared_key" to n.wireGuardPreSharedKey,
                "reserved" to reserved(n), "mtu" to n.wireGuardMTU)
            ProxyKind.ANYTLS -> fieldMap("password" to n.password, "idle_session_check_interval" to n.idleSessionCheckInterval?.let { "${it}s" },
                "idle_session_timeout" to n.idleSessionTimeout?.let { "${it}s" }, "min_idle_session" to n.minIdleSession)
            ProxyKind.SNELL -> fieldMap("psk" to n.password, "version" to 4, "obfs_mode" to simpleObfs(n), "obfs_host" to n.obfsParam)
            ProxyKind.HTTP, ProxyKind.SOCKS5 -> fieldMap("type" to if (kind == ProxyKind.SOCKS5) "socks" else "http",
                "version" to "5".takeIf { kind == ProxyKind.SOCKS5 }, "username" to n.username, "password" to n.password)
            ProxyKind.SSH -> fieldMap("user" to n.username, "password" to n.password, "private_key" to n.ssh?.privateKey,
                "private_key_passphrase" to n.ssh?.privateKeyPassphrase, "host_key" to n.ssh?.hostKeys?.ifEmpty { null },
                "host_key_algorithms" to n.ssh?.hostKeyAlgorithms?.ifEmpty { null })
            else -> error("Unsupported sing-box proxy type")
        }
        p.putAll(options)
        val network = transport(n.transport)
        if (kind in v2Kinds && network != null) {
            p["transport"] = fieldMap(
                "type" to if (network == "h2") "http" else network,
                "service_name" to n.path?.trimStart('/')?.takeIf { network == "grpc" },
                "path" to when (network) { "grpc", "quic" -> null; "ws" -> wsPath(n.path); else -> absolute(n.path) },
                "host" to n.hostHeader?.let { when (network) { "httpupgrade" -> it; "http", "h2" -> listOf(it); else -> null } },
                "headers" to n.hostHeader?.takeIf { network == "ws" }?.let { mapOf("Host" to it) },
                "max_early_data" to earlyData(n.path)?.takeIf { network == "ws" },
                "early_data_header_name" to "Sec-WebSocket-Protocol".takeIf { network == "ws" && earlyData(n.path) != null },
            )
        }
        val helper = n.shadowTLS?.let { shadow -> fieldMap("type" to "shadowtls", "tag" to helperName, "server" to n.server,
            "server_port" to n.port, "version" to shadow.version, "password" to shadow.password,
            "tls" to fieldMap("enabled" to true, "server_name" to shadow.host, "insecure" to shadow.skipCertificateVerification,
                "utls" to n.fingerprint?.let { fieldMap("enabled" to true, "fingerprint" to it) })) }
        return SingNode(p, helper = helper)
    }

    internal fun egern(n: ProxyNode): Fields {
        val kind = n.kind
        val type = when (kind) { ProxyKind.SS -> "shadowsocks"; ProxyKind.SSR -> "shadowsocksr"; else -> kind.rawValue }
        val p = linkedMapOf<String, Any?>("name" to n.name, "server" to n.server, "port" to n.port, "password" to n.password)
        val options = when (kind) {
            ProxyKind.SS, ProxyKind.SSR -> fieldMap("method" to if (n.cipher == "chacha20-ietf-poly1305") "chacha20-poly1305" else n.cipher,
                "protocol" to n.protocolName, "obfs" to n.obfs, "protocol_param" to n.protocolParam, "obfs_param" to n.obfsParam,
                "obfs_host" to (n.hostHeader ?: n.obfsParam).takeIf { kind == ProxyKind.SS },
                "shadow_tls" to n.shadowTLS?.let { fieldMap("password" to it.password, "sni" to it.host) })
            ProxyKind.VMESS, ProxyKind.VLESS -> {
                val network = transport(n.transport)
                val transportName = when (network) {
                    null -> "tls".takeIf { n.tls }
                    "ws" -> if (n.tls) "wss" else "ws"
                    "http" -> "http1"
                    "h2" -> "http2"
                    else -> network
                }
                fieldMap("user_id" to n.uuid?.let(::uuid), "flow" to n.flow, "legacy" to false.takeIf { kind == ProxyKind.VMESS },
                    "security" to (n.cipher?.takeUnless { it == "auto" } ?: "aes-128-gcm").takeIf { kind == ProxyKind.VMESS },
                    "transport" to transportName?.let { name -> fieldMap(name to fieldMap(
                        "path" to (wsPath(n.path) ?: "/").takeIf { network != null && network != "grpc" },
                        "headers" to n.hostHeader?.takeIf { network != null && network != "grpc" }?.let { mapOf("Host" to it) },
                        "service_name" to n.path?.trimStart('/')?.takeIf { network == "grpc" }, "sni" to sni(n).takeIf { n.tls },
                        "reality" to n.realityPublicKey?.let { fieldMap("public_key" to it, "short_id" to n.realityShortID) },
                        "skip_tls_verify" to n.skipCertificateVerification.takeIf { n.tls }, "fingerprint_sha256" to n.certificateFingerprint)) })
            }
            ProxyKind.TROJAN -> fieldMap("websocket" to (n.transport == "ws").takeIf { it }?.let {
                fieldMap("path" to (wsPath(n.path) ?: "/"), "host" to n.hostHeader) })
            ProxyKind.HYSTERIA2 -> { p.remove("password"); fieldMap("auth" to n.password, "obfs" to n.obfs?.takeIf { n.obfsParam != null },
                "obfs_password" to n.obfsParam, "port_hopping" to n.portHopping, "bandwidth" to n.upMbps) }
            ProxyKind.TUIC -> fieldMap("uuid" to n.uuid?.let(::uuid), "alpn" to (n.alpn?.strings() ?: listOf("h3")), "udp_relay_mode" to n.udpRelayMode)
            ProxyKind.WIREGUARD -> fieldMap("private_key" to n.wireGuardPrivateKey, "peer_public_key" to n.wireGuardPublicKey,
                "preshared_key" to n.wireGuardPreSharedKey, "local_ipv4" to n.wireGuardIPv4, "local_ipv6" to n.wireGuardIPv6,
                "reserved" to reserved(n), "mtu" to n.wireGuardMTU, "dns_servers" to n.wireGuardDNS?.strings(),
                "keepalive" to n.wireGuardPersistentKeepalive)
            ProxyKind.ANYTLS -> fieldMap("idle_session_check_interval" to n.idleSessionCheckInterval,
                "idle_session_timeout" to n.idleSessionTimeout, "min_idle_session" to n.minIdleSession)
            ProxyKind.SNELL -> { p.remove("password"); fieldMap("psk" to n.password, "version" to (n.version ?: 4)) }
            ProxyKind.SSH -> fieldMap("username" to n.username, "private_key" to n.ssh?.privateKey, "host_keys" to n.ssh?.hostKeys?.ifEmpty { null })
            ProxyKind.HTTP, ProxyKind.SOCKS5 -> fieldMap("username" to n.username, "tls" to n.tls)
            else -> error("Unsupported Egern proxy type")
        }
        p.putAll(options)
        if (n.tls && kind !in setOf(ProxyKind.VMESS, ProxyKind.VLESS)) p.putAll(fieldMap("sni" to sni(n),
            "skip_tls_verify" to n.skipCertificateVerification, "fingerprint_sha256" to n.certificateFingerprint))
        return mapOf(type to compact(p))
    }
}
