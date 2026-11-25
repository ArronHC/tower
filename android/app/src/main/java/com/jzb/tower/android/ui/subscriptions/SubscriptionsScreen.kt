package com.jzb.tower.android.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.MetricPill
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.AppTab
import com.jzb.tower.core.model.ProxyKind
import com.jzb.tower.core.model.ProxyNode
import com.jzb.tower.core.model.SubscriptionRequestOptions
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsScreen(
    viewModel: TowerViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val worldMap by viewModel.worldMap.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val isRefreshingAll by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshingSourceIds by viewModel.refreshingSourceIds.collectAsStateWithLifecycle()
    val isTestingLatency by viewModel.isTestingLatency.collectAsStateWithLifecycle()
    val incomingSharedText by viewModel.incomingSharedText.collectAsStateWithLifecycle()

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var sharedDraftText by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCountryCode by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var inclusionFilter by rememberSaveable { mutableStateOf(NodeInclusionFilter.ALL) }
    var selectedProtocol by rememberSaveable { mutableStateOf<ProxyKind?>(null) }
    var selectedDetailNodeId by remember { mutableStateOf<String?>(null) }
    var showExportFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(incomingSharedText) {
        val text = incomingSharedText
        if (!text.isNullOrBlank()) {
            sharedDraftText = text
            showAddSheet = true
            viewModel.consumeIncomingSharedText()
        }
    }

    val snapshot = state.snapshot
    val runtimeMap = state.runtime
    val excludedSet = remember(snapshot.excludedNodeIDs) { snapshot.excludedNodeIDs.toSet() }
    val enabledSubIds = remember(snapshot.subscriptions) {
        snapshot.subscriptions.filter { it.isEnabled }.map { it.id }.toSet()
    }

    val nodesBySub = remember(snapshot.nodes) {
        snapshot.nodes.groupBy { it.sourceID }
    }

    // Active nodes (from enabled subscriptions or own nodes, not excluded)
    val enabledPoolNodes = remember(snapshot.nodes, enabledSubIds, excludedSet) {
        snapshot.nodes.filter { node ->
            val sourceActive = node.sourceID == null || enabledSubIds.contains(node.sourceID)
            sourceActive && !excludedSet.contains(node.id)
        }
    }

    val coverageByCountry = remember(enabledPoolNodes, runtimeMap) {
        val grouped = HashMap<String, MutableList<ProxyNode>>()
        for (node in enabledPoolNodes) {
            val code = runtimeMap[node.id]?.countryCode?.uppercase() ?: continue
            grouped.getOrPut(code) { mutableListOf() }.add(node)
        }
        grouped.mapValues { (code, list) ->
            val firstRuntime = list.firstNotNullOfOrNull { runtimeMap[it.id] }
            val latencies = list.mapNotNull { runtimeMap[it.id]?.latency?.milliseconds }.sorted()
            val medianOrBest = if (latencies.isNotEmpty()) latencies[latencies.size / 2] else null
            CountryCoverageSummary(
                code = code,
                name = firstRuntime?.countryName ?: code,
                flagEmoji = firstRuntime?.flagEmoji ?: "",
                nodeCount = list.size,
                bestOrMedianLatencyMs = medianOrBest,
            )
        }
    }

    val availableProtocols = remember(snapshot.nodes) {
        snapshot.nodes.map { it.kind }.distinct().sortedBy { it.ordinal }
    }

    val filteredNodes = remember(
        snapshot.nodes,
        runtimeMap,
        excludedSet,
        enabledSubIds,
        selectedCountryCode,
        searchQuery,
        inclusionFilter,
        selectedProtocol,
    ) {
        val q = searchQuery.trim().lowercase()
        snapshot.nodes.filter { node ->
            // Show nodes from enabled subscriptions + own nodes (or excluded ones if filter allows)
            val sourceActive = node.sourceID == null || enabledSubIds.contains(node.sourceID)
            if (!sourceActive) return@filter false

            val isIncluded = !excludedSet.contains(node.id)
            when (inclusionFilter) {
                NodeInclusionFilter.ALL -> {}
                NodeInclusionFilter.ACTIVE -> if (!isIncluded) return@filter false
                NodeInclusionFilter.EXCLUDED -> if (isIncluded) return@filter false
            }

            if (selectedProtocol != null && node.kind != selectedProtocol) return@filter false

            if (selectedCountryCode != null) {
                val nodeCountry = runtimeMap[node.id]?.countryCode?.uppercase()
                if (nodeCountry != selectedCountryCode) return@filter false
            }

            if (q.isNotEmpty()) {
                val matches = node.name.lowercase().contains(q) ||
                    node.server.lowercase().contains(q) ||
                    node.kind.rawValue.lowercase().contains(q)
                if (!matches) return@filter false
            }
            true
        }
    }

    val selectedCount = remember(enabledPoolNodes, selectedCountryCode, runtimeMap) {
        if (selectedCountryCode != null) {
            enabledPoolNodes.count { runtimeMap[it.id]?.countryCode?.equals(selectedCountryCode, true) == true }
        } else {
            enabledPoolNodes.size
        }
    }

    val isEmptyState = snapshot.subscriptions.isEmpty() && snapshot.nodes.isEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TowerDimens.PagePadding,
            end = TowerDimens.PagePadding,
            top = 8.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Bar
        item(key = "top_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "我的订阅",
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "集中管理机场订阅与自有节点",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!isEmptyState) {
                        IconButton(
                            onClick = { viewModel.refreshAllSubscriptions() },
                            enabled = !isRefreshingAll && snapshot.subscriptions.isNotEmpty(),
                        ) {
                            if (isRefreshingAll) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = palette.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "刷新全部订阅",
                                    tint = palette.primary,
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.testNodeLatency(enabledPoolNodes.map { it.id }) },
                            enabled = !isTestingLatency && enabledPoolNodes.isNotEmpty(),
                        ) {
                            if (isTestingLatency) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = palette.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = "全部节点测速",
                                    tint = palette.primary,
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            sharedDraftText = null
                            showAddSheet = true
                        },
                        shape = CircleShape,
                        color = palette.primary,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "添加订阅或节点",
                                tint = palette.onPrimary,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }
        }

        if (isEmptyState) {
            item(key = "empty_state") {
                TowerCard(contentPadding = PaddingValues(22.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(palette.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        Text(
                            text = "先添加一个来源",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "支持机场订阅链接，也可以直接粘贴 SS、VMess、VLESS、Trojan、Hysteria 2 等节点或配置。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                        PrimaryActionButton(
                            text = "添加订阅或节点",
                            icon = Icons.Rounded.Add,
                            onClick = {
                                sharedDraftText = null
                                showAddSheet = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrivacyBadge()
                    }
                }
            }
        } else {
            // World Dot Map + 4 Metric Pills inside one unified card
            item(key = "map_metrics_card") {
                TowerCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MetricPill(
                            value = snapshot.subscriptions.count { it.isEnabled },
                            total = snapshot.subscriptions.size,
                            label = "订阅",
                            icon = Icons.Rounded.RssFeed,
                            tint = palette.indigo,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            value = enabledPoolNodes.size,
                            label = "启用节点",
                            icon = Icons.Rounded.Dns,
                            tint = palette.primary,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            value = coverageByCountry.size,
                            label = "地区",
                            icon = Icons.Rounded.Public,
                            tint = palette.teal,
                            modifier = Modifier.weight(1f),
                        )
                        MetricPill(
                            value = selectedCount,
                            label = "已选",
                            icon = Icons.Rounded.CheckCircle,
                            tint = palette.emerald,
                            onClick = if (selectedCountryCode != null) {
                                { selectedCountryCode = null }
                            } else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = palette.divider)
                    WorldDotMapCanvas(
                        mapData = worldMap,
                        coverageByCountry = coverageByCountry,
                        selectedCountryCode = selectedCountryCode,
                        onSelectCountry = { code -> selectedCountryCode = code },
                    )
                }
            }

            if (snapshot.subscriptions.isNotEmpty()) {
                item(key = "subs_heading") {
                    SectionHeading(
                        title = "订阅",
                        detail = "${snapshot.subscriptions.size} 个来源",
                    )
                }
                items(snapshot.subscriptions, key = { "sub_${it.id}" }) { source ->
                    SubscriptionCard(
                        source = source,
                        nodes = nodesBySub[source.id].orEmpty(),
                        runtimeMap = runtimeMap,
                        excludedNodeIds = excludedSet,
                        isRefreshing = refreshingSourceIds.contains(source.id),
                        onToggleEnabled = { viewModel.toggleSubscriptionEnabled(source) },
                        onRefresh = { viewModel.refreshSubscription(source) },
                        onEdit = { name, url, ua, doh ->
                            scope.launch {
                                val opts = SubscriptionRequestOptions(ua, doh).takeIf { !it.isEmpty }
                                viewModel.updateSubscription(
                                    id = source.id,
                                    name = name,
                                    urlString = url,
                                    requestOptions = opts,
                                ).onSuccess {
                                    viewModel.showToast("已保存订阅修改")
                                }.onFailure { err ->
                                    viewModel.showToast("更新失败：${err.message}")
                                }
                            }
                        },
                        onDelete = { viewModel.deleteSubscription(source) },
                        onNodeClick = { node -> selectedDetailNodeId = node.id },
                        onToggleNodeExcluded = { node, excluded ->
                            viewModel.toggleNodeExcluded(node, excluded)
                        },
                    )
                }
            }

            // Nodes filter & list section
            item(key = "nodes_header") {
                val selectedCountryInfo = selectedCountryCode?.let { coverageByCountry[it] }
                NodeSectionHeader(
                    totalVisibleCount = filteredNodes.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    inclusionFilter = inclusionFilter,
                    onInclusionFilterChange = { inclusionFilter = it },
                    selectedProtocol = selectedProtocol,
                    onSelectedProtocolChange = { selectedProtocol = it },
                    availableProtocols = availableProtocols,
                    selectedCountryCode = selectedCountryCode,
                    selectedCountryLabel = selectedCountryInfo?.let { "${it.flagEmoji} ${it.name}".trim() },
                    onClearCountryFilter = { selectedCountryCode = null },
                    isTestingLatency = isTestingLatency,
                    onTestVisibleLatencies = {
                        viewModel.testNodeLatency(filteredNodes.map { it.id })
                    },
                    activeExportFilter = snapshot.nodeExportNameFilter,
                    onOpenExportFilterDialog = { showExportFilterDialog = true },
                )
            }

            if (filteredNodes.isEmpty()) {
                item(key = "nodes_empty") {
                    TowerCard(contentPadding = PaddingValues(18.dp)) {
                        Text(
                            text = "没有符合当前筛选条件的节点。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                    }
                }
            } else {
                items(filteredNodes, key = { "node_${it.id}" }) { node ->
                    TowerCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        CompactNodeRow(
                            node = node,
                            runtime = runtimeMap[node.id],
                            isIncluded = !excludedSet.contains(node.id),
                            onClick = { selectedDetailNodeId = node.id },
                            onToggleIncluded = { included ->
                                viewModel.toggleNodeExcluded(node, !included)
                            },
                        )
                    }
                }
            }

            item(key = "footer_continue") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PrivacyBadge()
                    TintedActionButton(
                        text = "继续选择规则",
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        onClick = { viewModel.selectTab(AppTab.RULES) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddSourceSheet(
            initialSharedText = sharedDraftText,
            detectSourceInput = viewModel::detectSourceInput,
            onAddSubscription = { url, name, opts ->
                viewModel.addSubscription(url, name, opts)
            },
            onImportNodes = { content ->
                viewModel.importNodesFromText(content)
            },
            onDismiss = {
                showAddSheet = false
                sharedDraftText = null
            },
        )
    }

    val detailNode = selectedDetailNodeId?.let { id -> snapshot.nodes.firstOrNull { it.id == id } }
    if (detailNode != null) {
        val payload = remember(detailNode.id, detailNode.name) {
            viewModel.getNodeSharePayload(detailNode.id)
        }
        NodeDetailSheet(
            node = detailNode,
            runtime = runtimeMap[detailNode.id],
            countries = countries,
            sharePayload = payload,
            onDismiss = { selectedDetailNodeId = null },
            onRename = { newName -> viewModel.updateNodeName(detailNode, newName) },
            onSelectCountryOverride = { code -> viewModel.setNodeCountryOverride(detailNode, code) },
            onTestLatency = { viewModel.testNodeLatency(listOf(detailNode.id)) },
            onDeleteOwnNode = { viewModel.deleteNode(detailNode) },
            onCopiedToast = { msg -> viewModel.showToast(msg) },
        )
    }

    if (showExportFilterDialog) {
        NodeExportFilterDialog(
            initialFilter = snapshot.nodeExportNameFilter,
            onDismiss = { showExportFilterDialog = false },
            onSave = { filter -> viewModel.updateNodeExportFilter(filter) },
        )
    }
}
