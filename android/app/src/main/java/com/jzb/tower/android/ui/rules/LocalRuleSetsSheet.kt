package com.jzb.tower.android.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.LocalRuleSet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalRuleSetsSheet(
    ruleSets: List<LocalRuleSet>,
    availablePolicies: List<String>,
    onCreate: suspend (name: String, rulesText: String) -> Result<LocalRuleSet>,
    onUpdate: suspend (id: String, name: String?, rulesText: String?) -> Result<Unit>,
    onDelete: (id: String) -> Unit,
    onAttach: (LocalRuleSet, policyName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingRuleSet by remember { mutableStateOf<LocalRuleSet?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var attachingRuleSet by remember { mutableStateOf<LocalRuleSet?>(null) }

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
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TowerFloatingPillButton(
                    text = "新建",
                    icon = Icons.Rounded.Add,
                    onClick = { showCreateDialog = true },
                )
                Text(
                    text = "我的规则集",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                TowerFloatingPillButton(
                    text = "完成",
                    onClick = onDismiss,
                )
            }

            if (ruleSets.isEmpty()) {
                TowerCard(contentPadding = PaddingValues(18.dp)) {
                    Text(
                        text = "还没有本地规则集。你可以手动创建规则列表并随时挂载到当前规则方案。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ruleSets, key = { it.id }) { rs ->
                        TowerCard(contentPadding = PaddingValues(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rs.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.textPrimary,
                                    )
                                    val lineCount = rs.rulesText.lines().count { it.isNotBlank() }
                                    Text(
                                        text = "$lineCount 条规则",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textSecondary,
                                    )
                                }
                                IconButton(onClick = { attachingRuleSet = rs }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                        contentDescription = "挂载到当前方案",
                                        tint = palette.primary,
                                    )
                                }
                                IconButton(onClick = { editingRuleSet = rs }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "编辑",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                                IconButton(onClick = { onDelete(rs.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = palette.coral,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showCreateDialog || editingRuleSet != null) {
        val target = editingRuleSet
        var name by remember(target?.id) { mutableStateOf(target?.name.orEmpty()) }
        var rulesText by remember(target?.id) { mutableStateOf(target?.rulesText.orEmpty()) }
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                editingRuleSet = null
            },
            title = { Text(if (target == null) "新建本地规则集" else "编辑规则集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rulesText,
                        onValueChange = { rulesText = it },
                        label = { Text("规则内容（每行一条）") },
                        placeholder = { Text("DOMAIN-SUFFIX,openai.com\nIP-CIDR,1.1.1.1/32,no-resolve") },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (target == null) {
                                onCreate(name.trim(), rulesText)
                            } else {
                                onUpdate(target.id, name.trim(), rulesText)
                            }
                            showCreateDialog = false
                            editingRuleSet = null
                        }
                    },
                    enabled = name.isNotBlank() && rulesText.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        editingRuleSet = null
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    attachingRuleSet?.let { rs ->
        var selectedPolicy by remember(rs.id) {
            mutableStateOf(availablePolicies.firstOrNull() ?: "DIRECT")
        }
        AlertDialog(
            onDismissRequest = { attachingRuleSet = null },
            title = { Text("挂载「${rs.name}」") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(availablePolicies.size) { i ->
                        val policy = availablePolicies[i]
                        val chosen = selectedPolicy == policy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedPolicy = policy }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(policy, color = if (chosen) palette.primary else palette.textPrimary)
                            if (chosen) Text("✓", color = palette.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAttach(rs, selectedPolicy)
                        attachingRuleSet = null
                    },
                ) {
                    Text("挂载")
                }
            },
            dismissButton = {
                TextButton(onClick = { attachingRuleSet = null }) {
                    Text("取消")
                }
            },
        )
    }
}
