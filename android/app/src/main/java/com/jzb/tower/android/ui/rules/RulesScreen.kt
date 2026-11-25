package com.jzb.tower.android.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.PendingConflictNotice
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerSelectionCheckmark
import com.jzb.tower.android.ui.components.TowerSquircleIconBadge
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.AppTab
import com.jzb.tower.core.model.ConflictDirection
import com.jzb.tower.core.model.RuleSchemeSummary

@Composable
fun RulesScreen(
    viewModel: TowerViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingConflicts by viewModel.pendingConflicts.collectAsStateWithLifecycle()

    val snapshot = state.snapshot
    val allSchemes = remember(state.bundledSchemes, state.importedSchemes) {
        state.bundledSchemes + state.importedSchemes
    }
    val activeSchemeId = snapshot.selectedPresetID
    val activeSchemeSummary = remember(allSchemes, activeSchemeId) {
        allSchemes.firstOrNull { it.id == activeSchemeId } ?: allSchemes.firstOrNull()
    }
    val groups = state.activeSchemeGroups
    val customFlows = remember(snapshot.customRuleFlows, activeSchemeId) {
        snapshot.customRuleFlows.filter { it.schemeID == activeSchemeId && it.isGroupSupplement != true }
    }

    var customizingSchemeId by remember { mutableStateOf<String?>(null) }
    var selectedGroupForDetail by remember { mutableStateOf<String?>(null) }
    var showImportScheme by remember { mutableStateOf(false) }
    var catalogInitialQuery by remember { mutableStateOf<String?>(null) }
    var showLocalRuleSetsSheet by remember { mutableStateOf(false) }
    var showCreateGroupSheet by remember { mutableStateOf(false) }
    var pendingDeleteScheme by remember { mutableStateOf<RuleSchemeSummary?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = TowerDimens.PagePadding,
            end = TowerDimens.PagePadding,
            top = 8.dp,
            bottom = TowerDimens.FloatingNavClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top floating chrome matching iphone-1/2/3.jpg
        item(key = "rules_header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TowerFloatingPillButton(
                        text = "导入规则",
                        onClick = { showImportScheme = true },
                    )
                }

                Text(
                    text = "分流规则",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.textPrimary,
                )
            }
        }

        // Bundled Schemes ("本机规则 · 安装后离线可用")
        item(key = "bundled_schemes_section") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(
                    title = "本机规则",
                    detail = "安装后离线可用",
                )
                TowerCard(modifier = Modifier.fillMaxWidth()) {
                    state.bundledSchemes.forEachIndexed { idx, scheme ->
                        val isSelected = scheme.id == activeSchemeId
                        SchemeRow(
                            scheme = scheme,
                            isSelected = isSelected,
                            onRowClick = {
                                if (!isSelected) viewModel.selectRuleScheme(scheme.id)
                                customizingSchemeId = scheme.id
                            },
                            onToggleSelect = { viewModel.selectRuleScheme(scheme.id) },
                            onRefresh = null,
                            onDelete = null,
                        )
                        if (idx < state.bundledSchemes.lastIndex) {
                            HorizontalDivider(
                                color = palette.divider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Imported Schemes ("已导入")
        if (state.importedSchemes.isNotEmpty()) {
            item(key = "imported_schemes_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading(
                        title = "已导入",
                        detail = "${state.importedSchemes.size} 套",
                    )
                    TowerCard(modifier = Modifier.fillMaxWidth()) {
                        state.importedSchemes.forEachIndexed { idx, scheme ->
                            val isSelected = scheme.id == activeSchemeId
                            SchemeRow(
                                scheme = scheme,
                                isSelected = isSelected,
                                onRowClick = {
                                    if (!isSelected) viewModel.selectRuleScheme(scheme.id)
                                    customizingSchemeId = scheme.id
                                },
                                onToggleSelect = { viewModel.selectRuleScheme(scheme.id) },
                                onRefresh = if (!scheme.sourceURLString.isNullOrBlank()) {
                                    { viewModel.refreshRuleScheme(scheme.id) }
                                } else null,
                                onDelete = { pendingDeleteScheme = scheme },
                            )
                            if (idx < state.importedSchemes.lastIndex) {
                                HorizontalDivider(
                                    color = palette.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Scheme Quick Customization Entry + Tools
        if (activeSchemeSummary != null) {
            item(key = "active_customization_card") {
                TowerCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { customizingSchemeId = activeSchemeSummary.id },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TowerSquircleIconBadge(
                            icon = Icons.Rounded.Tune,
                            backgroundColor = palette.primary,
                            size = 34.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "查看与定制策略组",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = "${activeSchemeSummary.name} · 共 ${groups.size} 组，支持拖拽调整优先级",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 62.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { catalogInitialQuery = "" },
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TowerSquircleIconBadge(
                            icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                            backgroundColor = palette.indigo,
                            size = 34.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "在线规则库",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = "搜索 ACL4SSR 与 blackmatrix7 服务规则一键挂载",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 62.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showLocalRuleSetsSheet = true },
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TowerSquircleIconBadge(
                            icon = Icons.Rounded.FolderSpecial,
                            backgroundColor = palette.teal,
                            size = 34.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "我的规则集",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = "${snapshot.localRuleSets.size} 个本地规则集 · 挂载或编辑自定义规则",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        item(key = "continue_to_export") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrivacyBadge()
                TintedActionButton(
                    text = "继续前往导出",
                    trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = { viewModel.selectTab(AppTab.EXPORT) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Full-screen RuleCustomizationSheet (iphone-2.jpg)
    val customizingScheme = customizingSchemeId?.let { id -> allSchemes.firstOrNull { it.id == id } }
    if (customizingScheme != null) {
        RuleCustomizationSheet(
            scheme = customizingScheme,
            isActiveScheme = customizingScheme.id == activeSchemeId,
            activeSchemeName = activeSchemeSummary?.name ?: "",
            groups = groups,
            customFlows = customFlows,
            localRuleSets = snapshot.localRuleSets,
            onSelectThisScheme = { viewModel.selectRuleScheme(customizingScheme.id) },
            onMoveRuleGroup = { from, to ->
                viewModel.moveRuleGroup(customizingScheme.id, from, to)
            },
            onGroupClick = { group -> selectedGroupForDetail = group.sourceName },
            onToggleEmojiEnabled = { enabled ->
                viewModel.setSchemeEmojiSettings(customizingScheme.id, emojisEnabled = enabled)
            },
            onToggleEmojiAutofill = { autofill ->
                viewModel.setSchemeEmojiSettings(customizingScheme.id, emojiAutofill = autofill)
            },
            onOpenCatalog = { initialQ -> catalogInitialQuery = initialQ },
            onOpenLocalRuleSets = { showLocalRuleSetsSheet = true },
            onOpenCreateGroup = { showCreateGroupSheet = true },
            onDeleteCustomFlow = { id -> viewModel.deleteCustomRuleFlow(id) },
            onDismiss = { customizingSchemeId = null },
        )
    }

    // Detail sheet
    val detailGroup = selectedGroupForDetail?.let { name ->
        groups.firstOrNull { it.sourceName == name }
    }
    if (detailGroup != null) {
        RuleGroupDetailSheet(
            schemeId = activeSchemeId,
            group = detailGroup,
            routingTargets = state.activeSchemeRoutingTargets,
            additionalTargets = state.activeSchemeAdditionalTargets[detailGroup.sourceName].orEmpty(),
            onDismiss = { selectedGroupForDetail = null },
            onSave = { newName, kind, members, supplements ->
                viewModel.updateRuleGroup(
                    schemeId = activeSchemeId,
                    sourceName = detailGroup.sourceName,
                    newName = newName,
                    kind = kind,
                    members = members,
                    supplementRules = supplements,
                )
            },
            onDelete = {
                viewModel.deleteRuleGroup(activeSchemeId, detailGroup.sourceName)
                selectedGroupForDetail = null
            },
        )
    }

    if (showImportScheme) {
        ImportSchemeSheet(
            onImport = { name, url, content ->
                viewModel.importRuleScheme(name, url, content)
            },
            onDismiss = { showImportScheme = false },
        )
    }

    catalogInitialQuery?.let { initialQuery ->
        RuleCatalogSheet(
            schemeId = activeSchemeId,
            initialQuery = initialQuery,
            availablePolicies = groups.map { it.sourceName } + listOf("DIRECT", "REJECT"),
            onSearch = viewModel::searchRuleCatalog,
            onAttachEntry = { entry, policyName ->
                viewModel.addCatalogRuleFlow(activeSchemeId, entry, policyName)
            },
            onDismiss = { catalogInitialQuery = null },
        )
    }

    if (showLocalRuleSetsSheet) {
        LocalRuleSetsSheet(
            ruleSets = snapshot.localRuleSets,
            availablePolicies = groups.map { it.sourceName } + listOf("DIRECT", "REJECT"),
            onCreate = viewModel::createLocalRuleSet,
            onUpdate = viewModel::updateLocalRuleSet,
            onDelete = viewModel::deleteLocalRuleSet,
            onAttach = { ruleSet, policyName ->
                viewModel.attachLocalRuleSetToScheme(activeSchemeId, ruleSet, policyName)
            },
            onDismiss = { showLocalRuleSetsSheet = false },
        )
    }

    if (showCreateGroupSheet) {
        CreateGroupSheet(
            availableTargets = state.activeSchemeRoutingTargets,
            onCreate = { name, kind, members, isNodeFilter ->
                viewModel.createRuleGroup(activeSchemeId, name, kind, members, isNodeFilter)
            },
            onDismiss = { showCreateGroupSheet = false },
        )
    }

    if (pendingDeleteScheme != null) {
        val target = pendingDeleteScheme!!
        AlertDialog(
            onDismissRequest = { pendingDeleteScheme = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("这个规则方案和它下载的规则列表会从这台设备移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRuleScheme(target.id)
                        pendingDeleteScheme = null
                    },
                ) {
                    Text("删除", color = palette.coral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteScheme = null }) {
                    Text("取消")
                }
            },
        )
    }

    // CLAUDE.md #26: Conflict AlertDialog with one-tap Undo restoring previousCustomization
    pendingConflicts?.let { notice ->
        MatchingConflictAlertDialog(
            notice = notice,
            onUndo = { viewModel.undoPendingReorder() },
            onKeep = { viewModel.dismissPendingConflicts() },
        )
    }
}

@Composable
private fun SchemeRow(
    scheme: RuleSchemeSummary,
    isSelected: Boolean,
    onRowClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onRefresh: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val palette = TowerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRowClick,
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = scheme.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = scheme.summary.ifBlank { if (scheme.isBundled) "内置离线规则方案" else "用户导入规则方案" },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${scheme.groupCount} 组 · ${scheme.rulesetCount} 条规则",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
                Text(
                    text = "· 定制",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.primary,
                )
            }
        }

        if (onRefresh != null) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新方案",
                    tint = palette.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除方案",
                    tint = palette.coral,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        TowerSelectionCheckmark(
            selected = isSelected,
            onToggle = onToggleSelect,
            contentDescription = if (isSelected) "当前方案" else "选用此方案",
        )
    }
}

@Composable
private fun MatchingConflictAlertDialog(
    notice: PendingConflictNotice,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
) {
    val palette = TowerTheme.palette
    val message = remember(notice) {
        val lines = notice.conflicts.take(4).map { conflict ->
            val examples = conflict.sampleDomains.take(2).joinToString("、")
            val desc = when (conflict.direction) {
                ConflictDirection.SHADOWED_BY_MOVED ->
                    "「${conflict.otherGroup}」部分域名将改走「${conflict.movedGroup}」"
                ConflictDirection.NOW_SHADOWED_BY_OTHER ->
                    "「${conflict.movedGroup}」部分域名将改走「${conflict.otherGroup}」"
            }
            if (examples.isNotEmpty()) "$desc（如 $examples）" else desc
        }.toMutableList()
        if (notice.conflicts.size > 4) {
            lines.add("…另有 ${notice.conflicts.size - 4} 项变动")
        }
        lines.add("")
        lines.add("规则按从上到下的顺序命中，同一域名只会匹配排在最前面的策略组。")
        lines.joinToString("\n")
    }

    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("匹配结果会改变") },
        text = { Text(message, style = MaterialTheme.typography.bodySmall) },
        confirmButton = {
            TextButton(onClick = onUndo) {
                Text("撤销", color = palette.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text("保留新顺序")
            }
        },
    )
}
