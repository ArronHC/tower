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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.RuleCatalogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCatalogSheet(
    schemeId: String,
    availablePolicies: List<String>,
    onSearch: (query: String, source: String) -> List<RuleCatalogEntry>,
    onAttachEntry: (RuleCatalogEntry, policyName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf("all") }
    var pendingEntry by remember { mutableStateOf<RuleCatalogEntry?>(null) }

    val entries = remember(query, sourceFilter) {
        onSearch(query, sourceFilter)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.groupedBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "规则库",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.textPrimary,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索规则，如 YouTube、OpenAI、Apple") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("all" to "全部", "acl4ssr" to "ACL4SSR", "blackmatrix7" to "blackmatrix7").forEach { (id, label) ->
                    FilterChip(
                        selected = sourceFilter == id,
                        onClick = { sourceFilter = id },
                        label = { Text(label) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    TowerCard(
                        cornerRadius = 16.dp,
                        onClick = { pendingEntry = entry },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(text = entry.emoji.ifBlank { "📌" }, fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary,
                                )
                                Text(
                                    text = "${entry.source} · ${entry.category}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.AddCircleOutline,
                                contentDescription = "添加到方案",
                                tint = palette.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    pendingEntry?.let { entry ->
        var selectedPolicy by remember(entry.id) {
            mutableStateOf(availablePolicies.firstOrNull() ?: "DIRECT")
        }
        AlertDialog(
            onDismissRequest = { pendingEntry = null },
            title = { Text("添加「${entry.displayName}」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "选择命中后走哪一个策略组：",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAttachEntry(entry, selectedPolicy)
                        pendingEntry = null
                    },
                ) {
                    Text("加入方案")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEntry = null }) {
                    Text("取消")
                }
            },
        )
    }
}
