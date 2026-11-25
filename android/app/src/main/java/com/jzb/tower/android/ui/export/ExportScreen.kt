package com.jzb.tower.android.ui.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.AndroidPlatformActions
import com.jzb.tower.android.ui.components.ClientIcon
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.ToastTone
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerIosSwitch
import com.jzb.tower.android.ui.components.TowerSegmentedControl
import com.jzb.tower.android.ui.components.towerPressScale
import com.jzb.tower.android.ui.components.towerPressVisual
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.AppTab
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.ClientTarget
import com.jzb.tower.core.model.ExportContentMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ExportScreen(
    viewModel: TowerViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val generatedConfig by viewModel.generatedConfig.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isLanSelected by viewModel.isLanSharingSelected.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (generatedConfig == null) {
            viewModel.refreshGeneratedConfig()
        }
    }

    // Only show "正在转换…" if cold generation takes > 200ms (ExportView.swift parity)
    var showPendingText by remember { mutableStateOf(false) }
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            delay(200)
            showPendingText = true
        } else {
            showPendingText = false
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
    val filterableKinds = remember(meta) {
        ClientCatalog.ALL_PROXY_KINDS.filter { meta.supportedKinds.contains(it) }
    }
    val selectedKindsCount = remember(filterableKinds, excludedKinds) {
        filterableKinds.count { !excludedKinds.contains(it) }
    }
    val protocolSummary = remember(filterableKinds, excludedKinds, selectedKindsCount) {
        when {
            selectedKindsCount == 0 -> "未选择协议"
            selectedKindsCount == filterableKinds.size -> "全部协议"
            else -> "已排除 ${filterableKinds.size - selectedKindsCount} 项"
        }
    }

    val activeSchemeName = remember(state.bundledSchemes, state.importedSchemes, snapshot.selectedPresetID) {
        (state.bundledSchemes + state.importedSchemes)
            .firstOrNull { it.id == snapshot.selectedPresetID }?.name ?: "ACL4SSR 默认"
    }
    val activeLocalRuleCount = remember(state.activeSchemeGroups) {
        state.activeSchemeGroups.sumOf { it.ruleCount }
    }

    var showAdvancedOptions by remember { mutableStateOf(false) }
    var showWarningsExpanded by remember { mutableStateOf(false) }
    var showSkippedDetails by remember { mutableStateOf(false) }
    var showProtocolFilterSheet by remember { mutableStateOf(false) }
    var showViewerSheet by remember { mutableStateOf(false) }
    var showClientFilterSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
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
    val allWarnings = remember(sortedWarnings) {
        sortedWarnings.first + sortedWarnings.second
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = TowerDimens.PagePadding,
                end = TowerDimens.PagePadding,
                top = 8.dp,
                bottom = if (isLanSelected) TowerDimens.FloatingNavClearance else TowerDimens.ExportBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top floating "设置" pill + large bold title (iphone-3.jpg)
            item(key = "export_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TowerFloatingPillButton(
                            text = "设置",
                            onClick = { viewModel.openSettings() },
                        )
                    }
                    Text(
                        text = "生成与导出",
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.textPrimary,
                    )
                }
            }

            // Target Client Horizontal Bare-Icon Strip (iphone-3.jpg & Mobbin Jomo)
            item(key = "client_picker") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "目标客户端",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.textPrimary,
                        )
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .towerPressScale(onClick = { showClientFilterSheet = true })
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "客户端筛选",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = palette.primary,
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val lanIndex = if (snapshot.isLANSharingVisible) {
                            snapshot.lanSharingOrderIndex.coerceIn(0, visibleClients.size)
                        } else {
                            -1
                        }

                        visibleClients.forEachIndexed { idx, target ->
                            if (idx == lanIndex) {
                                ClientPickerLanItem(
                                    selected = isLanSelected,
                                    isActive = state.lanStatus.enabled,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.selectLanSharing(true)
                                    },
                                )
                            }
                            val targetMeta = ClientCatalog.meta(target)
                            val selected = !isLanSelected && target == selectedTarget
                            ClientPickerIconItem(
                                iconFile = targetMeta.iconFile,
                                name = targetMeta.name,
                                brandColorHex = targetMeta.brandColorHex,
                                selected = selected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.selectClientTarget(target)
                                },
                            )
                        }

                        if (lanIndex == visibleClients.size) {
                            ClientPickerLanItem(
                                selected = isLanSelected,
                                isActive = state.lanStatus.enabled,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.selectLanSharing(true)
                                },
                            )
                        }
                    }
                }
            }

            // Main Area: Inline LAN Sharing Card OR Unified Conversion Card (iphone-3.jpg)
            item(key = "export_main_card") {
                AnimatedContent(
                    targetState = isLanSelected,
                    transitionSpec = {
                        fadeIn(TowerMotion.selectionSpec()) togetherWith fadeOut(TowerMotion.selectionSpec())
                    },
                    label = "exportDestinationSwitch",
                ) { lanSelected ->
                    if (lanSelected) {
                        InlineLanSharingCard(
                            lanStatus = state.lanStatus,
                            onToggleSharing = viewModel::toggleLanSharing,
                            onRotateToken = viewModel::rotateLanToken,
                            buildUrl = viewModel::buildLanSubscriptionUrl,
                            generateQr = viewModel::generateQrMatrix,
                            onCopiedToast = { msg -> viewModel.showToast(msg, ToastTone.SUCCESS) },
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            TowerCard(contentPadding = PaddingValues(18.dp)) {
                                Column {
                                    // Status Header: "转换已就绪" + green Verified seal (dimmed to 0.3f while cold-generating)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val statusTitle = when {
                                            showPendingText -> "正在转换…"
                                            config == null -> "正在准备…"
                                            config.hasExportableProxies -> "转换已就绪"
                                            else -> "暂时无法导出"
                                        }
                                        Text(
                                            text = statusTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = palette.textPrimary,
                                        )

                                        val sealAlpha by animateFloatAsState(
                                            targetValue = if (config == null) 0f else if (showPendingText) 0.3f else 1f,
                                            animationSpec = TowerMotion.selectionSpec(),
                                            label = "sealAlpha",
                                        )
                                        val hasProxies = config?.hasExportableProxies == true
                                        Icon(
                                            imageVector = if (hasProxies) Icons.Rounded.Verified else Icons.Rounded.WarningAmber,
                                            contentDescription = if (hasProxies) "就绪" else "需要注意",
                                            tint = if (hasProxies) palette.emerald else palette.amber,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .alpha(sealAlpha),
                                        )
                                    }

                                    // Segmented Control: "完整配置" | "仅节点"
                                    if (meta.supportsFullConfiguration && meta.supportsNodesOnly) {
                                        Spacer(Modifier.height(14.dp))
                                        TowerSegmentedControl(
                                            options = listOf("完整配置", "仅节点"),
                                            selectedIndex = if (contentMode == ExportContentMode.FULL_CONFIGURATION) 0 else 1,
                                            onSelect = { idx ->
                                                val nextMode = if (idx == 0) {
                                                    ExportContentMode.FULL_CONFIGURATION
                                                } else {
                                                    ExportContentMode.NODES_ONLY
                                                }
                                                viewModel.setExportContentMode(selectedTarget, nextMode)
                                            },
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Option Rows
                                    if (contentMode == ExportContentMode.FULL_CONFIGURATION) {
                                        ExportOptionRow(
                                            icon = Icons.AutoMirrored.Rounded.ListAlt,
                                            title = "规则方案",
                                            value = activeSchemeName,
                                            onClick = { viewModel.selectTab(AppTab.RULES) },
                                        )
                                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                                    }

                                    if (filterableKinds.size > 1) {
                                        ExportOptionRow(
                                            icon = Icons.Rounded.FilterList,
                                            title = "协议筛选",
                                            value = protocolSummary,
                                            onClick = { showProtocolFilterSheet = true },
                                        )
                                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                                    }

                                    if (contentMode == ExportContentMode.FULL_CONFIGURATION) {
                                        val advChevron by animateFloatAsState(
                                            targetValue = if (showAdvancedOptions) 90f else 0f,
                                            animationSpec = TowerMotion.disclosureSpec(),
                                            label = "advChevron",
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { showAdvancedOptions = !showAdvancedOptions },
                                                )
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Tune,
                                                contentDescription = null,
                                                tint = palette.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(
                                                text = "高级选项",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                                color = palette.textPrimary,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = palette.textSecondary,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .graphicsLayer { rotationZ = advChevron },
                                            )
                                        }

                                        AnimatedVisibility(
                                            visible = showAdvancedOptions,
                                            enter = fadeIn(TowerMotion.disclosureSpec()) + expandVertically(TowerMotion.disclosureSpec()),
                                            exit = fadeOut(TowerMotion.disclosureSpec()) + shrinkVertically(TowerMotion.disclosureSpec()),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                                        Text("优先使用规则集", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                                                        Text("兼容时引用远程规则集，不兼容自动内联本地规则", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                                    }
                                                    TowerIosSwitch(
                                                        checked = snapshot.settings.preferRuleSets,
                                                        onCheckedChange = { v ->
                                                            viewModel.updateSettings { it.copy(preferRuleSets = v) }
                                                        },
                                                    )
                                                }
                                                if (meta.supportsEmbeddedRemoteSubscriptions) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                                            Text("代理集合", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                                                            Text("将原始订阅链接写入配置文件交由客户端更新", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                                        }
                                                        TowerIosSwitch(
                                                            checked = snapshot.settings.embedRemoteSubscriptionLinks,
                                                            onCheckedChange = { v ->
                                                                viewModel.updateSettings { it.copy(embedRemoteSubscriptionLinks = v) }
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                                    }

                                    // Compact Summary Line: "28 兼容节点   3,529 本地规则" (iphone-3.jpg)
                                    if (config != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 13.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "${FormatUtils.formatCount(config.supportedCount)} 兼容节点",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFeatureSettings = "tnum",
                                                ),
                                                color = palette.textSecondary,
                                            )
                                            if (contentMode == ExportContentMode.FULL_CONFIGURATION && activeLocalRuleCount > 0) {
                                                Text(
                                                    text = "${FormatUtils.formatCount(activeLocalRuleCount)} 本地规则",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFeatureSettings = "tnum",
                                                    ),
                                                    color = palette.textSecondary,
                                                )
                                            }
                                            if (config.skippedCount > 0) {
                                                Text(
                                                    text = "${config.skippedCount} 已跳过",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFeatureSettings = "tnum",
                                                    ),
                                                    color = palette.amber,
                                                )
                                            }
                                        }

                                        if (!config.hasExportableProxies) {
                                            Text(
                                                text = "当前没有可导出的兼容节点，请先启用订阅或调整协议筛选。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = palette.amber,
                                                modifier = Modifier.padding(bottom = 10.dp),
                                            )
                                        }

                                        // Compatibility Warnings (intercept warnings pinned first)
                                        if (allWarnings.isNotEmpty() || config.skippedReasons.isNotEmpty()) {
                                            val hasInterceptWarning = sortedWarnings.first.isNotEmpty()
                                            val totalNoticeCount = allWarnings.size + (if (config.skippedReasons.isNotEmpty()) 1 else 0)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { showWarningsExpanded = !showWarningsExpanded }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        text = "兼容性提示",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (hasInterceptWarning) palette.amber else palette.textSecondary,
                                                    )
                                                    Text(
                                                        text = totalNoticeCount.toString(),
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontFeatureSettings = "tnum",
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                        color = if (hasInterceptWarning) palette.amber else palette.textSecondary,
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.ChevronRight,
                                                    contentDescription = null,
                                                    tint = palette.textTertiary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }

                                            AnimatedVisibility(
                                                visible = showWarningsExpanded,
                                                enter = fadeIn(TowerMotion.disclosureSpec()) + expandVertically(TowerMotion.disclosureSpec()),
                                                exit = fadeOut(TowerMotion.disclosureSpec()) + shrinkVertically(TowerMotion.disclosureSpec()),
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    if (sortedWarnings.first.isNotEmpty()) {
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = palette.amberSurface,
                                                            border = BorderStroke(0.75.dp, palette.amber.copy(alpha = 0.45f)),
                                                            modifier = Modifier.fillMaxWidth(),
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(11.dp),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                            ) {
                                                                sortedWarnings.first.forEach { w ->
                                                                    Text(
                                                                        text = "• $w",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = palette.textPrimary,
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    sortedWarnings.second.forEach { w ->
                                                        Text(
                                                            text = "• $w",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = palette.textSecondary,
                                                        )
                                                    }
                                                    if (config.skippedReasons.isNotEmpty()) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(palette.cardSubtleSurface)
                                                                .padding(10.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                        }

                                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)

                                        // "配置预览 塔台.yaml >" (iphone-3.jpg -> opens iphone-4.jpg)
                                        ExportOptionRow(
                                            icon = Icons.Rounded.Description,
                                            title = "配置预览",
                                            value = config.fileName,
                                            onClick = { showViewerSheet = true },
                                        )
                                    }
                                }
                            }

                            // Privacy Note Footer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = palette.emerald,
                                    modifier = Modifier.size(16.dp).padding(top = 1.dp),
                                )
                                Text(
                                    text = when {
                                        selectedTarget == ClientTarget.QUANX && contentMode == ExportContentMode.FULL_CONFIGURATION ->
                                            "Quantumult X 官方 URL Scheme 不支持本地完整配置（含策略组）导入，因此保持系统文件分享与复制。"
                                        supportsDirectImport ->
                                            "塔台会通过 ${meta.name} 的 URL Scheme 打开客户端。配置只在本机 127.0.0.1 临时提供，不上传任何服务器。"
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
            }
        }

        // Floating Bottom Action Bar above the Floating 3-Tab Pill Navigation (with top gradient fade)
        AnimatedVisibility(
            visible = !isLanSelected,
            enter = fadeIn(TowerMotion.selectionSpec()),
            exit = fadeOut(TowerMotion.selectionSpec()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            val hasProxies = config?.hasExportableProxies == true
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                palette.groupedBackground.copy(alpha = 0.88f),
                                palette.groupedBackground,
                            ),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(start = TowerDimens.PagePadding, end = TowerDimens.PagePadding, top = 18.dp, bottom = 82.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // CLAUDE.md #4: Never fake one-click import for Quantumult X in full-config mode
                    if (supportsDirectImport) {
                        PrimaryActionButton(
                            text = if (isImporting) "正在准备导入…" else "一键导出到 ${meta.name}",
                            leadingContent = {
                                ClientIcon(
                                    iconFile = meta.iconFile,
                                    name = meta.name,
                                    brandColorHex = meta.brandColorHex,
                                    size = 24.dp,
                                )
                            },
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
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryActionButton(
                            text = "分享配置文件",
                            leadingContent = {
                                ClientIcon(
                                    iconFile = meta.iconFile,
                                    name = meta.name,
                                    brandColorHex = meta.brandColorHex,
                                    size = 24.dp,
                                )
                            },
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
                    }

                    // 52dp rounded square "..." overflow button (iphone-3.jpg)
                    Box {
                        val overflowInteraction = remember { MutableInteractionSource() }
                        val overflowPressed by overflowInteraction.collectIsPressedAsState()
                        Surface(
                            onClick = { showOverflowMenu = true },
                            enabled = config != null,
                            interactionSource = overflowInteraction,
                            shape = RoundedCornerShape(TowerDimens.CompactCornerRadius),
                            color = palette.cardSurface,
                            border = BorderStroke(0.75.dp, palette.cardBorder),
                            modifier = Modifier
                                .size(TowerDimens.ActionButtonHeight)
                                .towerPressVisual(overflowPressed, config != null),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = "更多导出方式",
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("分享配置文件") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                enabled = hasProxies,
                                onClick = {
                                    showOverflowMenu = false
                                    scope.launch {
                                        val unmasked = viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                                            .getOrNull() ?: config ?: return@launch
                                        AndroidPlatformActions.shareConfigurationFile(context, unmasked)
                                            .onFailure { viewModel.showToast("分享失败：${it.message}", ToastTone.ERROR) }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("复制配置文本") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                enabled = hasProxies,
                                onClick = {
                                    showOverflowMenu = false
                                    scope.launch {
                                        val unmasked = viewModel.generateUnmaskedConfiguration(selectedTarget, contentMode)
                                            .getOrNull() ?: config ?: return@launch
                                        AndroidPlatformActions.copyToClipboard(context, unmasked.fileName, unmasked.content)
                                        viewModel.showToast("已复制 ${meta.name} 配置", ToastTone.SUCCESS)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("预览与编辑") },
                                leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null) },
                                enabled = config != null,
                                onClick = {
                                    showOverflowMenu = false
                                    showViewerSheet = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProtocolFilterSheet) {
        ProtocolFilterSheet(
            meta = meta,
            excludedKinds = excludedKinds,
            onToggleKind = { kind -> viewModel.toggleExcludedKind(selectedTarget, kind) },
            onDismiss = { showProtocolFilterSheet = false },
        )
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
            isLanSharingVisible = snapshot.isLANSharingVisible,
            onSave = { order, visible, lanVisible ->
                viewModel.updateClientOrderAndVisibility(order, visible, lanVisible)
            },
            onDismiss = { showClientFilterSheet = false },
        )
    }
}

@Composable
private fun ClientPickerIconItem(
    iconFile: String,
    name: String,
    brandColorHex: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = TowerTheme.palette
    Column(
        modifier = Modifier
            .width(66.dp)
            .towerPressScale(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ClientIcon(
                iconFile = iconFile,
                name = name,
                brandColorHex = brandColorHex,
                size = 58.dp,
                withShadow = true,
            )
            ClientSelectionBadge(visible = selected)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) palette.textPrimary else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ClientPickerLanItem(
    selected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val palette = TowerTheme.palette
    val iconShape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(66.dp)
            .towerPressScale(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .then(
                        if (palette.isDark) Modifier else Modifier.shadow(
                            elevation = 6.dp,
                            shape = iconShape,
                            ambientColor = Color(0xFF0099FF).copy(alpha = 0.25f),
                            spotColor = Color(0xFF0099FF).copy(alpha = 0.25f),
                        ),
                    )
                    .clip(iconShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (isActive) {
                                listOf(Color(0xFF0FA67D), Color(0xFF2DD4BF))
                            } else {
                                listOf(Color(0xFF007AFF), Color(0xFF00C2FF))
                            },
                        ),
                    )
                    .border(0.6.dp, palette.cardBorder, iconShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiTethering,
                    contentDescription = "局域网共享",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            ClientSelectionBadge(visible = selected)
        }
        Text(
            text = "局域网共享",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) palette.textPrimary else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Animated pop-in 20dp blue circular checkmark badge with white ring matching iphone-3.jpg & Mobbin Jomo.
 */
@Composable
private fun ClientSelectionBadge(visible: Boolean) {
    val palette = TowerTheme.palette
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(TowerMotion.selectionSpec()) + scaleIn(
            initialScale = 0.6f,
            animationSpec = TowerMotion.pillSpring(),
        ),
        exit = fadeOut(TowerMotion.pressInSpec()) + scaleOut(
            targetScale = 0.6f,
            animationSpec = TowerMotion.pressInSpec(),
        ),
        modifier = Modifier.offset(x = 4.dp, y = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(21.dp)
                .clip(CircleShape)
                .background(palette.groupedBackground)
                .padding(2.dp)
                .clip(CircleShape)
                .background(palette.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已选中",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ExportOptionRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val palette = TowerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = palette.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
