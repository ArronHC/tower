package com.jzb.tower.android.ui.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.AndroidPlatformActions
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerSelectionCheckmark
import com.jzb.tower.android.ui.components.towerPressScale
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
    testingNodeIds: Set<String>,
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
    val context = LocalContext.current
    var isExpanded by rememberSaveable(source.id) { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }

    val usage = source.usage
    val remainingBytes = usage?.remainingBytes
    val totalBytes = usage?.totalBytes
    val usedFraction = usage?.usedFraction
    val distinctNotices = remember(usage?.notices) {
        usage?.notices?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = TowerMotion.disclosureSpec(),
        label = "subChevron",
    )

    val refreshRotation = if (isRefreshing) {
        val infinite = rememberInfiniteTransition(label = "subRefreshSpin")
        val angle by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "subRefreshAngle",
        )
        angle
    } else {
        0f
    }

    TowerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header Row: 40dp soft-blue icon | bold title + gray host | rotating chevron | TowerSelectionCheckmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isExpanded = !isExpanded },
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = null,
                            tint = if (source.isEnabled) palette.primary else palette.textTertiary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (source.isEnabled) palette.textPrimary else palette.textSecondary,
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
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "收起节点" else "展开节点",
                        tint = palette.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }

                TowerSelectionCheckmark(
                    selected = source.isEnabled,
                    onToggle = onToggleEnabled,
                    contentDescription = if (source.isEnabled) "停用订阅" else "启用订阅",
                )
            }

            // Quota Row + 3.5dp animated emerald progress bar (iphone-1.jpg)
            if (remainingBytes != null || totalBytes != null) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DonutLarge,
                                contentDescription = null,
                                tint = palette.textPrimary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (remainingBytes != null) {
                                    "剩余 ${FormatUtils.formatBytes(remainingBytes)}"
                                } else {
                                    "已用 ${FormatUtils.formatBytes(usage?.usedBytes ?: 0L)}"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = palette.textPrimary,
                            )
                        }
                        if (totalBytes != null) {
                            Text(
                                text = "总流量 ${FormatUtils.formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = palette.textSecondary,
                            )
                        }
                    }

                    if (usedFraction != null) {
                        val remainingFractionTarget = (1f - usedFraction.coerceIn(0f, 1f))
                        val animatedFraction by animateFloatAsState(
                            targetValue = remainingFractionTarget,
                            animationSpec = TowerMotion.disclosureSpec(),
                            label = "quotaProgress",
                        )
                        val barColor = when {
                            remainingFractionTarget < 0.1f -> palette.coral
                            remainingFractionTarget < 0.25f -> palette.amber
                            else -> palette.emerald
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .clip(CircleShape)
                                .background(palette.divider),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedFraction.coerceIn(0.02f, 1f))
                                    .height(3.5.dp)
                                    .clip(CircleShape)
                                    .background(barColor),
                            )
                        }
                    }
                }
            }

            if (!source.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
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

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // Bottom facts row: "13 个节点 · 160 天到期" | "🕒 6分钟前" + refresh + share + menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "${nodes.size} 个节点",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = palette.textSecondary,
                    )
                    if (usage?.expiresAtEpochMs != null) {
                        val (expiryText, isUrgent) = FormatUtils.formatExpiryCountdown(usage.expiresAtEpochMs!!)
                        Text("·", color = palette.textTertiary, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = expiryText,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = if (isUrgent) palette.amber else palette.textSecondary,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (source.lastUpdatedAtEpochMs != null) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = palette.textTertiary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = FormatUtils.formatRelativeTime(source.lastUpdatedAtEpochMs!!),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = palette.textTertiary,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .towerPressScale(enabled = !isRefreshing, onClick = onRefresh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新订阅",
                            tint = palette.primary,
                            modifier = Modifier
                                .size(19.dp)
                                .graphicsLayer { rotationZ = refreshRotation },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .towerPressScale(
                                onClick = {
                                    AndroidPlatformActions.shareText(context, source.name, source.urlString)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = "分享订阅链接",
                            tint = palette.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .towerPressScale(onClick = { showActionMenu = true }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "更多操作",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑订阅") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    showActionMenu = false
                                    showEditDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("分享链接") },
                                leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                                onClick = {
                                    showActionMenu = false
                                    AndroidPlatformActions.shareText(context, source.name, source.urlString)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除订阅", color = palette.coral) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = palette.coral)
                                },
                                onClick = {
                                    showActionMenu = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                }
            }
        }

        // CLAUDE.md #7: NEVER use top slide-in transition when expanding subscription details;
        // pure opacity + height expansion only, and NO "显示更多节点" button.
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(TowerMotion.disclosureSpec()) + expandVertically(TowerMotion.disclosureSpec()),
            exit = fadeOut(TowerMotion.disclosureSpec()) + shrinkVertically(TowerMotion.disclosureSpec()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            ) {
                if (distinctNotices.isNotEmpty()) {
                    HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
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
                            HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                        }
                    }
                }

                if (nodes.isNotEmpty()) {
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    nodes.forEachIndexed { idx, node ->
                        CompactNodeRow(
                            node = node,
                            runtime = runtimeMap[node.id],
                            isIncluded = !excludedNodeIds.contains(node.id),
                            isTestingLatency = testingNodeIds.contains(node.id),
                            onClick = { onNodeClick(node) },
                            onToggleIncluded = { included -> onToggleNodeExcluded(node, !included) },
                        )
                        if (idx < nodes.lastIndex) {
                            HorizontalDivider(
                                color = palette.divider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 38.dp),
                            )
                        }
                    }
                } else {
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
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
