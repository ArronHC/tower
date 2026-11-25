package com.jzb.tower.android.ui.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.AndroidPlatformActions
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.QrCodeView
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerIosSwitch
import com.jzb.tower.android.ui.components.TowerLatencyBadge
import com.jzb.tower.android.ui.components.TowerSelectionCheckmark
import com.jzb.tower.android.ui.components.towerPressScale
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.CountryEntry
import com.jzb.tower.core.model.NodeExportNameFilter
import com.jzb.tower.core.model.NodeRuntimeInfo
import com.jzb.tower.core.model.NodeSharePayload
import com.jzb.tower.core.model.ProxyKind
import com.jzb.tower.core.model.ProxyNode
import com.jzb.tower.core.model.RegionSource

enum class NodeInclusionFilter(val label: String) {
    ALL("全部"),
    ACTIVE("已启用"),
    EXCLUDED("已停用"),
}

@Composable
fun CompactNodeRow(
    node: ProxyNode,
    runtime: NodeRuntimeInfo?,
    isIncluded: Boolean,
    isTestingLatency: Boolean = false,
    onClick: () -> Unit,
    onToggleIncluded: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val flagEmoji = runtime?.flagEmoji?.takeIf { it.isNotBlank() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // CLAUDE.md #20: Flag Emoji rendered directly without circular background
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (flagEmoji != null) {
                Text(
                    text = flagEmoji,
                    fontSize = 20.sp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = palette.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isIncluded) palette.textPrimary else palette.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = palette.primaryContainer,
                ) {
                    Text(
                        text = FormatUtils.proxyKindLabel(node.kind),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = palette.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = "${node.server}:${node.port}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Fixed 58dp min-width slot so node name truncation never shifts during speed tests
        TowerLatencyBadge(
            measurement = runtime?.latency,
            isTesting = isTestingLatency,
        )

        TowerSelectionCheckmark(
            selected = isIncluded,
            onToggle = { onToggleIncluded(!isIncluded) },
            contentDescription = if (isIncluded) "停用节点" else "启用节点",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageNodesModalSheet(
    nodes: List<ProxyNode>,
    runtimeMap: Map<String, NodeRuntimeInfo>,
    excludedNodeIds: Set<String>,
    testingNodeIds: Set<String>,
    isTestingLatency: Boolean,
    activeExportFilter: NodeExportNameFilter?,
    onToggleNodeExcluded: (ProxyNode, Boolean) -> Unit,
    onSetNodesExcluded: (List<String>, Boolean) -> Unit,
    onTestVisibleLatencies: (List<String>) -> Unit,
    onNodeClick: (ProxyNode) -> Unit,
    onOpenExportFilter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var inclusionFilter by remember { mutableStateOf(NodeInclusionFilter.ALL) }
    var selectedProtocol by remember { mutableStateOf<ProxyKind?>(null) }

    val availableProtocols = remember(nodes) {
        nodes.map { it.kind }.distinct().sortedBy { it.ordinal }
    }

    val filtered = remember(nodes, excludedNodeIds, searchQuery, inclusionFilter, selectedProtocol) {
        val q = searchQuery.trim().lowercase()
        nodes.filter { node ->
            val included = !excludedNodeIds.contains(node.id)
            when (inclusionFilter) {
                NodeInclusionFilter.ALL -> true
                NodeInclusionFilter.ACTIVE -> included
                NodeInclusionFilter.EXCLUDED -> !included
            } &&
                (selectedProtocol == null || node.kind == selectedProtocol) &&
                (
                    q.isEmpty() ||
                        node.name.lowercase().contains(q) ||
                        node.server.lowercase().contains(q) ||
                        node.kind.rawValue.lowercase().contains(q)
                    )
        }
    }

    val hasExportFilter = activeExportFilter != null &&
        (activeExportFilter.includeKeywords.isNotEmpty() || activeExportFilter.excludeKeywords.isNotEmpty())

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
                    text = "节点管理",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                TowerFloatingPillButton(
                    text = "完成",
                    onClick = onDismiss,
                )
            }

            // Capsule search bar matching iphone-2.jpg
            Surface(
                shape = CircleShape,
                color = palette.cardSurface,
                border = BorderStroke(0.75.dp, palette.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索节点名称、服务器或协议",
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onOpenExportFilter,
                    shape = CircleShape,
                    color = if (hasExportFilter) palette.primaryContainer else palette.cardSurface,
                    border = BorderStroke(0.75.dp, palette.cardBorder),
                    modifier = Modifier.towerPressScale(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = null,
                            tint = if (hasExportFilter) palette.primary else palette.textSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "名称筛选",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasExportFilter) palette.primary else palette.textSecondary,
                        )
                    }
                }

                NodeInclusionFilter.entries.forEach { mode ->
                    FilterChip(
                        selected = inclusionFilter == mode,
                        onClick = { inclusionFilter = mode },
                        label = { Text(mode.label) },
                    )
                }

                availableProtocols.forEach { kind ->
                    FilterChip(
                        selected = selectedProtocol == kind,
                        onClick = {
                            selectedProtocol = if (selectedProtocol == kind) null else kind
                        },
                        label = { Text(FormatUtils.proxyKindLabel(kind)) },
                    )
                }
            }

            if (filtered.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${filtered.size} 个节点",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onSetNodesExcluded(filtered.map { it.id }, false) },
                    ) {
                        Text("全选")
                    }
                    TextButton(
                        onClick = { onSetNodesExcluded(filtered.map { it.id }, true) },
                    ) {
                        Text("全停用", color = palette.textSecondary)
                    }
                    TintedActionButton(
                        text = if (isTestingLatency) "测速中" else "测速",
                        icon = Icons.Rounded.Speed,
                        enabled = !isTestingLatency,
                        onClick = { onTestVisibleLatencies(filtered.map { it.id }) },
                        modifier = Modifier.height(34.dp),
                    )
                }
            }

            if (filtered.isEmpty()) {
                TowerCard(contentPadding = PaddingValues(18.dp)) {
                    Text(
                        text = "没有符合当前筛选条件的节点。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
            } else {
                TowerCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, n -> n.id }) { idx, node ->
                            CompactNodeRow(
                                node = node,
                                runtime = runtimeMap[node.id],
                                isIncluded = !excludedNodeIds.contains(node.id),
                                isTestingLatency = testingNodeIds.contains(node.id),
                                onClick = { onNodeClick(node) },
                                onToggleIncluded = { included -> onToggleNodeExcluded(node, !included) },
                            )
                            if (idx < filtered.lastIndex) {
                                HorizontalDivider(
                                    color = palette.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 36.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailSheet(
    node: ProxyNode,
    runtime: NodeRuntimeInfo?,
    countries: List<CountryEntry>,
    sharePayload: NodeSharePayload?,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onSelectCountryOverride: (String?) -> Unit,
    onTestLatency: () -> Unit,
    onDeleteOwnNode: () -> Unit,
    onCopiedToast: (String) -> Unit,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val flag = runtime?.flagEmoji?.takeIf { it.isNotBlank() }
                if (flag != null) {
                    Text(text = flag, fontSize = 28.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${FormatUtils.proxyKindLabel(node.kind)} · ${node.server}:${node.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "重命名节点", tint = palette.primary)
                }
                TowerFloatingPillButton(
                    text = "完成",
                    onClick = onDismiss,
                )
            }

            TowerCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NodeMetaRow("服务器", "${node.server}:${node.port}")
                    val regionSourceLabel = when (runtime?.regionSource) {
                        RegionSource.OVERRIDE -> "手动指定"
                        RegionSource.NAME -> "节点名称识别"
                        RegionSource.IP -> "离线 IP 国家库"
                        RegionSource.UNKNOWN, null -> "未知"
                    }
                    val countryDisplay = if (runtime?.countryName != null) {
                        "${runtime.flagEmoji.orEmpty()} ${runtime.countryName}（$regionSourceLabel）".trim()
                    } else {
                        "未知"
                    }
                    NodeMetaRow("归属地区", countryDisplay)
                    if (!runtime?.resolvedIP.isNullOrBlank()) {
                        NodeMetaRow("解析 IP", runtime!!.resolvedIP!!)
                    }
                    if (runtime?.asn != null || !runtime?.asnOrg.isNullOrBlank()) {
                        val asnText = buildString {
                            if (runtime?.asn != null) append("AS${runtime.asn} ")
                            if (!runtime?.asnOrg.isNullOrBlank()) append(runtime.asnOrg)
                        }.trim()
                        NodeMetaRow("网络组织", asnText)
                    }
                    NodeMetaRow("延迟状态", FormatUtils.latencyBadgeText(runtime?.latency))
                    Text(
                        text = "IP 地区与 ASN 信息来自服务器解析地址，不代表实际代理出口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textTertiary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TintedActionButton(
                    text = "手动设置地区",
                    icon = Icons.Rounded.Public,
                    onClick = { showCountryPicker = true },
                    modifier = Modifier.weight(1f),
                )
                TintedActionButton(
                    text = "测试延迟",
                    icon = Icons.Rounded.Speed,
                    onClick = onTestLatency,
                    modifier = Modifier.weight(1f),
                )
            }

            if (sharePayload != null) {
                TowerCard(contentPadding = PaddingValues(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "节点分享链接与二维码",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (sharePayload.qrMatrix != null) {
                            QrCodeView(matrix = sharePayload.qrMatrix, size = 188.dp)
                        }
                        Text(
                            text = sharePayload.uri,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = palette.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.cardSubtleSurface)
                                .padding(10.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TintedActionButton(
                                text = "复制链接",
                                icon = Icons.Rounded.ContentCopy,
                                onClick = {
                                    AndroidPlatformActions.copyToClipboard(context, node.name, sharePayload.uri)
                                    onCopiedToast("已复制节点链接")
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TintedActionButton(
                                text = "系统分享",
                                icon = Icons.Rounded.Share,
                                onClick = {
                                    AndroidPlatformActions.shareText(context, node.name, sharePayload.uri)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (node.sourceID == null) {
                TintedActionButton(
                    text = "删除自有节点",
                    icon = Icons.Rounded.DeleteOutline,
                    tint = palette.coral,
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showRenameDialog) {
        var draftName by remember { mutableStateOf(node.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("修改节点名称") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("节点名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(draftName.trim())
                        showRenameDialog = false
                    },
                    enabled = draftName.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCountryPicker) {
        NodeCountryPickerDialog(
            currentOverride = node.countryOverride,
            countries = countries,
            onDismiss = { showCountryPicker = false },
            onSelect = { code ->
                onSelectCountryOverride(code)
                showCountryPicker = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除「${node.name}」？") },
            text = { Text("这个自有节点将从本机移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteOwnNode()
                        onDismiss()
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
private fun NodeMetaRow(label: String, value: String) {
    val palette = TowerTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NodeCountryPickerDialog(
    currentOverride: String?,
    countries: List<CountryEntry>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val palette = TowerTheme.palette
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, countries) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) countries
        else countries.filter {
            it.chineseName.lowercase().contains(q) ||
                it.englishName.lowercase().contains(q) ||
                it.code.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择节点地区") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索国家或地区代码（如 HK、日本）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currentOverride != null) {
                    TextButton(
                        onClick = { onSelect(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("恢复自动识别", color = palette.primary)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(filtered, key = { it.code }) { entry ->
                        val selected = currentOverride.equals(entry.code, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) palette.primaryContainer else Color.Transparent)
                                .clickable { onSelect(entry.code) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(text = entry.flag, fontSize = 20.sp)
                            Text(
                                text = entry.chineseName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = entry.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                            )
                        }
                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
fun NodeExportFilterDialog(
    initialFilter: NodeExportNameFilter?,
    onDismiss: () -> Unit,
    onSave: (NodeExportNameFilter?) -> Unit,
) {
    var includeText by remember {
        mutableStateOf(initialFilter?.includeKeywords?.joinToString(", ").orEmpty())
    }
    var excludeText by remember {
        mutableStateOf(initialFilter?.excludeKeywords?.joinToString(", ").orEmpty())
    }
    var useRegex by remember { mutableStateOf(initialFilter?.useRegex ?: false) }

    fun parseKeywords(raw: String): List<String> =
        raw.split(",", "\n", "、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全局导出节点筛选") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "按节点名称筛选导出的节点（多个关键词用逗号分隔）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TowerTheme.palette.textSecondary,
                )
                OutlinedTextField(
                    value = includeText,
                    onValueChange = { includeText = it },
                    label = { Text("仅包含关键词（留空为全部）") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = excludeText,
                    onValueChange = { excludeText = it },
                    label = { Text("排除关键词（如 IPLC, 倍率）") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("使用正则表达式", style = MaterialTheme.typography.bodyMedium)
                    TowerIosSwitch(checked = useRegex, onCheckedChange = { useRegex = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val inc = parseKeywords(includeText)
                    val exc = parseKeywords(excludeText)
                    val filter = if (inc.isEmpty() && exc.isEmpty()) {
                        null
                    } else {
                        NodeExportNameFilter(inc, exc, useRegex)
                    }
                    onSave(filter)
                    onDismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onSave(null)
                    onDismiss()
                },
            ) {
                Text("清空")
            }
        },
    )
}
