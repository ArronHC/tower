package com.jzb.tower.core.engine

import com.jzb.tower.core.contract.*
import com.jzb.tower.core.model.*
import com.jzb.tower.core.service.*
import com.jzb.tower.core.store.ConfigurationCache
import com.jzb.tower.core.store.PersistenceStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TowerRepositoryImpl(
    resources: TowerResourceProvider,
    private val secrets: TailnetSecretStore,
    filesDirectory: File,
    private val lanHooks: LanServiceHooks = object : LanServiceHooks {},
    private val fetcher: SubscriptionFetcher = SubscriptionFetcher(),
) : TowerRepository {
    private val store = PersistenceStore(filesDirectory)
    private val countryDB = IPCountryDatabase(resources)
    private val asnDB = IPASNDatabase(resources)
    private val geography = NodeRegionResolver(countryDB, asnDB)
    private val latency = NodeLatencyService()
    private val schemes = RuleSchemeService(resources, fetcher = fetcher)
    private val generator = ConfigurationGenerator(resources, secrets, countryDB, asnDB, schemes)
    private val map by lazy { WorldDotMapLoader(resources).load() }
    private val lock = Mutex()
    private val lifecycle = Any()
    private val disposed = AtomicBoolean()
    private val hostLocks = ConcurrentHashMap<String, Mutex>()
    private val cache = ConfigurationCache()
    private val direct = DirectImportServer()
    private val lan = LanSubscriptionServer(
        generate = { target, mode ->
            val current = state.value
            generator.generate(current.snapshot, target, mode, false, current.runtime)
        },
        headers = { subscriptionHeaders(state.value.snapshot) },
    )
    private val currentState = MutableStateFlow(AppState())
    override val state = currentState.asStateFlow()
    private var initialized = false
    private var revision = 0L
    private var dataEpoch = 0L
    private var matchingReplays: List<RuleMatchingReplay> = emptyList()

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        lock.withLock { initializeLocked() }
    }

    private fun initializeLocked() {
        check(!disposed.get()) { "仓库已关闭" }
        if (initialized) return
        val document = store.readDocument()
        val initial = document?.snapshot ?: AppSnapshot()
        matchingReplays = document?.replays.orEmpty()
        schemes.setMatchingReplays(matchingReplays)
        val snapshot = initial.copy(tailnets = initial.tailnets.map(::deriveTailnet))
        publish(snapshot, rebuildRuntime(snapshot.nodes, emptyMap()))
        initialized = true
    }

    private suspend fun <T> edit(action: () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { initializeLocked(); action() }
    }

    private fun snapshot() = currentState.value.snapshot

    private fun commit(snapshot: AppSnapshot, runtime: Map<String, NodeRuntimeInfo>? = null, preserveTimestamp: Boolean = false,
                       nextReplays: List<RuleMatchingReplay> = matchingReplays) {
        val previous = currentState.value
        val now = System.currentTimeMillis()
        val next = if (preserveTimestamp) snapshot else snapshot.copy(updatedAtEpochMs =
            maxOf(now, previous.snapshot.updatedAtEpochMs.coerceAtMost(Long.MAX_VALUE - 1) + 1))
        val resolved = runtime ?: if (next.nodes === previous.snapshot.nodes) previous.runtime
            else rebuildRuntime(next.nodes, previous.runtime)
        schemes.setMatchingReplays(nextReplays)
        val state = try {
            val state = stateFor(next, resolved, previous.lanStatus)
            store.writeDocument(next, nextReplays)
            state
        } catch (e: Exception) {
            schemes.setMatchingReplays(matchingReplays)
            throw e
        }
        matchingReplays = nextReplays
        revision++
        cache.clear()
        currentState.update { state.copy(lanStatus = it.lanStatus) }
    }

    private fun publish(snapshot: AppSnapshot, runtime: Map<String, NodeRuntimeInfo>) {
        val next = stateFor(snapshot, runtime, currentState.value.lanStatus)
        currentState.update { next.copy(lanStatus = it.lanStatus) }
    }

    private fun stateFor(snapshot: AppSnapshot, runtime: Map<String, NodeRuntimeInfo>, sharing: LanSharingStatus): AppState {
        val active = activeNodes(snapshot)
        val measured = active.filterNot(SubscriptionParser::isMetadata)
        val selected = schemes.find(snapshot) ?: schemes.bundledSchemes.firstOrNull()
        val presentation = selected?.let { schemes.presentation(snapshot, it, measured) }.orEmpty()
        val groups = presentation.map { it.sourceName }
        val builtins = listOf("DIRECT", "REJECT", "REJECT-DROP")
        val targets = (builtins + groups).distinct()
        return AppState(snapshot, runtime, schemes.bundledSchemes.map { schemes.summary(snapshot, it) },
            snapshot.importedSchemes.map { schemes.summary(snapshot, it) }, presentation, targets,
            groups.associateWith { owner -> groups.filter { it != owner } }, sharing,
            AppStats(snapshot.subscriptions.size, snapshot.subscriptions.count { it.isEnabled }, snapshot.nodes.size, active.size,
                measured.mapNotNull { runtime[it.id]?.countryCode }.distinct().size),
            ipDbVersion = countryDB.version)
    }

    private fun rebuildRuntime(nodes: List<ProxyNode>, old: Map<String, NodeRuntimeInfo>): Map<String, NodeRuntimeInfo> =
        nodes.associate { node ->
            val previous = old[node.id]
            val named = geography.fromName(node)
            val ipCountry = previous?.resolvedIP?.let(countryDB::countryCode)?.let(CountryTable::get)
            val current = if (named.regionSource == RegionSource.UNKNOWN && ipCountry != null)
                NodeRuntimeInfo(node.id, ipCountry.code, ipCountry.chineseName, ipCountry.englishName, ipCountry.flag,
                    ipCountry.latitude, ipCountry.longitude, RegionSource.IP) else named
            node.id to current.copy(resolvedIP = previous?.resolvedIP, asn = previous?.asn, asnOrg = previous?.asnOrg, latency = previous?.latency)
        }

    override suspend fun getWorldDotMap(): WorldDotMapData = withContext(Dispatchers.IO) { map }
    override fun getCountries(): List<CountryEntry> = CountryTable.countries
    override fun detectSourceInput(input: String): SourceDetectionResult = SourceInputDetector.detect(input)

    private fun normalizedOptions(options: SubscriptionRequestOptions?): SubscriptionRequestOptions? {
        if (options == null) return null
        return options.copy(userAgent = options.userAgent?.let(::cleanLine)?.takeIf(String::isNotEmpty),
            dnsOverHTTPSURL = options.dnsOverHTTPSURL?.takeIf(String::isNotBlank)?.let { webURL(it, true).toString() })
            .takeUnless { it.isEmpty }
    }

    override suspend fun addSubscription(urlString: String, name: String?, requestOptions: SubscriptionRequestOptions?): SubscriptionMutationResult {
        val url = webURL(urlString)
        val options = normalizedOptions(requestOptions)
        val id = UUID.randomUUID().toString()
        val epoch = edit {
            require(snapshot().subscriptions.none { it.urlString == url.toString() }) { "此订阅已添加" }
            dataEpoch
        }
        return withHost(url.host) {
            val fetched = fetcher.fetch(url.toString(), options, id)
            val nodes = parsedNodes(fetched.parsed)
            edit {
                check(epoch == dataEpoch) { "数据已变更，请重新添加订阅" }
                val previous = snapshot()
                require(previous.subscriptions.none { it.urlString == url.toString() }) { "此订阅已添加" }
                val now = System.currentTimeMillis()
                val title = name?.let(::cleanLine)?.takeIf(String::isNotEmpty)
                val source = SubscriptionSource(id, title ?: fetched.title ?: fallbackName(url.host), url.toString(),
                    createdAtEpochMs = now, lastUpdatedAtEpochMs = now, usage = fetched.usage, requestOptions = options,
                    nameWasAutoGenerated = title == null)
                commit(previous.copy(subscriptions = previous.subscriptions + source, nodes = previous.nodes + nodes))
                SubscriptionMutationResult(source, nodes.size, fetched.parsed.skippedCount, fetched.parsed.skippedReasons)
            }
        }
    }

    override suspend fun updateSubscription(id: String, name: String?, urlString: String?, isEnabled: Boolean?,
                                            requestOptions: SubscriptionRequestOptions?) = edit {
        val previous = snapshot()
        val source = previous.subscriptions.firstOrNull { it.id == id } ?: throw NoSuchElementException("订阅不存在")
        val url = urlString?.let { webURL(it).toString() } ?: source.urlString
        require(previous.subscriptions.none { it.id != id && it.urlString == url }) { "此订阅已添加" }
        val updated = source.copy(name = name?.let(::cleanLine)?.takeIf(String::isNotEmpty) ?: source.name,
            urlString = url, isEnabled = isEnabled ?: source.isEnabled,
            requestOptions = if (requestOptions != null) normalizedOptions(requestOptions) else source.requestOptions,
            nameWasAutoGenerated = if (name != null && name.isNotBlank()) false else source.nameWasAutoGenerated,
            lastError = if (source.urlString != url) null else source.lastError)
        if (updated != source) commit(previous.copy(subscriptions = previous.subscriptions.map { if (it.id == id) updated else it }))
    }

    override suspend fun deleteSubscription(id: String) = edit {
        val previous = snapshot()
        if (previous.subscriptions.none { it.id == id }) return@edit
        val removed = previous.nodes.filter { it.sourceID == id }.mapTo(hashSetOf()) { it.id }
        commit(previous.copy(subscriptions = previous.subscriptions.filterNot { it.id == id }, nodes = previous.nodes.filterNot { it.id in removed },
            excludedNodeIDs = previous.excludedNodeIDs.filterNot { it in removed }))
    }

    override suspend fun refreshSubscription(id: String): SubscriptionMutationResult = refresh(id, propagateFailure = true)

    override suspend fun refreshAllSubscriptions(): List<SubscriptionMutationResult> = coroutineScope {
        val sources = edit { snapshot().subscriptions.filter { it.isEnabled } }
        sources.map { source -> async {
            try {
                refresh(source.id, propagateFailure = false)
            } catch (_: NoSuchElementException) {
                null // The subscription was deleted while waiting for its host.
            }
        } }.awaitAll().filterNotNull()
    }

    private data class RefreshTicket(val source: SubscriptionSource, val epoch: Long)

    private suspend fun refresh(id: String, propagateFailure: Boolean): SubscriptionMutationResult {
        val first = edit { findSubscription(id) }
        return withHost(webURL(first.urlString).host) {
            val ticket = edit {
                val source = findSubscription(id)
                check(webURL(source.urlString).host == webURL(first.urlString).host) { "订阅地址已变更" }
                RefreshTicket(source, dataEpoch)
            }
            val source = ticket.source
            val fetched: FetchedSubscription
            val nodes: List<ProxyNode>
            try {
                fetched = fetcher.fetch(source.urlString, source.requestOptions, id)
                nodes = parsedNodes(fetched.parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (e is SubscriptionContentException) e.message.orEmpty() else "无法读取订阅，请检查链接和网络"
                val failed = edit {
                    val current = validateTicket(ticket)
                    val updated = current.copy(lastError = message)
                    val previous = snapshot()
                    commit(previous.copy(subscriptions = previous.subscriptions.map { if (it.id == id) updated else it }))
                    updated
                }
                if (propagateFailure) throw IOException(message)
                val reasons = (e as? SubscriptionContentException)?.reasons.orEmpty()
                return@withHost SubscriptionMutationResult(failed, 0, reasons.sumOf { it.count }, reasons)
            }
            edit {
                val current = validateTicket(ticket)
                val previous = snapshot()
                val old = previous.nodes.filter { it.sourceID == id }
                val oldIDs = old.mapTo(hashSetOf()) { it.id }
                val excluded = previous.excludedNodeIDs.toSet()
                val carried = carriedOverExclusions(old, excluded, nodes)
                val refreshed = carried.nodes
                val updated = current.copy(name = if (current.nameWasAutoGenerated == true) fetched.title ?: current.name else current.name,
                    usage = fetched.usage, lastError = null, lastUpdatedAtEpochMs = System.currentTimeMillis())
                val insertion = previous.nodes.indexOfFirst { it.sourceID == id }.takeIf { it >= 0 } ?: previous.nodes.size
                val all = previous.nodes.filterNot { it.sourceID == id }.toMutableList().apply { addAll(insertion, refreshed) }
                val runtime = currentState.value.runtime.toMutableMap()
                carried.matches.forEach { (next, oldId) -> runtime[oldId]?.let { runtime[next] = it.copy(nodeId = next) } }
                commit(previous.copy(subscriptions = previous.subscriptions.map { if (it.id == id) updated else it }, nodes = all,
                    excludedNodeIDs = (excluded - oldIDs + carried.excludedNodeIDs).toList()), rebuildRuntime(all, runtime))
                SubscriptionMutationResult(updated, refreshed.size, fetched.parsed.skippedCount, fetched.parsed.skippedReasons)
            }
        }
    }

    private fun validateTicket(ticket: RefreshTicket): SubscriptionSource {
        val current = findSubscription(ticket.source.id)
        check(ticket.epoch == dataEpoch && current.urlString == ticket.source.urlString && current.requestOptions == ticket.source.requestOptions) {
            "订阅设置已变更，请重新刷新"
        }
        return current
    }

    private fun findSubscription(id: String) = snapshot().subscriptions.firstOrNull { it.id == id }
        ?: throw NoSuchElementException("订阅不存在")

    private suspend fun <T> withHost(host: String, action: suspend () -> T): T = hostLocks.computeIfAbsent(host) { Mutex() }.withLock { action() }

    private class SubscriptionContentException(val reasons: List<SkippedReasonCount>) : IOException("订阅没有可用节点")
    private fun parsedNodes(parsed: ParsedSubscription): List<ProxyNode> = parsed.nodes.distinctBy { canonicalKey(it) }
        .also { if (it.isEmpty() || it.all(SubscriptionParser::isMetadata)) throw SubscriptionContentException(parsed.skippedReasons) }

    override suspend fun importNodesFromText(content: String): NodeImportResult {
        val parsed = withContext(Dispatchers.Default) { SubscriptionParser.parse(content) }
        return edit {
            val previous = snapshot()
            val keys = previous.nodes.filter { it.sourceID == null }.mapTo(hashSetOf()) { canonicalKey(it) }
            val added = parsed.nodes.filter { keys.add(canonicalKey(it)) }
            val duplicates = parsed.nodes.size - added.size
            if (added.isNotEmpty()) commit(previous.copy(nodes = previous.nodes + added))
            val reasons = parsed.skippedReasons + listOfNotNull(SkippedReasonCount("重复节点", duplicates).takeIf { duplicates > 0 })
            NodeImportResult(added, reasons.sumOf { it.count }, reasons)
        }
    }

    override suspend fun updateNode(id: String, name: String?, countryOverride: String?, clearCountryOverride: Boolean, excluded: Boolean?) = edit {
        val previous = snapshot()
        val old = previous.nodes.firstOrNull { it.id == id } ?: throw NoSuchElementException("节点不存在")
        val country = when {
            clearCountryOverride -> null
            countryOverride != null -> requireNotNull(CountryTable[countryOverride]) { "未知的国家或地区" }.code
            else -> old.countryOverride
        }
        val next = old.copy(name = name?.let(::cleanLine)?.takeIf(String::isNotEmpty) ?: old.name, countryOverride = country)
        val ids = if (excluded == null) previous.excludedNodeIDs
            else if (excluded) (previous.excludedNodeIDs + id).distinct() else previous.excludedNodeIDs - id
        if (next != old || ids != previous.excludedNodeIDs)
            commit(previous.copy(nodes = previous.nodes.map { if (it.id == id) next else it }, excludedNodeIDs = ids))
    }

    override suspend fun deleteNode(id: String) = edit {
        val previous = snapshot()
        if (previous.nodes.any { it.id == id })
            commit(previous.copy(nodes = previous.nodes.filterNot { it.id == id }, excludedNodeIDs = previous.excludedNodeIDs - id))
    }

    override suspend fun setNodesExcluded(nodeIds: List<String>, excluded: Boolean) = edit {
        val previous = snapshot()
        val ids = previous.nodes.mapTo(hashSetOf()) { it.id }.intersect(nodeIds.toSet())
        val result = if (excluded) (previous.excludedNodeIDs + ids).distinct() else previous.excludedNodeIDs - ids
        if (result != previous.excludedNodeIDs) commit(previous.copy(excludedNodeIDs = result))
    }

    override suspend fun testNodeLatency(nodeIds: List<String>?, mode: NodeLatencyTestMode?): Map<String, NodeLatencyMeasurement> {
        val (nodes, selected, epoch) = edit { Triple(selectNodes(nodeIds), mode ?: snapshot().settings.latencyTestMode, dataEpoch) }
        val tested = latency.probe(nodes, selected)
        return edit {
            if (epoch != dataEpoch) return@edit emptyMap()
            val runtime = currentState.value.runtime.toMutableMap()
            val valid = tested.filterKeys { it in runtime }
            valid.forEach { (id, value) -> runtime[id]?.let { old ->
                if ((old.latency?.testedAtEpochMs ?: 0L) <= value.testedAtEpochMs) runtime[id] = old.copy(latency = value)
            } }
            currentState.update { it.copy(runtime = runtime) }
            valid
        }
    }

    override suspend fun resolveNodeGeography(nodeIds: List<String>?): Map<String, NodeRuntimeInfo> {
        val (nodes, epoch) = edit { selectNodes(nodeIds) to dataEpoch }
        val resolved = geography.resolve(nodes)
        return edit {
            if (epoch != dataEpoch) return@edit emptyMap()
            val selected = nodes.associateBy { it.id }
            val byId = snapshot().nodes.associateBy { it.id }
            val runtime = currentState.value.runtime.toMutableMap()
            val accepted = resolved.filterKeys { selected[it] == byId[it] }.mapValues { (id, geo) -> geo.copy(latency = runtime[id]?.latency) }
            if (accepted.any { (id, value) -> runtime[id] != value }) {
                runtime.putAll(accepted)
                revision++
                cache.clear()
                val stats = currentState.value.stats.copy(countryCount = activeNodes(snapshot()).asSequence()
                    .filterNot(SubscriptionParser::isMetadata).mapNotNull { runtime[it.id]?.countryCode }.toSet().size)
                currentState.update { it.copy(runtime = runtime, stats = stats) }
            }
            accepted
        }
    }

    private fun selectNodes(nodeIds: List<String>?): List<ProxyNode> {
        val ids = nodeIds?.toSet()
        return (if (ids == null) activeNodes(snapshot()) else snapshot().nodes.filter { it.id in ids })
            .filterNot(SubscriptionParser::isMetadata)
    }

    override fun getNodeSharePayload(nodeId: String): NodeSharePayload? {
        val node = state.value.snapshot.nodes.firstOrNull { it.id == nodeId } ?: return null
        val uri = NodeExport.uri(node) ?: return null
        return NodeSharePayload(uri, generateQrMatrix(uri))
    }

    override suspend fun updateNodeExportFilter(filter: NodeExportNameFilter?) = edit {
        filter?.takeIf { it.useRegex }?.let {
            (it.includeKeywords + it.excludeKeywords).filter(String::isNotBlank).forEach(::Regex)
        }
        commit(snapshot().copy(nodeExportNameFilter = filter))
    }

    override suspend fun selectRuleScheme(schemeId: String) = edit {
        requireScheme(schemeId)
        commit(snapshot().copy(selectedPresetID = schemeId))
    }

    override suspend fun importRuleScheme(name: String?, url: String?, content: String?): RuleScheme {
        val epoch = edit { dataEpoch }
        val imported = schemes.importScheme(name, url, content)
        return edit {
            check(epoch == dataEpoch) { "数据已变更，请重新导入方案" }
            val previous = snapshot()
            val existing = imported.sourceURLString?.let { link -> previous.importedSchemes.firstOrNull { it.sourceURLString == link } }
            val selected = if (existing == null) imported else imported.copy(id = existing.id,
                name = if (name.isNullOrBlank()) existing.name else imported.name,
                summary = if (existing.summaryIsUserEdited == true) existing.summary else imported.summary,
                summaryIsUserEdited = existing.summaryIsUserEdited)
            val all = if (existing == null) previous.importedSchemes + selected
                else previous.importedSchemes.map { if (it.id == existing.id) selected else it }
            commit(previous.copy(importedSchemes = all, selectedPresetID = selected.id))
            selected
        }
    }

    override suspend fun deleteRuleScheme(schemeId: String) = edit {
        val previous = snapshot()
        if (previous.importedSchemes.none { it.id == schemeId }) return@edit
        commit(previous.copy(importedSchemes = previous.importedSchemes.filterNot { it.id == schemeId },
            selectedPresetID = if (previous.selectedPresetID == schemeId) AppSnapshot().selectedPresetID else previous.selectedPresetID,
            ruleSchemeCustomizations = previous.ruleSchemeCustomizations - schemeId, selectedRuleGroups = previous.selectedRuleGroups - schemeId,
            ruleGroupEmojisEnabled = previous.ruleGroupEmojisEnabled - schemeId, ruleGroupEmojiAutofill = previous.ruleGroupEmojiAutofill - schemeId,
            customRuleFlows = previous.customRuleFlows.filterNot { it.schemeID == schemeId }),
            nextReplays = matchingReplays.filterNot { it.schemeId == schemeId })
    }

    override suspend fun refreshRuleScheme(schemeId: String): RuleScheme {
        val (old, epoch) = edit { requireScheme(schemeId) to dataEpoch }
        val url = requireNotNull(old.sourceURLString) { "此方案没有远程链接" }
        require(!old.isBundled) { "内置方案使用离线快照" }
        return withHost(webURL(url, true).host) {
            val refreshed = schemes.importScheme(old.name, url, null, old.id)
            edit {
                val current = requireScheme(schemeId)
                check(epoch == dataEpoch && current.sourceURLString == url) { "方案来源已变更，请重新刷新" }
                val updated = refreshed.copy(name = current.name, summary = current.summary,
                    summaryIsUserEdited = current.summaryIsUserEdited)
                val previous = snapshot()
                commit(previous.copy(importedSchemes = previous.importedSchemes.map { if (it.id == schemeId) updated else it }))
                updated
            }
        }
    }

    override suspend fun setEnabledRuleGroups(schemeId: String, enabledGroupNames: List<String>?) = edit {
        requireScheme(schemeId)
        val previous = snapshot()
        val enabled = if (enabledGroupNames == null) previous.selectedRuleGroups - schemeId
            else previous.selectedRuleGroups + (schemeId to enabledGroupNames.distinct())
        commit(previous.copy(selectedRuleGroups = enabled))
    }

    override suspend fun setSchemeEmojiSettings(schemeId: String, emojisEnabled: Boolean?, emojiAutofill: Boolean?) = edit {
        requireScheme(schemeId)
        val previous = snapshot()
        commit(previous.copy(ruleGroupEmojisEnabled = emojisEnabled?.let { previous.ruleGroupEmojisEnabled + (schemeId to it) } ?: previous.ruleGroupEmojisEnabled,
            ruleGroupEmojiAutofill = emojiAutofill?.let { previous.ruleGroupEmojiAutofill + (schemeId to it) } ?: previous.ruleGroupEmojiAutofill))
    }

    override suspend fun reorderRuleGroups(schemeId: String, fromIndex: Int, toIndex: Int, orderedGroupNames: List<String>): ReorderGroupsResult = edit {
        val previous = snapshot()
        val scheme = requireScheme(schemeId)
        val oldCustomization = previous.ruleSchemeCustomizations[schemeId] ?: RuleSchemeCustomization()
        val nodes = activeNodes(previous).filterNot(SubscriptionParser::isMetadata)
        val display = schemes.presentation(previous, scheme, nodes).map { it.sourceName }
        require(fromIndex in display.indices && toIndex in display.indices) { "无效的策略组位置" }
        val moved = display[fromIndex]
        val expected = display.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        require(orderedGroupNames == expected || orderedGroupNames == display) { "策略组已变更，请重新排序" }
        val ordered = if (orderedGroupNames == display) expected else orderedGroupNames
        val before = schemes.effective(previous, scheme, rename = false).rulesets
        val matching = schemes.matchingGroups(before)
        val desired = schemes.matchingOrderAfterDrag(moved, display, ordered, matching)
        val customization = oldCustomization.copy(groupOrder = ordered,
            matchingOrder = if (desired == matching) oldCustomization.matchingOrder else desired)
        val next = previous.copy(ruleSchemeCustomizations = previous.ruleSchemeCustomizations + (schemeId to customization))
        val movedIndex = desired.indexOf(moved)
        val move = if (desired == matching) null else {
            val down = toIndex > fromIndex
            RuleMatchingMove(moved, desired[movedIndex + if (down) -1 else 1], down,
                desired.getOrNull(movedIndex + 1).takeIf { down })
        }
        val after = if (move == null) before else schemes.moveRuleBlock(before, move)
        val conflicts = schemes.conflicts(before, after, moved)
        if (customization != oldCustomization) {
            val previousReplay = schemes.matchingReplay(schemeId, oldCustomization)
            val nextReplays = if (move == null && previousReplay == null) matchingReplays else {
                val replay = RuleMatchingReplay(schemeId, customization.groupOrder, customization.matchingOrder,
                    previousReplay?.baseGroupOrder ?: oldCustomization.groupOrder,
                    previousReplay?.baseMatchingOrder ?: oldCustomization.matchingOrder,
                    previousReplay?.moves.orEmpty() + listOfNotNull(move))
                val all = matchingReplays.filterNot { it.matches(schemeId, customization) } + replay
                val (active, previous) = all.partition { entry ->
                    next.ruleSchemeCustomizations[entry.schemeId]?.let { entry.matches(entry.schemeId, it) } == true
                }
                previous.takeLast(64) + active
            }
            commit(next, nextReplays = nextReplays)
        }
        ReorderGroupsResult(schemes.presentation(next, scheme, nodes), customization, oldCustomization, conflicts)
    }

    override suspend fun updateRuleSchemeCustomization(schemeId: String, customization: RuleSchemeCustomization) = edit {
        requireScheme(schemeId)
        val previous = snapshot()
        commit(previous.copy(ruleSchemeCustomizations = previous.ruleSchemeCustomizations + (schemeId to customization)))
    }

    override suspend fun createRuleGroup(schemeId: String, name: String, kind: RuleSchemeGroupKind,
                                         members: List<RuleSchemeGroupMember>, isNodeFilterGroup: Boolean) = edit {
        val scheme = requireScheme(schemeId)
        val previous = snapshot()
        val text = validGroupName(name)
        val presentation = schemes.presentation(previous, scheme, emptyList())
        require(scheme.groups.none { it.name.equals(text, true) } &&
            presentation.none { it.sourceName.equals(text, true) || it.displayName.equals(text, true) }) { "策略组名称已存在" }
        validateMembers(text, members, isNodeFilterGroup)
        val current = previous.ruleSchemeCustomizations[schemeId] ?: RuleSchemeCustomization()
        val group = RuleSchemeGroup(text, kind, members.distinct(), isCustomNodeFilter = isNodeFilterGroup)
        val selection = previous.selectedRuleGroups[schemeId]?.let { previous.selectedRuleGroups + (schemeId to (it + text).distinct()) }
            ?: previous.selectedRuleGroups
        commit(previous.copy(ruleSchemeCustomizations = previous.ruleSchemeCustomizations +
            (schemeId to current.copy(addedNodeGroups = current.addedNodeGroups + group, removedGroupNames = current.removedGroupNames - text)),
            selectedRuleGroups = selection))
    }

    override suspend fun updateRuleGroup(schemeId: String, sourceName: String, newName: String?, kind: RuleSchemeGroupKind?,
                                         members: List<RuleSchemeGroupMember>?, supplementRules: List<String>?) = edit {
        val scheme = requireScheme(schemeId)
        val previous = snapshot()
        val existing = schemes.presentation(previous, scheme, emptyList())
        val original = existing.firstOrNull { it.sourceName == sourceName } ?: throw NoSuchElementException("策略组不存在")
        val current = previous.ruleSchemeCustomizations[schemeId] ?: RuleSchemeCustomization()
        val name = newName?.let(::validGroupName)
        require(name == null || existing.none { it.sourceName != sourceName &&
            (it.sourceName.equals(name, true) || it.displayName.equals(name, true)) }) { "策略组名称已存在" }
        if (members != null) {
            validateMembers(sourceName, members, original.editorMode == "nodePatternsOnly")
            validateGroupCycles(existing, sourceName, members)
        }
        val renamed = if (name == null) current.renamedGroups else if (name == sourceName) current.renamedGroups - sourceName
            else current.renamedGroups + (sourceName to name)
        val updated = current.copy(renamedGroups = renamed,
            groupKindOverrides = kind?.let { current.groupKindOverrides + (sourceName to it) } ?: current.groupKindOverrides,
            groupMemberOverrides = members?.let { current.groupMemberOverrides + (sourceName to it.distinct()) } ?: current.groupMemberOverrides)
        val flows = if (supplementRules == null) previous.customRuleFlows else {
            val text = supplementRules.joinToString("\n")
            val normalized = parseSupplement(text)
            val others = previous.customRuleFlows.filterNot { it.schemeID == schemeId && it.policyName == sourceName && it.isGroupSupplement == true }
            if (normalized.isEmpty()) others else others + CustomRuleFlow(UUID.randomUUID().toString(), schemeId, sourceName, sourceName,
                normalized, isGroupSupplement = true)
        }
        commit(previous.copy(ruleSchemeCustomizations = previous.ruleSchemeCustomizations + (schemeId to updated), customRuleFlows = flows))
    }

    override suspend fun deleteRuleGroup(schemeId: String, sourceName: String) = edit {
        val source = requireScheme(schemeId)
        require(sourceName !in schemes.protectedNames(source)) { "此策略组受保护" }
        val previous = snapshot()
        val custom = previous.ruleSchemeCustomizations[schemeId] ?: RuleSchemeCustomization()
        val updated = custom.copy(removedGroupNames = (custom.removedGroupNames + sourceName).distinct(),
            addedNodeGroups = custom.addedNodeGroups.filterNot { it.name == sourceName },
            groupOrder = custom.groupOrder - sourceName, matchingOrder = custom.matchingOrder - sourceName,
            renamedGroups = custom.renamedGroups - sourceName, groupKindOverrides = custom.groupKindOverrides - sourceName,
            groupMemberOverrides = custom.groupMemberOverrides - sourceName)
        val selection = previous.selectedRuleGroups[schemeId]?.let { previous.selectedRuleGroups + (schemeId to (it - sourceName)) }
            ?: previous.selectedRuleGroups
        commit(previous.copy(ruleSchemeCustomizations = previous.ruleSchemeCustomizations + (schemeId to updated),
            selectedRuleGroups = selection, customRuleFlows = previous.customRuleFlows.filterNot {
                it.schemeID == schemeId && (it.policyName == sourceName || it.generatedPolicyGroup?.name == sourceName)
            }))
    }

    private fun requireScheme(id: String) = schemes.find(snapshot(), id) ?: throw NoSuchElementException("规则方案不存在")

    private fun validGroupName(name: String): String = cleanLine(name).take(256).also {
        require(it.isNotEmpty() && !isBuiltinPolicy(it)) { "无效的策略组名称" }
    }

    private fun validateMembers(name: String, members: List<RuleSchemeGroupMember>, patternOnly: Boolean) {
        members.forEach {
            when (it) {
                is RuleSchemeGroupMember.Reference -> require(!patternOnly && it.name.isNotBlank() && it.name != name) { "无效的策略组成员" }
                is RuleSchemeGroupMember.NodePattern -> { require(it.pattern.isNotBlank()) { "请输入节点名称规则" }; Regex(it.pattern) }
            }
        }
    }

    private fun validateGroupCycles(groups: List<RuleGroupPresentation>, owner: String, members: List<RuleSchemeGroupMember>) {
        val lookup = groups.associate { it.sourceName to it.members } + (owner to members)
        val visited = hashSetOf<String>()
        fun reachesOwner(start: String): Boolean {
            if (start == owner) return true
            if (!visited.add(start)) return false
            return lookup[start].orEmpty().filterIsInstance<RuleSchemeGroupMember.Reference>().any { reachesOwner(it.name) }
        }
        require(members.filterIsInstance<RuleSchemeGroupMember.Reference>().none { reachesOwner(it.name) }) { "策略组成员形成循环引用" }
    }

    private fun parseSupplement(text: String): String {
        val types = setOf("DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-WILDCARD", "IP-CIDR", "IP-CIDR6")
        val lines = ruleLines(text).mapNotNull(::splitRule)
        require(text.isBlank() || lines.isNotEmpty()) { "未找到补充分流规则" }
        require(lines.all { it.type in types && it.options.isEmpty() }) { "不支持的补充分流规则" }
        return lines.map { it.line }.distinct().joinToString("\n")
    }

    override fun searchRuleCatalog(query: String, source: String, limit: Int) = schemes.searchCatalog(query, source, limit)

    override suspend fun addCustomRuleFlow(schemeId: String, name: String, policyName: String, rulesText: String, catalogId: String?,
                                           localRuleSetId: String?, sourceURLString: String?, isGroupSupplement: Boolean): CustomRuleFlow {
        val epoch = edit { requireScheme(schemeId); dataEpoch }
        val catalogUrl = catalogId?.let { requireNotNull(schemes.catalogEntry(it)) { "规则库条目不存在" }.url }
        val url = (sourceURLString ?: catalogUrl)?.takeIf(String::isNotBlank)?.let { webURL(it, true).toString() }
        val text = when {
            isGroupSupplement -> parseSupplement(rulesText)
            rulesText.isNotBlank() || url == null || localRuleSetId != null -> rulesText
            url.substringBefore('?').endsWith(".srs", true) || url.substringBefore('?').endsWith(".mrs", true) -> ""
            else -> fetcher.fetchRuleText(url).also { require(ruleLines(it).isNotEmpty()) { "远程规则集没有可用规则" } }
        }
        return edit {
            check(epoch == dataEpoch) { "数据已变更，请重新添加规则" }
            val previous = snapshot()
            val scheme = requireScheme(schemeId)
            val policy = cleanLine(policyName)
            val groups = schemes.presentation(previous, scheme, emptyList())
            require(isBuiltinPolicy(policy) || groups.any { it.sourceName == policy }) { "目标策略不存在" }
            require(localRuleSetId == null || previous.localRuleSets.any { it.id == localRuleSetId }) { "本地规则集不存在" }
            require(text.isNotBlank() || url != null || localRuleSetId != null) { "请输入分流规则" }
            val flow = CustomRuleFlow(UUID.randomUUID().toString(), schemeId, cleanLine(name).ifEmpty { policy }, policy, text,
                localRuleSetID = localRuleSetId, catalogID = catalogId, sourceURLString = url, isGroupSupplement = isGroupSupplement)
            commit(previous.copy(customRuleFlows = previous.customRuleFlows + flow))
            flow
        }
    }

    override suspend fun updateCustomRuleFlow(id: String, name: String?, policyName: String?, rulesText: String?, isEnabled: Boolean?) = edit {
        val previous = snapshot()
        val old = previous.customRuleFlows.firstOrNull { it.id == id } ?: throw NoSuchElementException("自定义规则不存在")
        val policy = policyName?.let(::cleanLine) ?: old.policyName
        val scheme = requireScheme(old.schemeID)
        require(isBuiltinPolicy(policy) || schemes.presentation(previous, scheme, emptyList()).any { it.sourceName == policy }) { "目标策略不存在" }
        val text = rulesText?.let { if (old.isGroupSupplement == true) parseSupplement(it) else it } ?: old.rulesText
        val flow = old.copy(name = name?.let(::cleanLine)?.takeIf(String::isNotEmpty) ?: old.name,
            policyName = policy, rulesText = text, isEnabled = isEnabled ?: old.isEnabled)
        commit(previous.copy(customRuleFlows = previous.customRuleFlows.map { if (it.id == id) flow else it }))
    }

    override suspend fun deleteCustomRuleFlow(id: String) = edit {
        val previous = snapshot()
        if (previous.customRuleFlows.any { it.id == id }) commit(previous.copy(customRuleFlows = previous.customRuleFlows.filterNot { it.id == id }))
    }

    override suspend fun createLocalRuleSet(name: String, rulesText: String, sourceURLString: String?): LocalRuleSet {
        val source = sourceURLString?.takeIf(String::isNotBlank)?.let { webURL(it, true).toString() }
        val text = if (rulesText.isBlank() && source != null) fetcher.fetchRuleText(source) else rulesText
        require(ruleLines(text).isNotEmpty()) { "未找到分流规则" }
        return edit {
            val previous = snapshot()
            val result = LocalRuleSet(UUID.randomUUID().toString(), cleanLine(name).ifEmpty { "我的规则集" }, text, source)
            commit(previous.copy(localRuleSets = previous.localRuleSets + result))
            result
        }
    }

    override suspend fun updateLocalRuleSet(id: String, name: String?, rulesText: String?) = edit {
        val previous = snapshot()
        val old = previous.localRuleSets.firstOrNull { it.id == id } ?: throw NoSuchElementException("本地规则集不存在")
        if (rulesText != null) require(ruleLines(rulesText).isNotEmpty()) { "未找到分流规则" }
        val next = old.copy(name = name?.let(::cleanLine)?.takeIf(String::isNotEmpty) ?: old.name, rulesText = rulesText ?: old.rulesText)
        commit(previous.copy(localRuleSets = previous.localRuleSets.map { if (it.id == id) next else it }))
    }

    override suspend fun deleteLocalRuleSet(id: String) = edit {
        val previous = snapshot()
        if (previous.localRuleSets.none { it.id == id }) return@edit
        commit(previous.copy(localRuleSets = previous.localRuleSets.filterNot { it.id == id },
            customRuleFlows = previous.customRuleFlows.filterNot { it.localRuleSetID == id }))
    }

    override suspend fun generateConfiguration(target: ClientTarget, mode: ExportContentMode, maskSecrets: Boolean): GeneratedConfiguration {
        val (current, key) = edit {
            currentState.value to ConfigurationCache.Key(revision, target, mode)
        }
        if (maskSecrets) cache.get(key)?.let { return it }
        val generated = withContext(Dispatchers.Default) { generator.generate(current.snapshot, target, mode, maskSecrets, current.runtime) }
        if (maskSecrets) edit { if (revision == key.revision) cache.put(key, generated) }
        return generated
    }

    override suspend fun prepareDirectImport(target: ClientTarget, mode: ExportContentMode): DirectImportHandle = edit {
        val current = currentState.value
        val generated = generator.generate(current.snapshot, target, mode, false, current.runtime)
        require(generated.hasExportableProxies || generated.contentMode == ExportContentMode.FULL_CONFIGURATION) { "没有可导入的节点" }
        synchronized(lifecycle) {
            check(!disposed.get())
            direct.issue(generated, subscriptionHeaders(current.snapshot))
        }
    }

    override suspend fun updateExportPreferences(selectedTarget: ClientTarget?, clientOrder: List<ClientTarget>?,
                                                  visibleClientTargets: List<ClientTarget>?, exportContentModes: Map<String, ExportContentMode>?,
                                                  excludedKinds: Map<String, List<ProxyKind>>?, isLANSharingVisible: Boolean?) = edit {
        val previous = snapshot()
        commit(previous.copy(selectedTarget = selectedTarget ?: previous.selectedTarget,
            clientOrder = clientOrder?.let { (it + ClientCatalog.DEFAULT_CLIENT_ORDER).distinct() } ?: previous.clientOrder,
            visibleClientTargets = visibleClientTargets?.distinct() ?: previous.visibleClientTargets,
            exportContentModes = exportContentModes ?: previous.exportContentModes, excludedKinds = excludedKinds ?: previous.excludedKinds,
            isLANSharingVisible = isLANSharingVisible ?: previous.isLANSharingVisible))
    }

    override suspend fun toggleLanSharing(enabled: Boolean): LanSharingStatus = edit {
        synchronized(lifecycle) {
            check(!disposed.get())
            val current = currentState.value.lanStatus
            if (current.enabled == enabled) return@synchronized current
            if (!enabled) {
                stopLan()
                return@synchronized currentState.value.lanStatus
            }
            val port = lan.start()
            try {
                val status = lanStatus(port)
                lanHooks.onLanSharingStarted(status.primaryURL, port)
                currentState.value = currentState.value.copy(lanStatus = status)
                status
            } catch (e: Exception) {
                lan.close()
                throw e
            }
        }
    }

    override suspend fun rotateLanToken(): LanSharingStatus = edit {
        synchronized(lifecycle) {
            val current = currentState.value.lanStatus
            lan.rotateToken()
            if (!current.enabled) return@synchronized current
            val status = lanStatus(current.port)
            currentState.value = currentState.value.copy(lanStatus = status)
            lanHooks.onLanSharingStarted(status.primaryURL, status.port)
            status
        }
    }

    private fun lanStatus(port: Int): LanSharingStatus {
        val token = lan.token
        val addresses = lanHooks.getLocalLanAddresses().distinct().filter { literalIP(it)?.size == 4 && !it.startsWith("127.") }
        val urls = addresses.map { "http://$it:$port/sub/$token" }
        return LanSharingStatus(true, token, port, urls, urls.firstOrNull().orEmpty())
    }

    private fun stopLan() {
        lan.close()
        val previous = currentState.value
        currentState.value = previous.copy(lanStatus = LanSharingStatus())
        if (previous.lanStatus.enabled) lanHooks.onLanSharingStopped()
    }

    override fun buildLanSubscriptionUrl(format: LanSubscriptionFormat, contentMode: ExportContentMode, baseUrl: String?): String {
        val status = state.value.lanStatus
        if (!status.enabled) return ""
        val base = baseUrl?.takeIf(String::isNotBlank) ?: status.primaryURL.takeIf(String::isNotBlank) ?: return ""
        val parsed = runCatching { webURL(base) }.getOrNull() ?: return ""
        return parsed.newBuilder().encodedPath("/sub/${status.token}").query(null).fragment(null).apply {
            if (format != LanSubscriptionFormat.AUTO) addQueryParameter("target", format.rawValue)
            if (contentMode != ExportContentMode.FULL_CONFIGURATION) addQueryParameter("content", contentMode.rawValue)
        }.build().toString()
    }

    override fun generateQrMatrix(content: String) = QrCodeGenerator.generate(content)

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = edit {
        val previous = snapshot()
        val settings = transform(previous.settings)
        if (settings != previous.settings) commit(previous.copy(settings = settings))
    }

    override suspend fun completeOnboarding() = updateSettings { it.copy(hasCompletedOnboarding = true) }

    private fun deriveTailnet(value: TailnetConnection): TailnetConnection = TailnetConfigurationWriter.normalize(value)
        .copy(hasAuthKey = secrets.hasAuthKey(value.id))

    private fun normalizedAuthKey(key: String): String = key.trim().also {
        require(it.matches(Regex("tskey-auth-[A-Za-z0-9_-]+"))) { "无效的 Tailscale Auth Key" }
    }

    override suspend fun createTailnet(name: String, controlURLString: String?, deviceName: String?, subnets: List<String>,
                                        magicDNSSuffix: String?, authKey: String?, isEnabled: Boolean): TailnetConnection = edit {
        val key = authKey?.takeIf(String::isNotBlank)?.let(::normalizedAuthKey)
        val id = UUID.randomUUID().toString()
        val entry = TailnetConfigurationWriter.normalize(TailnetConnection(id, name, controlURLString, deviceName, subnets,
            magicDNSSuffix, isEnabled, key != null))
        if (key != null) secrets.setAuthKey(id, key)
        try {
            val previous = snapshot()
            commit(previous.copy(tailnets = previous.tailnets + entry))
        } catch (e: Exception) {
            if (key != null) restoreSecret(id, null, e)
            throw e
        }
        entry
    }

    override suspend fun updateTailnet(id: String, name: String?, controlURLString: String?, deviceName: String?, subnets: List<String>?,
                                        magicDNSSuffix: String?, isEnabled: Boolean?, authKey: String?, clearAuthKey: Boolean) = edit {
        val previous = snapshot()
        val old = previous.tailnets.firstOrNull { it.id == id } ?: throw NoSuchElementException("内网连接不存在")
        val priorKey = secrets.getAuthKey(id)
        val key = if (clearAuthKey) null else authKey?.takeIf(String::isNotBlank)?.let(::normalizedAuthKey) ?: priorKey
        val updated = TailnetConfigurationWriter.normalize(old.copy(name = name ?: old.name,
            controlURLString = controlURLString ?: old.controlURLString, deviceName = deviceName ?: old.deviceName,
            subnets = subnets ?: old.subnets, magicDNSSuffix = magicDNSSuffix ?: old.magicDNSSuffix,
            isEnabled = isEnabled ?: old.isEnabled, hasAuthKey = key != null))
        if (key != priorKey) changeSecret(id, key)
        try {
            commit(previous.copy(tailnets = previous.tailnets.map { if (it.id == id) updated else it }))
        } catch (e: Exception) {
            if (key != priorKey) restoreSecret(id, priorKey, e)
            throw e
        }
    }

    override suspend fun deleteTailnet(id: String) = edit {
        val previous = snapshot()
        if (previous.tailnets.none { it.id == id }) return@edit
        val auth = secrets.getAuthKey(id)
        secrets.deleteAuthKey(id)
        try {
            commit(previous.copy(tailnets = previous.tailnets.filterNot { it.id == id }))
        } catch (e: Exception) {
            restoreSecret(id, auth, e)
            throw e
        }
    }

    private fun changeSecret(id: String, key: String?) {
        if (key == null) secrets.deleteAuthKey(id) else secrets.setAuthKey(id, key)
    }

    private fun restoreSecret(id: String, key: String?, original: Exception) {
        try { changeSecret(id, key) } catch (error: Exception) { original.addSuppressed(error) }
    }

    override suspend fun exportBackupJson(): String = edit { PersistenceStore.encodeDocument(snapshot(), matchingReplays) }

    override suspend fun importBackupJson(jsonText: String) {
        val document = withContext(Dispatchers.Default) { PersistenceStore.decodeDocument(jsonText) }
        val imported = document.snapshot
        edit {
            val previous = snapshot()
            val retained = imported.tailnets.mapTo(hashSetOf()) { it.id }
            val removed = previous.tailnets.filter { it.id !in retained }.associate { it.id to secrets.getAuthKey(it.id) }
            val hydrated = imported.copy(tailnets = imported.tailnets.map(::deriveTailnet))
            try {
                removed.keys.forEach(secrets::deleteAuthKey)
                commit(hydrated, rebuildRuntime(hydrated.nodes, emptyMap()), preserveTimestamp = true, nextReplays = document.replays)
            } catch (e: Exception) {
                removed.forEach { (id, key) -> restoreSecret(id, key, e) }
                throw e
            }
            dataEpoch++
            synchronized(lifecycle) { direct.clear(); stopLan() }
        }
    }

    override suspend fun resetAllData() = edit {
        val previous = snapshot()
        val stored = previous.tailnets.associate { it.id to secrets.getAuthKey(it.id) }
        try {
            secrets.clearAll()
            commit(AppSnapshot(), emptyMap(), nextReplays = emptyList())
        } catch (e: Exception) {
            stored.forEach { (id, key) -> restoreSecret(id, key, e) }
            throw e
        }
        dataEpoch++
        synchronized(lifecycle) { direct.clear(); stopLan() }
    }

    override fun shutdown() {
        if (!disposed.compareAndSet(false, true)) return
        synchronized(lifecycle) { direct.close(); stopLan() }
        cache.clear()
    }

    companion object {
        private fun fallbackName(host: String) = host.split('.').firstOrNull { !it.equals("www", true) } ?: host

        internal fun canonicalKey(node: ProxyNode, relaxed: Boolean = false) = node.copy(
            id = "", sourceID = null, name = if (relaxed) "" else node.name, server = node.server.lowercase(Locale.ROOT),
            rawURI = "", isSubscriptionMetadata = null, countryOverride = null,
        )

        internal data class CarriedNodes(val nodes: List<ProxyNode>, val excludedNodeIDs: Set<String>, val matches: Map<String, String>)

        internal fun carriedOverExclusions(previous: List<ProxyNode>, previouslyExcludedIDs: Set<String>,
                                           refreshed: List<ProxyNode>): CarriedNodes {
            val exact = previous.groupBy { canonicalKey(it) }
            val relaxed = previous.groupBy { canonicalKey(it, true) }
            val refreshedCounts = refreshed.groupingBy { canonicalKey(it, true) }.eachCount()
            val excluded = hashSetOf<String>()
            val matches = linkedMapOf<String, String>()
            val nodes = refreshed.map { node ->
                val accurate = exact[canonicalKey(node)]
                val stable = canonicalKey(node, true)
                val candidates = accurate ?: relaxed[stable]?.takeIf { it.size == 1 && refreshedCounts[stable] == 1 }
                if (candidates.isNullOrEmpty()) node else {
                    if (candidates.any { it.id in previouslyExcludedIDs }) excluded.add(node.id)
                    val old = candidates.first()
                    matches[node.id] = old.id
                    node.copy(countryOverride = candidates.firstNotNullOfOrNull { it.countryOverride } ?: node.countryOverride)
                }
            }
            return CarriedNodes(nodes, excluded, matches)
        }
    }
}
