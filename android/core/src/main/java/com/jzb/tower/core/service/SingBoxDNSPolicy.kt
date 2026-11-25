package com.jzb.tower.core.service

import com.jzb.tower.core.model.ClientTarget
import com.jzb.tower.core.model.DnsProtectionMode
import java.net.URI

internal object SingBoxDNSPolicy {
    private const val ruleMode = "规则判定"
    private const val globalMode = "全局代理"
    private const val directMode = "直接连接"
    private const val chinaSubnet = "114.114.114.0/24"
    private val domainKeys = setOf("domain", "domain_suffix", "domain_keyword", "domain_regex")

    data class Profile(
        val dns: Fields,
        val route: Fields,
        val tun: Fields,
        val outbounds: List<Fields>,
        val experimental: Fields?,
        val httpClients: List<Fields>?,
    )

    fun build(target: ClientTarget, mode: DnsProtectionMode, ipv6: Boolean, local: List<String>, upstream: List<String>,
              proxy: String?, primary: String, rules: RoutingOutput, tailnet: TailnetOutput, outbounds: List<Fields>,
              nodeTags: List<String>, taken: MutableSet<String>, exclusions: List<String>?): Profile {
        val official = target == ClientTarget.SING_BOX
        val follow = mode == DnsProtectionMode.FOLLOW_SCHEME
        val strict = mode == DnsProtectionMode.STRICT
        val output = outbounds.toMutableList()
        val definitions = outbounds.associateBy { it["tag"] }
        fun direct(tag: String, visited: Set<String> = emptySet()): Boolean {
            if (tag == "direct") return true
            if (tag in visited) return false
            val entry = definitions[tag] ?: return false
            val first = (entry["outbounds"] as? List<*>)?.firstOrNull() as? String
            return entry["type"] == "selector" && first != null && direct(first, visited + tag)
        }
        val remote = dnsServer(upstream.first(), "remote", proxy)
        val localServer = dnsServer(local.first(), "local", null)
        val servers = mutableListOf<Fields>(localServer, mapOf("type" to "local", "tag" to "bootstrap"))
        if (proxy != null || strict || (follow && upstream.isNotEmpty())) servers.add(remote)
        servers.addAll(tailnet.dnsServers)
        val fallback = if (proxy == null && !strict) "local" else "remote"
        val routedFinal = rules.final.takeUnless(::isBlock) ?: primary
        val finalDns = if (!strict && direct(routedFinal)) "local" else fallback
        val dnsRules = tailnet.dnsRules.toMutableList()
        val routeRules = mutableListOf<Fields>(mapOf("action" to "sniff"),
            if (follow) mapOf("protocol" to "dns", "action" to "hijack-dns")
            else mapOf("type" to "logical", "mode" to "or", "rules" to listOf(mapOf("protocol" to "dns"), mapOf("port" to 53)),
                "action" to "hijack-dns"))
        routeRules.addAll(tailnet.singRules)
        if (official) {
            routeRules.add(mapOf("action" to "resolve"))
            val global = if (nodeTags.isNotEmpty()) {
                val automatic = outbounds.firstOrNull { it["type"] == "urltest" &&
                    (it["outbounds"] as? List<*>)?.toSet() == nodeTags.toSet() }?.get("tag") as? String
                    ?: uniqueName("全局自动选择", taken).also { name -> output.add(mapOf("type" to "urltest", "tag" to name,
                        "outbounds" to nodeTags, "url" to testURL, "interval" to "300s", "tolerance" to 50)) }
                val name = uniqueName(globalMode, taken)
                output.add(0, mapOf("type" to "selector", "tag" to name, "outbounds" to (listOf(automatic) + nodeTags),
                    "default" to automatic, "interrupt_exist_connections" to true))
                servers.add(remote + mapOf("tag" to "remote-global", "detour" to name))
                name
            } else null
            dnsRules.add(mapOf("clash_mode" to directMode, "action" to "route", "server" to "local"))
            dnsRules.add(if (global == null) mapOf("clash_mode" to globalMode, "action" to "reject")
                else mapOf("clash_mode" to globalMode, "action" to "route", "server" to "remote-global"))
            routeRules.add(if (global == null) mapOf("clash_mode" to globalMode, "action" to "reject")
                else mapOf("clash_mode" to globalMode, "action" to "route", "outbound" to global))
            routeRules.add(mapOf("clash_mode" to directMode, "action" to "route", "outbound" to "direct"))
        }
        val china = official && !follow && proxy != null && finalDns == "remote" &&
            rules.ruleSets.any { it["tag"] == "tower-geoip-cn" }
        if (china) servers.add(dnsServer("https://8.8.8.8/dns-query", "remote-cn", proxy) +
            ("tls" to mapOf("enabled" to true, "server_name" to "dns.google")))
        if (!follow) {
            rules.objects.forEach { rule ->
                val domainSet = (rule["rule_set"] as? List<*>)?.singleOrNull()?.let { rules.domainRuleSets[it] }
                val predicates = if (domainSet != null) mapOf("rule_set" to listOf(domainSet)) else rule.filterKeys { it in domainKeys }
                if (predicates.isEmpty()) return@forEach
                val usesDirect = (rule["outbound"] as? String)?.let { direct(it) } == true
                dnsRules.add(predicates + when {
                    rule["action"] == "reject" -> mapOf("action" to "reject")
                    usesDirect && strict && china -> mapOf("action" to "route", "server" to "remote-cn", "client_subnet" to chinaSubnet)
                    usesDirect && !strict -> mapOf("action" to "route", "server" to "local")
                    else -> mapOf("action" to "route", "server" to fallback)
                })
            }
        }
        if (china) {
            dnsRules.add(mapOf("query_type" to listOf("A", "AAAA"), "action" to "evaluate", "server" to "remote-cn", "client_subnet" to chinaSubnet))
            dnsRules.add(mapOf("match_response" to true, "rule_set" to listOf("tower-geoip-cn"), "action" to "respond"))
        }
        routeRules.addAll(rules.objects)
        if (isBlock(rules.final)) routeRules.add(mapOf("action" to "reject"))
        val ruleSets = rules.ruleSets.ifEmpty { null }
        val httpClients = if (official && ruleSets?.any { it["type"] == "remote" } == true) listOf(fieldMap("tag" to "tower-rule-set", "engine" to "go",
            "domain_resolver" to "local", "detour" to proxy)) else null
        return Profile(
            fieldMap("servers" to servers, "rules" to dnsRules, "final" to finalDns, "strategy" to if (ipv6) "prefer_ipv4" else "ipv4_only"),
            fieldMap("auto_detect_interface" to true, "default_domain_resolver" to "local", "final" to routedFinal,
                "rules" to routeRules, "rule_set" to ruleSets, "default_http_client" to "tower-rule-set".takeIf { httpClients != null }),
            fieldMap("type" to "tun", "tag" to "tun-in", "address" to (listOf("172.19.0.1/30") + listOfNotNull("fdfe:dcba:9876::1/126".takeIf { ipv6 })),
                "auto_route" to true, "strict_route" to if (official) !follow else strict, "stack" to "mixed",
                "route_exclude_address" to exclusions?.ifEmpty { null }),
            output, if (official) mapOf("clash_api" to mapOf("default_mode" to ruleMode)) else null, httpClients,
        )
    }

    private fun dnsServer(value: String, tag: String, detour: String?): Fields {
        if (literalIP(value) != null) return fieldMap("type" to "udp", "tag" to tag, "server" to value, "detour" to detour)
        val url = runCatching { URI(value) }.getOrNull()
        val host = url?.host?.removeSurrounding("[", "]")
        val type = url?.scheme
        if (host == null || type !in setOf("https", "tls", "udp", "tcp", "quic", "h3")) return mapOf("type" to "local", "tag" to tag)
        return fieldMap("type" to type, "tag" to tag, "server" to host, "domain_resolver" to "bootstrap".takeIf { literalIP(host) == null },
            "server_port" to url.port.takeIf { it > 0 }, "path" to url.rawPath?.takeIf { it.isNotEmpty() && it != "/" && type in setOf("https", "h3") },
            "detour" to detour)
    }
}
