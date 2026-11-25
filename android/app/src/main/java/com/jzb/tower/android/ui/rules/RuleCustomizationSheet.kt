package com.jzb.tower.android.ui.rules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerIosSwitch
import com.jzb.tower.android.ui.components.towerPressScale
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.CustomRuleFlow
import com.jzb.tower.core.model.LocalRuleSet
import com.jzb.tower.core.model.RuleGroupPresentation
import com.jzb.tower.core.model.RuleSchemeGroupMember
import com.jzb.tower.core.model.RuleSchemeSummary
import kotlin.math.roundToInt

/**
 * Full-height rule customization modal sheet matching iphone-2.jpg & Mobbin Bevel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCustomizationSheet(
    scheme: RuleSchemeSummary,
    isActiveScheme: Boolean,
    activeSchemeName: String,
    groups: List<RuleGroupPresentation>,
    customFlows: List<CustomRuleFlow>,
    localRuleSets: List<LocalRuleSet>,
    onSelectThisScheme: () -> Unit,
    onMoveRuleGroup: (fromIndex: Int, toIndex: Int) -> Unit,
    onGroupClick: (RuleGroupPresentation) -> Unit,
    onToggleEmojiEnabled: (Boolean) -> Unit,
    onToggleEmojiAutofill: (Boolean) -> Unit,
    onOpenCatalog: (initialQuery: String) -> Unit,
    onOpenLocalRuleSets: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onDeleteCustomFlow: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchQuery by remember { mutableStateOf("") }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var hoverTargetIndex by remember { mutableIntStateOf(-1) }
    var rowHeightPx by remember { mutableFloatStateOf(140f) }

    val filteredGroups = remember(groups, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) groups
        else groups.filter { g ->
            g.displayName.lowercase().contains(q) ||
                g.sourceName.lowercase().contains(q) ||
                g.members.any { m ->
                    when (m) {
                        is RuleSchemeGroupMember.Reference -> m.name.lowercase().contains(q)
                        is RuleSchemeGroupMember.NodePattern -> m.pattern.lowercase().contains(q)
                    }
                }
        }
    }
    val canDragReorder = searchQuery.isBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = palette.groupedBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top bar matching iphone-2.jpg: "规则库" pill (left) | "规则定制" (center) | "完成" pill (right)
            Box(modifier = Modifier.fillMaxWidth()) {
                TowerFloatingPillButton(
                    text = "规则库",
                    onClick = { onOpenCatalog("") },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    text = "规则定制",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = palette.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
                TowerFloatingPillButton(
                    text = "完成",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Animation-plans/002: Prompt when customizing an unselected scheme
                if (!isActiveScheme) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = palette.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "当前导出使用「$activeSchemeName」",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onSelectThisScheme,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    text = "改用此方案",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.primary,
                                )
                            }
                        }
                    }
                }

                // Capsule search bar: "🔍 搜索规则：如 YouTube OpenAI" (iphone-2.jpg)
                Surface(
                    shape = CircleShape,
                    color = palette.cardSurface,
                    border = BorderStroke(0.75.dp, palette.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "搜索规则：如 YouTube OpenAI",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textTertiary,
                                    )
                                }
                                inner()
                            },
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "清除",
                                tint = palette.textSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { searchQuery = "" },
                            )
                        }
                    }
                }

                if (searchQuery.isNotBlank()) {
                    TowerCard(
                        cornerRadius = 16.dp,
                        onClick = { onOpenCatalog(searchQuery.trim()) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "在规则库中搜索「${searchQuery.trim()}」",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Single Unified White Card of ALL Policy Groups (CLAUDE.md #26 & iphone-2.jpg)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "策略组",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.textSecondary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "${filteredGroups.size} 组",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = palette.textSecondary,
                        )
                        Text(
                            text = "+ 新建分组",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .towerPressScale(onClick = onOpenCreateGroup)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                TowerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        filteredGroups.forEachIndexed { idx, group ->
                            val isDragging = draggingIndex == idx
                            val shiftOffsetPx = if (draggingIndex in filteredGroups.indices && hoverTargetIndex in filteredGroups.indices && !isDragging) {
                                when {
                                    draggingIndex < hoverTargetIndex && idx in (draggingIndex + 1)..hoverTargetIndex -> -rowHeightPx
                                    draggingIndex > hoverTargetIndex && idx in hoverTargetIndex until draggingIndex -> rowHeightPx
                                    else -> 0f
                                }
                            } else {
                                0f
                            }
                            val animatedShift by animateFloatAsState(
                                targetValue = shiftOffsetPx,
                                animationSpec = TowerMotion.capsuleSpring(),
                                label = "rowShift_$idx",
                            )
                            val dragScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.02f else 1f,
                                animationSpec = TowerMotion.pressInSpec(),
                                label = "rowDragScale_$idx",
                            )

                            val targetLabel = remember(group) {
                                val firstRef = group.members.filterIsInstance<RuleSchemeGroupMember.Reference>()
                                    .firstOrNull()?.name
                                firstRef ?: FormatUtils.ruleGroupKindLabel(group.kind)
                            }

                            val rowBg = if (isDragging) {
                                palette.cardSurface
                            } else {
                                Color.Transparent
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 10f else 0f)
                                    .onSizeChanged { size ->
                                        if (idx == 0 && size.height > 0) {
                                            rowHeightPx = size.height.toFloat()
                                        }
                                    }
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffsetY else animatedShift
                                        scaleX = dragScale
                                        scaleY = dragScale
                                    }
                                    .then(
                                        if (isDragging && !palette.isDark) {
                                            Modifier.shadow(10.dp, RoundedCornerShape(14.dp))
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .background(rowBg)
                                    .then(
                                        if (canDragReorder) {
                                            Modifier.pointerInput(group.sourceName, filteredGroups.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        draggingIndex = idx
                                                        hoverTargetIndex = idx
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                                        val nextTarget = (idx + steps).coerceIn(0, filteredGroups.lastIndex)
                                                        if (nextTarget != hoverTargetIndex) {
                                                            hoverTargetIndex = nextTarget
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val from = draggingIndex
                                                        val to = hoverTargetIndex
                                                        draggingIndex = -1
                                                        hoverTargetIndex = -1
                                                        dragOffsetY = 0f
                                                        if (from in filteredGroups.indices && to in filteredGroups.indices && from != to) {
                                                            onMoveRuleGroup(from, to)
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggingIndex = -1
                                                        hoverTargetIndex = -1
                                                        dragOffsetY = 0f
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onGroupClick(group) },
                                        )
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (!group.emoji.isNullOrBlank()) {
                                        Text(
                                            text = group.emoji!!,
                                            fontSize = 19.sp,
                                        )
                                    }
                                    Text(
                                        text = group.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = if (group.isEnabled) palette.textPrimary else palette.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "编辑",
                                        tint = palette.primary.copy(alpha = 0.72f),
                                        modifier = Modifier.size(13.dp),
                                    )

                                    Spacer(Modifier.weight(1f))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = targetLabel,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            color = palette.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = palette.primary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }

                                    if (canDragReorder) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .pointerInput(group.sourceName, filteredGroups.size) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            draggingIndex = idx
                                                            hoverTargetIndex = idx
                                                            dragOffsetY = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY += dragAmount.y
                                                            val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                                            val nextTarget = (idx + steps).coerceIn(0, filteredGroups.lastIndex)
                                                            if (nextTarget != hoverTargetIndex) {
                                                                hoverTargetIndex = nextTarget
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val from = draggingIndex
                                                            val to = hoverTargetIndex
                                                            draggingIndex = -1
                                                            hoverTargetIndex = -1
                                                            dragOffsetY = 0f
                                                            if (from in filteredGroups.indices && to in filteredGroups.indices && from != to) {
                                                                onMoveRuleGroup(from, to)
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            draggingIndex = -1
                                                            hoverTargetIndex = -1
                                                            dragOffsetY = 0f
                                                        },
                                                    )
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragHandle,
                                                contentDescription = "拖动排序",
                                                tint = if (isDragging) palette.primary else palette.textTertiary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            if (idx < filteredGroups.lastIndex) {
                                HorizontalDivider(
                                    color = palette.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 42.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "拖动右侧 ≡ 手柄可调整策略组顺序，并同步最小化移动该组规则的匹配优先级。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                // Attached custom rule flows if any
                if (customFlows.isNotEmpty()) {
                    SectionHeading(
                        title = "已挂载规则流",
                        detail = "${customFlows.size} 项",
                    )
                    TowerCard(modifier = Modifier.fillMaxWidth()) {
                        customFlows.forEachIndexed { idx, flow ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
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
                                IconButton(
                                    onClick = { onDeleteCustomFlow(flow.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "移除附加规则",
                                        tint = palette.coral,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (idx < customFlows.lastIndex) {
                                HorizontalDivider(
                                    color = palette.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }

                // "我的规则集" card (iphone-2.jpg)
                SectionHeading(
                    title = "我的规则集",
                    detail = "${localRuleSets.size} 个",
                )
                TowerCard(
                    onClick = onOpenLocalRuleSets,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddCircleOutline,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (localRuleSets.isEmpty()) "新建规则集" else "管理或新建规则集（${localRuleSets.size}）",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Emoji toggle card
                TowerCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "策略组 Emoji 图标",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary,
                                )
                                Text(
                                    text = "控制面板与导出配置中的前置 Emoji",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                            TowerIosSwitch(
                                checked = scheme.emojiEnabled,
                                onCheckedChange = onToggleEmojiEnabled,
                            )
                        }
                        if (!scheme.isBundled && scheme.emojiEnabled) {
                            HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
                                TowerIosSwitch(
                                    checked = scheme.emojiAutofill,
                                    onCheckedChange = onToggleEmojiAutofill,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
