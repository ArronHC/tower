package com.jzb.tower.android.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jzb.tower.android.ui.components.ClientIcon
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.ClientTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFilterSheet(
    clientOrder: List<ClientTarget>,
    visibleTargets: List<ClientTarget>,
    onSave: (order: List<ClientTarget>, visible: List<ClientTarget>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val orderedList = remember(clientOrder) {
        mutableStateListOf<ClientTarget>().apply {
            addAll((clientOrder + ClientCatalog.DEFAULT_CLIENT_ORDER).distinct())
        }
    }
    val visibleSet = remember(visibleTargets) {
        mutableStateListOf<ClientTarget>().apply {
            addAll(visibleTargets.distinct())
        }
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
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "客户端显示与排序",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.textPrimary,
            )
            Text(
                text = "选择在导出页显示的客户端并调整顺序（至少保留 1 个）。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(orderedList, key = { _, t -> t.rawValue }) { index, target ->
                    val meta = ClientCatalog.meta(target)
                    val isVisible = visibleSet.contains(target)
                    TowerCard(
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ClientIcon(
                                iconFile = meta.iconFile,
                                name = meta.name,
                                brandColorHex = meta.brandColorHex,
                                size = 38.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary,
                                )
                                Text(
                                    text = if (meta.isAndroidNative) "Android 客户端 · ${meta.subtitle}" else meta.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val item = orderedList.removeAt(index)
                                        orderedList.add(index - 1, item)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "上移",
                                    tint = if (index > 0) palette.textSecondary else palette.textTertiary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (index < orderedList.lastIndex) {
                                        val item = orderedList.removeAt(index)
                                        orderedList.add(index + 1, item)
                                    }
                                },
                                enabled = index < orderedList.lastIndex,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "下移",
                                    tint = if (index < orderedList.lastIndex) palette.textSecondary else palette.textTertiary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            Switch(
                                checked = isVisible,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!visibleSet.contains(target)) visibleSet.add(target)
                                    } else if (visibleSet.size > 1) {
                                        visibleSet.remove(target)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            PrimaryActionButton(
                text = "保存排序与显示",
                onClick = {
                    val finalVisible = orderedList.filter { visibleSet.contains(it) }
                    onSave(orderedList.toList(), finalVisible)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
