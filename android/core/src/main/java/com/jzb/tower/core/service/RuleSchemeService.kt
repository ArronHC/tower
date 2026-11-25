package com.jzb.tower.core.service

import com.jzb.tower.core.contract.TowerResourceProvider
import com.jzb.tower.core.model.*
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal fun RuleSchemeRuleset.isFinal() = (resource as? RuleSchemeResource.Inline)?.rule?.uppercase(Locale.ROOT) in setOf("FINAL", "MATCH")
internal fun RuleSchemeRuleset.isGeo() = (resource as? RuleSchemeResource.Inline)?.rule?.startsWith("GEOIP,", true) == true
internal fun RuleSchemeRuleset.isMovable() = origin != "supplement" && !isFinal() && !isGeo()
internal fun nodeMatchesPattern(name: String, pattern: String) =
    runCatching { Regex(pattern).containsMatchIn(name) }.getOrDefault(false)

class RuleSchemeService(
    private val resources: TowerResourceProvider,
    private val icons: PolicyGroupIcons = PolicyGroupIcons(resources),
    private val fetcher: SubscriptionFetcher = SubscriptionFetcher(),
) {
    private val manifest by lazy {
        resources.readText("ACL4SSR/ACL4SSR_manifest.json")?.let { Json.parseToJsonElement(it).jsonObject }.orEmpty()
    }
    private val sets by lazy { manifest["rulesets"]?.jsonObject.orEmpty() }
    private val localLines = ConcurrentHashMap<String, List<String>>()
    @Volatile private var matchingReplays: List<RuleMatchingReplay> = emptyList()

    internal fun setMatchingReplays(replays: List<RuleMatchingReplay>) {
        matchingReplays = replays
    }

    internal fun matchingReplay(scheme: String, customization: RuleSchemeCustomization): RuleMatchingReplay? =
        matchingReplays.lastOrNull { it.matches(scheme, customization) }

    val bundledSchemes: List<RuleScheme> by lazy {
        val filesByUrl = sets.entries.associate { (file, entry) -> entry.jsonObject.getValue("source").jsonPrimitive.content to file }
        manifest["configs"]?.jsonObject.orEmpty().map { (id, config) ->
            val fields = config.jsonObject
            fun field(key: String) = fields.getValue(key).jsonPrimitive.content
            val content = requireNotNull(resources.readText("ACL4SSR/" + field("file"))) { "缺少内置方案 $id" }
            val scheme = RuleSchemeParser.parse(content, field("name"), id = id, summary = field("summary"), isBundled = true)
            scheme.copy(rulesets = scheme.rulesets.map {
                val remote = it.resource as? RuleSchemeResource.Remote ?: return@map it
                val file = requireNotNull(filesByUrl[remote.url]) { "缺少内置规则集 ${remote.url.substringAfterLast('/')}" }
                it.copy(resource = RuleSchemeResource.Bundled(file))
            })
        }
    }

    private val catalog by lazy {
        val categories = mapOf("🤖" to "ai", "🔷" to "ai", "🎥" to "streaming", "📹" to "streaming", "🎬" to "streaming",
            "🧸" to "streaming", "📺" to "streaming", "🎵" to "streaming", "🎧" to "streaming", "🍿" to "streaming",
            "💬" to "social", "📲" to "social", "🐦" to "social", "♾️" to "social", "🎮" to "gaming", "🛑" to "advertising",
            "🍃" to "advertising", "🎯" to "domestic", "🏠" to "infrastructure", "🌐" to "infrastructure",
            "🍎" to "infrastructure", "Ⓜ️" to "infrastructure")
        val fallbacks = mapOf("ai" to "✨", "streaming" to "🎬", "social" to "💬", "gaming" to "🎮", "advertising" to "🛑",
            "domestic" to "🎯", "infrastructure" to "🌐")
        listOf(Triple("acl4ssr", "ACL4SSR", "ACL4SSR/ACL4SSR"), Triple("blackmatrix7", "blackmatrix7", "blackmatrix7/ios_rule_script"))
            .flatMap { (source, file, repo) ->
                resources.readText("RuleCatalog/$file.list-index.txt").orEmpty().lineSequence().map(String::trim)
                    .filter { it.endsWith(".list") }.map { path ->
                        val name = path.substringAfterLast('/').removeSuffix(".list")
                        val category = categories[PolicyGroupEmoji.ruleList(name)] ?: "other"
                        val emoji = PolicyGroupEmoji.catalog(name, fallbacks[category] ?: "🧩")
                        val display = if (PolicyGroupEmoji.leading(name) == null) "$emoji $name" else name
                        RuleCatalogEntry("$source:$path", source, name, display, category, emoji,
                            "https://raw.githubusercontent.com/$repo/master/" + path.split('/').joinToString("/", transform = ::percentEncode))
                    }.toList()
            }
    }

    fun searchCatalog(query: String, source: String, limit: Int): List<RuleCatalogEntry> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        return catalog.asSequence().filter { source.equals("all", true) || it.source.equals(source, true) }
            .filter { entry -> terms.all { term -> entry.name.contains(term, true) || entry.category.contains(term, true) || entry.id.contains(term, true) } }
            .take(limit.coerceAtLeast(0)).toList()
    }

    fun catalogEntry(id: String): RuleCatalogEntry? = catalog.firstOrNull { it.id == id }

    fun find(snapshot: AppSnapshot, id: String = snapshot.selectedPresetID) =
        (bundledSchemes + snapshot.importedSchemes).firstOrNull { it.id == id }

    fun lines(rule: RuleSchemeRuleset): List<String> = when (val resource = rule.resource) {
        is RuleSchemeResource.Inline -> listOf(resource.rule)
        is RuleSchemeResource.Bundled -> localLines.computeIfAbsent(resource.file) {
            ruleLines(requireNotNull(resources.readText("ACL4SSR/$it")) { "缺少内置规则集 $it" })
        }
        is RuleSchemeResource.Remote -> emptyList()
    }

    internal fun published(file: String, dialect: String): JsonObject? = sets[file]?.jsonObject?.get(dialect)?.jsonObject
    internal fun mrsResidualCount(file: String): Int? = sets[file]?.jsonObject?.get("mrsResidualRuleCount")?.jsonPrimitive?.intOrNull
    internal fun publishedInline(rule: String, dialect: String): String? =
        manifest["inlineRules"]?.jsonObject?.get(rule)?.jsonObject?.get(dialect)?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
    internal val quanxCatchAll get() = manifest["quanxCatchAll"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

    suspend fun importScheme(name: String?, url: String?, content: String?, id: String? = null): RuleScheme {
        val normalized = url?.takeIf(String::isNotBlank)?.let { webURL(it, true).toString() }
        val text = content?.takeIf(String::isNotBlank) ?: normalized?.let { fetcher.fetchRuleText(it) }
            ?: throw IllegalArgumentException("请输入规则链接或内容")
        val title = name?.takeIf(String::isNotBlank) ?: normalized?.substringBefore('?')?.substringAfterLast('/')?.substringBeforeLast('.')
        val scheme = if (id == null) RuleSchemeParser.parse(text, title, normalized) else RuleSchemeParser.parse(text, title, normalized, id)
        return materializeRemote(scheme)
    }

    private suspend fun materializeRemote(scheme: RuleScheme): RuleScheme {
        val downloaded = mutableMapOf<String, List<String>>()
        for (url in scheme.rulesets.mapNotNull { (it.resource as? RuleSchemeResource.Remote)?.url }.distinct()) {
            if (url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT) in setOf("mrs", "srs")) continue
            try {
                downloaded[url] = ruleLines(fetcher.fetchRuleText(url))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: java.io.IOException) {
                // A remote reference remains usable by clients that support it.
            } catch (_: IllegalArgumentException) {
                // The imported source still retains its original reference.
            }
        }
        return scheme.copy(rulesets = scheme.rulesets.flatMap { rule ->
            val parsed = (rule.resource as? RuleSchemeResource.Remote)?.url?.let(downloaded::get)
            if (parsed.isNullOrEmpty()) listOf(rule) else parsed.map { rule.copy(resource = RuleSchemeResource.Inline(it)) }
        })
    }

    fun protectedNames(source: RuleScheme): Set<String> {
        val fixed = setOf("全球直连", "全球拦截", "漏网之鱼", "global direct", "global reject", "final", "final match")
        return (source.rulesets.filter { it.isFinal() }.map { it.groupName } +
            source.groups.filter { PolicyGroupEmoji.withoutLeading(it.name).lowercase(Locale.ROOT) in fixed }.map { it.name }).toSet()
    }

    private fun groups(snapshot: AppSnapshot, source: RuleScheme, includeDisabledFlows: Boolean): List<RuleSchemeGroup> {
        val custom = snapshot.ruleSchemeCustomizations[source.id] ?: RuleSchemeCustomization()
        val positions = custom.groupOrder.withIndex().associate { it.value to it.index }
        return (source.groups + snapshot.customRuleFlows.filter { it.schemeID == source.id && (includeDisabledFlows || it.isEnabled) }
            .mapNotNull { it.generatedPolicyGroup } + custom.addedNodeGroups).distinctBy { it.name }
            .sortedBy { positions[it.name] ?: Int.MAX_VALUE }
    }

    private fun configured(group: RuleSchemeGroup, custom: RuleSchemeCustomization) = group.copy(
        name = custom.renamedGroups[group.name] ?: group.name,
        kind = custom.groupKindOverrides[group.name] ?: group.kind,
        members = custom.groupMemberOverrides[group.name] ?: group.members,
    )

    fun effective(snapshot: AppSnapshot, source: RuleScheme, match: Boolean = true, rename: Boolean = true): RuleScheme {
        val custom = snapshot.ruleSchemeCustomizations[source.id] ?: RuleSchemeCustomization()
        val declarations = groups(snapshot, source, false)
        val protected = protectedNames(source)
        val removed = custom.removedGroupNames.toSet() - protected
        val enabled = snapshot.selectedRuleGroups[source.id]
        val byName = declarations.associateBy { it.name }
        val selected = (enabled ?: byName.keys.toList()).toMutableSet()
        selected.addAll(protected)
        val pending = ArrayDeque(selected)
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (name in removed) continue
            val group = byName[name] ?: continue
            configured(group, custom).members.filterIsInstance<RuleSchemeGroupMember.Reference>().forEach {
                if (selected.add(it.name)) pending.add(it.name)
            }
        }
        fun active(name: String) = name !in removed && (enabled == null || name in selected || isBuiltinPolicy(name))
        fun renamed(name: String) = if (rename) custom.renamedGroups[name] ?: name else name
        val rules = source.rulesets.filter { active(it.groupName) || it.isFinal() }.toMutableList()
        val supplements = mutableListOf<RuleSchemeRuleset>()
        for (flow in snapshot.customRuleFlows.filter { it.schemeID == source.id && it.isEnabled && active(it.policyName) }) {
            val local = snapshot.localRuleSets.firstOrNull { it.id == flow.localRuleSetID }
            val text = local?.rulesText ?: flow.rulesText
            val url = flow.sourceURLString ?: flow.catalogID?.let(::catalogEntry)?.url ?: local?.sourceURLString
            val supplement = flow.isGroupSupplement == true
            var additions = ruleLines(text).mapNotNull { line ->
                val rule = splitRule(line) ?: return@mapNotNull null
                if (supplement && rule.type !in supplementTypes) return@mapNotNull null
                RuleSchemeRuleset(flow.policyName, RuleSchemeResource.Inline(rule.line), rule.options,
                    if (supplement) "supplement" else "added", flow.name)
            }
            if (!supplement && additions.isEmpty() && url != null) {
                additions = listOf(RuleSchemeRuleset(flow.policyName, RuleSchemeResource.Remote(url), origin = "added", addedLabel = flow.name))
            }
            if (supplement) supplements.addAll(additions)
            else if (additions.isNotEmpty()) {
                val predicate = DomainMatcher(additions.flatMap(::lines))
                val index = rules.indexOfFirst { overlaps(predicate, DomainMatcher(lines(it))).any() }
                val fallback = rules.indexOfFirst { it.isGeo() || it.isFinal() }
                rules.addAll(index.takeIf { it >= 0 } ?: fallback.takeIf { it >= 0 } ?: rules.size, additions)
            }
        }
        val ordered = if (match) applyMatchingOrder(source, custom, rules) else rules
        val sortedSupplements = supplements.sortedByDescending(::specificity)
            .distinctBy { (it.resource as RuleSchemeResource.Inline).rule.lowercase(Locale.ROOT) }
        return source.copy(
            networkSettings = if (custom.overridesNetworkSettings) custom.networkSettingsOverride else source.networkSettings,
            groups = declarations.filter { active(it.name) }.map { group ->
                configured(group, custom).copy(name = renamed(group.name), members = configured(group, custom).members.mapNotNull {
                    when (it) {
                        is RuleSchemeGroupMember.Reference -> it.takeUnless { ref -> ref.name in removed }?.copy(name = renamed(it.name))
                        is RuleSchemeGroupMember.NodePattern -> it
                    }
                })
            },
            rulesets = (sortedSupplements + ordered).map { it.copy(groupName = renamed(it.groupName)) },
        )
    }

    fun presentation(snapshot: AppSnapshot, source: RuleScheme, nodes: List<ProxyNode>): List<RuleGroupPresentation> {
        val custom = snapshot.ruleSchemeCustomizations[source.id] ?: RuleSchemeCustomization()
        val flows = snapshot.customRuleFlows.filter { it.schemeID == source.id }
        val effective = effective(snapshot, source)
        val active = effective.groups.mapTo(mutableSetOf()) { it.name }
        val protected = protectedNames(source)
        val selected = snapshot.selectedRuleGroups[source.id]
        val emojiEnabled = snapshot.ruleGroupEmojisEnabled[source.id] != false
        val auto = snapshot.ruleGroupEmojiAutofill[source.id] ?: !source.isBundled
        return groups(snapshot, source, true).filter { it.name in protected || it.name !in custom.removedGroupNames }.map { group ->
            val configured = configured(group, custom)
            val title = configured.name
            val patterns = configured.members.filterIsInstance<RuleSchemeGroupMember.NodePattern>()
                .mapNotNull { runCatching { Regex(it.pattern) }.getOrNull() }
            val filter = group.isCustomNodeFilter == true || (patterns.isNotEmpty() && patterns.all { it.pattern != ".*" } &&
                configured.members.all { it is RuleSchemeGroupMember.NodePattern })
            val emoji = if (emojiEnabled) if (auto) PolicyGroupEmoji.resolve(configured, effective.rulesets)
                else PolicyGroupEmoji.leading(title) ?: configured.iconEmoji else null
            val matchingRules = effective.rulesets.filter { it.groupName == title }.ifEmpty { source.rulesets.filter { it.groupName == group.name } }
            RuleGroupPresentation(group.name, PolicyGroupEmoji.withoutLeading(title).ifEmpty { title }, emoji, icons.url(emoji),
                configured.kind, configured.members, group.name in protected || (selected != null && group.name !in selected && title in active),
                title in active, filter, matchingRules.sumOf { lines(it).size.coerceAtLeast(1) },
                flows.filter { it.policyName == group.name && it.isGroupSupplement == true }.flatMap { ruleLines(it.rulesText) },
                if (patterns.isEmpty()) 0 else nodes.count { node -> patterns.any { it.containsMatchIn(node.name) } },
                if (filter) "nodePatternsOnly" else "routingTargets")
        }
    }

    fun summary(snapshot: AppSnapshot, source: RuleScheme) = RuleSchemeSummary(source.id, source.name, source.summary,
        source.isBundled, source.sourceURLString, source.updatedAtEpochMs, source.groups.size, source.rulesets.size,
        snapshot.ruleGroupEmojisEnabled[source.id] != false, snapshot.ruleGroupEmojiAutofill[source.id] ?: !source.isBundled)

    private fun applyMatchingOrder(
        source: RuleScheme,
        customization: RuleSchemeCustomization,
        rules: List<RuleSchemeRuleset>,
    ): List<RuleSchemeRuleset> {
        val replay = matchingReplay(source.id, customization)
        if (replay == null) return reorderRules(rules, customization.matchingOrder, inferMovedGroup(source, customization, rules))
        val base = customization.copy(groupOrder = replay.baseGroupOrder, matchingOrder = replay.baseMatchingOrder)
        val initial = reorderRules(rules, base.matchingOrder, inferMovedGroup(source, base, rules))
        val applied = replay.moves.fold(initial, ::moveRuleBlock)
        val desired = customization.matchingOrder.filter { name -> applied.any { it.groupName == name && it.isMovable() } }.distinct()
        return if (matchingGroups(applied).filter { it in desired } == desired) applied else reorderRules(applied, desired)
    }

    internal fun moveRuleBlock(rules: List<RuleSchemeRuleset>, move: RuleMatchingMove): List<RuleSchemeRuleset> {
        fun isDragged(rule: RuleSchemeRuleset) = rule.groupName == move.group && rule.isMovable()
        fun isAnchor(rule: RuleSchemeRuleset) = rule.groupName == move.anchor && rule.isMovable()
        val block = rules.filter(::isDragged)
        if (block.isEmpty()) return rules
        val kept = rules.filterNot(::isDragged).toMutableList()
        val limit = move.before?.let { name -> kept.indexOfFirst { it.groupName == name && it.isMovable() } }
            ?.takeIf { it >= 0 } ?: kept.size
        val anchor = if (move.afterward) kept.subList(0, limit).indexOfLast(::isAnchor) else kept.indexOfFirst(::isAnchor)
        if (anchor < 0) return rules
        kept.addAll(anchor + if (move.afterward) 1 else 0, block)
        return kept
    }

    private fun inferMovedGroup(source: RuleScheme, custom: RuleSchemeCustomization, rules: List<RuleSchemeRuleset>): String? {
        val current = matchingGroups(rules)
        val desired = custom.matchingOrder.filter { it in current }.distinct()
        if (current.size != desired.size || current == desired) return null
        val originalDisplay = source.groups.map { it.name }
        val candidates = desired.filter { moved ->
            desired.filterNot { it == moved } == current.filterNot { it == moved }
        }
        return candidates.maxByOrNull { moved ->
            val from = originalDisplay.indexOf(moved)
            val to = custom.groupOrder.indexOf(moved)
            if (from < 0 || to < 0) 0 else kotlin.math.abs(from - to)
        }
    }

    internal fun matchingGroups(rules: List<RuleSchemeRuleset>) = rules.filter { it.isMovable() && !isBuiltinPolicy(it.groupName) }
        .map { it.groupName }.distinct()

    internal fun matchingOrderAfterDrag(moved: String, previous: List<String>, next: List<String>, matching: List<String>): List<String> {
        val from = previous.indexOf(moved)
        val to = next.indexOf(moved)
        val current = matching.indexOf(moved)
        if (from < 0 || to < 0 || from == to || current < 0) return matching
        val down = from < to
        val crossed = if (down) next.subList(from, to) else next.subList(to + 1, from + 1)
        val candidates = crossed.map(matching::indexOf).filter { if (down) it > current else it in 0 until current }
        val anchorIndex = (if (down) candidates.maxOrNull() else candidates.minOrNull()) ?: return matching
        val result = matching.toMutableList()
        result.removeAt(current)
        result.add(result.indexOf(matching[anchorIndex]) + if (down) 1 else 0, moved)
        return result
    }

    internal fun reorderRules(rules: List<RuleSchemeRuleset>, desiredOrder: List<String>, dragged: String? = null): List<RuleSchemeRuleset> {
        val rank = matchingGroups(rules).withIndex().associate { it.value to it.index }
        val desired = desiredOrder.filter { it in rank }.distinct()
        if (desired.size < 2) return rules
        val values = desired.map(rank::getValue)
        val lengths = IntArray(values.size) { 1 }
        val previous = IntArray(values.size) { -1 }
        for (index in values.indices) for (before in 0 until index) {
            if (values[before] < values[index] && lengths[before] + 1 >= lengths[index]) {
                lengths[index] = lengths[before] + 1
                previous[index] = before
            }
        }
        val withoutDragged = desired.filterNot { it == dragged }
        val draggedIndex = desired.indexOf(dragged)
        if (draggedIndex >= 0 && withoutDragged.map(rank::getValue).zipWithNext().all { it.first < it.second }) {
            val originalIndex = rank.getValue(desired[draggedIndex])
            if (draggedIndex == originalIndex) return rules
            val down = draggedIndex > originalIndex
            val anchor = desired[draggedIndex + if (down) -1 else 1]
            return moveRuleBlock(rules, RuleMatchingMove(desired[draggedIndex], anchor, down,
                desired.getOrNull(draggedIndex + 1).takeIf { down }))
        }
        val retained = buildSet {
            var cursor = values.indices.filter { lengths[it] == lengths.maxOrNull() }.minBy { values[it] }
            while (cursor >= 0) { add(desired[cursor]); cursor = previous[cursor] }
        }
        val moved = desired.toSet() - retained
        if (moved.isEmpty()) return rules
        val blocks = rules.filter { it.isMovable() && it.groupName in moved }.groupBy { it.groupName }
        val before = mutableMapOf<String, MutableList<RuleSchemeRuleset>>()
        val trailing = mutableListOf<RuleSchemeRuleset>()
        desired.forEachIndexed { index, name ->
            val block = blocks[name] ?: return@forEachIndexed
            val anchor = desired.drop(index + 1).firstOrNull { it in retained }
            if (anchor == null) trailing.addAll(block) else before.getOrPut(anchor) { mutableListOf() }.addAll(block)
        }
        val result = mutableListOf<RuleSchemeRuleset>()
        rules.filterNot { it.groupName in moved && it.isMovable() }.forEach { rule ->
            if (rule.isMovable()) before.remove(rule.groupName)?.let(result::addAll)
            result.add(rule)
        }
        if (trailing.isNotEmpty()) {
            val last = desired.lastOrNull { it in retained }
            val index = result.indexOfLast { it.isMovable() && it.groupName == last }
            val fallback = result.indexOfFirst { it.isGeo() || it.isFinal() }
            result.addAll(if (index >= 0) index + 1 else fallback.takeIf { it >= 0 } ?: result.size, trailing)
        }
        return result
    }

    internal fun conflicts(
        before: List<RuleSchemeRuleset>,
        after: List<RuleSchemeRuleset>,
        moved: String,
    ): List<MatchingOrderConflict> {
        if (before == after) return emptyList()
        val old = OrderedDomainRules(before)
        val new = OrderedDomainRules(after)
        val samples = (old.samples.asSequence() + new.samples.asSequence()).distinct()
        val changes = linkedMapOf<Pair<String, ConflictDirection>, MutableList<String>>()
        for (sample in samples) {
            val first = old.firstPolicy(sample) ?: continue
            val next = new.firstPolicy(sample) ?: continue
            if (first == next || (first != moved && next != moved)) continue
            val direction = if (next == moved) ConflictDirection.SHADOWED_BY_MOVED else ConflictDirection.NOW_SHADOWED_BY_OTHER
            val domains = changes.getOrPut((if (next == moved) first else next) to direction) { mutableListOf() }
            if (domains.size < 5) domains.add(sample)
        }
        return changes.map { (key, domains) -> MatchingOrderConflict(moved, key.first, domains, key.second) }
    }

    private data class DomainMatch(val index: Int, val policy: String)

    private inner class OrderedDomainRules(rules: List<RuleSchemeRuleset>) {
        private val exact = hashMapOf<String, DomainMatch>()
        private val suffix = hashMapOf<String, DomainMatch>()
        private val patterns = mutableListOf<Pair<DomainMatch, DomainMatcher>>()
        val samples = linkedSetOf<String>()

        init {
            rules.forEachIndexed { index, rule ->
                val matcher = DomainMatcher(lines(rule))
                if (matcher.samples.isEmpty()) return@forEachIndexed
                val match = DomainMatch(index, rule.groupName)
                matcher.exact.forEach { exact.putIfAbsent(it, match) }
                matcher.suffix.forEach { suffix.putIfAbsent(it, match) }
                if (matcher.hasPatterns) patterns.add(match to matcher)
                samples.addAll(matcher.samples)
            }
        }

        fun firstPolicy(value: String): String? {
            var first = exact[value]
            var rest = value
            while (rest.isNotEmpty()) {
                val match = suffix[rest]
                if (match != null && (first == null || match.index < first.index)) first = match
                rest = rest.substringAfter('.', "")
            }
            for ((match, matcher) in patterns) {
                if (first != null && match.index >= first.index) break
                if (matcher.matchesPattern(value)) return match.policy
            }
            return first?.policy
        }
    }

    private class DomainMatcher(lines: List<String>) {
        val exact = mutableSetOf<String>()
        val suffix = mutableSetOf<String>()
        private val keywords = mutableSetOf<String>()
        private val wildcards = mutableListOf<Regex>()
        val samples = linkedSetOf<String>()
        val hasPatterns get() = keywords.isNotEmpty() || wildcards.isNotEmpty()

        init {
            for (line in lines) {
                val rule = splitRule(line) ?: continue
                val value = rule.value.trim('.').lowercase(Locale.ROOT)
                when (rule.type) {
                    "DOMAIN" -> { exact.add(value); samples.add(value) }
                    "DOMAIN-SUFFIX" -> { suffix.add(value); samples.add(value); samples.add("www.$value") }
                    "DOMAIN-KEYWORD" -> { keywords.add(value); samples.add(if ('.' in value) value else "$value.example") }
                    "DOMAIN-WILDCARD" -> {
                        wildcards.add(Regex(value.map { if (it == '*') ".*" else if (it == '?') "." else Regex.escape(it.toString()) }.joinToString("")))
                        samples.add(value.replace("*", "www").replace("?", "a"))
                    }
                }
            }
        }

        fun matches(value: String): Boolean {
            if (value in exact) return true
            var candidate = value
            while (candidate.isNotEmpty()) {
                if (candidate in suffix) return true
                candidate = candidate.substringAfter('.', "")
            }
            return matchesPattern(value)
        }

        fun matchesPattern(value: String) = keywords.any(value::contains) || wildcards.any { it.matches(value) }
    }

    private fun overlaps(first: DomainMatcher, second: DomainMatcher) =
        (first.samples.asSequence() + second.samples.asSequence()).distinct().filter { first.matches(it) && second.matches(it) }

    private fun specificity(rule: RuleSchemeRuleset): Int {
        val condition = splitRule((rule.resource as RuleSchemeResource.Inline).rule) ?: return 0
        return when (condition.type) {
            "DOMAIN" -> 10_000 + condition.value.length
            "IP-CIDR", "IP-CIDR6" -> 7500 + (condition.value.substringAfter('/').toIntOrNull() ?: 0)
            "DOMAIN-SUFFIX" -> 5000 + condition.value.length
            "DOMAIN-WILDCARD" -> 2500 + condition.value.length
            else -> condition.value.length
        }
    }

    companion object {
        private val supplementTypes = setOf("DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "IP-CIDR", "IP-CIDR6")
    }
}
