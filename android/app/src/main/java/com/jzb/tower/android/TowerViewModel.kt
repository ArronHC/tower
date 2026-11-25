package com.jzb.tower.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jzb.tower.android.ui.components.ToastMessage
import com.jzb.tower.android.ui.components.ToastTone
import com.jzb.tower.core.contract.DirectImportHandle
import com.jzb.tower.core.contract.TowerRepository
import com.jzb.tower.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingConflictNotice(
    val schemeId: String,
    val previousCustomization: RuleSchemeCustomization,
    val conflicts: List<MatchingOrderConflict>,
)

class TowerViewModel(
    private val repository: TowerRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = repository.state

    private val _selectedTab = MutableStateFlow(AppTab.SUBSCRIPTIONS)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _showSettingsSheet = MutableStateFlow(false)
    val showSettingsSheet: StateFlow<Boolean> = _showSettingsSheet.asStateFlow()

    private val _isLanSharingSelected = MutableStateFlow(false)
    val isLanSharingSelected: StateFlow<Boolean> = _isLanSharingSelected.asStateFlow()

    private val _worldMap = MutableStateFlow<WorldDotMapData?>(null)
    val worldMap: StateFlow<WorldDotMapData?> = _worldMap.asStateFlow()

    private val _countries = MutableStateFlow<List<CountryEntry>>(emptyList())
    val countries: StateFlow<List<CountryEntry>> = _countries.asStateFlow()

    private val _generatedConfig = MutableStateFlow<GeneratedConfiguration?>(null)
    val generatedConfig: StateFlow<GeneratedConfiguration?> = _generatedConfig.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshingSourceIds = MutableStateFlow<Set<String>>(emptySet())
    val refreshingSourceIds: StateFlow<Set<String>> = _refreshingSourceIds.asStateFlow()

    private val _isTestingLatency = MutableStateFlow(false)
    val isTestingLatency: StateFlow<Boolean> = _isTestingLatency.asStateFlow()

    private val _testingNodeIds = MutableStateFlow<Set<String>>(emptySet())
    val testingNodeIds: StateFlow<Set<String>> = _testingNodeIds.asStateFlow()

    private val _latencyTestProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val latencyTestProgress: StateFlow<Pair<Int, Int>?> = _latencyTestProgress.asStateFlow()

    private val _latencyBatchCompletedTick = MutableStateFlow(0)
    val latencyBatchCompletedTick: StateFlow<Int> = _latencyBatchCompletedTick.asStateFlow()

    private val _toastMessage = MutableStateFlow<ToastMessage?>(null)
    val toastMessage: StateFlow<ToastMessage?> = _toastMessage.asStateFlow()

    private val _pendingConflicts = MutableStateFlow<PendingConflictNotice?>(null)
    val pendingConflicts: StateFlow<PendingConflictNotice?> = _pendingConflicts.asStateFlow()

    private val _incomingSharedText = MutableStateFlow<String?>(null)
    val incomingSharedText: StateFlow<String?> = _incomingSharedText.asStateFlow()

    private var toastDismissJob: Job? = null
    private var generateJob: Job? = null
    private var refreshAllJob: Job? = null
    private var latencyTestJob: Job? = null
    private var geographyJob: Job? = null
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            runCatching {
                repository.initialize()
                _countries.value = repository.getCountries()
                _worldMap.value = repository.getWorldDotMap()
                resolveNodeGeographyInBackground()
                if (repository.state.value.snapshot.settings.autoRefreshOnOpen &&
                    repository.state.value.snapshot.subscriptions.any { it.isEnabled }
                ) {
                    refreshAllSubscriptions(silent = true)
                }
                refreshGeneratedConfig()
            }.onFailure { error ->
                showToast("初始化失败：${error.message ?: "未知错误"}", ToastTone.ERROR)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        if (tab == AppTab.SETTINGS) {
            _showSettingsSheet.value = true
            return
        }
        _selectedTab.value = tab
        if (tab == AppTab.EXPORT && !_isLanSharingSelected.value) {
            refreshGeneratedConfig()
        }
    }

    fun openSettings() {
        _showSettingsSheet.value = true
    }

    fun closeSettings() {
        _showSettingsSheet.value = false
    }

    fun selectLanSharing(selected: Boolean) {
        _isLanSharingSelected.value = selected
        if (!selected) {
            refreshGeneratedConfig()
        }
    }

    fun setIncomingSharedText(text: String?) {
        _incomingSharedText.value = text?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun consumeIncomingSharedText() {
        _incomingSharedText.value = null
    }

    fun showToast(text: String, tone: ToastTone = ToastTone.NEUTRAL) {
        toastDismissJob?.cancel()
        val msg = ToastMessage(text = text, tone = tone)
        _toastMessage.value = msg
        toastDismissJob = viewModelScope.launch {
            delay(3000)
            if (_toastMessage.value?.id == msg.id) {
                _toastMessage.value = null
            }
        }
    }

    fun dismissToast() {
        toastDismissJob?.cancel()
        _toastMessage.value = null
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            runCatching { repository.completeOnboarding() }
        }
    }

    fun replayOnboarding() {
        _showSettingsSheet.value = false
        viewModelScope.launch {
            runCatching {
                repository.updateSettings { it.copy(hasCompletedOnboarding = false) }
            }
        }
    }

    private fun resolveNodeGeographyInBackground() {
        geographyJob?.cancel()
        geographyJob = viewModelScope.launch {
            runCatching { repository.resolveNodeGeography() }
        }
    }

    // --- Subscriptions & Nodes ---

    fun detectSourceInput(input: String): SourceDetectionResult =
        repository.detectSourceInput(input)

    suspend fun addSubscription(
        urlString: String,
        name: String?,
        requestOptions: SubscriptionRequestOptions?,
    ): Result<SubscriptionMutationResult> = runCatchingExceptCancellation {
        val result = repository.addSubscription(urlString, name, requestOptions)
        showToast("已添加「${result.subscription.name}」（${result.addedNodeCount} 个节点）", ToastTone.SUCCESS)
        resolveNodeGeographyInBackground()
        refreshGeneratedConfig()
        result
    }

    suspend fun importNodesFromText(content: String): Result<NodeImportResult> = runCatchingExceptCancellation {
        val result = repository.importNodesFromText(content)
        val skippedSuffix = if (result.skippedCount > 0) "，跳过 ${result.skippedCount} 个" else ""
        showToast("已导入 ${result.addedNodes.size} 个自有节点$skippedSuffix", ToastTone.SUCCESS)
        resolveNodeGeographyInBackground()
        refreshGeneratedConfig()
        result
    }

    suspend fun updateSubscription(
        id: String,
        name: String? = null,
        urlString: String? = null,
        isEnabled: Boolean? = null,
        requestOptions: SubscriptionRequestOptions? = null,
    ): Result<Unit> = runCatchingExceptCancellation {
        repository.updateSubscription(id, name, urlString, isEnabled, requestOptions)
        refreshGeneratedConfig()
    }

    fun toggleSubscriptionEnabled(source: SubscriptionSource) {
        viewModelScope.launch {
            runCatching {
                repository.updateSubscription(id = source.id, isEnabled = !source.isEnabled)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("更新失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun deleteSubscription(source: SubscriptionSource) {
        viewModelScope.launch {
            runCatching {
                repository.deleteSubscription(source.id)
                showToast("已删除「${source.name}」", ToastTone.NEUTRAL)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("删除失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun refreshSubscription(source: SubscriptionSource) {
        if (_refreshingSourceIds.value.contains(source.id)) return
        viewModelScope.launch {
            _refreshingSourceIds.value = _refreshingSourceIds.value + source.id
            try {
                val res = repository.refreshSubscription(source.id)
                showToast("已更新「${res.subscription.name}」（${res.addedNodeCount} 个节点）", ToastTone.SUCCESS)
                resolveNodeGeographyInBackground()
                refreshGeneratedConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("更新失败：${e.message ?: "请检查网络或链接"}", ToastTone.ERROR)
            } finally {
                _refreshingSourceIds.value = _refreshingSourceIds.value - source.id
            }
        }
    }

    fun refreshAllSubscriptions(silent: Boolean = false) {
        if (_isRefreshing.value) return
        refreshAllJob?.cancel()
        refreshAllJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val results = repository.refreshAllSubscriptions()
                val failures = results.count { it.subscription.lastError != null }
                if (!silent) {
                    if (failures > 0) {
                        showToast("更新完成，${failures} 个订阅失败", ToastTone.WARNING)
                    } else {
                        showToast("已更新全部 ${results.size} 个订阅", ToastTone.SUCCESS)
                    }
                }
                resolveNodeGeographyInBackground()
                refreshGeneratedConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!silent) showToast("刷新失败：${e.message}", ToastTone.ERROR)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun cancelRefreshAll() {
        refreshAllJob?.cancel()
        refreshAllJob = null
        _isRefreshing.value = false
        showToast("已取消刷新订阅", ToastTone.NEUTRAL)
    }

    fun testNodeLatency(nodeIds: List<String>? = null) {
        if (_isTestingLatency.value) return
        val targetIds = nodeIds ?: run {
            val snap = state.value.snapshot
            val enabledSubs = snap.subscriptions.filter { it.isEnabled }.map { it.id }.toSet()
            val excluded = snap.excludedNodeIDs.toSet()
            snap.nodes.filter { (it.sourceID == null || it.sourceID in enabledSubs) && it.id !in excluded }
                .map { it.id }
        }
        if (targetIds.isEmpty()) return

        latencyTestJob?.cancel()
        latencyTestJob = viewModelScope.launch {
            _isTestingLatency.value = true
            _testingNodeIds.value = targetIds.toSet()
            val total = targetIds.size
            var completed = 0
            var reachable = 0
            _latencyTestProgress.value = 0 to total
            val mode = state.value.snapshot.settings.latencyTestMode
            try {
                val chunkSize = if (total > 12) 6 else total
                for (chunk in targetIds.chunked(chunkSize)) {
                    val res = repository.testNodeLatency(chunk, mode)
                    completed += chunk.size
                    reachable += res.values.count { it.milliseconds != null }
                    _testingNodeIds.value = _testingNodeIds.value - chunk.toSet()
                    _latencyTestProgress.value = completed.coerceAtMost(total) to total
                }
                if (total > 1) {
                    _latencyBatchCompletedTick.value = _latencyBatchCompletedTick.value + 1
                }
                showToast("测速完成：$reachable/$total 个节点可达", ToastTone.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("测速失败：${e.message}", ToastTone.ERROR)
            } finally {
                _isTestingLatency.value = false
                _testingNodeIds.value = emptySet()
                _latencyTestProgress.value = null
            }
        }
    }

    fun cancelLatencyTest() {
        latencyTestJob?.cancel()
        latencyTestJob = null
        _isTestingLatency.value = false
        _testingNodeIds.value = emptySet()
        _latencyTestProgress.value = null
    }

    fun toggleNodeExcluded(node: ProxyNode, excluded: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.updateNode(id = node.id, excluded = excluded)
                refreshGeneratedConfig()
            }
        }
    }

    fun updateNodeName(node: ProxyNode, name: String) {
        viewModelScope.launch {
            runCatching {
                repository.updateNode(id = node.id, name = name)
                showToast("已更新节点名称", ToastTone.SUCCESS)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("修改失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun setNodeCountryOverride(node: ProxyNode, countryCode: String?) {
        viewModelScope.launch {
            runCatching {
                if (countryCode == null) {
                    repository.updateNode(id = node.id, clearCountryOverride = true)
                } else {
                    repository.updateNode(id = node.id, countryOverride = countryCode)
                }
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("设置地区失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun deleteNode(node: ProxyNode) {
        viewModelScope.launch {
            runCatching {
                repository.deleteNode(node.id)
                showToast("已删除节点", ToastTone.NEUTRAL)
                refreshGeneratedConfig()
            }
        }
    }

    fun setNodesExcluded(nodeIds: List<String>, excluded: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.setNodesExcluded(nodeIds, excluded)
                refreshGeneratedConfig()
            }
        }
    }

    fun getNodeSharePayload(nodeId: String): NodeSharePayload? =
        repository.getNodeSharePayload(nodeId)

    fun updateNodeExportFilter(filter: NodeExportNameFilter?) {
        viewModelScope.launch {
            runCatching {
                repository.updateNodeExportFilter(filter)
                showToast("已保存导出名称筛选", ToastTone.SUCCESS)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("筛选保存失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    // --- Rules ---

    fun selectRuleScheme(schemeId: String) {
        viewModelScope.launch {
            runCatching {
                repository.selectRuleScheme(schemeId)
                refreshGeneratedConfig()
            }
        }
    }

    suspend fun importRuleScheme(name: String?, url: String?, content: String?): Result<RuleScheme> =
        runCatchingExceptCancellation {
            val scheme = repository.importRuleScheme(name, url, content)
            showToast("已导入规则方案「${scheme.name}」", ToastTone.SUCCESS)
            refreshGeneratedConfig()
            scheme
        }

    fun refreshRuleScheme(schemeId: String) {
        viewModelScope.launch {
            runCatching {
                val scheme = repository.refreshRuleScheme(schemeId)
                showToast("已更新「${scheme.name}」", ToastTone.SUCCESS)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("更新规则失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun deleteRuleScheme(schemeId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteRuleScheme(schemeId)
                showToast("已删除规则方案", ToastTone.NEUTRAL)
                refreshGeneratedConfig()
            }
        }
    }

    fun setSchemeEmojiSettings(schemeId: String, emojisEnabled: Boolean? = null, emojiAutofill: Boolean? = null) {
        viewModelScope.launch {
            runCatching {
                repository.setSchemeEmojiSettings(schemeId, emojisEnabled, emojiAutofill)
                refreshGeneratedConfig()
            }
        }
    }

    fun toggleRuleGroupEnabled(schemeId: String, groupName: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                val currentGroups = state.value.activeSchemeGroups
                val currentEnabled = state.value.snapshot.selectedRuleGroups[schemeId]
                    ?: currentGroups.filter { it.isEnabled }.map { it.sourceName }
                val updated = if (enabled) {
                    (currentEnabled + groupName).distinct()
                } else {
                    currentEnabled.filter { it != groupName }
                }
                repository.setEnabledRuleGroups(schemeId, updated)
                refreshGeneratedConfig()
            }
        }
    }

    fun moveRuleGroup(schemeId: String, fromIndex: Int, toIndex: Int) {
        val groups = state.value.activeSchemeGroups
        if (fromIndex !in groups.indices || toIndex !in groups.indices || fromIndex == toIndex) return
        val mutable = groups.map { it.sourceName }.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)

        viewModelScope.launch {
            runCatching {
                val result = repository.reorderRuleGroups(
                    schemeId = schemeId,
                    fromIndex = fromIndex,
                    toIndex = toIndex,
                    orderedGroupNames = mutable,
                )
                if (result.conflicts.isNotEmpty() && result.previousCustomization != null) {
                    _pendingConflicts.value = PendingConflictNotice(
                        schemeId = schemeId,
                        previousCustomization = result.previousCustomization!!,
                        conflicts = result.conflicts,
                    )
                }
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("调整顺序失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun undoPendingReorder() {
        val notice = _pendingConflicts.value ?: return
        _pendingConflicts.value = null
        viewModelScope.launch {
            runCatching {
                repository.updateRuleSchemeCustomization(notice.schemeId, notice.previousCustomization)
                showToast("已撤销顺序更改", ToastTone.NEUTRAL)
                refreshGeneratedConfig()
            }
        }
    }

    fun dismissPendingConflicts() {
        _pendingConflicts.value = null
    }

    suspend fun updateRuleGroup(
        schemeId: String,
        sourceName: String,
        newName: String? = null,
        kind: RuleSchemeGroupKind? = null,
        members: List<RuleSchemeGroupMember>? = null,
        supplementRules: List<String>? = null,
    ): Result<Unit> = runCatchingExceptCancellation {
        repository.updateRuleGroup(schemeId, sourceName, newName, kind, members, supplementRules)
        refreshGeneratedConfig()
    }

    suspend fun createRuleGroup(
        schemeId: String,
        name: String,
        kind: RuleSchemeGroupKind,
        members: List<RuleSchemeGroupMember>,
        isNodeFilterGroup: Boolean,
    ): Result<Unit> = runCatchingExceptCancellation {
        repository.createRuleGroup(schemeId, name, kind, members, isNodeFilterGroup)
        showToast("已创建策略组「$name」", ToastTone.SUCCESS)
        refreshGeneratedConfig()
    }

    fun deleteRuleGroup(schemeId: String, sourceName: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteRuleGroup(schemeId, sourceName)
                showToast("已删除策略组", ToastTone.NEUTRAL)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("删除失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun searchRuleCatalog(query: String, source: String): List<RuleCatalogEntry> =
        repository.searchRuleCatalog(query = query, source = source, limit = 80)

    fun addCatalogRuleFlow(
        schemeId: String,
        entry: RuleCatalogEntry,
        policyName: String,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addCustomRuleFlow(
                    schemeId = schemeId,
                    name = entry.displayName,
                    policyName = policyName,
                    catalogId = entry.id,
                    sourceURLString = entry.url,
                    isGroupSupplement = false,
                )
                showToast("已添加规则「${entry.displayName}」", ToastTone.SUCCESS)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("添加失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun deleteCustomRuleFlow(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteCustomRuleFlow(id)
                refreshGeneratedConfig()
            }
        }
    }

    suspend fun createLocalRuleSet(name: String, rulesText: String): Result<LocalRuleSet> =
        runCatchingExceptCancellation {
            val rs = repository.createLocalRuleSet(name, rulesText)
            showToast("已保存本地规则集「${rs.name}」", ToastTone.SUCCESS)
            rs
        }

    suspend fun updateLocalRuleSet(id: String, name: String?, rulesText: String?): Result<Unit> =
        runCatchingExceptCancellation {
            repository.updateLocalRuleSet(id, name, rulesText)
            refreshGeneratedConfig()
        }

    fun deleteLocalRuleSet(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteLocalRuleSet(id)
                refreshGeneratedConfig()
            }
        }
    }

    fun attachLocalRuleSetToScheme(schemeId: String, ruleSet: LocalRuleSet, policyName: String) {
        viewModelScope.launch {
            runCatching {
                repository.addCustomRuleFlow(
                    schemeId = schemeId,
                    name = ruleSet.name,
                    policyName = policyName,
                    rulesText = ruleSet.rulesText,
                    localRuleSetId = ruleSet.id,
                    sourceURLString = ruleSet.sourceURLString,
                    isGroupSupplement = false,
                )
                showToast("已挂载「${ruleSet.name}」到当前方案", ToastTone.SUCCESS)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("挂载失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    // --- Export & LAN ---

    fun selectClientTarget(target: ClientTarget) {
        _isLanSharingSelected.value = false
        viewModelScope.launch {
            runCatching {
                repository.updateExportPreferences(selectedTarget = target)
                refreshGeneratedConfig()
            }
        }
    }

    fun setExportContentMode(target: ClientTarget, mode: ExportContentMode) {
        viewModelScope.launch {
            runCatching {
                val updated = state.value.snapshot.exportContentModes.toMutableMap().apply {
                    put(target.rawValue, mode)
                }
                repository.updateExportPreferences(exportContentModes = updated)
                refreshGeneratedConfig()
            }
        }
    }

    fun toggleExcludedKind(target: ClientTarget, kind: ProxyKind) {
        viewModelScope.launch {
            runCatching {
                val currentMap = state.value.snapshot.excludedKinds.toMutableMap()
                val currentList = currentMap[target.rawValue].orEmpty().toMutableList()
                if (currentList.contains(kind)) {
                    currentList.remove(kind)
                } else {
                    currentList.add(kind)
                }
                currentMap[target.rawValue] = currentList
                repository.updateExportPreferences(excludedKinds = currentMap)
                refreshGeneratedConfig()
            }
        }
    }

    fun updateClientOrderAndVisibility(
        order: List<ClientTarget>,
        visible: List<ClientTarget>,
        isLanVisible: Boolean? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.updateExportPreferences(
                    clientOrder = order,
                    visibleClientTargets = visible,
                    isLANSharingVisible = isLanVisible,
                )
            }
        }
    }

    fun refreshGeneratedConfig() {
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            val snap = state.value.snapshot
            val target = snap.selectedTarget
            val meta = ClientCatalog.meta(target)
            val savedMode = snap.exportContentModes[target.rawValue]
            val mode = when {
                savedMode == ExportContentMode.NODES_ONLY && meta.supportsNodesOnly -> ExportContentMode.NODES_ONLY
                savedMode == ExportContentMode.FULL_CONFIGURATION && meta.supportsFullConfiguration -> ExportContentMode.FULL_CONFIGURATION
                meta.supportsFullConfiguration -> ExportContentMode.FULL_CONFIGURATION
                else -> ExportContentMode.NODES_ONLY
            }
            _isGenerating.value = true
            try {
                val config = repository.generateConfiguration(target = target, mode = mode, maskSecrets = true)
                _generatedConfig.value = config
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("生成配置失败：${e.message}", ToastTone.ERROR)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    suspend fun generateUnmaskedConfiguration(target: ClientTarget, mode: ExportContentMode): Result<GeneratedConfiguration> =
        runCatchingExceptCancellation {
            repository.generateConfiguration(target = target, mode = mode, maskSecrets = false)
        }

    suspend fun prepareDirectImport(target: ClientTarget, mode: ExportContentMode): Result<DirectImportHandle> =
        runCatchingExceptCancellation {
            repository.prepareDirectImport(target, mode)
        }

    fun toggleLanSharing(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                val status = repository.toggleLanSharing(enabled)
                showToast(
                    if (status.enabled) "局域网共享已开启" else "局域网共享已关闭",
                    if (status.enabled) ToastTone.SUCCESS else ToastTone.NEUTRAL,
                )
            }.onFailure { e ->
                showToast("局域网共享切换失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun rotateLanToken() {
        viewModelScope.launch {
            runCatching {
                repository.rotateLanToken()
                showToast("已更新访问密钥，旧链接已失效", ToastTone.SUCCESS)
            }.onFailure { e ->
                showToast("更换密钥失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    fun buildLanSubscriptionUrl(
        format: LanSubscriptionFormat,
        contentMode: ExportContentMode,
        baseUrl: String? = null,
    ): String = repository.buildLanSubscriptionUrl(format, contentMode, baseUrl)

    fun generateQrMatrix(content: String): QrMatrix? =
        repository.generateQrMatrix(content)

    // --- Settings, Tailscale, Backup ---

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            runCatching {
                repository.updateSettings(transform)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("设置更新失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    suspend fun saveTailnet(
        existingId: String?,
        name: String,
        controlURLString: String?,
        deviceName: String?,
        subnets: List<String>,
        magicDNSSuffix: String?,
        isEnabled: Boolean,
        authKey: String?,
        clearAuthKey: Boolean,
    ): Result<Unit> = runCatchingExceptCancellation {
        if (existingId == null) {
            repository.createTailnet(
                name = name,
                controlURLString = controlURLString,
                deviceName = deviceName,
                subnets = subnets,
                magicDNSSuffix = magicDNSSuffix,
                authKey = authKey,
                isEnabled = isEnabled,
            )
            showToast("已添加 Tailscale 内网", ToastTone.SUCCESS)
        } else {
            repository.updateTailnet(
                id = existingId,
                name = name,
                controlURLString = controlURLString,
                deviceName = deviceName,
                subnets = subnets,
                magicDNSSuffix = magicDNSSuffix,
                isEnabled = isEnabled,
                authKey = authKey,
                clearAuthKey = clearAuthKey,
            )
            showToast("已保存 Tailscale 设置", ToastTone.SUCCESS)
        }
        refreshGeneratedConfig()
    }

    fun toggleTailnetEnabled(connection: TailnetConnection) {
        viewModelScope.launch {
            runCatching {
                repository.updateTailnet(id = connection.id, isEnabled = !connection.isEnabled)
                refreshGeneratedConfig()
            }
        }
    }

    fun deleteTailnet(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteTailnet(id)
                showToast("已删除 Tailscale 内网", ToastTone.NEUTRAL)
                refreshGeneratedConfig()
            }
        }
    }

    suspend fun exportBackupJson(): Result<String> = runCatchingExceptCancellation {
        repository.exportBackupJson()
    }

    suspend fun importBackupJson(jsonText: String): Result<Unit> = runCatchingExceptCancellation {
        repository.importBackupJson(jsonText)
        showToast("已导入备份配置", ToastTone.SUCCESS)
        resolveNodeGeographyInBackground()
        refreshGeneratedConfig()
    }

    fun resetAllData() {
        viewModelScope.launch {
            runCatching {
                repository.resetAllData()
                showToast("已重置全部数据", ToastTone.NEUTRAL)
                refreshGeneratedConfig()
            }.onFailure { e ->
                showToast("重置失败：${e.message}", ToastTone.ERROR)
            }
        }
    }

    private inline fun <T> runCatchingExceptCancellation(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    class Factory(
        private val repository: TowerRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TowerViewModel(repository) as T
        }
    }
}
