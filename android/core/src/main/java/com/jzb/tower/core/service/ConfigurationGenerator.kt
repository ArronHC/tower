package com.jzb.tower.core.service

import com.jzb.tower.core.contract.TailnetSecretStore
import com.jzb.tower.core.contract.TowerResourceProvider
import com.jzb.tower.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer
import java.util.Base64
import java.util.Locale

internal fun activeNodes(snapshot: AppSnapshot): List<ProxyNode> {
    val sources = snapshot.subscriptions.filter { it.isEnabled }.mapTo(hashSetOf()) { it.id }
    val excluded = snapshot.excludedNodeIDs.toHashSet()
    return snapshot.nodes.filter { (it.sourceID == null || it.sourceID in sources) && it.id !in excluded }
}

private fun yamlDocument(value: Fields): String {
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        setSplitLines(false)
        indent = 2
        indicatorIndent = 2
        indentWithIndicator = true
    }
    val representer = object : Representer(options) {
        init {
            representers[String::class.java] = Represent { value ->
                representScalar(Tag.STR, value as String, DumperOptions.ScalarStyle.DOUBLE_QUOTED)
            }
        }
    }
    return Yaml(representer, options).dump(value)
}

private val prettyJSON = Json { prettyPrint = true }
internal fun jsonDocument(value: Fields): String = prettyJSON.encodeToString(JsonElement.serializer(), value.jsonValue()) + "\n"
internal const val testURL = "https://www.gstatic.com/generate_204"
private fun testURL(value: String?) = value?.let { runCatching { webURL(it).toString() }.getOrNull() }
    ?.takeUnless { it.any { char -> char in ",;#" } } ?: testURL
private fun section(name: String, rows: List<String>) = "[$name]\n" + rows.joinToString("\n")
private fun rowList(rows: List<String>) = if (rows.isEmpty()) "" else rows.joinToString("\n", postfix = "\n")
private fun exactPattern(names: List<String>) = if (names.isEmpty()) "(?!)"
    else names.joinToString("|", "^(?:", ")$") { name ->
        name.map { if (it in "\\.^$|()[]{}+*?") "\\$it" else it.toString() }.joinToString("")
    }

class ConfigurationGenerator(
    resources: TowerResourceProvider,
    secrets: TailnetSecretStore,
    private val countries: IPCountryDatabase = IPCountryDatabase(resources),
    asns: IPASNDatabase = IPASNDatabase(resources),
    private val schemes: RuleSchemeService = RuleSchemeService(resources),
) {
    private val icons = PolicyGroupIcons(resources)
    private val ruleExporter = RoutingExport(schemes, countries, asns)
    private val tailnetWriter = TailnetConfigurationWriter(secrets)

    private data class Remote(val id: String, val source: SubscriptionSource) {
        val url get() = source.urlString.replace(",", "%2C")
    }

    private data class Group(
        val source: RuleSchemeGroup,
        val name: String,
        val icon: String?,
        val choices: MutableList<String> = mutableListOf(),
        val remoteIDs: MutableList<String> = mutableListOf(),
        val remoteNames: MutableList<String> = mutableListOf(),
        var allRemote: Boolean = false,
    ) {
        val kind get() = source.kind
        val interval get() = (source.interval ?: 300).coerceAtLeast(1)
        val tolerance get() = (source.tolerance ?: 50).coerceAtLeast(0)
    }

    private data class Network(val settings: RuleSchemeNetworkSettings?, val mode: DnsProtectionMode) {
        val follow get() = mode == DnsProtectionMode.FOLLOW_SCHEME
        val strict get() = mode == DnsProtectionMode.STRICT
        val ipv6 get() = settings?.ipv6 != false
        val local get() = if (follow) settings?.directDNSServers?.ifEmpty { null } ?: settings?.dnsServers?.ifEmpty { null }
            ?: listOf("223.5.5.5", "119.29.29.29") else listOf("223.5.5.5", "119.29.29.29")
        val remote get() = if (follow) settings?.fallbackDNSServers?.ifEmpty { null } ?: defaultRemote else defaultRemote
        companion object {
            val defaultRemote = listOf("https://8.8.8.8/dns-query", "https://1.1.1.1/dns-query")
            val localEncrypted = listOf("https://223.5.5.5/dns-query", "https://doh.pub/dns-query")
        }
    }

    fun generate(
        snapshot: AppSnapshot,
        target: ClientTarget,
        mode: ExportContentMode,
        maskSecrets: Boolean = true,
        runtime: Map<String, NodeRuntimeInfo> = emptyMap(),
    ): GeneratedConfiguration {
        val meta = ClientCatalog.meta(target)
        val actualMode = when {
            !meta.supportsFullConfiguration -> ExportContentMode.NODES_ONLY
            !meta.supportsNodesOnly -> ExportContentMode.FULL_CONFIGURATION
            else -> mode
        }
        val full = actualMode == ExportContentMode.FULL_CONFIGURATION
        val ini = target in surgeTargets || target in setOf(ClientTarget.LOON, ClientTarget.QUANX)
        val clash = meta.usesClashFormat || target == ClientTarget.SHADOWROCKET
        val title = cleanLine(snapshot.settings.configurationName).ifEmpty { "Tower" }
        val schemeSource = schemes.find(snapshot) ?: schemes.bundledSchemes.firstOrNull()
            ?: RuleScheme("default", "Tower", "")
        val scheme = if (full) schemes.effective(snapshot, schemeSource) else schemeSource
        val nodesBySource = snapshot.nodes.groupBy(ProxyNode::sourceID)
        val excludedNodes = snapshot.excludedNodeIDs.toHashSet()
        val remotes = if (full && snapshot.settings.embedRemoteSubscriptionLinks && meta.supportsEmbeddedRemoteSubscriptions &&
            target != ClientTarget.SURFBOARD && snapshot.excludedKinds[target.rawValue].isNullOrEmpty() &&
            snapshot.nodeExportNameFilter == null && !snapshot.settings.appendSubscriptionNameToNodes) {
            snapshot.subscriptions.filter { it.isEnabled }.mapIndexedNotNull { index, source ->
                val valid = runCatching { webURL(source.urlString).toString() }.getOrNull()
                if (valid == null || !source.requestOptions?.dnsOverHTTPSURL.isNullOrBlank() ||
                    (!meta.usesClashFormat && !source.requestOptions?.userAgent.isNullOrBlank()) ||
                    nodesBySource[source.id].orEmpty().any { node ->
                        node.id in excludedNodes || (snapshot.settings.filterSubscriptionInfoNodes && SubscriptionParser.isMetadata(node)) ||
                            (ini && confName(node.name) != node.name) || NodeExport.rejection(node, target, actualMode) != null
                    }) null
                else Remote("tower-source-${index + 1}", source.copy(urlString = valid))
            }
        } else emptyList()
        val remoteBySource = remotes.associateBy { it.source.id }
        val taken = (builtinPolicies + setOf("proxy", "global", "dns")).mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
        val sources = snapshot.subscriptions.associateBy { it.id }
        val excluded = snapshot.excludedKinds[target.rawValue].orEmpty().toHashSet()
        val skipped = linkedMapOf<String, Int>()
        val originals = mutableListOf<ProxyNode>()
        val supported = mutableListOf<ProxyNode>()
        for (original in activeNodes(snapshot)) {
            if ((snapshot.settings.filterSubscriptionInfoNodes && SubscriptionParser.isMetadata(original)) ||
                !matchesFilter(snapshot.nodeExportNameFilter, original.name)) continue
            val reason = if (original.kind in excluded) "已排除此客户端的协议" else NodeExport.rejection(original, target, actualMode)
            if (reason != null) {
                skipped[reason] = skipped.getOrDefault(reason, 0) + 1
                continue
            }
            val source = sources[original.sourceID]
            val base = if (snapshot.settings.appendSubscriptionNameToNodes && source != null) "${original.name} · ${source.name}" else original.name
            val exported = when {
                ini -> confName(base)
                full && clash -> cleanLine(base).replace(',', '，')
                else -> cleanLine(base)
            }
            val name = if (original.sourceID in remoteBySource) original.name else uniqueName(exported.ifEmpty { "节点" }, taken)
            val node = original.copy(name = name)
            if (target == ClientTarget.V2BOX && NodeExport.uri(node) == null) {
                val unavailable = "节点链接无法完整表达该代理"
                skipped[unavailable] = skipped.getOrDefault(unavailable, 0) + 1
                continue
            }
            originals.add(original)
            supported.add(node)
        }
        val local = supported.filterNot { it.sourceID in remoteBySource }
        val warnings = mutableListOf<String>()
        var extension = if (full) if (clash) "yaml" else meta.fileExtension else if (meta.usesClashFormat) "yaml" else "txt"
        var ajson: String? = null
        val content = if (!full) when {
            meta.usesClashFormat -> yamlDocument(mapOf("proxies" to local.map { NodeExport.clash(it, target) }))
            target == ClientTarget.ANYWHERE -> {
                val proxies = local.mapNotNull(NodeExport::anywhere)
                if (proxies.any { it !is String }) {
                    ajson = jsonDocument(mapOf("anywhere" to 1, "name" to title, "proxies" to proxies))
                    warnings.add("跳过证书校验的节点仅在 Anywhere AJSON 导入中可用。")
                }
                rowList(proxies.filterIsInstance<String>())
            }
            target == ClientTarget.V2BOX || target == ClientTarget.SHADOWROCKET -> {
                if (target == ClientTarget.SHADOWROCKET && local.any { NodeExport.uri(it) == null ||
                        it.kind in setOf(ProxyKind.SSH, ProxyKind.TRUSTTUNNEL, ProxyKind.MASQUE) }) {
                    extension = "yaml"
                    yamlDocument(mapOf("proxies" to local.map { NodeExport.clash(it, target) }))
                } else Base64.getEncoder().encodeToString(local.mapNotNull { NodeExport.uri(it) }.joinToString("\n").toByteArray())
            }
            target == ClientTarget.HIDDIFY -> {
                if (local.any { it.shadowTLS != null || it.kind == ProxyKind.SSH || NodeExport.uri(it) == null }) {
                    extension = "json"
                    jsonDocument(mapOf("outbounds" to local.flatMap { node ->
                        val export = NodeExport.sing(node, target, uniqueName(node.name + " ShadowTLS", taken))
                        listOfNotNull(export.helper, export.outbound)
                    }))
                } else rowList(local.mapNotNull { NodeExport.uri(it) })
            }
            else -> rowList(local.map { iniProxy(target, it) })
        } else {
            val mapping = linkedMapOf<String, String>()
            val groups = planGroups(snapshot, scheme, target, originals, supported, remotes, runtime, taken, mapping)
            val primary = groups.first().name
            val byName = groups.associateBy { it.name }
            fun defaultsToBlock(name: String): Boolean {
                if (isBlock(name)) return true
                val group = byName[name] ?: return false
                return group.kind == RuleSchemeGroupKind.SELECT && group.choices.firstOrNull()?.let(::defaultsToBlock) == true
            }
            val blocking = groups.filter { defaultsToBlock(it.name) }.mapTo(hashSetOf()) { it.source.name }
            val network = Network(scheme.networkSettings, snapshot.settings.dnsProtectionMode)
            val localNames = local.map { it.name }
            val dnsProxy = if (!network.follow && (target in mihomoTargets || meta.usesSingBoxFormat) && (localNames.isNotEmpty() || remotes.isNotEmpty())) {
                val existing = groups.firstOrNull { group -> group.kind == RuleSchemeGroupKind.URL_TEST &&
                    group.choices.isNotEmpty() && group.choices.all { it in localNames } && group.name.none { it in "#&" } &&
                    (group.remoteIDs.isEmpty() || group.allRemote) }
                existing?.name ?: Group(RuleSchemeGroup("DNS 自动选择", RuleSchemeGroupKind.URL_TEST, emptyList()),
                    uniqueName("DNS 自动选择", taken), null, localNames.toMutableList(), remotes.map { it.id }.toMutableList(),
                    allRemote = true).also(groups::add).name
            } else null
            originals.zip(supported).forEach { (original, exported) -> mapping.putIfAbsent(original.name, exported.name) }
            val rules = ruleExporter.generate(target, scheme.rulesets, mapping, primary, blocking,
                snapshot.settings.preferRuleSets, network.follow || dnsProxy != null)
            val tailnets = tailnetWriter.write(snapshot.tailnets, target, maskSecrets, taken)
            warnings.addAll(rules.warnings)
            warnings.addAll(tailnets.warnings)
            if (remotes.isNotEmpty()) warnings.add("远程代理集合由客户端自行下载，塔台的筛选和改名仅应用于本地代理。")
            val unsupportedKinds = if (target in mihomoTargets) setOf(RuleSchemeGroupKind.SSID, RuleSchemeGroupKind.UNSUPPORTED)
                else setOf(RuleSchemeGroupKind.SSID, RuleSchemeGroupKind.SMART, RuleSchemeGroupKind.RELAY, RuleSchemeGroupKind.UNSUPPORTED)
            groups.filter { it.kind in unsupportedKinds }.forEach {
                warnings.add("策略组“${it.name}”的类型无法完整表达，已保留候选策略。")
            }
            when {
                clash -> clashProfile(target, local, groups, remotes, rules, tailnets, network, dnsProxy)
                meta.usesSingBoxFormat -> singProfile(target, local, groups, rules, tailnets, network, primary, dnsProxy, taken)
                target == ClientTarget.EGERN -> egernProfile(local, groups, remotes, rules)
                else -> iniProfile(target, title, local, groups, remotes, rules, tailnets, network)
            }
        }
        if (!full && snapshot.tailnets.any { it.isEnabled } &&
            (!meta.supportsFullConfiguration || target !in surgeTargets + mihomoTargets + setOf(ClientTarget.CLASH, ClientTarget.SING_BOX))) {
            warnings.add("${meta.name} 的节点导出不支持 Tailscale，已跳过。")
        }
        val filename = title.replace(Regex("[/\\\\:*?\"<>|]"), "-").trim('.', ' ').take(90).ifEmpty { "Tower" }
        return GeneratedConfiguration(target, actualMode, "$filename-${target.rawValue}.$extension", extension,
            when (extension) { "json" -> "application/json"; "yaml" -> "application/yaml"; else -> "text/plain" },
            content, local.size, skipped.values.sum(), skipped.map { SkippedReasonCount(it.key, it.value) }, warnings,
            remotes.size, local.isNotEmpty() || remotes.isNotEmpty(), ajson, generatedAtEpochMs = System.currentTimeMillis())
    }

    private fun matchesFilter(filter: NodeExportNameFilter?, name: String): Boolean {
        if (filter == null) return true
        fun matches(term: String) = if (filter.useRegex) nodeMatchesPattern(name, term) else name.contains(term, true)
        val include = filter.includeKeywords.filter(String::isNotBlank)
        val exclude = filter.excludeKeywords.filter(String::isNotBlank)
        return (include.isEmpty() || include.any(::matches)) && exclude.none(::matches)
    }

    private fun planGroups(snapshot: AppSnapshot, scheme: RuleScheme, target: ClientTarget, originals: List<ProxyNode>,
                           nodes: List<ProxyNode>, remotes: List<Remote>, runtime: Map<String, NodeRuntimeInfo>,
                           taken: MutableSet<String>, mapping: MutableMap<String, String>): MutableList<Group> {
        val direct = builtinPolicy("DIRECT", target) ?: "DIRECT"
        val ini = target in surgeTargets || target in setOf(ClientTarget.LOON, ClientTarget.QUANX)
        val csvRules = ini || ClientCatalog.meta(target).usesClashFormat || target == ClientTarget.SHADOWROCKET
        val emojiEnabled = snapshot.ruleGroupEmojisEnabled[scheme.id] != false
        val autoEmoji = snapshot.ruleGroupEmojiAutofill[scheme.id] ?: !scheme.isBundled
        fun group(source: RuleSchemeGroup): Group {
            val flagOrEmoji = PolicyGroupEmoji.resolve(source, scheme.rulesets)
            val flag = flagOrEmoji?.let(PolicyGroupEmoji::isFlag) == true
            val iconEmoji = flagOrEmoji ?: "🌐"
            val icon = if (emojiEnabled && target in PolicyGroupIcons.writesIcons &&
                !(flag && target in setOf(ClientTarget.SURGE, ClientTarget.SURGE_MAC)))
                icons.url(iconEmoji, target == ClientTarget.QUANX) else null
            val named = when {
                !emojiEnabled || (icon != null && target in PolicyGroupIcons.drawsIcons && !flag) ->
                    PolicyGroupEmoji.withoutLeading(source.name).ifEmpty { source.name }
                PolicyGroupEmoji.leading(source.name) == null && flagOrEmoji != null && (flag || autoEmoji) -> "$flagOrEmoji ${source.name}"
                else -> source.name
            }
            val sanitized = when {
                ini -> confName(named)
                csvRules -> cleanLine(named).replace(',', '，')
                else -> cleanLine(named)
            }
            val name = uniqueName(sanitized.ifEmpty { "策略组" }, taken)
            mapping[source.name] = name
            return Group(source, name, icon)
        }
        val result = scheme.groups.filterNot { isBuiltinPolicy(it.name) }.map(::group).toMutableList()
        val remoteBySource = remotes.associateBy { it.source.id }
        fun addNode(group: Group, node: ProxyNode) {
            val remote = remoteBySource[node.sourceID]
            if (remote == null || target == ClientTarget.QUANX) {
                // QuanX groups refer to the remote tags directly in an all-node benchmark.
                group.choices.add(node.name)
            }
            if (remote != null) {
                group.remoteIDs.add(remote.id)
                group.remoteNames.add(node.name)
            }
        }
        val byOriginalName = originals.zip(nodes).groupBy({ it.first.name }, { it.second })
        for (group in result) {
            group.source.members.forEach { member ->
                when (member) {
                    is RuleSchemeGroupMember.Reference -> {
                        val reference = if (isBuiltinPolicy(member.name)) builtinPolicy(member.name, target) else mapping[member.name]
                        if (reference != null && reference != group.name) group.choices.add(reference)
                        else byOriginalName[member.name].orEmpty().forEach { addNode(group, it) }
                    }
                    is RuleSchemeGroupMember.NodePattern -> {
                        if (member.pattern in setOf(".*", "(?i).*", "^.*$")) {
                            group.remoteIDs.addAll(remotes.map { it.id })
                            group.allRemote = true
                        }
                        val regex = runCatching { Regex(member.pattern) }.getOrNull()
                        originals.zip(nodes).forEach { (original, node) ->
                            if (regex?.containsMatchIn(original.name) == true) addNode(group, node)
                        }
                    }
                }
            }
        }
        if (result.isEmpty()) {
            val fallback = group(RuleSchemeGroup("节点选择", RuleSchemeGroupKind.SELECT, emptyList()))
            fallback.allRemote = true
            fallback.remoteIDs.addAll(remotes.map { it.id })
            nodes.forEach { addNode(fallback, it) }
            result.add(fallback)
        }
        if (scheme.id == "self-configuration" || (scheme.isBundled && scheme.groups.none { candidate ->
                candidate.members.any { it is RuleSchemeGroupMember.NodePattern && it.pattern !in setOf(".*", "(?i).*", "^.*$") }
            })) {
            val byId = originals.associateBy { it.id }
            val regionNodes = nodes.groupBy { node ->
                node.countryOverride?.let(CountryTable::get)?.code ?: byId[node.id]?.let(CountryTable::codeFromNodeName)
                    ?: runtime[node.id]?.countryCode ?: countries.countryCode(node.server)
            }
            regionNodes.filterKeys { it != null }.forEach { (code, members) ->
                val country = code?.let(CountryTable::get) ?: return@forEach
                val label = "${country.flag} ${country.chineseName}"
                val selection = group(RuleSchemeGroup(label, RuleSchemeGroupKind.SELECT, emptyList()))
                val automatic = group(RuleSchemeGroup("$label 自动选择", RuleSchemeGroupKind.URL_TEST, emptyList()))
                selection.choices.add(automatic.name)
                members.forEach { addNode(selection, it); addNode(automatic, it) }
                result.first().choices.add(selection.name)
                result.add(selection)
                result.add(automatic)
            }
        }
        val localNames = nodes.mapTo(hashSetOf()) { it.name }
        val byName = result.associateBy { it.name }
        val visiting = hashSetOf<String>()
        val visited = hashSetOf<String>()
        fun sanitize(group: Group) {
            if (!visited.add(group.name)) return
            visiting.add(group.name)
            val choices = group.choices.distinct().filterNot { it in visiting }
            group.choices.clear()
            for (name in choices) {
                if (target == ClientTarget.QUANX && group.kind == RuleSchemeGroupKind.URL_TEST && group.allRemote && name !in localNames)
                    continue
                byName[name]?.let(::sanitize)
                group.choices.add(name)
            }
            visiting.remove(group.name)
            if (group.choices.isEmpty() && group.remoteIDs.isEmpty()) group.choices.add(direct)
        }
        result.forEach(::sanitize)
        return result
    }

    private fun clashProfile(target: ClientTarget, nodes: List<ProxyNode>, groups: List<Group>, remotes: List<Remote>,
                             rules: RoutingOutput, tailnet: TailnetOutput, network: Network, dnsProxy: String?): String {
        val policies = groups.map { g ->
            val kind = when (g.kind) {
                RuleSchemeGroupKind.SELECT, RuleSchemeGroupKind.URL_TEST, RuleSchemeGroupKind.FALLBACK,
                RuleSchemeGroupKind.LOAD_BALANCE -> g.kind.rawValue
                RuleSchemeGroupKind.SMART, RuleSchemeGroupKind.RELAY -> if (target in mihomoTargets) g.kind.rawValue else "select"
                else -> "select"
            }
            fieldMap("name" to g.name, "type" to kind, "proxies" to g.choices.ifEmpty { null }, "use" to g.remoteIDs.distinct().ifEmpty { null },
                "filter" to exactPattern(g.remoteNames.distinct()).takeIf { g.remoteIDs.isNotEmpty() && !g.allRemote },
                "url" to testURL(g.source.testURL).takeIf { kind in setOf("url-test", "fallback", "load-balance") },
                "interval" to g.interval.takeIf { kind in setOf("url-test", "fallback", "load-balance") },
                "tolerance" to g.tolerance.takeIf { kind == "url-test" }, "icon" to g.icon)
        }
        val resolver = when {
            dnsProxy != null && target in mihomoTargets -> listOf(network.remote.first() + "#$dnsProxy&ecs=114.114.114.0/24&ecs-override=true")
            network.follow -> network.local
            network.strict -> network.remote
            else -> Network.localEncrypted
        }
        val routeExclude = network.settings?.tunExcludedRoutes?.ifEmpty { null }
        val tun = if (network.strict && target in mihomoTargets) fieldMap("enable" to true, "stack" to "mixed", "auto-route" to true,
            "auto-detect-interface" to true, "dns-hijack" to listOf("any:53", "tcp://any:53"), "strict-route" to true, "route-exclude-address" to routeExclude)
            else routeExclude?.let { mapOf("route-exclude-address" to it) }
        return yamlDocument(fieldMap("mode" to "rule", "mixed-port" to 7890, "allow-lan" to false, "log-level" to "info", "ipv6" to network.ipv6,
            "dns" to fieldMap("enable" to true, "ipv6" to network.ipv6, "enhanced-mode" to "fake-ip".takeUnless { network.follow },
                "fake-ip-filter" to listOf("*.lan", "+.local", "+.msftconnecttest.com", "+.msftncsi.com").takeUnless { network.follow },
                "nameserver" to resolver, "default-nameserver" to network.local.filter { literalIP(it) != null }.ifEmpty { listOf("223.5.5.5") },
                "fallback" to network.settings?.fallbackDNSServers?.ifEmpty { null }?.takeIf { network.follow },
                "proxy-server-nameserver" to Network.localEncrypted.takeUnless { network.follow },
                "direct-nameserver" to if (network.follow) network.settings?.directDNSServers?.ifEmpty { null }
                    else Network.localEncrypted.takeIf { dnsProxy != null && !network.strict }),
            "tun" to tun, "proxies" to (tailnet.proxies + nodes.map { NodeExport.clash(it, target) }), "proxy-groups" to policies,
            "proxy-providers" to remotes.associate { item -> item.id to fieldMap("type" to "http", "url" to item.url,
                "path" to "./providers/${item.id}.yaml", "interval" to 86400,
                "header" to item.source.requestOptions?.userAgent?.let { mapOf("User-Agent" to listOf(cleanLine(it))) }) }.ifEmpty { null },
            "rule-providers" to rules.providers.ifEmpty { null }, "rules" to (tailnet.rules + rules.lines + "MATCH,${rules.final}")))
    }

    private fun singProfile(target: ClientTarget, nodes: List<ProxyNode>, groups: List<Group>, rules: RoutingOutput, tailnet: TailnetOutput,
                            network: Network, primary: String, dnsProxy: String?, taken: MutableSet<String>): String {
        val nodeExports = nodes.map { NodeExport.sing(it, target, uniqueName(it.name + " ShadowTLS", taken)) }
        val groupOutbounds = groups.map { group ->
            val test = group.kind == RuleSchemeGroupKind.URL_TEST
            val choices = group.choices.filterNot { test && isBlock(it) }.ifEmpty { listOf("direct") }
            fieldMap("type" to if (test) "urltest" else "selector", "tag" to group.name, "outbounds" to choices,
                "url" to testURL(group.source.testURL).takeIf { test }, "interval" to "${group.interval}s".takeIf { test },
                "tolerance" to group.tolerance.takeIf { test })
        }
        val outbounds = mutableListOf<Fields>(mapOf("type" to "direct", "tag" to "direct"))
        if (groups.any { g -> g.choices.any(::isBlock) }) outbounds.add(mapOf("type" to "block", "tag" to "reject"))
        outbounds.addAll(groupOutbounds)
        outbounds.addAll(nodeExports.flatMap { listOfNotNull(it.helper, it.outbound) })
        val endpoints = nodeExports.mapNotNull { it.endpoint } + tailnet.endpoints
        val policy = SingBoxDNSPolicy.build(target, network.mode, network.ipv6, network.local, network.remote, dnsProxy, primary,
            rules, tailnet, outbounds, nodes.map { it.name }, taken, network.settings?.tunExcludedRoutes)
        return jsonDocument(fieldMap("log" to mapOf("level" to "warn"), "dns" to policy.dns, "route" to policy.route,
            "inbounds" to listOf(policy.tun), "outbounds" to policy.outbounds, "endpoints" to endpoints.ifEmpty { null },
            "experimental" to policy.experimental, "http_clients" to policy.httpClients))
    }

    private fun egernProfile(nodes: List<ProxyNode>, groups: List<Group>, remotes: List<Remote>, rules: RoutingOutput): String {
        val urls = remotes.associate { it.id to it.url }
        return yamlDocument(mapOf("proxies" to nodes.map(NodeExport::egern), "policy_groups" to groups.map { group ->
            val kind = when (group.kind) {
                RuleSchemeGroupKind.URL_TEST -> "auto_test"
                RuleSchemeGroupKind.FALLBACK -> "fallback"
                RuleSchemeGroupKind.LOAD_BALANCE -> "load_balance"
                else -> "select"
            }
            mapOf(kind to fieldMap("name" to group.name, "policies" to group.choices.ifEmpty { null },
                "urls" to group.remoteIDs.distinct().mapNotNull(urls::get).ifEmpty { null },
                "update_interval" to 86400.takeIf { group.remoteIDs.isNotEmpty() },
                "filter" to exactPattern(group.remoteNames.distinct()).takeIf { group.remoteIDs.isNotEmpty() && !group.allRemote },
                "interval" to group.interval.takeIf { kind in setOf("auto_test", "fallback") },
                "latency_test_url" to testURL(group.source.testURL).takeIf { kind in setOf("auto_test", "fallback") },
                "tolerance" to group.tolerance.takeIf { kind == "auto_test" }, "algorithm" to "hash".takeIf { kind == "load_balance" },
                "icon" to group.icon))
        }, "rules" to (rules.objects + mapOf("default" to mapOf("policy" to rules.final)))))
    }

    private fun iniProxy(target: ClientTarget, node: ProxyNode): String = when (target) {
        ClientTarget.LOON -> NodeExport.loon(node)
        ClientTarget.QUANX -> NodeExport.quanx(node)
        else -> NodeExport.surge(node, target == ClientTarget.SURFBOARD)
    }

    private fun iniProfile(target: ClientTarget, title: String, nodes: List<ProxyNode>, groups: List<Group>, remotes: List<Remote>,
                           rules: RoutingOutput, tailnet: TailnetOutput, network: Network): String {
        val proxyRows = nodes.map { iniProxy(target, it) }
        fun safeDNS(values: List<String>) = values.map(::cleanLine).filterNot { value -> value.any { it in ",;#[]" } || " //" in value }
        if (target == ClientTarget.QUANX) {
            val proxyTags = nodes.mapTo(hashSetOf()) { it.name }
            val policies = groups.map { group ->
                var kind = when (group.kind) {
                    RuleSchemeGroupKind.URL_TEST -> "url-latency-benchmark"
                    RuleSchemeGroupKind.FALLBACK -> "available"
                    RuleSchemeGroupKind.LOAD_BALANCE -> "round-robin"
                    else -> "static"
                }
                val fullBenchmark = kind == "url-latency-benchmark" && group.allRemote
                val choices = if (fullBenchmark) (group.choices.filter { it in proxyTags } + group.remoteNames).distinct() else group.choices
                val params = if (!fullBenchmark && group.remoteIDs.isNotEmpty() && group.remoteNames.isNotEmpty())
                    listOf("server-tag-regex=" + exactPattern(group.remoteNames.distinct())) else emptyList()
                if (fullBenchmark && choices.isEmpty()) kind = "static"
                val members = if (choices.isEmpty() && params.isEmpty()) listOf("direct") else choices
                val latency = if (kind == "url-latency-benchmark") listOf("check-interval=${group.interval}", "tolerance=${group.tolerance}") else emptyList()
                "$kind=" + (listOf(group.name) + members + params + latency + listOfNotNull(group.icon?.let { "img-url=$it" })).joinToString(", ")
            }
            val remoteRules = rules.remoteLines + listOfNotNull(schemes.quanxCatchAll?.takeIf { rules.remoteLines.isNotEmpty() }
                ?.let { "$it, tag=Tower Final, force-policy=${rules.final}, enabled=true" })
            return listOf("# " + confName(title), section("general", listOf("server_check_url=$testURL")),
                section("dns", (if (network.strict) listOf("no-system") else emptyList()) +
                    safeDNS(if (network.strict) network.remote else network.local).map { (if (it.startsWith("https:")) "doh-server=" else "server=") + it }),
                section("policy", policies), section("server_remote", remotes.map {
                    "${it.url}, tag=${confName(it.source.name)}, update-interval=86400, enabled=true" }),
                section("filter_remote", remoteRules), section("rewrite_remote", emptyList()), section("server_local", proxyRows),
                section("filter_local", rules.lines + listOfNotNull("HOST-KEYWORD,.,${rules.final}".takeIf { !network.follow && remoteRules.isEmpty() }) +
                    "FINAL,${rules.final}")).joinToString("\n\n", postfix = "\n")
        }
        val sources = remotes.associateBy { it.id }
        val filters = mutableListOf<String>()
        val policies = groups.map { group ->
            val kind = when (group.kind) {
                RuleSchemeGroupKind.SELECT, RuleSchemeGroupKind.URL_TEST, RuleSchemeGroupKind.FALLBACK,
                RuleSchemeGroupKind.LOAD_BALANCE -> group.kind.rawValue
                else -> "select"
            }
            val ids = group.remoteIDs.distinct()
            val imports = when {
                ids.isEmpty() -> emptyList()
                target != ClientTarget.LOON -> listOf("policy-path=" + quoted(ids.mapNotNull { sources[it]?.url }.joinToString(",")),
                    "update-interval=86400") + listOfNotNull(
                    ("policy-regex-filter=" + quoted(exactPattern(group.remoteNames.distinct()))).takeUnless { group.allRemote })
                group.allRemote -> ids
                else -> {
                    val label = "Tower Filter ${filters.size + 1}"
                    filters.add("$label = NameRegex," + ids.joinToString(",") + ",FilterKey=" + quoted(exactPattern(group.remoteNames.distinct())))
                    listOf(label)
                }
            }
            val tuning = if (kind == "select") emptyList() else listOf("url=${testURL(group.source.testURL)}", "interval=${group.interval}") +
                listOfNotNull("tolerance=${group.tolerance}".takeIf { group.kind == RuleSchemeGroupKind.URL_TEST })
            val icon = group.icon?.let { (if (target == ClientTarget.LOON) "img-url=" else "icon-url=") + it }
            "${group.name} = " + (listOf(kind) + group.choices + imports + tuning + listOfNotNull(icon)).joinToString(", ")
        }
        val general = listOf("loglevel = notify", "ipv6 = ${network.ipv6}",
            "dns-server = " + if (network.strict && target != ClientTarget.LOON) "127.0.0.1" else safeDNS(network.local).joinToString(", ")) +
            listOfNotNull(
                "encrypted-dns-server = ${safeDNS(network.remote).joinToString(", ")}".takeIf {
                    network.strict || (network.follow && network.settings?.fallbackDNSServers?.isNotEmpty() == true) },
                network.settings?.skipProxyHosts?.let(::safeDNS)?.ifEmpty { null }?.let { "skip-proxy = ${it.joinToString(", ")}" },
                network.settings?.tunExcludedRoutes?.let(::safeDNS)?.ifEmpty { null }?.let { "tun-excluded-routes = ${it.joinToString(", ")}" },
            )
        return (listOf("# " + confName(title), section("General", general), section("Proxy", tailnet.proxyLines + proxyRows),
            section("Proxy Group", policies), section("Rule", tailnet.rules + rules.lines + "FINAL,${rules.final}")) +
            listOfNotNull(section("Remote Proxy", remotes.map { "${it.id} = ${it.url}" }).takeIf { target == ClientTarget.LOON && remotes.isNotEmpty() },
                section("Remote Filter", filters).takeIf { filters.isNotEmpty() },
                section("Remote Rule", rules.remoteLines).takeIf { target == ClientTarget.LOON && rules.remoteLines.isNotEmpty() }) +
            if (target in surgeTargets) nodes.filter { it.kind == ProxyKind.WIREGUARD }.map(NodeExport::surgeWireguard) + tailnet.sections else emptyList())
            .joinToString("\n\n", postfix = "\n")
    }
}
