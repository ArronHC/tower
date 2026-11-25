package com.jzb.tower.android.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.RuleGroupPresentation
import com.jzb.tower.core.model.RuleSchemeGroupKind
import com.jzb.tower.core.model.RuleSchemeGroupMember
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleGroupDetailSheet(
    schemeId: String,
    group: RuleGroupPresentation,
    routingTargets: List<String>,
    additionalTargets: List<String>,
    onDismiss: () -> Unit,
    onSave: suspend (
        newName: String?,
        kind: RuleSchemeGroupKind?,
        members: List<RuleSchemeGroupMember>?,
        supplementRules: List<String>?,
    ) -> Result<Unit>,
    onDelete: () -> Unit,
) {
    val palette = TowerTheme.palette
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var nameDraft by remember(group.sourceName) { mutableStateOf(group.displayName) }
    var selectedKind by remember(group.sourceName) { mutableStateOf(group.kind) }

    val selectedRefs = remember(group.sourceName) {
        mutableStateListOf<String>().apply {
            addAll(group.members.filterIsInstance<RuleSchemeGroupMember.Reference>().map { it.name })
        }
    }
    var patternsText by remember(group.sourceName) {
        mutableStateOf(
            group.members.filterIsInstance<RuleSchemeGroupMember.NodePattern>()
                .joinToString("\n") { it.pattern },
        )
    }
    val supplements = remember(group.sourceName) {
        mutableStateListOf<String>().apply { addAll(group.supplementRules) }
    }
    var newRuleDraft by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val isRoutingMode = group.editorMode == "routingTargets"
    val allReferenceCandidates = remember(routingTargets, additionalTargets, group.sourceName) {
        (routingTargets + additionalTargets + listOf("DIRECT", "REJECT"))
            .distinct()
            .filter { it != group.sourceName }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "策略组详情",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                TowerFloatingPillButton(
                    text = "关闭",
                    onClick = onDismiss,
                )
            }

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text("显示名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "策略类型",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            RuleSchemeGroupKind.SELECT,
                            RuleSchemeGroupKind.URL_TEST,
                            RuleSchemeGroupKind.FALLBACK,
                            RuleSchemeGroupKind.LOAD_BALANCE,
                        ).forEach { kind ->
                            FilterChip(
                                selected = selectedKind == kind,
                                onClick = { selectedKind = kind },
                                label = { Text(FormatUtils.ruleGroupKindLabel(kind)) },
                            )
                        }
                    }
                }
            }

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                if (isRoutingMode) {
                    Text(
                        text = "候选策略成员",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    allReferenceCandidates.forEachIndexed { idx, candidate ->
                        val isChecked = selectedRefs.contains(candidate)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isChecked) {
                                        if (selectedRefs.size > 1) selectedRefs.remove(candidate)
                                    } else {
                                        selectedRefs.add(candidate)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = candidate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textPrimary,
                            )
                            if (isChecked) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选",
                                    tint = palette.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (idx < allReferenceCandidates.lastIndex) {
                            HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                        }
                    }
                } else {
                    Text(
                        text = "节点名称正则筛选",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = patternsText,
                        onValueChange = { patternsText = it },
                        label = { Text("每行一条正则或关键词") },
                        minLines = 2,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Text(
                    text = "我添加的规则（优先匹配）",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newRuleDraft,
                        onValueChange = { newRuleDraft = it },
                        placeholder = { Text("如 DOMAIN-SUFFIX,example.com") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val trimmed = newRuleDraft.trim()
                            if (trimmed.isNotEmpty()) {
                                supplements.add(trimmed)
                                newRuleDraft = ""
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加规则", tint = palette.primary)
                    }
                }
                if (supplements.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    supplements.forEachIndexed { idx, rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = rule,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { supplements.removeAt(idx) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除规则",
                                    tint = palette.coral,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        if (idx < supplements.lastIndex) {
                            HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                        }
                    }
                }
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.coral,
                )
            }

            PrimaryActionButton(
                text = if (isSaving) "保存中…" else "保存修改",
                enabled = !isSaving && nameDraft.isNotBlank(),
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorMsg = null
                        val updatedMembers = if (isRoutingMode) {
                            selectedRefs.map { RuleSchemeGroupMember.Reference(it) } +
                                group.members.filterIsInstance<RuleSchemeGroupMember.NodePattern>()
                        } else {
                            patternsText.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .map { RuleSchemeGroupMember.NodePattern(it) }
                        }
                        val trimmedName = nameDraft.trim()
                        val res = onSave(
                            if (trimmedName != group.displayName) trimmedName else null,
                            selectedKind,
                            updatedMembers,
                            supplements.toList(),
                        )
                        isSaving = false
                        res.onSuccess { onDismiss() }
                            .onFailure { errorMsg = it.message ?: "保存失败" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Deletion confirmation dialog hosted directly on this detail sheet
            if (!group.isProtected) {
                TintedActionButton(
                    text = "删除策略组",
                    icon = Icons.Rounded.DeleteOutline,
                    tint = palette.coral,
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除「${group.displayName}」？") },
            text = { Text("这会移除该策略组以及指向它的规则与引用，不会修改上游仓库。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = palette.coral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}
