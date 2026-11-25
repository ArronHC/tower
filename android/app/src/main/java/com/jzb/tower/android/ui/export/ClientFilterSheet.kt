package com.jzb.tower.android.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jzb.tower.android.ui.components.ClientIcon
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerIosSwitch
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.ClientTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFilterSheet(
    clientOrder: List<ClientTarget>,
    visibleTargets: List<ClientTarget>,
    isLanSharingVisible: Boolean,
    onSave: (order: List<ClientTarget>, visible: List<ClientTarget>, isLanVisible: Boolean) -> Unit,
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
    var lanVisible by remember(isLanSharingVisible) { mutableStateOf(isLanSharingVisible) }

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
                Text(
                    text = "客户端筛选与排序",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                TowerFloatingPillButton(
                    text = "取消",
                    onClick = onDismiss,
                )
            }
            Text(
                text = "选择在导出页显示的客户端并调整顺序（至少保留 1 个）。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )

            TowerCard(
                cornerRadius = 16.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0099FF), Color(0xFF00C6FF)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiTethering,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "局域网共享",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "在客户端条中显示局域网订阅入口",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                    }
                    TowerIosSwitch(
                        checked = lanVisible,
                        onCheckedChange = { lanVisible = it },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
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
                            TowerIosSwitch(
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
                    onSave(orderedList.toList(), finalVisible, lanVisible)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
