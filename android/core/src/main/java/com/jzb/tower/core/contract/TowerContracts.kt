package com.jzb.tower.core.contract

import com.jzb.tower.core.model.*
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

/**
 * Abstraction for reading bundled Tower assets (`Tower/Resources/...`).
 * On Android, implemented via `AssetManager` reading `tower_resources/...`.
 * In JVM unit tests, implemented directly against the repo's `Tower/Resources/` directory.
 */
interface TowerResourceProvider {
    fun openResource(relativePath: String): InputStream?
    fun readText(relativePath: String): String? =
        openResource(relativePath)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    fun readBytes(relativePath: String): ByteArray? =
        openResource(relativePath)?.use { it.readBytes() }
    fun listFiles(relativeDir: String): List<String>
}

/**
 * Secure local-only storage for Tailscale Auth Keys (Android KeyStore AES-GCM on device).
 * Auth keys are never written to AppSnapshot or exported backup JSON.
 */
interface TailnetSecretStore {
    fun getAuthKey(tailnetId: String): String?
    fun setAuthKey(tailnetId: String, authKey: String)
    fun deleteAuthKey(tailnetId: String)
    fun clearAll()
    fun hasAuthKey(tailnetId: String): Boolean = !getAuthKey(tailnetId).isNullOrEmpty()
}

/**
 * Callback hook for platform-specific LAN server lifecycle (e.g., Android Foreground Service).
 */
interface LanServiceHooks {
    fun onLanSharingStarted(primaryUrl: String, port: Int) {}
    fun onLanSharingStopped() {}
    fun getLocalLanAddresses(): List<String> = emptyList()
}

/**
 * Primary backend contract shared between `:core` (backend) and `:app` (Compose frontend).
 * All state mutations emit updated immutable `AppState` through [state].
 */
interface TowerRepository {
    val state: StateFlow<AppState>

    /** Initialize state, bundled schemes, and offline geo metadata. Safe to call multiple times. */
    suspend fun initialize()

    /** Static offline assets */
    suspend fun getWorldDotMap(): WorldDotMapData
    fun getCountries(): List<CountryEntry>

    // --- Subscriptions & Nodes ---
    fun detectSourceInput(input: String): SourceDetectionResult

    suspend fun addSubscription(
        urlString: String,
        name: String? = null,
        requestOptions: SubscriptionRequestOptions? = null,
    ): SubscriptionMutationResult

    suspend fun updateSubscription(
        id: String,
        name: String? = null,
        urlString: String? = null,
        isEnabled: Boolean? = null,
        requestOptions: SubscriptionRequestOptions? = null,
    )

    suspend fun deleteSubscription(id: String)
    suspend fun refreshSubscription(id: String): SubscriptionMutationResult
    suspend fun refreshAllSubscriptions(): List<SubscriptionMutationResult>

    suspend fun importNodesFromText(content: String): NodeImportResult

    suspend fun updateNode(
        id: String,
        name: String? = null,
        countryOverride: String? = null,
        clearCountryOverride: Boolean = false,
        excluded: Boolean? = null,
    )

    suspend fun deleteNode(id: String)
    suspend fun setNodesExcluded(nodeIds: List<String>, excluded: Boolean)
    suspend fun testNodeLatency(
        nodeIds: List<String>? = null,
        mode: NodeLatencyTestMode? = null,
    ): Map<String, NodeLatencyMeasurement>

    suspend fun resolveNodeGeography(nodeIds: List<String>? = null): Map<String, NodeRuntimeInfo>
    fun getNodeSharePayload(nodeId: String): NodeSharePayload?
    suspend fun updateNodeExportFilter(filter: NodeExportNameFilter?)

    // --- Rules & Policy Schemes ---
    suspend fun selectRuleScheme(schemeId: String)
    suspend fun importRuleScheme(
        name: String? = null,
        url: String? = null,
        content: String? = null,
    ): RuleScheme

    suspend fun deleteRuleScheme(schemeId: String)
    suspend fun refreshRuleScheme(schemeId: String): RuleScheme

    suspend fun setEnabledRuleGroups(schemeId: String, enabledGroupNames: List<String>?)
    suspend fun setSchemeEmojiSettings(
        schemeId: String,
        emojisEnabled: Boolean? = null,
        emojiAutofill: Boolean? = null,
    )

    /**
     * Drag-reorders a policy group in the unified list.
     * Updates both `groupOrder` and minimal `matchingOrder` (preserving trailing GEOIP/FINAL),
     * and returns any domain-matching conflicts alongside `previousCustomization` for Undo.
     */
    suspend fun reorderRuleGroups(
        schemeId: String,
        fromIndex: Int,
        toIndex: Int,
        orderedGroupNames: List<String>,
    ): ReorderGroupsResult

    suspend fun updateRuleSchemeCustomization(
        schemeId: String,
        customization: RuleSchemeCustomization,
    )

    suspend fun createRuleGroup(
        schemeId: String,
        name: String,
        kind: RuleSchemeGroupKind,
        members: List<RuleSchemeGroupMember>,
        isNodeFilterGroup: Boolean = true,
    )

    suspend fun updateRuleGroup(
        schemeId: String,
        sourceName: String,
        newName: String? = null,
        kind: RuleSchemeGroupKind? = null,
        members: List<RuleSchemeGroupMember>? = null,
        supplementRules: List<String>? = null,
    )

    suspend fun deleteRuleGroup(schemeId: String, sourceName: String)

    fun searchRuleCatalog(
        query: String = "",
        source: String = "all", // "all" | "acl4ssr" | "blackmatrix7"
        limit: Int = 60,
    ): List<RuleCatalogEntry>

    suspend fun addCustomRuleFlow(
        schemeId: String,
        name: String,
        policyName: String,
        rulesText: String = "",
        catalogId: String? = null,
        localRuleSetId: String? = null,
        sourceURLString: String? = null,
        isGroupSupplement: Boolean = false,
    ): CustomRuleFlow

    suspend fun updateCustomRuleFlow(
        id: String,
        name: String? = null,
        policyName: String? = null,
        rulesText: String? = null,
        isEnabled: Boolean? = null,
    )

    suspend fun deleteCustomRuleFlow(id: String)

    suspend fun createLocalRuleSet(name: String, rulesText: String, sourceURLString: String? = null): LocalRuleSet
    suspend fun updateLocalRuleSet(id: String, name: String? = null, rulesText: String? = null)
    suspend fun deleteLocalRuleSet(id: String)

    // --- Export, Direct Import (127.0.0.1 / 3 min), & LAN Sharing ---
    suspend fun generateConfiguration(
        target: ClientTarget,
        mode: ExportContentMode,
        maskSecrets: Boolean = true,
    ): GeneratedConfiguration

    /**
     * Starts / refreshes the loopback-only (127.0.0.1, 3-minute TTL) HTTP server serving the
     * real (unmasked) configuration and returns the local URL + client scheme URL ready to launch.
     */
    suspend fun prepareDirectImport(
        target: ClientTarget,
        mode: ExportContentMode,
    ): DirectImportHandle

    suspend fun updateExportPreferences(
        selectedTarget: ClientTarget? = null,
        clientOrder: List<ClientTarget>? = null,
        visibleClientTargets: List<ClientTarget>? = null,
        exportContentModes: Map<String, ExportContentMode>? = null,
        excludedKinds: Map<String, List<ProxyKind>>? = null,
        isLANSharingVisible: Boolean? = null,
    )

    suspend fun toggleLanSharing(enabled: Boolean): LanSharingStatus
    suspend fun rotateLanToken(): LanSharingStatus
    fun buildLanSubscriptionUrl(
        format: LanSubscriptionFormat,
        contentMode: ExportContentMode = ExportContentMode.FULL_CONFIGURATION,
        baseUrl: String? = null,
    ): String

    fun generateQrMatrix(content: String): QrMatrix?

    // --- Settings, Tailscale, & Backup ---
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun completeOnboarding()

    suspend fun createTailnet(
        name: String,
        controlURLString: String? = null,
        deviceName: String? = null,
        subnets: List<String> = emptyList(),
        magicDNSSuffix: String? = null,
        authKey: String? = null,
        isEnabled: Boolean = true,
    ): TailnetConnection

    suspend fun updateTailnet(
        id: String,
        name: String? = null,
        controlURLString: String? = null,
        deviceName: String? = null,
        subnets: List<String>? = null,
        magicDNSSuffix: String? = null,
        isEnabled: Boolean? = null,
        authKey: String? = null,
        clearAuthKey: Boolean = false,
    )

    suspend fun deleteTailnet(id: String)

    /** Exports AppSnapshot JSON (strictly without Tailnet Auth Keys). */
    suspend fun exportBackupJson(): String
    suspend fun importBackupJson(jsonText: String)
    suspend fun resetAllData()

    fun shutdown()
}

data class DirectImportHandle(
    val localUrl: String,
    val schemeUrls: List<String>,
    val expiresAtEpochMs: Long,
    val fileName: String,
    val mimeType: String,
)
