package com.jzb.tower.core.service

import com.jzb.tower.core.contract.TailnetSecretStore
import com.jzb.tower.core.model.*
import java.util.Locale

internal data class TailnetOutput(
    val proxyLines: List<String> = emptyList(),
    val sections: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val proxies: List<Fields> = emptyList(),
    val endpoints: List<Fields> = emptyList(),
    val singRules: List<Fields> = emptyList(),
    val dnsServers: List<Fields> = emptyList(),
    val dnsRules: List<Fields> = emptyList(),
    val warnings: List<String> = emptyList(),
)

class TailnetConfigurationWriter(private val secrets: TailnetSecretStore) {
    internal fun write(connections: List<TailnetConnection>, target: ClientTarget, maskSecrets: Boolean,
                       taken: MutableSet<String>): TailnetOutput {
        val active = connections.filter { it.isEnabled }
        if (active.isEmpty()) return TailnetOutput()
        val surge = target in surgeTargets
        val mihomo = target in mihomoTargets
        val stash = target == ClientTarget.CLASH
        val sing = target == ClientTarget.SING_BOX
        if (!(surge || mihomo || stash || sing))
            return TailnetOutput(warnings = listOf("${ClientCatalog.meta(target).name} 不支持 Tailscale，已跳过。"))
        val proxyLines = mutableListOf<String>()
        val sections = mutableListOf<String>()
        val rules = mutableListOf<String>()
        val proxies = mutableListOf<Fields>()
        val endpoints = mutableListOf<Fields>()
        val singRules = mutableListOf<Fields>()
        val dnsServers = mutableListOf<Fields>()
        val dnsRules = mutableListOf<Fields>()
        val warnings = mutableListOf<String>()
        val claimed = mutableSetOf<String>()
        val dnsTags = mutableSetOf("local", "remote", "bootstrap")
        for (raw in active) {
            val connection = normalize(raw)
            val name = uniqueName(confName(connection.name).replace('"', '＂').ifEmpty { "Tailscale" }, taken)
            val slug = connection.stableSlug
            val client = if (stash) "stash" else target.rawValue
            val hostname = (connection.deviceName ?: "tower").take(63 - client.length - 1).trimEnd('-') + "-$client"
            val key = if (!surge) {
                if (maskSecrets) {
                    if (secrets.hasAuthKey(connection.id)) MASKED_KEY else null
                } else secrets.getAuthKey(connection.id)
            } else null
            val control = connection.controlURLString
            val domain = connection.magicDNSSuffix
            val cidrs = (listOf(IPv4, IPv6) + connection.subnets).distinct()
            when {
                surge -> {
                    proxyLines.add("$name = tailscale, section-name=$slug")
                    sections.add(listOfNotNull("[Tailscale $slug]", "interactive-login = true", control?.let { "control-url = $it" },
                        "hostname = $hostname").joinToString("\n"))
                }
                sing -> {
                    endpoints.add(fieldMap("type" to "tailscale", "tag" to name, "state_directory" to slug,
                        "hostname" to hostname, "accept_routes" to true, "control_url" to control, "auth_key" to key))
                    if (domain != null) {
                        val tag = uniqueName("$name DNS", dnsTags)
                        dnsServers.add(mapOf("type" to "tailscale", "tag" to tag, "endpoint" to name))
                        dnsRules.add(mapOf("action" to "route", "domain_suffix" to listOf(domain), "server" to tag))
                        singRules.add(mapOf("action" to "route", "domain_suffix" to listOf(domain), "outbound" to name))
                    }
                    singRules.add(mapOf("action" to "route", "ip_cidr" to cidrs, "outbound" to name))
                }
                else -> proxies.add(fieldMap("name" to name, "type" to "tailscale", "hostname" to hostname,
                    "control-url" to control, "auth-key" to key, "state-dir" to slug.takeIf { mihomo },
                    "udp" to true.takeIf { mihomo }, "accept-routes" to true.takeIf { mihomo }))
            }
            if (!sing) {
                if (domain != null) rules.add("DOMAIN-SUFFIX,$domain,$name")
                cidrs.forEach { rules.add("IP-CIDR${if (':' in it) "6" else ""},$it,$name,no-resolve") }
            }
            if (!surge && key == null) warnings.add("“$name”没有 Auth Key。")
            if (stash) warnings.add(if (domain == null) "“$name”未填写 MagicDNS 后缀，Stash 默认跳过 Tailscale 地址段。"
                else "Stash 默认跳过 Tailscale 地址段和局域网网段，可使用“设备名.$domain”访问“$name”。")
            (cidrs + listOfNotNull(domain)).forEach { route ->
                if (!claimed.add(route) && route != IPv4 && route != IPv6) warnings.add("Tailscale 路由 $route 已由先声明的连接匹配。")
            }
        }
        return TailnetOutput(proxyLines, sections, rules, proxies, endpoints, singRules, dnsServers, dnsRules, warnings)
    }

    companion object {
        const val MASKED_KEY = "tskey-auth-***"
        private const val IPv4 = "100.64.0.0/10"
        private const val IPv6 = "fd7a:115c:a1e0::/48"

        fun stableSlug(id: String): String = "tower-tailnet-" +
            if (Regex("(?i)^[a-f0-9]{8}-").containsMatchIn(id)) id.take(8).lowercase(Locale.ROOT) else sha256(id).take(8)

        fun normalize(connection: TailnetConnection): TailnetConnection {
            val control = connection.controlURLString?.trim()?.takeIf(String::isNotEmpty)?.let {
                webURL(it, true).also { url -> require(url.fragment == null && url.query == null) { "无效的 Tailscale 控制地址" } }.toString()
            }
            val subnets = connection.subnets.map(String::trim).filter(String::isNotEmpty).distinct()
            require(subnets.all(::validCidr)) { "无效的 Tailscale 子网" }
            val domain = connection.magicDNSSuffix?.trim()?.trim('.')?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
            require(domain == null || (domain.length <= 253 && '.' in domain &&
                domain.split('.').all { it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) })) { "无效的 MagicDNS 后缀" }
            val device = connection.deviceName?.lowercase(Locale.ROOT)?.replace(Regex("[^a-z0-9]+"), "-")
                ?.trim('-')?.take(40)?.trimEnd('-')?.ifEmpty { null }
            return connection.copy(name = cleanLine(connection.name).ifEmpty { "Tailscale" }, controlURLString = control,
                deviceName = device, subnets = subnets, magicDNSSuffix = domain, stableSlug = stableSlug(connection.id))
        }
    }
}

internal fun uniqueName(base: String, taken: MutableSet<String>): String {
    var name = base
    var suffix = 2
    while (!taken.add(name.lowercase(Locale.ROOT))) name = "$base (${suffix++})"
    return name
}
