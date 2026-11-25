package com.jzb.tower.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AppTab {
    SUBSCRIPTIONS,
    RULES,
    EXPORT,
    SETTINGS,
}

@Serializable
enum class ProxyKind(val rawValue: String) {
    @SerialName("ss") SS("ss"),
    @SerialName("ssr") SSR("ssr"),
    @SerialName("vmess") VMESS("vmess"),
    @SerialName("vless") VLESS("vless"),
    @SerialName("trojan") TROJAN("trojan"),
    @SerialName("hysteria") HYSTERIA("hysteria"),
    @SerialName("hysteria2") HYSTERIA2("hysteria2"),
    @SerialName("tuic") TUIC("tuic"),
    @SerialName("masque") MASQUE("masque"),
    @SerialName("wireguard") WIREGUARD("wireguard"),
    @SerialName("anytls") ANYTLS("anytls"),
    @SerialName("snell") SNELL("snell"),
    @SerialName("socks5") SOCKS5("socks5"),
    @SerialName("http") HTTP("http"),
    @SerialName("ssh") SSH("ssh"),
    @SerialName("trusttunnel") TRUSTTUNNEL("trusttunnel"),
    @SerialName("unknown") UNKNOWN("unknown");

    val isUdpOnly: Boolean
        get() = this == HYSTERIA || this == HYSTERIA2 || this == TUIC || this == WIREGUARD || this == MASQUE

    companion object {
        fun fromRaw(raw: String): ProxyKind =
            entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
enum class ClientTarget(val rawValue: String) {
    @SerialName("flclash") FLCLASH("flclash"),
    @SerialName("clash-apple") CLASH_APPLE("clash-apple"),
    @SerialName("sing-box") SING_BOX("sing-box"),
    @SerialName("hiddify") HIDDIFY("hiddify"),
    @SerialName("surfboard") SURFBOARD("surfboard"),
    @SerialName("v2box") V2BOX("v2box"),
    @SerialName("clash-mi") CLASH_MI("clash-mi"),
    @SerialName("karing") KARING("karing"),
    @SerialName("clash-verge") CLASH_VERGE("clash-verge"),
    @SerialName("mihomo-party") MIHOMO_PARTY("mihomo-party"),
    @SerialName("clashmac") CLASHMAC("clashmac"),
    @SerialName("clash") CLASH("clash"), // Stash
    @SerialName("surge") SURGE("surge"),
    @SerialName("surge-mac") SURGE_MAC("surge-mac"),
    @SerialName("shadowrocket") SHADOWROCKET("shadowrocket"),
    @SerialName("loon") LOON("loon"),
    @SerialName("quanx") QUANX("quanx"),
    @SerialName("egern") EGERN("egern"),
    @SerialName("anywhere") ANYWHERE("anywhere");

    companion object {
        fun fromRaw(raw: String): ClientTarget? =
            entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) }
    }
}

@Serializable
enum class ExportContentMode(val rawValue: String) {
    @SerialName("fullConfiguration") FULL_CONFIGURATION("fullConfiguration"),
    @SerialName("nodesOnly") NODES_ONLY("nodesOnly"),
}

@Serializable
enum class LanSubscriptionFormat(val rawValue: String) {
    @SerialName("auto") AUTO("auto"),
    @SerialName("clash") CLASH("clash"),
    @SerialName("stash") STASH("stash"),
    @SerialName("surge") SURGE("surge"),
    @SerialName("surfboard") SURFBOARD("surfboard"),
    @SerialName("shadowrocket") SHADOWROCKET("shadowrocket"),
    @SerialName("loon") LOON("loon"),
    @SerialName("quanx") QUANX("quanx"),
    @SerialName("sing-box") SING_BOX("sing-box"),
    @SerialName("hiddify") HIDDIFY("hiddify"),
    @SerialName("egern") EGERN("egern"),
    @SerialName("v2ray") V2RAY("v2ray");

    companion object {
        fun fromRaw(raw: String): LanSubscriptionFormat? =
            entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) }
    }
}

@Serializable
enum class NodeProbeMethod {
    ICMP,
    TCP,
}

@Serializable
enum class NodeLatencyTestMode(val rawValue: String) {
    @SerialName("automatic") AUTOMATIC("automatic"),
    @SerialName("icmp") ICMP("icmp"),
    @SerialName("tcp") TCP("tcp"),
}

@Serializable
enum class DnsProtectionMode(val rawValue: String) {
    @SerialName("standard") STANDARD("standard"),
    @SerialName("strict") STRICT("strict"),
    @SerialName("followScheme") FOLLOW_SCHEME("followScheme"),
}

@Serializable
enum class ThemePreference(val rawValue: String) {
    @SerialName("system") SYSTEM("system"),
    @SerialName("light") LIGHT("light"),
    @SerialName("dark") DARK("dark"),
}

data class ClientTargetMeta(
    val id: ClientTarget,
    val name: String,
    val subtitle: String,
    val iconFile: String,
    val brandColorHex: Long,
    val fileExtension: String,
    val usesClashFormat: Boolean,
    val usesSingBoxFormat: Boolean,
    val supportsFullConfiguration: Boolean,
    val supportsNodesOnly: Boolean,
    val supportsDirectConfigurationImport: Boolean,
    val supportsNodesOnlyImport: Boolean,
    val supportsEmbeddedRemoteSubscriptions: Boolean,
    val expressesReality: Boolean,
    val supportedKinds: Set<ProxyKind>,
    val isAndroidNative: Boolean,
    val urlSchemeTemplates: List<String>,
)

data class LanFormatOption(
    val id: LanSubscriptionFormat,
    val label: String,
    val subtitle: String,
    val iconFile: String? = null,
)

@Serializable
data class SubscriptionRequestOptions(
    val userAgent: String? = null,
    val dnsOverHTTPSURL: String? = null,
) {
    val isEmpty: Boolean
        get() = userAgent.isNullOrBlank() && dnsOverHTTPSURL.isNullOrBlank()
}

@Serializable
data class SubscriptionUsage(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val notices: List<String> = emptyList(),
) {
    val usedBytes: Long?
        get() = if (uploadBytes == null && downloadBytes == null) null
        else {
            val u = (uploadBytes ?: 0L).coerceAtLeast(0L)
            val d = (downloadBytes ?: 0L).coerceAtLeast(0L)
            if (Long.MAX_VALUE - u < d) Long.MAX_VALUE else u + d
        }

    val remainingBytes: Long?
        get() {
            val total = totalBytes ?: return null
            val used = usedBytes ?: return null
            return (total - used).coerceAtLeast(0L)
        }

    val usedFraction: Float?
        get() {
            val total = totalBytes ?: return null
            val used = usedBytes ?: return null
            if (total <= 0L) return null
            return (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val isEmpty: Boolean
        get() = totalBytes == null && expiresAtEpochMs == null && notices.isEmpty()

    val hasPlanDetail: Boolean
        get() = totalBytes != null || expiresAtEpochMs != null
}

@Serializable
data class SubscriptionSource(
    val id: String,
    val name: String,
    val urlString: String,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long,
    val lastUpdatedAtEpochMs: Long? = null,
    val lastError: String? = null,
    val usage: SubscriptionUsage? = null,
    val requestOptions: SubscriptionRequestOptions? = null,
    val nameWasAutoGenerated: Boolean? = null,
)

@Serializable
data class ShadowTlsOptions(
    val version: Int,
    val host: String,
    val password: String,
    val skipCertificateVerification: Boolean = false,
)

@Serializable
data class SshOptions(
    val privateKey: String? = null,
    val privateKeyPassphrase: String? = null,
    val hostKeys: List<String> = emptyList(),
    val hostKeyAlgorithms: List<String> = emptyList(),
)

@Serializable
data class TrustTunnelOptions(
    val quic: Boolean = false,
    val clientRandomAuthKey: String? = null,
)

@Serializable
data class MasqueOptions(
    val mode: String = "keyConnectIP", // "keyConnectIP" | "basicConnectIP"
    val privateKey: String? = null,
    val publicKey: String? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val network: String? = null,
    val mtu: Int? = null,
    val dns: List<String>? = null,
    val remoteDNSResolve: Boolean? = null,
    val httpVersion: Int? = null,
    val disableVersionFallback: Boolean? = null,
    val headers: Map<String, String>? = null,
    val certificate: List<String>? = null,
)

@Serializable
data class ProxyNode(
    val id: String,
    val sourceID: String? = null,
    val kind: ProxyKind,
    val unrecognizedKind: String? = null,
    val name: String,
    val server: String,
    val port: Int,
    val cipher: String? = null,
    val password: String? = null,
    val uuid: String? = null,
    val username: String? = null,
    val transport: String? = null,
    val transportMode: String? = null,
    val plugin: String? = null,
    val pluginMux: Boolean? = null,
    val shadowTLS: ShadowTlsOptions? = null,
    val masque: MasqueOptions? = null,
    val ssh: SshOptions? = null,
    val trustTunnel: TrustTunnelOptions? = null,
    val tls: Boolean = false,
    val sni: String? = null,
    val hostHeader: String? = null,
    val path: String? = null,
    val alpn: String? = null,
    val realityPublicKey: String? = null,
    val realityShortID: String? = null,
    val realitySupportsX25519MLKEM768: Boolean? = null,
    val certificateFingerprint: String? = null,
    val fingerprint: String? = null,
    val flow: String? = null,
    val vlessEncryption: String? = null,
    val skipCertificateVerification: Boolean = false,
    val alterID: Int? = null,
    val protocolName: String? = null,
    val protocolParam: String? = null,
    val obfs: String? = null,
    val obfsParam: String? = null,
    val idleSessionCheckInterval: Int? = null,
    val idleSessionTimeout: Int? = null,
    val minIdleSession: Int? = null,
    val version: Int? = null,
    val congestionControl: String? = null,
    val udpRelayMode: String? = null,
    val udpRelayEnabled: Boolean? = null,
    val portHopping: String? = null,
    val upMbps: Int? = null,
    val downMbps: Int? = null,
    val wireGuardPrivateKey: String? = null,
    val wireGuardPublicKey: String? = null,
    val wireGuardPreSharedKey: String? = null,
    val wireGuardIPv4: String? = null,
    val wireGuardIPv6: String? = null,
    val wireGuardAllowedIPs: String? = null,
    val wireGuardReserved: String? = null,
    val wireGuardMTU: Int? = null,
    val wireGuardPersistentKeepalive: Int? = null,
    val wireGuardDNS: String? = null,
    val rawURI: String = "",
    val isSubscriptionMetadata: Boolean? = null,
    val countryOverride: String? = null,
    val hostsSourceServer: String? = null,
)

@Serializable
data class NodeLatencyMeasurement(
    val milliseconds: Int? = null,
    val method: NodeProbeMethod? = null,
    val testedAtEpochMs: Long,
    val errorMessage: String? = null,
    val isApplicable: Boolean = true,
)

@Serializable
enum class RegionSource {
    OVERRIDE,
    NAME,
    IP,
    UNKNOWN,
}

@Serializable
data class NodeRuntimeInfo(
    val nodeId: String,
    val countryCode: String? = null,
    val countryName: String? = null,
    val countryEnglishName: String? = null,
    val flagEmoji: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val regionSource: RegionSource = RegionSource.UNKNOWN,
    val resolvedIP: String? = null,
    val asn: Long? = null,
    val asnOrg: String? = null,
    val latency: NodeLatencyMeasurement? = null,
)

@Serializable
data class CountryEntry(
    val code: String,
    val chineseName: String,
    val englishName: String,
    val flag: String,
    val latitude: Double,
    val longitude: Double,
)

data class WorldDotMapData(
    val columns: Int,
    val rows: Int,
    val landRows: List<String>,
    val countryCells: Map<String, IntArray>,
    val minLat: Double = -60.0,
    val maxLat: Double = 84.0,
    val minLon: Double = -180.0,
    val maxLon: Double = 180.0,
)

@Serializable
data class NodeExportNameFilter(
    val includeKeywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val useRegex: Boolean = false,
)

@Serializable
enum class RuleSchemeGroupKind(val rawValue: String) {
    @SerialName("select") SELECT("select"),
    @SerialName("url-test") URL_TEST("url-test"),
    @SerialName("fallback") FALLBACK("fallback"),
    @SerialName("load-balance") LOAD_BALANCE("load-balance"),
    @SerialName("smart") SMART("smart"),
    @SerialName("ssid") SSID("ssid"),
    @SerialName("relay") RELAY("relay"),
    @SerialName("unsupported") UNSUPPORTED("unsupported");

    companion object {
        fun fromRaw(raw: String): RuleSchemeGroupKind =
            entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) } ?: SELECT
    }
}

@Serializable
sealed class RuleSchemeGroupMember {
    @Serializable
    @SerialName("reference")
    data class Reference(val name: String) : RuleSchemeGroupMember()

    @Serializable
    @SerialName("nodePattern")
    data class NodePattern(val pattern: String) : RuleSchemeGroupMember()
}

@Serializable
data class RuleSchemeGroup(
    val name: String,
    val kind: RuleSchemeGroupKind,
    val members: List<RuleSchemeGroupMember>,
    val testURL: String? = null,
    val interval: Int? = null,
    val tolerance: Int? = null,
    val isCustomNodeFilter: Boolean? = null,
    val iconEmoji: String? = null,
    val iconURL: String? = null,
)

@Serializable
sealed class RuleSchemeResource {
    @Serializable
    @SerialName("inline")
    data class Inline(val rule: String) : RuleSchemeResource()

    @Serializable
    @SerialName("bundled")
    data class Bundled(val file: String) : RuleSchemeResource()

    @Serializable
    @SerialName("remote")
    data class Remote(val url: String) : RuleSchemeResource()
}

@Serializable
data class RuleSchemeRuleset(
    val groupName: String,
    val resource: RuleSchemeResource,
    val options: List<String> = emptyList(),
    val origin: String = "source", // "source" | "supplement" | "added"
    val addedLabel: String? = null,
)

@Serializable
data class RuleSchemeNetworkSettings(
    val dnsServers: List<String> = emptyList(),
    val fallbackDNSServers: List<String> = emptyList(),
    val directDNSServers: List<String> = emptyList(),
    val skipProxyHosts: List<String> = emptyList(),
    val tunExcludedRoutes: List<String> = emptyList(),
    val ipv6: Boolean? = null,
)

@Serializable
data class RuleScheme(
    val id: String,
    val name: String,
    val summary: String,
    val sourceURLString: String? = null,
    val rawConfigurationText: String? = null,
    val networkSettings: RuleSchemeNetworkSettings? = null,
    val groups: List<RuleSchemeGroup> = emptyList(),
    val rulesets: List<RuleSchemeRuleset> = emptyList(),
    val updatedAtEpochMs: Long? = null,
    val summaryIsUserEdited: Boolean? = null,
    val isBundled: Boolean = false,
)

@Serializable
data class RuleSchemeCustomization(
    val groupOrder: List<String> = emptyList(),
    val matchingOrder: List<String> = emptyList(),
    val renamedGroups: Map<String, String> = emptyMap(),
    val removedGroupNames: List<String> = emptyList(),
    val groupMemberOverrides: Map<String, List<RuleSchemeGroupMember>> = emptyMap(),
    val groupKindOverrides: Map<String, RuleSchemeGroupKind> = emptyMap(),
    val addedNodeGroups: List<RuleSchemeGroup> = emptyList(),
    val overridesNetworkSettings: Boolean = false,
    val networkSettingsOverride: RuleSchemeNetworkSettings? = null,
)

@Serializable
enum class ConflictDirection {
    SHADOWED_BY_MOVED,
    NOW_SHADOWED_BY_OTHER,
}

@Serializable
data class MatchingOrderConflict(
    val movedGroup: String,
    val otherGroup: String,
    val sampleDomains: List<String>,
    val direction: ConflictDirection,
)

@Serializable
data class LocalRuleSet(
    val id: String,
    val name: String,
    val rulesText: String,
    val sourceURLString: String? = null,
)

@Serializable
data class CustomRuleFlow(
    val id: String,
    val schemeID: String,
    val name: String,
    val policyName: String,
    val rulesText: String,
    val isEnabled: Boolean = true,
    val localRuleSetID: String? = null,
    val catalogID: String? = null,
    val sourceURLString: String? = null,
    val generatedPolicyGroup: RuleSchemeGroup? = null,
    val isGroupSupplement: Boolean? = null,
)

@Serializable
data class RuleCatalogEntry(
    val id: String,
    val source: String, // "acl4ssr" | "blackmatrix7"
    val name: String,
    val displayName: String,
    val category: String,
    val emoji: String,
    val url: String,
)

@Serializable
data class TailnetConnection(
    val id: String,
    val name: String,
    val controlURLString: String? = null,
    val deviceName: String? = null,
    val subnets: List<String> = emptyList(),
    val magicDNSSuffix: String? = null,
    val isEnabled: Boolean = true,
    /** Derived runtime state; Auth Key is NEVER stored in snapshots or serialized here. */
    val hasAuthKey: Boolean = false,
    val stableSlug: String = "",
)

@Serializable
data class AppSettings(
    val configurationName: String = "Tower",
    val appendSubscriptionNameToNodes: Boolean = false,
    val filterSubscriptionInfoNodes: Boolean = true,
    val autoRefreshOnOpen: Boolean = false,
    val preferRuleSets: Boolean = false,
    val embedRemoteSubscriptionLinks: Boolean = false,
    val latencyTestMode: NodeLatencyTestMode = NodeLatencyTestMode.AUTOMATIC,
    val dnsProtectionMode: DnsProtectionMode = DnsProtectionMode.STANDARD,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val hasCompletedOnboarding: Boolean = false,
)

@Serializable
data class AppSnapshot(
    val version: Int = 1,
    val updatedAtEpochMs: Long = 0L,
    val subscriptions: List<SubscriptionSource> = emptyList(),
    val nodes: List<ProxyNode> = emptyList(),
    val selectedPresetID: String = "acl4ssr-default",
    val selectedTarget: ClientTarget = ClientTarget.FLCLASH,
    val importedSchemes: List<RuleScheme> = emptyList(),
    val selectedRuleGroups: Map<String, List<String>> = emptyMap(),
    val ruleSchemeCustomizations: Map<String, RuleSchemeCustomization> = emptyMap(),
    val ruleGroupEmojisEnabled: Map<String, Boolean> = emptyMap(),
    val ruleGroupEmojiAutofill: Map<String, Boolean> = emptyMap(),
    val excludedNodeIDs: List<String> = emptyList(),
    val nodeExportNameFilter: NodeExportNameFilter? = null,
    val customRuleFlows: List<CustomRuleFlow> = emptyList(),
    val localRuleSets: List<LocalRuleSet> = emptyList(),
    val excludedKinds: Map<String, List<ProxyKind>> = emptyMap(),
    val clientOrder: List<ClientTarget> = ClientCatalog.DEFAULT_CLIENT_ORDER,
    val visibleClientTargets: List<ClientTarget> = ClientCatalog.DEFAULT_CLIENT_ORDER,
    val lanSharingOrderIndex: Int = 0,
    val isLANSharingVisible: Boolean = true,
    val exportContentModes: Map<String, ExportContentMode> = emptyMap(),
    val tailnets: List<TailnetConnection> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

data class LanSharingStatus(
    val enabled: Boolean = false,
    val token: String = "",
    val port: Int = 38420,
    val urls: List<String> = emptyList(),
    val primaryURL: String = "",
)

@Serializable
data class SkippedReasonCount(
    val reason: String,
    val count: Int,
)

data class GeneratedConfiguration(
    val target: ClientTarget,
    val contentMode: ExportContentMode,
    val fileName: String,
    val fileExtension: String,
    val mimeType: String,
    val content: String,
    val supportedCount: Int,
    val skippedCount: Int,
    val skippedReasons: List<SkippedReasonCount>,
    val warnings: List<String>,
    val remoteSourceCount: Int,
    val hasExportableProxies: Boolean,
    val anywhereAJSON: String? = null,
    val directImportURL: String? = null,
    val generatedAtEpochMs: Long,
)

@Serializable
enum class DetectedSourceKind {
    SUBSCRIPTION_URL,
    PROXY_NODES,
    CLASH_YAML,
    SURGE_CONFIG,
    RULE_SCHEME,
    UNKNOWN,
}

data class SourceDetectionResult(
    val kind: DetectedSourceKind,
    val summary: String,
    val detectedNodeCount: Int = 0,
    val normalizedURL: String? = null,
)

data class RuleGroupPresentation(
    val sourceName: String,
    val displayName: String,
    val emoji: String?,
    val iconURL: String?,
    val kind: RuleSchemeGroupKind,
    val members: List<RuleSchemeGroupMember>,
    val isProtected: Boolean,
    val isEnabled: Boolean,
    val isNodeFilterGroup: Boolean,
    val ruleCount: Int,
    val supplementRules: List<String>,
    val matchedNodeCount: Int,
    val editorMode: String, // "routingTargets" | "nodePatternsOnly"
)

data class RuleSchemeSummary(
    val id: String,
    val name: String,
    val summary: String,
    val isBundled: Boolean,
    val sourceURLString: String? = null,
    val updatedAtEpochMs: Long? = null,
    val groupCount: Int,
    val rulesetCount: Int,
    val emojiEnabled: Boolean,
    val emojiAutofill: Boolean,
)

data class AppStats(
    val subscriptionCount: Int = 0,
    val enabledSubscriptionCount: Int = 0,
    val totalNodeCount: Int = 0,
    val activeNodeCount: Int = 0,
    val countryCount: Int = 0,
)

data class AppState(
    val snapshot: AppSnapshot = AppSnapshot(),
    val runtime: Map<String, NodeRuntimeInfo> = emptyMap(),
    val bundledSchemes: List<RuleSchemeSummary> = emptyList(),
    val importedSchemes: List<RuleSchemeSummary> = emptyList(),
    val activeSchemeGroups: List<RuleGroupPresentation> = emptyList(),
    val activeSchemeRoutingTargets: List<String> = emptyList(),
    val activeSchemeAdditionalTargets: Map<String, List<String>> = emptyMap(),
    val lanStatus: LanSharingStatus = LanSharingStatus(),
    val stats: AppStats = AppStats(),
    val isLoading: Boolean = false,
    val ipDbVersion: String = "bundled",
)

data class ReorderGroupsResult(
    val activeSchemeGroups: List<RuleGroupPresentation>,
    val customization: RuleSchemeCustomization,
    val previousCustomization: RuleSchemeCustomization?,
    val conflicts: List<MatchingOrderConflict>,
)

data class SubscriptionMutationResult(
    val subscription: SubscriptionSource,
    val addedNodeCount: Int,
    val skippedCount: Int,
    val skippedReasons: List<SkippedReasonCount>,
)

data class NodeImportResult(
    val addedNodes: List<ProxyNode>,
    val skippedCount: Int,
    val skippedReasons: List<SkippedReasonCount>,
)

data class QrMatrix(
    val size: Int,
    val modules: BooleanArray, // row-major size * size; true = dark
) {
    fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return size == other.size && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}

data class NodeSharePayload(
    val uri: String,
    val qrMatrix: QrMatrix?,
)
