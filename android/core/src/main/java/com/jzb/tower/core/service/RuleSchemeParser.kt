package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import java.util.Locale
import java.util.UUID

internal val builtinPolicies = setOf("DIRECT", "REJECT", "REJECT-DROP", "REJECT-NO-DROP", "REJECT-TINYGIF",
    "REJECT-IMG", "PASS", "PASS-RULE", "COMPATIBLE", "CELLULAR", "CELLULAR-ONLY", "HYBRID", "NO-HYBRID")
internal fun isBuiltinPolicy(name: String) = name.uppercase(Locale.ROOT) in builtinPolicies

internal data class RuleCondition(val type: String, val value: String, val options: List<String> = emptyList()) {
    val line: String get() = if (value.isEmpty()) type else "$type,$value"
}

internal fun splitRule(line: String): RuleCondition? {
    if (line.any { it == '\r' || it == '\n' || it == '\u0085' || it == '\u2028' || it == '\u2029' }) return null
    val fields = splitFields(line)
    val type = when (val raw = fields.first().uppercase(Locale.ROOT)) {
        "MATCH" -> "FINAL"
        "HOST" -> "DOMAIN"
        "HOST-SUFFIX" -> "DOMAIN-SUFFIX"
        "HOST-KEYWORD" -> "DOMAIN-KEYWORD"
        "IP6-CIDR" -> "IP-CIDR6"
        else -> raw
    }
    if (type == "FINAL") return RuleCondition(type, "", fields.drop(1))
    val value = fields.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
    if (!type.matches(Regex("[A-Z][A-Z0-9-]*"))) return null
    return RuleCondition(type, unquote(value), fields.drop(2).filter(String::isNotEmpty))
}

internal fun ruleLines(text: String): List<String> {
    val lines = if (Regex("(?m)^\\s*payload\\s*:").containsMatchIn(text)) yamlObject(text).list("payload")
        else text.lineSequence().toList()
    return lines.mapNotNull { raw ->
        val line = raw.trim().removePrefix("\uFEFF")
        when {
            line.isEmpty() || line.startsWith('#') || line.startsWith(';') || line.startsWith("//") || line.startsWith('[') -> null
            ',' in line || line.equals("FINAL", true) || line.equals("MATCH", true) -> line
            line.matches(Regex("[A-Fa-f0-9:.]+/\\d+")) -> (if (':' in line) "IP-CIDR6," else "IP-CIDR,") + line
            line.matches(Regex("\\*\\.[\\p{L}\\p{N}.-]+")) -> "DOMAIN-WILDCARD,$line"
            line.matches(Regex("\\+?\\.[\\p{L}\\p{N}.-]+")) -> "DOMAIN-SUFFIX," + line.removePrefix("+").removePrefix(".")
            line.matches(Regex("[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+")) -> "DOMAIN,$line"
            else -> null
        }
    }
}

object RuleSchemeParser {
    private val yamlKeys = Regex("(?m)^\\s*(?:proxy-groups|rule-providers|rules|proxies)\\s*:")
    private fun kind(input: String): RuleSchemeGroupKind {
        val raw = when (val type = input.lowercase(Locale.ROOT)) {
            "static", "selector" -> "select"
            "url-latency-benchmark", "auto_test", "urltest" -> "url-test"
            "available" -> "fallback"
            "loadbalance", "load_balance", "round-robin", "dest-hash" -> "load-balance"
            "conditional", "subnet" -> "ssid"
            else -> type
        }
        return RuleSchemeGroupKind.entries.firstOrNull { it.rawValue == raw } ?: RuleSchemeGroupKind.UNSUPPORTED
    }

    fun parse(
        content: String,
        name: String? = null,
        sourceURLString: String? = null,
        id: String = UUID.randomUUID().toString(),
        summary: String = "",
        isBundled: Boolean = false,
    ): RuleScheme {
        val sourceUrl = sourceURLString?.let { webURL(it, true).toString() }
        val text = content.trim().removePrefix("\uFEFF")
        val (groups, rulesets, network) = if (yamlKeys.containsMatchIn(text)) parseYaml(text) else parseIni(text)
        require(groups.isNotEmpty() || rulesets.isNotEmpty()) { "未找到策略组或分流规则" }
        return RuleScheme(id, cleanLine(name.orEmpty()).take(256).ifEmpty { "导入的规则方案" }, summary,
            sourceUrl, if (isBundled) null else persistableSource(text), network, groups.filter { it.name.isNotEmpty() && !isBuiltinPolicy(it.name) }
                .distinctBy { it.name }, rulesets, if (isBundled) null else System.currentTimeMillis(), isBundled = isBundled)
    }

    private fun persistableSource(text: String): String? {
        val sanitized = buildString {
            var skipIni = false
            var skipYaml = false
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith('[') && line.endsWith(']')) {
                    val section = line.removeSurrounding("[", "]").lowercase(Locale.ROOT)
                    skipIni = section in setOf("proxy", "keystore") || section.startsWith("wireguard ") || section.startsWith("tailscale ")
                    if (skipIni) continue
                }
                if (skipIni) continue
                val topLevelKey = if (raw.firstOrNull()?.isWhitespace() == false && ':' in line && !line.startsWith('-') &&
                    !line.startsWith('#')) line.substringBefore(':').trim().trim('"', '\'').lowercase(Locale.ROOT) else null
                if (topLevelKey != null) skipYaml = topLevelKey in setOf("proxies", "proxy-providers")
                if (!skipYaml) appendLine(raw)
            }
        }.trimEnd()
        return sanitized.takeIf { it.toByteArray(Charsets.UTF_8).size <= 512 * 1024 }
    }

    private fun parseRule(line: String, remote: Map<String, String>, local: Map<String, List<String>>): List<RuleSchemeRuleset> {
        val condition = splitRule(line) ?: return emptyList()
        val policy = condition.options.firstOrNull() ?: return emptyList()
        val options = condition.options.drop(1)
        if (condition.type in setOf("RULE-SET", "DOMAIN-SET")) {
            val pointer = condition.value
            (local[pointer] ?: local[pointer.lowercase(Locale.ROOT)])?.let {
                return it.map { item -> RuleSchemeRuleset(policy, RuleSchemeResource.Inline(item), options) }
            }
            val url = remote[pointer] ?: pointer
            if (url.startsWith("http:", true) || url.startsWith("https:", true)) {
                return listOf(RuleSchemeRuleset(policy, RuleSchemeResource.Remote(webURL(url, true).toString()), options))
            }
        }
        return listOf(RuleSchemeRuleset(policy, RuleSchemeResource.Inline(condition.line), options))
    }

    private fun parseIni(text: String): Triple<List<RuleSchemeGroup>, List<RuleSchemeRuleset>, RuleSchemeNetworkSettings> {
        val sections = iniSections(text)
        val groups = mutableListOf<RuleSchemeGroup>()
        val rules = mutableListOf<RuleSchemeRuleset>()
        val local = sections.filterKeys { it.startsWith("ruleset ") }.mapKeys { it.key.removePrefix("ruleset ") }
            .mapValues { ruleLines(it.value.joinToString("\n")) }
        val general = keyValues(sections["general"].orEmpty())
        val network = RuleSchemeNetworkSettings(general.list("dns-server"), general.list("encrypted-dns-server"),
            general.list("direct-dns-server"), general.list("skip-proxy"), general.list("tun-excluded-routes"), general["ipv6"]?.truth())
        for (line in sections["custom"].orEmpty() + sections[""].orEmpty()) {
            val value = line.substringAfter('=', "").trim()
            when (line.substringBefore('=').trim().lowercase(Locale.ROOT)) {
                "ruleset" -> {
                    val policy = value.substringBefore(',').trim()
                    val target = value.substringAfter(',', "").trim()
                    if (policy.isEmpty() || target.isEmpty()) continue
                    if (target.startsWith("[]")) {
                        splitRule(target.removePrefix("[]"))?.let {
                            rules.add(RuleSchemeRuleset(policy, RuleSchemeResource.Inline(it.line), it.options))
                        }
                    } else rules.add(RuleSchemeRuleset(policy, RuleSchemeResource.Remote(webURL(target, true).toString())))
                }
                "custom_proxy_group" -> {
                    val parts = value.split('`').map(String::trim)
                    if (parts.size < 2) continue
                    val members = parts.drop(2)
                    val index = members.indexOfFirst { it.startsWith("http://", true) || it.startsWith("https://", true) }
                    val tuning = if (index >= 0) members.getOrNull(index + 1)?.split(',').orEmpty() else emptyList()
                    groups.add(RuleSchemeGroup(cleanLine(parts[0]), kind(parts[1]),
                        (if (index >= 0) members.take(index) else members).filter(String::isNotEmpty).map {
                            if (it.startsWith("[]")) RuleSchemeGroupMember.Reference(it.removePrefix("[]"))
                            else RuleSchemeGroupMember.NodePattern(it)
                        }, if (index >= 0) members[index] else null, tuning.getOrNull(0).integer(), tuning.getOrNull(2).integer()))
                }
            }
        }
        for (line in sections["proxy group"].orEmpty()) {
            if ('=' !in line) continue
            val fields = splitFields(line.substringAfter('='))
            val options = keyValues(fields.drop(1))
            val members = fields.drop(1).filter { it.isNotBlank() && '=' !in it }
                .mapTo(mutableListOf<RuleSchemeGroupMember>()) { RuleSchemeGroupMember.Reference(unquote(it)) }
            val pattern = options.string("policy-regex-filter") ?: ".*".takeIf {
                options.flag("include-all-proxies") || options.string("policy-path") != null
            }
            if (pattern != null) members.add(RuleSchemeGroupMember.NodePattern(pattern))
            groups.add(RuleSchemeGroup(cleanLine(line.substringBefore('=')), kind(fields.first()), members,
                options.string("url"), options.int("interval"), options.int("tolerance")))
        }
        for (line in sections["proxy chain"].orEmpty()) {
            if ('=' !in line) continue
            groups.add(RuleSchemeGroup(cleanLine(line.substringBefore('=')), RuleSchemeGroupKind.RELAY,
                splitFields(line.substringAfter('=')).filter(String::isNotBlank).map { RuleSchemeGroupMember.Reference(it) }))
        }
        rules.addAll(sections["rule"].orEmpty().flatMap { parseRule(it, emptyMap(), local) })
        for (line in sections["remote rule"].orEmpty()) {
            val fields = splitFields(line)
            val options = keyValues(fields.drop(1))
            val policy = options.string("policy") ?: fields.drop(1).firstOrNull { '=' !in it } ?: continue
            if (options["enabled"] != null && !options.flag("enabled")) continue
            val index = rules.indexOfFirst { it.isFinal() }.takeIf { it >= 0 } ?: rules.size
            rules.add(index, RuleSchemeRuleset(policy, RuleSchemeResource.Remote(webURL(fields.first(), true).toString())))
        }
        return Triple(groups, rules, network)
    }

    private fun parseYaml(text: String): Triple<List<RuleSchemeGroup>, List<RuleSchemeRuleset>, RuleSchemeNetworkSettings> {
        val root = yamlObject(text)
        val rawProviders = root["rule-providers"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val remote = linkedMapOf<String, String>()
        val local = linkedMapOf<String, List<String>>()
        rawProviders.forEach { (raw, value) ->
            val key = raw as? String ?: return@forEach
            val provider = value.fields()
            provider.string("url")?.let { remote[key] = webURL(it, true).toString() }
            if (provider["payload"] is List<*>) local[key] = ruleLines(provider.list("payload").joinToString("\n"))
        }
        val groups = (root["proxy-groups"] as? List<*>).orEmpty().mapNotNull { raw ->
            val fields = raw.fields()
            val name = fields.string("name") ?: return@mapNotNull null
            val members = fields.list("proxies").mapTo(mutableListOf<RuleSchemeGroupMember>()) { RuleSchemeGroupMember.Reference(it) }
            val pattern = fields.string("filter") ?: ".*".takeIf {
                fields.flag("include-all") || fields.flag("include-all-proxies") || fields.flag("include-all-providers") ||
                    fields.list("use").isNotEmpty()
            }
            if (pattern != null) members.add(RuleSchemeGroupMember.NodePattern(pattern))
            RuleSchemeGroup(cleanLine(name), kind(fields.string("type") ?: "select"), members,
                fields.string("url"), fields.int("interval"), fields.int("tolerance"), iconURL = fields.string("icon"))
        }
        val rules = root.list("rules").flatMap { parseRule(it, remote, local) }
        val dns = root["dns"].fields()
        val network = RuleSchemeNetworkSettings(dns.list("nameserver"), dns.list("fallback"), dns.list("direct-nameserver"),
            tunExcludedRoutes = root["tun"].fields().list("route-exclude-address"), ipv6 = dns["ipv6"]?.truth())
        return Triple(groups, rules, network)
    }
}
