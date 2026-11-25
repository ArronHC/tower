package com.jzb.tower.core.service

import com.jzb.tower.core.model.*
import kotlinx.serialization.json.*
import java.util.Locale

internal enum class RuleDialect { MIHOMO, STASH, SURGE, SHADOWROCKET, LOON, QUANX, SING_BOX, EGERN }
internal fun dialect(target: ClientTarget) = when {
    target in surgeTargets -> RuleDialect.SURGE
    ClientCatalog.meta(target).usesSingBoxFormat -> RuleDialect.SING_BOX
    target == ClientTarget.CLASH -> RuleDialect.STASH
    target == ClientTarget.SHADOWROCKET -> RuleDialect.SHADOWROCKET
    target == ClientTarget.LOON -> RuleDialect.LOON
    target == ClientTarget.QUANX -> RuleDialect.QUANX
    target == ClientTarget.EGERN -> RuleDialect.EGERN
    else -> RuleDialect.MIHOMO
}
internal fun isBlock(policy: String) = policy.equals("reject", true) || policy.startsWith("reject-", true)
internal fun builtinPolicy(name: String, target: ClientTarget): String? {
    val family = dialect(target)
    val policy = name.uppercase(Locale.ROOT)
    return when (policy) {
        "DIRECT", "REJECT" -> if (family in setOf(RuleDialect.SING_BOX, RuleDialect.EGERN, RuleDialect.QUANX))
            policy.lowercase(Locale.ROOT) else policy
        "REJECT-DROP" -> if (family in setOf(RuleDialect.MIHOMO, RuleDialect.STASH, RuleDialect.SURGE, RuleDialect.LOON, RuleDialect.SHADOWROCKET))
            policy else builtinPolicy("REJECT", target)
        "REJECT-NO-DROP", "REJECT-TINYGIF" -> when {
            family in setOf(RuleDialect.SURGE, RuleDialect.SHADOWROCKET) -> policy
            family == RuleDialect.LOON && policy == "REJECT-TINYGIF" -> "REJECT-IMG"
            else -> builtinPolicy("REJECT", target)
        }
        "REJECT-IMG" -> policy.takeIf { family == RuleDialect.LOON }
        "PASS", "PASS-RULE", "COMPATIBLE" -> policy.takeIf { family == RuleDialect.MIHOMO }
        "CELLULAR", "CELLULAR-ONLY", "HYBRID", "NO-HYBRID" -> policy.takeIf { target == ClientTarget.SURGE }
        else -> null
    }
}

internal data class RoutingOutput(
    val final: String,
    val lines: List<String>,
    val remoteLines: List<String>,
    val objects: List<Fields>,
    val providers: Map<String, Fields>,
    val ruleSets: List<Fields>,
    val domainRuleSets: Map<String, String>,
    val warnings: List<String>,
)

class RoutingExport(
    private val schemes: RuleSchemeService,
    private val countries: IPCountryDatabase,
    private val asns: IPASNDatabase,
) {
    private data class Condition(val type: String, val value: String = "", val options: List<String> = emptyList(),
                                 val children: List<Condition> = emptyList()) {
        fun csv(nested: Boolean = false): String {
            val target = if (children.isEmpty()) value else children.joinToString(",", "(", ")") { "(${it.csv(true)})" }
            return (listOf(type, target) + if (nested) options else emptyList()).joinToString(",")
        }
    }
    private val ipTypes = setOf("IP-CIDR", "IP-CIDR6", "GEOIP", "IP-ASN", "SRC-GEOIP", "SRC-IP-ASN", "SRC-IP-CIDR", "IP-SUFFIX", "SRC-IP-SUFFIX")
    private val common = "DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD IP-CIDR IP-CIDR6 IP-ASN GEOIP PROCESS-NAME SRC-PORT IN-PORT"
    private val supported = mapOf(
        RuleDialect.MIHOMO to "$common DOMAIN-REGEX GEOSITE IP-SUFFIX SRC-GEOIP SRC-IP-ASN SRC-IP-CIDR SRC-IP-SUFFIX DST-PORT NETWORK IN-TYPE IN-USER IN-NAME REMATCH-NAME PROCESS-PATH PROCESS-PATH-WILDCARD PROCESS-PATH-REGEX PROCESS-NAME-WILDCARD PROCESS-NAME-REGEX DSCP",
        RuleDialect.SURGE to "$common RULE-SET DOMAIN-SET DEST-PORT SRC-IP PROTOCOL USER-AGENT URL-REGEX SUBNET DEVICE-NAME MAC-ADDRESS HOSTNAME-TYPE CELLULAR-RADIO CELLULAR-CARRIER",
        RuleDialect.STASH to "DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD DOMAIN-REGEX GEOSITE IP-CIDR IP-CIDR6 IP-ASN GEOIP DST-PORT NETWORK PROTOCOL PROCESS-NAME PROCESS-PATH USER-AGENT URL-REGEX SRC-IP",
        RuleDialect.SHADOWROCKET to "DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD USER-AGENT URL-REGEX IP-CIDR IP-CIDR6 IP-ASN GEOIP DST-PORT PROTOCOL",
        RuleDialect.LOON to "DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD DOMAIN-WILDCARD IP-CIDR IP-CIDR6 GEOIP IP-ASN URL-REGEX USER-AGENT SRC-PORT DEST-PORT PROTOCOL",
        RuleDialect.QUANX to "DOMAIN DOMAIN-SUFFIX DOMAIN-KEYWORD IP-CIDR IP-CIDR6 GEOIP USER-AGENT",
    ).mapValues { it.value.split(' ').toSet() }
    private val structuredFields = mapOf("DOMAIN" to "domain", "DOMAIN-SUFFIX" to "domain_suffix", "DOMAIN-KEYWORD" to "domain_keyword",
        "DOMAIN-REGEX" to "domain_regex", "IP-CIDR" to "ip_cidr", "IP-CIDR6" to "ip_cidr", "PROCESS-NAME" to "process_name",
        "PROCESS-PATH" to "process_path", "PROCESS-PATH-REGEX" to "process_path_regex", "NETWORK" to "network")
    private val egernFields = structuredFields + mapOf("DOMAIN-WILDCARD" to "domain_wildcard", "GEOIP" to "geoip",
        "IP-ASN" to "ip_asn", "SRC-IP-CIDR" to "src_ip_cidr", "USER-AGENT" to "user_agent", "URL-REGEX" to "url_regex",
        "DST-PORT" to "dest_port", "SRC-PORT" to "src_port")

    private fun options(family: RuleDialect, values: List<String>): List<String>? {
        val allowed = when (family) {
            RuleDialect.SURGE -> setOf("no-resolve", "dns-failed", "extended-matching", "pre-matching", "notification-text",
                "notification-interval", "always-capture", "update-interval")
            RuleDialect.STASH -> setOf("no-resolve", "no-track")
            RuleDialect.MIHOMO, RuleDialect.SING_BOX, RuleDialect.EGERN -> setOf("no-resolve", "src")
            RuleDialect.SHADOWROCKET -> setOf("no-resolve", "extended-matching", "pre-matching")
            else -> setOf("no-resolve")
        }
        val ignorable = setOf("notification-text", "notification-interval", "always-capture")
        val result = mutableListOf<String>()
        for (value in values.distinct()) {
            val name = value.substringBefore('=').lowercase(Locale.ROOT)
            if (name in ignorable && name !in allowed) continue
            if (name !in allowed || cleanLine(value) != value || value.any { it in "#;," } || " //" in value) return null
            result.add(name + value.substringAfter('=', "").let { if ('=' in value) "=$it" else "" })
        }
        return result
    }

    private fun normalizePorts(value: String): List<String>? {
        val pattern = Regex("(>=|<=|>|<)?(\\d+)(?:-(\\d+))?")
        return value.split('/').map { part ->
            val match = pattern.matchEntire(part) ?: return null
            val (operator, first, last) = match.destructured
            val a = first.toIntOrNull()?.takeIf { it in 0..65535 } ?: return null
            val b = last.takeIf(String::isNotEmpty)?.toIntOrNull() ?: a
            val low = when (operator) { "<", "<=" -> 0; ">" -> a + 1; else -> a }
            val high = when (operator) { ">", ">=" -> 65535; "<" -> a - 1; else -> b }
            if (low !in 0..high || high !in 0..65535) return null
            if (low == high) "$low" else "$low-$high"
        }
    }

    private fun condition(line: String, added: List<String>, family: RuleDialect, depth: Int = 0): Condition? {
        if (depth > 10) return null
        val parsed = splitRule(line) ?: return null
        val opts = options(family, parsed.options + added) ?: return null
        var type = parsed.type
        var value = parsed.value
        if (type in setOf("AND", "OR", "NOT")) {
            if (family == RuleDialect.QUANX || !value.startsWith('(') || !value.endsWith(')')) return null
            val items = splitFields(value.removeSurrounding("(", ")"))
            if ((type == "NOT" && items.size != 1) || (type != "NOT" && items.size < 2)) return null
            val children = items.map {
                if (!it.startsWith('(') || !it.endsWith(')')) return null
                condition(it.removeSurrounding("(", ")"), emptyList(), family, depth + 1) ?: return null
            }
            return Condition(type, options = opts, children = children)
        }
        if ("src" in opts && type !in setOf("IP-CIDR", "IP-CIDR6")) return null
        if (cleanLine(value) != value || value.any { it in "#;," } || " //" in value) return null
        if (type in setOf("IP-CIDR", "IP-CIDR6", "SRC-IP", "SRC-IP-CIDR")) {
            if (type.startsWith("SRC") && '/' !in value) {
                val address = literalIP(value) ?: return null
                value += "/" + address.size * 8
            }
            if (!validCidr(value)) return null
            type = if (type.startsWith("SRC")) if (family in setOf(RuleDialect.SURGE, RuleDialect.STASH)) "SRC-IP" else "SRC-IP-CIDR"
                else if (':' in value) "IP-CIDR6" else "IP-CIDR"
        }
        if (type == "IP-ASN") {
            value = value.uppercase(Locale.ROOT).removePrefix("AS")
            if (value.toLongOrNull() !in 1L..0xffffffffL) return null
        }
        if (type in setOf("DEST-PORT", "DST-PORT", "SRC-PORT", "IN-PORT")) {
            if (type in setOf("DEST-PORT", "DST-PORT")) type = if (family in setOf(RuleDialect.SURGE, RuleDialect.LOON)) "DEST-PORT" else "DST-PORT"
            val ports = normalizePorts(value) ?: return null
            if (ports.size > 1 && family in setOf(RuleDialect.SURGE, RuleDialect.LOON, RuleDialect.SHADOWROCKET)) {
                if (type !in supported[family].orEmpty()) return null
                return Condition("OR", options = opts, children = ports.map { Condition(type, it) })
            }
            value = ports.joinToString("/")
        }
        if (type == "PROTOCOL" || type == "NETWORK") {
            value = value.uppercase(Locale.ROOT)
            val known = if (family == RuleDialect.LOON) setOf("HTTP", "HTTPS", "TCP", "QUIC", "STUN", "UDP") else setOf("TCP", "UDP")
            if (family != RuleDialect.SURGE && value !in known || (family == RuleDialect.SHADOWROCKET && depth == 0)) return null
            type = if (family in setOf(RuleDialect.MIHOMO, RuleDialect.SING_BOX)) "NETWORK" else "PROTOCOL"
        }
        if (type == "DOMAIN-WILDCARD" && family == RuleDialect.LOON) {
            if (value.any { it in "[]" }) return null
            if (value.none { it in "*?" }) type = "DOMAIN"
            else if (value.startsWith("*.") && value.drop(2).none { it in "*?" }) {
                val apex = value.drop(2)
                return Condition("AND", options = opts, children = listOf(Condition("DOMAIN-SUFFIX", apex),
                    Condition("NOT", children = listOf(Condition("DOMAIN", apex)))))
            }
        }
        if (family == RuleDialect.MIHOMO && type == "PROCESS-NAME") {
            if (value.startsWith('/')) {
                type = if (value.any { it in "*?" } || value.endsWith('/')) "PROCESS-PATH-WILDCARD" else "PROCESS-PATH"
                if (value.endsWith('/')) value += "*"
            } else if (value.any { it in "*?" }) type = "PROCESS-NAME-WILDCARD"
        }
        if (type in setOf("RULE-SET", "DOMAIN-SET") && !(family == RuleDialect.SURGE && type == "RULE-SET" && value in setOf("SYSTEM", "LAN")))
            return null
        if (family !in setOf(RuleDialect.SING_BOX, RuleDialect.EGERN) && type !in supported[family].orEmpty()) return null
        if (depth > 0 && type in ipTypes && family in setOf(RuleDialect.SURGE, RuleDialect.STASH, RuleDialect.MIHOMO, RuleDialect.LOON, RuleDialect.SHADOWROCKET) &&
            "no-resolve" !in opts && "src" !in opts) return Condition(type, value, opts + "no-resolve")
        return Condition(type, value, opts)
    }

    private fun sing(condition: Condition, register: ((String, List<String>) -> Fields)?): Fields? {
        val (type, value, options, original) = condition
        if (original.isNotEmpty()) {
            val children = original.map { sing(it, register) ?: return null }
            return fieldMap("type" to "logical", "mode" to if (type == "OR") "or" else "and", "rules" to children,
                "invert" to true.takeIf { type == "NOT" })
        }
        if (type == "GEOIP" || type == "IP-ASN") {
            if (type == "GEOIP" && value.uppercase(Locale.ROOT) in setOf("LAN", "PRIVATE")) return mapOf("ip_is_private" to true)
            val ranges = if (type == "GEOIP") countries.cidrsForCountry(value) else asns.cidrsForAsn(value.toLong())
            if (ranges.isEmpty()) return null
            val tag = (if (type == "GEOIP") "tower-geoip-" else "tower-asn-") + value.lowercase(Locale.ROOT)
            return register?.invoke(tag, ranges) ?: mapOf("ip_cidr" to ranges)
        }
        if (type == "DOMAIN-WILDCARD") {
            val regex = "^" + value.map { when (it) { '*' -> ".*"; '?' -> "."; else ->
                if (it in "\\.^$|()[]{}+") "\\$it" else it.toString() } }.joinToString("") + "$"
            return mapOf("domain_regex" to listOf(regex))
        }
        if (type in setOf("SRC-PORT", "DST-PORT")) {
            val prefix = if (type == "SRC-PORT") "source_port" else "port"
            val ports = value.split('/')
            val exact = ports.filter { '-' !in it }.map(String::toInt)
            val range = ports.filter { '-' in it }.map { it.replace('-', ':') }
            if (exact.isNotEmpty() && range.isNotEmpty()) return mapOf("type" to "logical", "mode" to "or",
                "rules" to listOf(mapOf(prefix to exact), mapOf("${prefix}_range" to range)))
            return if (exact.isNotEmpty()) mapOf(prefix to exact) else mapOf("${prefix}_range" to range)
        }
        val field = if (type == "SRC-IP-CIDR" || (type in setOf("IP-CIDR", "IP-CIDR6") && "src" in options))
            "source_ip_cidr" else structuredFields[type] ?: return null
        return mapOf(field to listOf(if (type == "NETWORK") value.lowercase(Locale.ROOT) else value))
    }

    private fun egern(condition: Condition): Pair<String, Fields>? {
        val (type, value, options, original) = condition
        if (original.isNotEmpty()) {
            val children = original.map { egern(it)?.let { pair -> mapOf(pair) } ?: return null }
            return type.lowercase(Locale.ROOT) to mapOf("match" to if (type == "NOT") children.first() else children)
        }
        val field = if (type in setOf("IP-CIDR", "IP-CIDR6") && "src" in options) "src_ip_cidr"
            else egernFields[type] ?: return null
        return field to fieldMap("match" to value, "no_resolve" to true.takeIf { type in ipTypes })
    }

    internal fun generate(target: ClientTarget, rules: List<RuleSchemeRuleset>, names: Map<String, String>,
                         primary: String, blocking: Set<String>, preferSets: Boolean, protectedDNS: Boolean): RoutingOutput {
        val family = dialect(target)
        val deferred = family in setOf(RuleDialect.LOON, RuleDialect.QUANX)
        val lines = mutableListOf<String>()
        val remoteLines = mutableListOf<String>()
        val objects = mutableListOf<Fields>()
        val providers = linkedMapOf<String, Fields>()
        val ruleSets = mutableListOf<Fields>()
        val localRanges = linkedMapOf<String, List<String>>()
        val domainRuleSets = linkedMapOf<String, String>()
        val registerLocal: ((String, List<String>) -> Fields)? = if (target == ClientTarget.SING_BOX) {
            { tag, ranges ->
                localRanges.putIfAbsent(tag, ranges)
                mapOf("rule_set" to listOf(tag))
            }
        } else null
        val skips = linkedMapOf<Pair<Boolean, String>, Int>()
        var final = primary
        fun policy(raw: String) = if (isBuiltinPolicy(raw)) builtinPolicy(raw, target) else names[raw]
        fun mark(raw: String, reason: String) {
            val key = (isBlock(raw) || raw in blocking) to reason
            skips[key] = skips.getOrDefault(key, 0) + 1
        }
        val publishedDialect = family.name.lowercase(Locale.ROOT)
        fun artifact(rule: RuleSchemeRuleset): Pair<String, String?>? {
            return when (val resource = rule.resource) {
                is RuleSchemeResource.Inline -> schemes.publishedInline(resource.rule, publishedDialect)?.let { it to null }
                is RuleSchemeResource.Bundled -> {
                    val key = when (family) { RuleDialect.SING_BOX -> "srs"; else -> publishedDialect }
                    val entry = schemes.published(resource.file, key)
                    if (entry?.get("residualRuleCount")?.jsonPrimitive?.intOrNull != 0) null
                    else entry["url"]?.jsonPrimitive?.contentOrNull?.let { it to when (key) {
                        "srs" -> "srs"
                        "egern" -> "egern"
                        else -> null
                    } }
                }
                else -> null
            }
        }
        fun remote(url: String, destination: String, tag: String, format: String?, opts: List<String>, behavior: String = "classical"): Boolean {
            val safe = runCatching { webURL(url, true).toString() }.getOrNull() ?: return false
            val normalized = options(family, opts) ?: return false
            if (',' in safe && family in setOf(RuleDialect.SURGE, RuleDialect.QUANX, RuleDialect.LOON)) return false
            return when {
                family == RuleDialect.SURGE && format == null -> {
                    lines.add((listOf("RULE-SET", safe, destination) + normalized).joinToString(",")); true
                }
                family in setOf(RuleDialect.MIHOMO, RuleDialect.STASH) && format != "srs" && format != "egern" &&
                    (format != "mrs" || target != ClientTarget.KARING) -> {
                    val actual = format ?: if (safe.substringBefore('?').endsWith(".yaml") || safe.substringBefore('?').endsWith(".yml")) "yaml" else "text"
                    providers[tag] = fieldMap("type" to "http", "behavior" to behavior, "format" to actual,
                        "url" to safe, "path" to "./ruleset/$tag.${if (actual == "text") "txt" else actual}", "interval" to 86400)
                    lines.add((listOf("RULE-SET", tag, destination) + normalized).joinToString(",")); true
                }
                family == RuleDialect.SING_BOX && format == "srs" && normalized.all { it == "no-resolve" } -> {
                    ruleSets.add(fieldMap("type" to "remote", "tag" to tag, "format" to "binary", "url" to safe,
                        "download_detour" to primary.takeIf { target == ClientTarget.HIDDIFY && it != "direct" }))
                    objects.add(singAction(mapOf("rule_set" to listOf(tag)), destination)); true
                }
                family == RuleDialect.EGERN && format == "egern" && normalized.isEmpty() -> {
                    objects.add(mapOf("rule_set" to mapOf("match" to safe, "policy" to destination, "update_interval" to 86400))); true
                }
                deferred && format == null && normalized.isEmpty() -> {
                    remoteLines.add(if (family == RuleDialect.QUANX) "$safe, tag=$tag, force-policy=$destination, update-interval=86400, enabled=true"
                        else "$safe,policy=$destination,tag=$tag,enabled=true"); true
                }
                else -> false
            }
        }
        val lastOrdinary = rules.indexOfLast { !it.isFinal() }
        val rawLines = rules.map(schemes::lines)
        val canDefer = BooleanArray(rules.size)
        var localAfter = false
        for (index in rules.indices.reversed()) {
            val rule = rules[index]
            canDefer[index] = !localAfter
            if (deferred && !rule.isFinal() && policy(rule.groupName) != null &&
                !(artifact(rule) != null && (preferSets || rule.isGeo())) &&
                rawLines[index].any { condition(it, rule.options, family) != null }) localAfter = true
        }
        rules.forEachIndexed { index, rule ->
            val destination = policy(rule.groupName)
            if (destination == null) { mark(rule.groupName, "目标策略不可用"); return@forEachIndexed }
            if (rule.isFinal()) { final = destination; return@forEachIndexed }
            val tag = "tower-${index + 1}"
            val canRemote = !deferred || canDefer[index]
            val resource = rule.resource
            if (resource is RuleSchemeResource.Remote) {
                val extension = resource.url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)
                val format = extension.takeIf { it in setOf("mrs", "srs") }
                if (!canRemote || !remote(resource.url, destination, tag, format, rule.options,
                        if (format == "mrs") if (resource.url.contains("ipcidr", true)) "ipcidr" else "domain" else "classical"))
                    mark(rule.groupName, if (canRemote) "远程规则集无法完整表达" else "远程规则集会改变匹配顺序")
                return@forEachIndexed
            }
            if (canRemote && (preferSets || deferred && rule.isGeo() && remoteLines.isNotEmpty())) {
                val artifact = artifact(rule)
                if (artifact != null && remote(artifact.first, destination, tag, artifact.second, rule.options)) {
                    if (target == ClientTarget.SING_BOX && artifact.second == "srs" && resource is RuleSchemeResource.Bundled) {
                        val domain = schemes.published(resource.file, "srsDomain")?.get("url")?.jsonPrimitive?.contentOrNull
                        if (domain != null) {
                            val domainTag = "$tag-domain"
                            ruleSets.add(mapOf("type" to "remote", "tag" to domainTag, "format" to "binary", "url" to domain))
                            domainRuleSets[tag] = domainTag
                        }
                    }
                    return@forEachIndexed
                }
                if (preferSets && resource is RuleSchemeResource.Bundled && family in setOf(RuleDialect.MIHOMO, RuleDialect.STASH) &&
                    target != ClientTarget.KARING && rule.options.isEmpty() && schemes.mrsResidualCount(resource.file) == 0) {
                    val entries = schemes.published(resource.file, "mrs").orEmpty()
                    if (entries.isNotEmpty() && entries.values.all { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull != null }) {
                        entries.forEach { (behavior, item) ->
                            val data = item.jsonObject
                            remote(data.getValue("url").jsonPrimitive.content, destination, "$tag-$behavior", "mrs",
                                if (data["noResolve"]?.jsonPrimitive?.booleanOrNull == true) listOf("no-resolve") else emptyList(), behavior)
                        }
                        return@forEachIndexed
                    }
                }
            }
            rawLines[index].forEach { line ->
                val match = condition(line, rule.options, family)
                if (match == null) {
                    mark(rule.groupName, "不支持 ${splitRule(line)?.type ?: "此规则"}")
                    return@forEach
                }
                when (family) {
                    RuleDialect.SING_BOX -> {
                        val fields = sing(match, registerLocal)
                        if (fields != null) objects.add(singAction(fields, destination)) else mark(rule.groupName, "不支持 ${match.type}")
                    }
                    RuleDialect.EGERN -> {
                        val mapped = egern(match)
                        if (mapped != null) objects.add(mapOf(mapped.first to (mapped.second + ("policy" to destination))))
                        else mark(rule.groupName, "不支持 ${match.type}")
                    }
                    else -> {
                        val omit = target in mihomoTargets && match.type == "GEOIP" && index == lastOrdinary && protectedDNS &&
                            "no-resolve" !in match.options
                        val end = if (family == RuleDialect.QUANX) emptyList() else match.options +
                            listOfNotNull("no-resolve".takeIf { !omit && match.type in ipTypes && "no-resolve" !in match.options && "src" !in match.options })
                        val rendered = if (family == RuleDialect.QUANX) match.copy(type = match.type.replaceFirst("DOMAIN", "HOST")
                            .let { if (it == "IP-CIDR6") "IP6-CIDR" else it }) else match
                        lines.add((listOf(rendered.csv(), destination) + end).joinToString(","))
                    }
                }
            }
        }
        val warnings = skips.entries.sortedByDescending { it.key.first }.map { (key, count) ->
            if (key.first) "这些请求不会被拦截：已跳过 $count 条拦截规则（${key.second}）。"
            else "已跳过 $count 条规则（${key.second}）。"
        }
        val inline = localRanges.map { (tag, cidrs) ->
            mapOf("type" to "inline", "tag" to tag, "rules" to listOf(mapOf("ip_cidr" to cidrs)))
        }
        return RoutingOutput(final, lines, remoteLines, objects, providers, ruleSets + inline, domainRuleSets, warnings)
    }

    private fun singAction(condition: Fields, policy: String): Fields = condition +
        if (isBlock(policy)) mapOf("action" to "reject") else mapOf("action" to "route", "outbound" to policy)
}
