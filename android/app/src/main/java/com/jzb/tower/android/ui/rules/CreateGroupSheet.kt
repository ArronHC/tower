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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerSegmentedControl
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.RuleSchemeGroupKind
import com.jzb.tower.core.model.RuleSchemeGroupMember
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupSheet(
    availableTargets: List<String>,
    onCreate: suspend (
        name: String,
        kind: RuleSchemeGroupKind,
        members: List<RuleSchemeGroupMember>,
        isNodeFilterGroup: Boolean,
    ) -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var groupModeIndex by remember { mutableIntStateOf(0) } // 0 = Node Filter, 1 = Policy Group
    var selectedKind by remember { mutableStateOf(RuleSchemeGroupKind.URL_TEST) }
    var nodeRegex by remember { mutableStateOf("") }
    val selectedTargets = remember { mutableStateListOf<String>() }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
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
                    text = "新建策略组",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                TowerFloatingPillButton(
                    text = "取消",
                    enabled = !isSaving,
                    onClick = onDismiss,
                )
            }

            TowerSegmentedControl(
                options = listOf("节点筛选组", "分流策略组"),
                selectedIndex = groupModeIndex,
                onSelect = { idx ->
                    groupModeIndex = idx
                    if (idx == 1) selectedKind = RuleSchemeGroupKind.SELECT
                },
            )

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMsg = null },
                        label = { Text("分组名称（如 🇯🇵 日本自动 / 🤖 AI）") },
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
                if (groupModeIndex == 0) {
                    OutlinedTextField(
                        value = nodeRegex,
                        onValueChange = { nodeRegex = it; errorMsg = null },
                        label = { Text("节点匹配关键词或正则（每行一条）") },
                        placeholder = { Text("日本|Tokyo|JP") },
                        minLines = 2,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "选择包含的候选策略组",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(6.dp))
                    val candidates = (availableTargets + listOf("DIRECT", "REJECT")).distinct()
                    candidates.forEachIndexed { idx, target ->
                        val checked = selectedTargets.contains(target)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selectedTargets.remove(target) else selectedTargets.add(target)
                                }
                                .padding(horizontal = 6.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(target, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                            if (checked) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选",
                                    tint = palette.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (idx < candidates.lastIndex) {
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

            val canCreate = !isSaving && name.isNotBlank() && if (groupModeIndex == 0) {
                nodeRegex.isNotBlank()
            } else {
                selectedTargets.isNotEmpty()
            }

            PrimaryActionButton(
                text = if (isSaving) "创建中…" else "创建策略组",
                icon = Icons.Rounded.Add,
                enabled = canCreate,
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorMsg = null
                        val members = if (groupModeIndex == 0) {
                            nodeRegex.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                .map { RuleSchemeGroupMember.NodePattern(it) }
                        } else {
                            selectedTargets.map { RuleSchemeGroupMember.Reference(it) }
                        }
                        val res = onCreate(name.trim(), selectedKind, members, groupModeIndex == 0)
                        isSaving = false
                        res.onSuccess { onDismiss() }
                            .onFailure { errorMsg = it.message ?: "创建失败" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
