package com.jzb.tower.android.di

import android.content.Context
import com.jzb.tower.core.contract.DirectImportHandle
import com.jzb.tower.core.contract.TowerRepository
import com.jzb.tower.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Dependency container instantiated once by [com.jzb.tower.android.TowerApplication].
 * Backend agent (`gpt-6.2-sol`) replaces [StubTowerRepository] with the real `TowerRepositoryImpl`
 * wired to `AndroidResourceProvider`, `AndroidTailnetSecretStore`, and `LanSharingForegroundService`.
 */
class TowerAppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val repository: TowerRepository = StubTowerRepository()

    fun shutdown() {
        repository.shutdown()
    }
}

private class StubTowerRepository : TowerRepository {
    private val _state = MutableStateFlow(AppState())
    override val state: StateFlow<AppState> = _state

    override suspend fun initialize() {}
    override suspend fun getWorldDotMap(): WorldDotMapData = WorldDotMapData(0, 0, emptyList(), emptyMap())
    override fun getCountries(): List<CountryEntry> = emptyList()
    override fun detectSourceInput(input: String): SourceDetectionResult = SourceDetectionResult(DetectedSourceKind.UNKNOWN, "")
    override suspend fun addSubscription(urlString: String, name: String?, requestOptions: SubscriptionRequestOptions?): SubscriptionMutationResult = error("Not implemented")
    override suspend fun updateSubscription(id: String, name: String?, urlString: String?, isEnabled: Boolean?, requestOptions: SubscriptionRequestOptions?) {}
    override suspend fun deleteSubscription(id: String) {}
    override suspend fun refreshSubscription(id: String): SubscriptionMutationResult = error("Not implemented")
    override suspend fun refreshAllSubscriptions(): List<SubscriptionMutationResult> = emptyList()
    override suspend fun importNodesFromText(content: String): NodeImportResult = NodeImportResult(emptyList(), 0, emptyList())
    override suspend fun updateNode(id: String, name: String?, countryOverride: String?, clearCountryOverride: Boolean, excluded: Boolean?) {}
    override suspend fun deleteNode(id: String) {}
    override suspend fun setNodesExcluded(nodeIds: List<String>, excluded: Boolean) {}
    override suspend fun testNodeLatency(nodeIds: List<String>?, mode: NodeLatencyTestMode?): Map<String, NodeLatencyMeasurement> = emptyMap()
    override suspend fun resolveNodeGeography(nodeIds: List<String>?): Map<String, NodeRuntimeInfo> = emptyMap()
    override fun getNodeSharePayload(nodeId: String): NodeSharePayload? = null
    override suspend fun updateNodeExportFilter(filter: NodeExportNameFilter?) {}
    override suspend fun selectRuleScheme(schemeId: String) {}
    override suspend fun importRuleScheme(name: String?, url: String?, content: String?): RuleScheme = error("Not implemented")
    override suspend fun deleteRuleScheme(schemeId: String) {}
    override suspend fun refreshRuleScheme(schemeId: String): RuleScheme = error("Not implemented")
    override suspend fun setEnabledRuleGroups(schemeId: String, enabledGroupNames: List<String>?) {}
    override suspend fun setSchemeEmojiSettings(schemeId: String, emojisEnabled: Boolean?, emojiAutofill: Boolean?) {}
    override suspend fun reorderRuleGroups(schemeId: String, fromIndex: Int, toIndex: Int, orderedGroupNames: List<String>): ReorderGroupsResult = error("Not implemented")
    override suspend fun updateRuleSchemeCustomization(schemeId: String, customization: RuleSchemeCustomization) {}
    override suspend fun createRuleGroup(schemeId: String, name: String, kind: RuleSchemeGroupKind, members: List<RuleSchemeGroupMember>, isNodeFilterGroup: Boolean) {}
    override suspend fun updateRuleGroup(schemeId: String, sourceName: String, newName: String?, kind: RuleSchemeGroupKind?, members: List<RuleSchemeGroupMember>?, supplementRules: List<String>?) {}
    override suspend fun deleteRuleGroup(schemeId: String, sourceName: String) {}
    override fun searchRuleCatalog(query: String, source: String, limit: Int): List<RuleCatalogEntry> = emptyList()
    override suspend fun addCustomRuleFlow(schemeId: String, name: String, policyName: String, rulesText: String, catalogId: String?, localRuleSetId: String?, sourceURLString: String?, isGroupSupplement: Boolean): CustomRuleFlow = error("Not implemented")
    override suspend fun updateCustomRuleFlow(id: String, name: String?, policyName: String?, rulesText: String?, isEnabled: Boolean?) {}
    override suspend fun deleteCustomRuleFlow(id: String) {}
    override suspend fun createLocalRuleSet(name: String, rulesText: String, sourceURLString: String?): LocalRuleSet = error("Not implemented")
    override suspend fun updateLocalRuleSet(id: String, name: String?, rulesText: String?) {}
    override suspend fun deleteLocalRuleSet(id: String) {}
    override suspend fun generateConfiguration(target: ClientTarget, mode: ExportContentMode, maskSecrets: Boolean): GeneratedConfiguration = error("Not implemented")
    override suspend fun prepareDirectImport(target: ClientTarget, mode: ExportContentMode): DirectImportHandle = error("Not implemented")
    override suspend fun updateExportPreferences(selectedTarget: ClientTarget?, clientOrder: List<ClientTarget>?, visibleClientTargets: List<ClientTarget>?, exportContentModes: Map<String, ExportContentMode>?, excludedKinds: Map<String, List<ProxyKind>>?, isLANSharingVisible: Boolean?) {}
    override suspend fun toggleLanSharing(enabled: Boolean): LanSharingStatus = LanSharingStatus()
    override suspend fun rotateLanToken(): LanSharingStatus = LanSharingStatus()
    override fun buildLanSubscriptionUrl(format: LanSubscriptionFormat, contentMode: ExportContentMode, baseUrl: String?): String = ""
    override fun generateQrMatrix(content: String): QrMatrix? = null
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {}
    override suspend fun completeOnboarding() {}
    override suspend fun createTailnet(name: String, controlURLString: String?, deviceName: String?, subnets: List<String>, magicDNSSuffix: String?, authKey: String?, isEnabled: Boolean): TailnetConnection = error("Not implemented")
    override suspend fun updateTailnet(id: String, name: String?, controlURLString: String?, deviceName: String?, subnets: List<String>?, magicDNSSuffix: String?, isEnabled: Boolean?, authKey: String?, clearAuthKey: Boolean) {}
    override suspend fun deleteTailnet(id: String) {}
    override suspend fun exportBackupJson(): String = "{}"
    override suspend fun importBackupJson(jsonText: String) {}
    override suspend fun resetAllData() {}
    override fun shutdown() {}
}
