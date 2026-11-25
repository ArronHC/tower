package com.jzb.tower.android.ui.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.MetricPill
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingCircleButton
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.AppTab
import com.jzb.tower.core.model.ProxyNode
import com.jzb.tower.core.model.SubscriptionRequestOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val testingNodeIds by viewModel.testingNodeIds.collectAsStateWithLifecycle()
    val latencyProgress by viewModel.latencyTestProgress.collectAsStateWithLifecycle()
    val latencyCompletedTick by viewModel.latencyBatchCompletedTick.collectAsStateWithLifecycle()
    val incomingSharedText by viewModel.incomingSharedText.collectAsStateWithLifecycle()

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showManageSheet by rememberSaveable { mutableStateOf(false) }
    var sharedDraftText by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCountryCode by rememberSaveable { mutableStateOf<String?>(null) }
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
    val ownNodes = remember(snapshot.nodes) {
        snapshot.nodes.filter { it.sourceID == null }
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
            val median = if (latencies.isNotEmpty()) latencies[latencies.size / 2] else null
            val allFailed = latencies.isEmpty() && list.all {
                val m = runtimeMap[it.id]?.latency
                m != null && m.isApplicable && m.milliseconds == null
            }
            CountryCoverageSummary(
                code = code,
                name = firstRuntime?.countryName ?: code,
                flagEmoji = firstRuntime?.flagEmoji ?: "",
                nodeCount = list.size,
                bestOrMedianLatencyMs = median,
                allFailed = allFailed,
                latitude = firstRuntime?.latitude,
                longitude = firstRuntime?.longitude,
            )
        }
    }

    val selectedRegionNodes = remember(enabledPoolNodes, selectedCountryCode, runtimeMap) {
        val code = selectedCountryCode ?: return@remember emptyList()
        enabledPoolNodes.filter { runtimeMap[it.id]?.countryCode.equals(code, ignoreCase = true) }
    }

    val isEmptyState = snapshot.subscriptions.isEmpty() && snapshot.nodes.isEmpty()

    PullToRefreshBox(
        isRefreshing = isRefreshingAll,
        onRefresh = {
            if (snapshot.subscriptions.isNotEmpty()) {
                viewModel.refreshAllSubscriptions()
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = TowerDimens.PagePadding,
                end = TowerDimens.PagePadding,
                top = 8.dp,
                bottom = TowerDimens.FloatingNavClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top floating chrome matching iphone-1.jpg: "管理" pill on left + "+" circle on right, then large bold title
            item(key = "top_chrome") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TowerFloatingPillButton(
                            text = "管理",
                            enabled = !isEmptyState,
                            onClick = { showManageSheet = true },
                        )

                        TowerFloatingCircleButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = "添加订阅或节点",
                            onClick = {
                                sharedDraftText = null
                                showAddSheet = true
                            },
                        )
                    }

                    Text(
                        text = "我的订阅",
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.textPrimary,
                    )
                }
            }

            if (isEmptyState) {
                item(key = "empty_state") {
                    TowerCard(contentPadding = PaddingValues(24.dp)) {
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
                // ONE unified elevated Overview + World Dot Map card (iphone-1.jpg)
                item(key = "map_metrics_card") {
                    TowerCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricPill(
                                value = enabledPoolNodes.size,
                                label = "节点",
                                icon = Icons.Rounded.Language,
                                tint = palette.primary,
                                onClick = { showManageSheet = true },
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
                                value = snapshot.subscriptions.count { it.isEnabled },
                                total = snapshot.subscriptions.size,
                                label = "订阅",
                                icon = Icons.Rounded.Sensors,
                                tint = palette.purple,
                                modifier = Modifier.weight(1f),
                            )
                            MetricPill(
                                value = ownNodes.size,
                                label = "自有节点",
                                icon = Icons.Rounded.Dns,
                                tint = palette.orange,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                        WorldDotMapCanvas(
                            mapData = worldMap,
                            coverageByCountry = coverageByCountry,
                            selectedCountryCode = selectedCountryCode,
                            isTestingLatency = isTestingLatency,
                            latencyProgress = latencyProgress,
                            latencyBatchCompletedTick = latencyCompletedTick,
                            hasTestableNodes = enabledPoolNodes.isNotEmpty(),
                            onSelectCountry = { code -> selectedCountryCode = code },
                            onToggleLatencyTest = {
                                if (isTestingLatency) {
                                    viewModel.cancelLatencyTest()
                                } else {
                                    viewModel.testNodeLatency(enabledPoolNodes.map { it.id })
                                }
                            },
                        )
                    }
                }

                // Selected Region Nodes card right beneath the map card (smooth height + opacity)
                item(key = "selected_region_nodes") {
                    val countryInfo = selectedCountryCode?.let { coverageByCountry[it] }
                    AnimatedVisibility(
                        visible = countryInfo != null && selectedRegionNodes.isNotEmpty(),
                        enter = fadeIn(TowerMotion.disclosureSpec()) + expandVertically(TowerMotion.disclosureSpec()),
                        exit = fadeOut(TowerMotion.disclosureSpec()) + shrinkVertically(TowerMotion.disclosureSpec()),
                    ) {
                        if (countryInfo != null) {
                            TowerCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (countryInfo.flagEmoji.isNotBlank()) {
                                                Text(text = countryInfo.flagEmoji, fontSize = 20.sp)
                                            }
                                            Text(
                                                text = countryInfo.name,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = palette.textPrimary,
                                            )
                                            Text(
                                                text = "${selectedRegionNodes.size} 个节点",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = palette.textSecondary,
                                            )
                                        }
                                        IconButton(
                                            onClick = { selectedCountryCode = null },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "收起地区节点",
                                                tint = palette.textSecondary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
                                    selectedRegionNodes.forEachIndexed { idx, node ->
                                        CompactNodeRow(
                                            node = node,
                                            runtime = runtimeMap[node.id],
                                            isIncluded = !excludedSet.contains(node.id),
                                            isTestingLatency = testingNodeIds.contains(node.id),
                                            onClick = { selectedDetailNodeId = node.id },
                                            onToggleIncluded = { included ->
                                                viewModel.toggleNodeExcluded(node, !included)
                                            },
                                        )
                                        if (idx < selectedRegionNodes.lastIndex) {
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
                            testingNodeIds = testingNodeIds,
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

                if (ownNodes.isNotEmpty()) {
                    item(key = "own_nodes_heading") {
                        SectionHeading(
                            title = "自有节点",
                            detail = "${ownNodes.size} 个",
                        )
                    }
                    item(key = "own_nodes_card") {
                        TowerCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                            ownNodes.forEachIndexed { idx, node ->
                                CompactNodeRow(
                                    node = node,
                                    runtime = runtimeMap[node.id],
                                    isIncluded = !excludedSet.contains(node.id),
                                    isTestingLatency = testingNodeIds.contains(node.id),
                                    onClick = { selectedDetailNodeId = node.id },
                                    onToggleIncluded = { included ->
                                        viewModel.toggleNodeExcluded(node, !included)
                                    },
                                )
                                if (idx < ownNodes.lastIndex) {
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
                            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                            onClick = { viewModel.selectTab(AppTab.RULES) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showManageSheet) {
        ManageNodesModalSheet(
            nodes = snapshot.nodes.filter { it.sourceID == null || enabledSubIds.contains(it.sourceID) },
            runtimeMap = runtimeMap,
            excludedNodeIds = excludedSet,
            testingNodeIds = testingNodeIds,
            isTestingLatency = isTestingLatency,
            activeExportFilter = snapshot.nodeExportNameFilter,
            onToggleNodeExcluded = { node, excluded -> viewModel.toggleNodeExcluded(node, excluded) },
            onSetNodesExcluded = { ids, excluded -> viewModel.setNodesExcluded(ids, excluded) },
            onTestVisibleLatencies = { ids -> viewModel.testNodeLatency(ids) },
            onNodeClick = { node -> selectedDetailNodeId = node.id },
            onOpenExportFilter = { showExportFilterDialog = true },
            onDismiss = { showManageSheet = false },
        )
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
