package com.jzb.tower.android.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.PendingConflictNotice
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.AppTab
import com.jzb.tower.core.model.ConflictDirection
import com.jzb.tower.core.model.RuleGroupPresentation
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

    var selectedGroupForDetail by remember { mutableStateOf<String?>(null) }
    var showImportScheme by remember { mutableStateOf(false) }
    var showCatalogSheet by remember { mutableStateOf(false) }
    var showLocalRuleSetsSheet by remember { mutableStateOf(false) }
    var showCreateGroupSheet by remember { mutableStateOf(false) }
    var pendingDeleteScheme by remember { mutableStateOf<RuleSchemeSummary?>(null) }

    // Drag reorder tracking for the unified group list
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

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
        item(key = "rules_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "分流规则",
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "选择规则方案并调整策略组匹配优先级",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                TintedActionButton(
                    text = "导入方案",
                    icon = Icons.Rounded.Link,
                    onClick = { showImportScheme = true },
                    modifier = Modifier.height(40.dp),
                )
            }
        }

        // Scheme selector cards
        item(key = "schemes_heading") {
            SectionHeading(
                title = "规则方案",
                detail = "${allSchemes.size} 套可用",
            )
        }

        item(key = "schemes_strip") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                allSchemes.forEach { scheme ->
                    val isSelected = scheme.id == activeSchemeId
                    SchemeSelectorCard(
                        scheme = scheme,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectRuleScheme(scheme.id) },
                        onRefresh = if (!scheme.isBundled && !scheme.sourceURLString.isNullOrBlank()) {
                            { viewModel.refreshRuleScheme(scheme.id) }
                        } else null,
                        onDelete = if (!scheme.isBundled) {
                            { pendingDeleteScheme = scheme }
                        } else null,
                    )
                }
            }
        }

        // Active scheme emoji & quick actions bar
        if (activeSchemeSummary != null) {
            item(key = "scheme_controls") {
                TowerCard(contentPadding = PaddingValues(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "策略组 Emoji 图标",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary,
                                )
                                Text(
                                    text = "控制面板与导出配置中的前缀 Emoji",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                            Switch(
                                checked = activeSchemeSummary.emojiEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setSchemeEmojiSettings(
                                        schemeId = activeSchemeSummary.id,
                                        emojisEnabled = enabled,
                                    )
                                },
                            )
                        }

                        if (!activeSchemeSummary.isBundled && activeSchemeSummary.emojiEnabled) {
                            HorizontalDivider(color = palette.divider)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = "自动补全 Emoji",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.textPrimary,
                                    )
                                    Text(
                                        text = "为导入方案中缺省 Emoji 的分组识别图标",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary,
                                    )
                                }
                                Switch(
                                    checked = activeSchemeSummary.emojiAutofill,
                                    onCheckedChange = { autofill ->
                                        viewModel.setSchemeEmojiSettings(
                                            schemeId = activeSchemeSummary.id,
                                            emojiAutofill = autofill,
                                        )
                                    },
                                )
                            }
                        }

                        HorizontalDivider(color = palette.divider)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TintedActionButton(
                                text = "规则库",
                                icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                                onClick = { showCatalogSheet = true },
                                modifier = Modifier.weight(1f).height(42.dp),
                            )
                            TintedActionButton(
                                text = "本地规则集",
                                icon = Icons.Rounded.FolderSpecial,
                                onClick = { showLocalRuleSetsSheet = true },
                                modifier = Modifier.weight(1f).height(42.dp),
                            )
                            TintedActionButton(
                                text = "新建分组",
                                icon = Icons.Rounded.Add,
                                onClick = { showCreateGroupSheet = true },
                                modifier = Modifier.weight(1f).height(42.dp),
                            )
                        }
                    }
                }
            }
        }

        // Attached custom rule flows if any
        if (customFlows.isNotEmpty()) {
            item(key = "custom_flows_heading") {
                SectionHeading(
                    title = "附加规则流",
                    detail = "${customFlows.size} 项",
                )
            }
            itemsIndexed(customFlows, key = { _, flow -> "flow_${flow.id}" }) { _, flow ->
                TowerCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = flow.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = "指向策略：${flow.policyName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteCustomRuleFlow(flow.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "移除附加规则",
                                tint = palette.coral,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }

        // Single Unified Policy Group List (CLAUDE.md #26)
        item(key = "groups_heading") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeading(
                    title = "策略组顺序",
                    detail = "${groups.size} 组",
                )
                Text(
                    text = "所有策略组在同一列表中排序（长按拖拽或使用上下箭头），调整顺序同时最小化移动该组规则块的匹配优先级。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }

        itemsIndexed(groups, key = { _, g -> "group_${g.sourceName}" }) { index, group ->
            val isDragging = draggingIndex == index
            UnifiedPolicyGroupRow(
                group = group,
                index = index,
                totalCount = groups.size,
                isDragging = isDragging,
                dragTranslationY = if (isDragging) dragOffsetY else 0f,
                onClick = { selectedGroupForDetail = group.sourceName },
                onToggleEnabled = { enabled ->
                    viewModel.toggleRuleGroupEnabled(activeSchemeId, group.sourceName, enabled)
                },
                onMoveUp = {
                    if (index > 0) viewModel.moveRuleGroup(activeSchemeId, index, index - 1)
                },
                onMoveDown = {
                    if (index < groups.lastIndex) viewModel.moveRuleGroup(activeSchemeId, index, index + 1)
                },
                onDragStart = {
                    draggingIndex = index
                    dragOffsetY = 0f
                },
                onDragDelta = { deltaY ->
                    dragOffsetY += deltaY
                },
                onDragEnd = {
                    val approxSteps = (dragOffsetY / 170f).toInt()
                    val targetIdx = (index + approxSteps).coerceIn(0, groups.lastIndex)
                    draggingIndex = -1
                    dragOffsetY = 0f
                    if (targetIdx != index) {
                        viewModel.moveRuleGroup(activeSchemeId, index, targetIdx)
                    }
                },
            )
        }

        item(key = "continue_to_export") {
            TintedActionButton(
                text = "继续前往导出",
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = { viewModel.selectTab(AppTab.EXPORT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
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

    if (showCatalogSheet) {
        RuleCatalogSheet(
            schemeId = activeSchemeId,
            availablePolicies = groups.map { it.sourceName } + listOf("DIRECT", "REJECT"),
            onSearch = viewModel::searchRuleCatalog,
            onAttachEntry = { entry, policyName ->
                viewModel.addCatalogRuleFlow(activeSchemeId, entry, policyName)
            },
            onDismiss = { showCatalogSheet = false },
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
private fun SchemeSelectorCard(
    scheme: RuleSchemeSummary,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRefresh: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val palette = TowerTheme.palette
    TowerCard(
        cornerRadius = 18.dp,
        borderColor = if (isSelected) palette.primary else palette.cardBorder,
        containerColor = if (isSelected) palette.primaryContainer else palette.cardSurface,
        onClick = onSelect,
        modifier = Modifier.width(230.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = scheme.name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "已选中",
                    tint = palette.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = scheme.summary.ifBlank { if (scheme.isBundled) "内置离线规则方案" else "用户导入规则方案" },
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${scheme.groupCount} 组 · ${scheme.rulesetCount} 条规则",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新方案",
                            tint = palette.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "删除方案",
                            tint = palette.coral,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedPolicyGroupRow(
    group: RuleGroupPresentation,
    index: Int,
    totalCount: Int,
    isDragging: Boolean,
    dragTranslationY: Float,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val palette = TowerTheme.palette
    TowerCard(
        cornerRadius = 18.dp,
        borderColor = if (isDragging) palette.primary else palette.cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationY = dragTranslationY
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "长按拖动排序",
                    tint = if (isDragging) palette.primary else palette.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (!group.emoji.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.cardSubtleSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = group.emoji!!, fontSize = 18.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (group.isEnabled) palette.textPrimary else palette.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(FormatUtils.ruleGroupKindLabel(group.kind))
                        if (group.ruleCount > 0) append(" · ${group.ruleCount} 规则")
                        if (group.matchedNodeCount > 0) append(" · ${group.matchedNodeCount} 节点")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Accessible up/down reorder buttons alongside drag
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "上移",
                    tint = if (index > 0) palette.textSecondary else palette.textTertiary.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < totalCount - 1,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = "下移",
                    tint = if (index < totalCount - 1) palette.textSecondary else palette.textTertiary.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp),
                )
            }

            if (!group.isProtected) {
                Switch(
                    checked = group.isEnabled,
                    onCheckedChange = onToggleEnabled,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "详情",
                    tint = palette.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
