package com.jzb.tower.android.ui.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.NodeRuntimeInfo
import com.jzb.tower.core.model.ProxyNode
import com.jzb.tower.core.model.SubscriptionRequestOptions
import com.jzb.tower.core.model.SubscriptionSource

@Composable
fun SubscriptionCard(
    source: SubscriptionSource,
    nodes: List<ProxyNode>,
    runtimeMap: Map<String, NodeRuntimeInfo>,
    excludedNodeIds: Set<String>,
    isRefreshing: Boolean,
    onToggleEnabled: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: (name: String, url: String, userAgent: String?, dohUrl: String?) -> Unit,
    onDelete: () -> Unit,
    onNodeClick: (ProxyNode) -> Unit,
    onToggleNodeExcluded: (ProxyNode, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    var isExpanded by rememberSaveable(source.id) { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val usage = source.usage
    val remainingBytes = usage?.remainingBytes
    val totalBytes = usage?.totalBytes
    val usedFraction = usage?.usedFraction
    val distinctNotices = remember(usage?.notices) {
        usage?.notices?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
    }

    TowerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (source.isEnabled) palette.primaryContainer else palette.cardSubtleSurface,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RssFeed,
                            contentDescription = null,
                            tint = if (source.isEnabled) palette.primary else palette.textTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = FormatUtils.safeHost(source.urlString),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "收起节点" else "展开节点",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Switch(
                    checked = source.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                )
            }

            if (remainingBytes != null) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "剩余 ${FormatUtils.formatBytes(remainingBytes)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = palette.textPrimary,
                        )
                        if (totalBytes != null) {
                            Text(
                                text = "总计 ${FormatUtils.formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = palette.textSecondary,
                            )
                        }
                    }
                    if (usedFraction != null) {
                        val remainingFraction = (1f - usedFraction.coerceIn(0f, 1f))
                        val barColor = when {
                            remainingFraction < 0.1f -> palette.coral
                            remainingFraction < 0.25f -> palette.amber
                            else -> palette.emerald
                        }
                        LinearProgressIndicator(
                            progress = { remainingFraction },
                            color = barColor,
                            trackColor = palette.divider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(50)),
                        )
                    }
                }
            }

            if (!source.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.amberSurface)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = palette.amber,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = source.lastError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.amber,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${nodes.size} 个节点",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                    )
                    if (usage?.expiresAtEpochMs != null) {
                        val (expiryText, isUrgent) = FormatUtils.formatExpiryCountdown(usage.expiresAtEpochMs!!)
                        Text("·", color = palette.textTertiary, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = expiryText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUrgent) palette.amber else palette.textSecondary,
                        )
                    }
                    if (source.lastUpdatedAtEpochMs != null) {
                        Text("·", color = palette.textTertiary, style = MaterialTheme.typography.labelSmall)
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = FormatUtils.formatRelativeTime(source.lastUpdatedAtEpochMs!!),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = palette.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新订阅",
                            tint = palette.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "编辑订阅",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除订阅",
                        tint = palette.coral,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        // CLAUDE.md #7: NEVER use top slide-in transition when expanding subscription details;
        // use smooth opacity/height expansion only, and NO "显示更多节点" button.
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(TowerMotion.disclosureSpec()) + expandVertically(TowerMotion.disclosureSpec()),
            exit = fadeOut(TowerMotion.disclosureSpec()) + shrinkVertically(TowerMotion.disclosureSpec()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            ) {
                if (distinctNotices.isNotEmpty()) {
                    HorizontalDivider(color = palette.divider)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "机场公告",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    distinctNotices.forEachIndexed { idx, notice ->
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                        if (idx < distinctNotices.lastIndex) {
                            HorizontalDivider(color = palette.divider)
                        }
                    }
                }

                if (nodes.isNotEmpty()) {
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 8.dp))
                    nodes.forEachIndexed { idx, node ->
                        CompactNodeRow(
                            node = node,
                            runtime = runtimeMap[node.id],
                            isIncluded = !excludedNodeIds.contains(node.id),
                            onClick = { onNodeClick(node) },
                            onToggleIncluded = { included -> onToggleNodeExcluded(node, !included) },
                        )
                        if (idx < nodes.lastIndex) {
                            HorizontalDivider(
                                color = palette.divider,
                                modifier = Modifier.padding(start = 38.dp),
                            )
                        }
                    }
                } else {
                    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "当前没有解析到节点",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditSubscriptionDialog(
            source = source,
            onDismiss = { showEditDialog = false },
            onSave = { name, url, ua, doh ->
                onEdit(name, url, ua, doh)
                showEditDialog = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除「${source.name}」？") },
            text = { Text("该订阅及已读取的节点会从这台设备移除。") },
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

@Composable
private fun EditSubscriptionDialog(
    source: SubscriptionSource,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, userAgent: String?, dohUrl: String?) -> Unit,
) {
    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.urlString) }
    var userAgent by remember { mutableStateOf(source.requestOptions?.userAgent.orEmpty()) }
    var dohUrl by remember { mutableStateOf(source.requestOptions?.dnsOverHTTPSURL.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑订阅") },
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
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅链接") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("自定义 User-Agent（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dohUrl,
                    onValueChange = { dohUrl = it },
                    label = { Text("DNS-over-HTTPS 地址（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        url.trim(),
                        userAgent.trim().takeIf { it.isNotEmpty() },
                        dohUrl.trim().takeIf { it.isNotEmpty() },
                    )
                },
                enabled = url.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
