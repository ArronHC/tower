package com.jzb.tower.android.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.AndroidPlatformActions
import com.jzb.tower.android.ui.components.ClientIcon
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.ToastTone
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.ClientTarget
import com.jzb.tower.core.model.ExportContentMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    viewModel: TowerViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val generatedConfig by viewModel.generatedConfig.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (generatedConfig == null) {
            viewModel.refreshGeneratedConfig()
        }
    }

    val snapshot = state.snapshot
    val visibleClients = remember(snapshot.clientOrder, snapshot.visibleClientTargets) {
        val visibleSet = snapshot.visibleClientTargets.toSet()
        val ordered = snapshot.clientOrder.filter { visibleSet.contains(it) }
        ordered.ifEmpty { listOf(snapshot.selectedTarget) }
    }
    val selectedTarget = snapshot.selectedTarget
    val meta = remember(selectedTarget) { ClientCatalog.meta(selectedTarget) }

    val savedMode = snapshot.exportContentModes[selectedTarget.rawValue]
    val contentMode = remember(selectedTarget, savedMode, meta) {
        when {
            savedMode == ExportContentMode.NODES_ONLY && meta.supportsNodesOnly -> ExportContentMode.NODES_ONLY
            savedMode == ExportContentMode.FULL_CONFIGURATION && meta.supportsFullConfiguration -> ExportContentMode.FULL_CONFIGURATION
            meta.supportsFullConfiguration -> ExportContentMode.FULL_CONFIGURATION
            else -> ExportContentMode.NODES_ONLY
        }
    }

    val supportsDirectImport = when (contentMode) {
        ExportContentMode.FULL_CONFIGURATION -> meta.supportsDirectConfigurationImport
        ExportContentMode.NODES_ONLY -> meta.supportsNodesOnlyImport
    }

    val excludedKinds = snapshot.excludedKinds[selectedTarget.rawValue].orEmpty().toSet()

    var showSkippedDetails by remember { mutableStateOf(false) }
    var showViewerSheet by remember { mutableStateOf(false) }
    var showClientFilterSheet by remember { mutableStateOf(false) }
    var showLanSheet by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val config = generatedConfig

    // Partition warnings so intercept/REJECT warnings sit prominently at top (CLAUDE.md #25)
    val sortedWarnings = remember(config?.warnings) {
        val list = config?.warnings.orEmpty()
        val (interceptWarnings, normalWarnings) = list.partition { w ->
            w.contains("拦截") || w.contains("REJECT", ignoreCase = true) || w.contains("不会拦截")
        }
        interceptWarnings to normalWarnings
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TowerDimens.PagePadding,
            end = TowerDimens.PagePadding,
            top = 8.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "export_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "生成与导出",
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "本机生成配置，一键导入或局域网共享",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                Surface(
                    onClick = { showLanSheet = true },
                    shape = CircleShape,
                    color = if (state.lanStatus.enabled) palette.emeraldSurface else palette.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiTethering,
                            contentDescription = null,
                            tint = if (state.lanStatus.enabled) palette.emerald else palette.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (state.lanStatus.enabled) "局域网共享中" else "局域网共享",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.lanStatus.enabled) palette.emerald else palette.primary,
                        )
                    }
                }
            }
        }

        // Client Picker header + horizontal strip
        item(key = "client_picker") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(
                    title = "目标客户端",
                    trailing = {
                        IconButton(
                            onClick = { showClientFilterSheet = true },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "客户端显示与排序",
                                tint = palette.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    visibleClients.forEach { target ->
                        val targetMeta = ClientCatalog.meta(target)
                        val selected = target == selectedTarget
                        val brandColor = Color(targetMeta.brandColorHex)

                        Surface(
                            onClick = { viewModel.selectClientTarget(target) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) brandColor.copy(alpha = 0.12f) else palette.cardSurface,
                            border = BorderStroke(
                                width = if (selected) 1.75.dp else 0.75.dp,
                                color = if (selected) brandColor else palette.cardBorder,
                            ),
                            modifier = Modifier.width(86.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                ClientIcon(
                                    iconFile = targetMeta.iconFile,
                                    name = targetMeta.name,
                                    brandColorHex = targetMeta.brandColorHex,
                                    size = 46.dp,
                                )
                                Text(
                                    text = targetMeta.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode segmented control & Compatibility summary card
        item(key = "export_summary_card") {
            TowerCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = meta.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = meta.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        if (isGenerating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = palette.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else if (config?.hasExportableProxies == true) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "就绪",
                                tint = palette.emerald,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "无可用节点",
                                tint = palette.amber,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    if (meta.supportsFullConfiguration && meta.supportsNodesOnly) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = contentMode == ExportContentMode.FULL_CONFIGURATION,
                                onClick = {
                                    viewModel.setExportContentMode(selectedTarget, ExportContentMode.FULL_CONFIGURATION)
                                },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("完整配置") }
                            SegmentedButton(
                                selected = contentMode == ExportContentMode.NODES_ONLY,
                                onClick = {
                                    viewModel.setExportContentMode(selectedTarget, ExportContentMode.NODES_ONLY)
                                },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("仅节点") }
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    if (config != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Column {
                                Text(
                                    text = "${config.supportedCount}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                                    color = palette.emerald,
                                )
                                Text(
                                    text = "兼容节点",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                            Column {
                                Text(
                                    text = "${config.skippedCount}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                                    color = if (config.skippedCount > 0) palette.amber else palette.textSecondary,
                                )
                                Text(
                                    text = "已跳过",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                            if (config.remoteSourceCount > 0) {
                                Column {
                                    Text(
                                        text = "${config.remoteSourceCount}",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                                        color = palette.primary,
                                    )
                                    Text(
                                        text = "代理集合",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary,
                                    )
                                }
                            }
                        }

                        if (!config.hasExportableProxies) {
                            Text(
                                text = "当前没有可导出的兼容节点，请先添加/启用订阅或调整协议筛选。",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.amber,
                            )
                        }

                        // Highlighted intercept/REJECT warnings at top
                        val (interceptWarnings, normalWarnings) = sortedWarnings
                        if (interceptWarnings.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = palette.amberSurface,
                                border = BorderStroke(0.75.dp, palette.amber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = palette.amber,
                                            modifier = Modifier.size(17.dp),
                                        )
                                        Text(
                                            text = "拦截与规则兼容性提醒",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = palette.amber,
                                        )
                                    }
                                    interceptWarnings.forEach { w ->
                                        Text(
                                            text = "• $w",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = palette.textPrimary,
                                        )
                                    }
                                }
                            }
                        }

                        if (normalWarnings.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                normalWarnings.forEach { w ->
                                    Text(
                                        text = "提示：$w",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary,
                                    )
                                }
                            }
                        }

                        if (config.skippedReasons.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showSkippedDetails = !showSkippedDetails }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "查看跳过原因（${config.skippedReasons.size} 类）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.primary,
                                )
                                Icon(
                                    imageVector = if (showSkippedDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = palette.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (showSkippedDetails) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.cardSubtleSurface)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    config.skippedReasons.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = item.reason,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = palette.textPrimary,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "${item.count} 个",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.textSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Protocol filter chips for this client
                    HorizontalDivider(color = palette.divider)
                    Text(
                        text = "协议包含筛选（仅作用于 ${meta.name}）",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ClientCatalog.ALL_PROXY_KINDS
                            .filter { meta.supportedKinds.contains(it) }
                            .forEach { kind ->
                                val isIncluded = !excludedKinds.contains(kind)
                                FilterChip(
                                    selected = isIncluded,
                                    onClick = { viewModel.toggleExcludedKind(selectedTarget, kind) },
                                    label = { Text(FormatUtils.proxyKindLabel(kind)) },
                                )
                            }
                    }
                }
            }
        }

        // Primary & Secondary Export Action Buttons
        item(key = "export_actions") {
            val hasProxies = config?.hasExportableProxies == true
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // CLAUDE.md #4: Never fake one-click import for Quantumult X in full-config mode
                if (supportsDirectImport) {
                    PrimaryActionButton(
                        text = if (isImporting) "正在准备本地导入…" else "一键导入 ${meta.name}",
                        icon = Icons.Rounded.Bolt,
                        enabled = hasProxies && !isImporting,
                        onClick = {
                            scope.launch {
                                isImporting = true
                                val res = viewModel.prepareDirectImport(selectedTarget, contentMode)
                                isImporting = false
                                res.onSuccess { handle ->
                                    val launched = AndroidPlatformActions.launchClientSchemeUrls(
                                        context = context,
                                        schemeUrls = handle.schemeUrls,
                                    )
                                    if (launched) {
                                        viewModel.showToast("已交给 ${meta.name} 导入（临时链接 3 分钟有效）", ToastTone.SUCCESS)
                                    } else {
                                        AndroidPlatformActions.copyToClipboard(context, meta.name, handle.localUrl)
                                        viewModel.showToast(
                                            "未检测到 ${meta.name} 客户端，已复制本机临时订阅链接，也可改用分享文件",
                                            ToastTone.WARNING,
                                        )
                                    }
                                }.onFailure { err ->
                                    viewModel.showToast("一键导入准备失败：${err.message}", ToastTone.ERROR)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryActionButton(
                        text = "分享配置文件",
                        icon = Icons.Rounded.Share,
                        enabled = hasProxies,
                        onClick = {
                            scope.launch {
                                val unmasked = viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                                    .getOrNull() ?: config ?: return@launch
                                AndroidPlatformActions.shareConfigurationFile(context, unmasked)
                                    .onFailure { viewModel.showToast("分享失败：${it.message}", ToastTone.ERROR) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TintedActionButton(
                        text = "预览与编辑配置",
                        icon = Icons.Rounded.Code,
                        enabled = config != null,
                        onClick = { showViewerSheet = true },
                        modifier = Modifier.weight(1f),
                    )
                    if (supportsDirectImport) {
                        TintedActionButton(
                            text = "分享文件",
                            icon = Icons.Rounded.Share,
                            enabled = hasProxies,
                            onClick = {
                                scope.launch {
                                    val unmasked = viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                                        .getOrNull() ?: config ?: return@launch
                                    AndroidPlatformActions.shareConfigurationFile(context, unmasked)
                                        .onFailure { viewModel.showToast("分享失败：${it.message}", ToastTone.ERROR) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        TintedActionButton(
                            text = "复制配置",
                            icon = Icons.Rounded.ContentCopy,
                            enabled = hasProxies,
                            onClick = {
                                scope.launch {
                                    val unmasked = viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                                        .getOrNull() ?: config ?: return@launch
                                    AndroidPlatformActions.copyToClipboard(context, unmasked.fileName, unmasked.content)
                                    viewModel.showToast("已复制 ${meta.name} 配置", ToastTone.SUCCESS)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (supportsDirectImport) {
                    TintedActionButton(
                        text = "复制配置",
                        icon = Icons.Rounded.ContentCopy,
                        enabled = hasProxies,
                        onClick = {
                            scope.launch {
                                val unmasked = viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                                    .getOrNull() ?: config ?: return@launch
                                AndroidPlatformActions.copyToClipboard(context, unmasked.fileName, unmasked.content)
                                viewModel.showToast("已复制 ${meta.name} 配置", ToastTone.SUCCESS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                TintedActionButton(
                    text = "局域网共享订阅",
                    icon = Icons.Rounded.WifiTethering,
                    onClick = { showLanSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Privacy / client note footer
        item(key = "export_privacy_note") {
            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = palette.emerald,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = when {
                            selectedTarget == ClientTarget.QUANX && contentMode == ExportContentMode.FULL_CONFIGURATION ->
                                "Quantumult X 官方 URL Scheme 不支持本地完整配置（含策略组）导入，因此仅提供系统文件分享与复制，不会伪装为一键导入。"
                            supportsDirectImport ->
                                "一键导入会在本机 127.0.0.1 开启仅存活 3 分钟的临时 HTTP 服务供 ${meta.name} 拉取，不上传任何数据。"
                            else ->
                                "所有配置均在设备本地生成，可直接分享文件或复制配置文本。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
        }
    }

    if (showViewerSheet && config != null) {
        ConfigurationViewerSheet(
            configuration = config,
            onCopyUnmasked = { editedText ->
                scope.launch {
                    val textToCopy = editedText ?: viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                        .getOrNull()?.content ?: config.content
                    AndroidPlatformActions.copyToClipboard(context, config.fileName, textToCopy)
                    viewModel.showToast("已复制配置", ToastTone.SUCCESS)
                }
            },
            onShareUnmasked = { editedText ->
                scope.launch {
                    val unmasked = viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                        .getOrNull() ?: config
                    AndroidPlatformActions.shareConfigurationFile(
                        context = context,
                        configuration = unmasked,
                        contentOverride = editedText,
                    ).onFailure { viewModel.showToast("分享失败：${it.message}", ToastTone.ERROR) }
                }
            },
            onDismiss = { showViewerSheet = false },
        )
    }

    if (showClientFilterSheet) {
        ClientFilterSheet(
            clientOrder = snapshot.clientOrder,
            visibleTargets = snapshot.visibleClientTargets,
            onSave = { order, visible ->
                viewModel.updateClientOrderAndVisibility(order, visible)
            },
            onDismiss = { showClientFilterSheet = false },
        )
    }

    if (showLanSheet) {
        LanSharingSheet(
            lanStatus = state.lanStatus,
            onToggleSharing = viewModel::toggleLanSharing,
            onRotateToken = viewModel::rotateLanToken,
            buildUrl = viewModel::buildLanSubscriptionUrl,
            generateQr = viewModel::generateQrMatrix,
            onCopiedToast = { msg -> viewModel.showToast(msg, ToastTone.SUCCESS) },
            onDismiss = { showLanSheet = false },
        )
    }
}
