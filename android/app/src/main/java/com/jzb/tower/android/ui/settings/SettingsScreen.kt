package com.jzb.tower.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.BuildConfig
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.ToastTone
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFilledSquircleIconBadge
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerIosSwitch
import com.jzb.tower.android.ui.components.TowerSegmentedControl
import com.jzb.tower.android.ui.components.TowerSquircleIconBadge
import com.jzb.tower.android.ui.components.towerPressScale
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.DnsProtectionMode
import com.jzb.tower.core.model.NodeLatencyTestMode
import com.jzb.tower.core.model.ThemePreference
import kotlinx.coroutines.launch

/**
 * Settings screen presented inside a modal sheet matching iphone-5.jpg + Mobbin Luma/Aave/Perplexity.
 */
@Composable
fun SettingsScreen(
    viewModel: TowerViewModel,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val settings = state.snapshot.settings
    val tailnets = state.snapshot.tailnets

    var showConfigNameDialog by remember { mutableStateOf(false) }
    var showTailnetSheet by remember { mutableStateOf(false) }
    var resetStep by remember { mutableIntStateOf(0) } // 0 = none, 1 = first confirm, 2 = final confirm

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.exportBackupJson().onSuccess { jsonText ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开输出文件")
                }.onSuccess {
                    viewModel.showToast("已导出配置备份（不含 Tailnet 密钥）", ToastTone.SUCCESS)
                }.onFailure { err ->
                    viewModel.showToast("导出失败：${err.message}", ToastTone.ERROR)
                }
            }.onFailure { err ->
                viewModel.showToast("生成备份失败：${err.message}", ToastTone.ERROR)
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val readResult = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("无法读取所选文件")
            }
            readResult.onSuccess { text ->
                viewModel.importBackupJson(text).onFailure { err ->
                    viewModel.showToast("导入失败：${err.message}", ToastTone.ERROR)
                }
            }.onFailure { err ->
                viewModel.showToast("读取失败：${err.message}", ToastTone.ERROR)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Modal top bar matching iphone-5.jpg: centered "设置" + right "完成" pill
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = palette.textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            TowerFloatingPillButton(
                text = "完成",
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Section 1: 节点与配置 (iphone-5.jpg)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading(title = "节点与配置", detail = "默认保持原始订阅")
                TowerCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.LocalOffer,
                        iconColor = palette.primary,
                        filledBadge = true,
                        title = "节点附加订阅名称",
                        subtitle = "节点名后显示来源名称，便于区分多个订阅服务。",
                        checked = settings.appendSubscriptionNameToNodes,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(appendSubscriptionNameToNodes = v) }
                        },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.FilterList,
                        iconColor = palette.orange,
                        filledBadge = true,
                        title = "过滤订阅节点信息",
                        subtitle = "开启后隐藏流量、到期、官网和客服等信息节点。",
                        checked = settings.filterSubscriptionInfoNodes,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(filterSubscriptionInfoNodes = v) }
                        },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Layers,
                        iconColor = palette.purple,
                        filledBadge = true,
                        title = "优先使用规则集",
                        subtitle = "兼容时引用远程规则集，不兼容的客户端自动保留本地规则。",
                        checked = settings.preferRuleSets,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(preferRuleSets = v) }
                        },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Sync,
                        iconColor = palette.emerald,
                        filledBadge = true,
                        title = "代理集合",
                        subtitle = "原始订阅链接直接写入配置文件，交由客户端更新。不支持 Hiddify、V2Box 和 Shadowrocket。",
                        checked = settings.embedRemoteSubscriptionLinks,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(embedRemoteSubscriptionLinks = v) }
                        },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Description,
                        iconColor = palette.cyan,
                        title = "配置名称",
                        trailingValue = settings.configurationName,
                        onClick = { showConfigNameDialog = true },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Refresh,
                        iconColor = palette.indigo,
                        filledBadge = false,
                        title = "打开塔台时自动更新订阅",
                        subtitle = "关闭时仅在手动刷新时联网拉取订阅。",
                        checked = settings.autoRefreshOnOpen,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(autoRefreshOnOpen = v) }
                        },
                    )
                }
            }

            // Section 2: 连接 (Tailscale 内网)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading(title = "连接")
                val enabledCount = tailnets.count { it.isEnabled }
                TowerCard(
                    onClick = { showTailnetSheet = true },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TowerSquircleIconBadge(
                            icon = Icons.Rounded.Hub,
                            backgroundColor = palette.indigo,
                            size = 34.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tailscale 内网",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = if (tailnets.isEmpty()) {
                                    "代理时也能访问家里电脑等 Tailscale 设备"
                                } else {
                                    "已配置 ${tailnets.size} 个 · 已启用 $enabledCount 个"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = palette.textTertiary,
                        )
                    }
                }
            }

            // Section 3: 外观与测速 (Perplexity 3-tile theme picker + DNS + Latency mode)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading(title = "外观与测速")
                TowerCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "外观",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                        )
                        // Perplexity-style 3-tile Appearance picker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ThemePickerTile(
                                icon = Icons.Rounded.LightMode,
                                label = "浅色",
                                selected = settings.theme == ThemePreference.LIGHT,
                                onClick = { viewModel.updateSettings { it.copy(theme = ThemePreference.LIGHT) } },
                                modifier = Modifier.weight(1f),
                            )
                            ThemePickerTile(
                                icon = Icons.Rounded.DarkMode,
                                label = "深色",
                                selected = settings.theme == ThemePreference.DARK,
                                onClick = { viewModel.updateSettings { it.copy(theme = ThemePreference.DARK) } },
                                modifier = Modifier.weight(1f),
                            )
                            ThemePickerTile(
                                icon = Icons.Rounded.Contrast,
                                label = "跟随系统",
                                selected = settings.theme == ThemePreference.SYSTEM,
                                onClick = { viewModel.updateSettings { it.copy(theme = ThemePreference.SYSTEM) } },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TowerSquircleIconBadge(icon = Icons.Rounded.Security, backgroundColor = palette.primary, size = 28.dp)
                            Text("DNS 保护模式", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        }
                        TowerSegmentedControl(
                            options = DnsProtectionMode.entries.map { FormatUtils.dnsProtectionTitle(it) },
                            selectedIndex = DnsProtectionMode.entries.indexOf(settings.dnsProtectionMode).coerceAtLeast(0),
                            onSelect = { idx ->
                                val mode = DnsProtectionMode.entries[idx]
                                viewModel.updateSettings { it.copy(dnsProtectionMode = mode) }
                            },
                        )

                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TowerSquircleIconBadge(icon = Icons.Rounded.Speed, backgroundColor = palette.emerald, size = 28.dp)
                            Text("延迟测速方式", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        }
                        TowerSegmentedControl(
                            options = NodeLatencyTestMode.entries.map {
                                when (it) {
                                    NodeLatencyTestMode.AUTOMATIC -> "自动"
                                    NodeLatencyTestMode.ICMP -> "ICMP"
                                    NodeLatencyTestMode.TCP -> "TCP"
                                }
                            },
                            selectedIndex = NodeLatencyTestMode.entries.indexOf(settings.latencyTestMode).coerceAtLeast(0),
                            onSelect = { idx ->
                                val mode = NodeLatencyTestMode.entries[idx]
                                viewModel.updateSettings { it.copy(latencyTestMode = mode) }
                            },
                        )
                    }
                }
            }

            // Section 4: 备份与重置
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading(title = "备份与重置", detail = "本地 JSON 文件")
                TowerCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Upload,
                        iconColor = palette.primary,
                        title = "导出备份 JSON",
                        subtitle = "不含 Tailscale Auth Key",
                        onClick = { exportBackupLauncher.launch("tower-backup.json") },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Download,
                        iconColor = palette.teal,
                        title = "导入备份 JSON",
                        subtitle = "从本地文件恢复订阅、节点与规则定制",
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        iconColor = palette.amber,
                        title = "重看使用引导",
                        subtitle = "再次查看本机隐私与工作流介绍",
                        onClick = { viewModel.replayOnboarding() },
                    )
                    HorizontalDivider(
                        color = palette.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 60.dp),
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.DeleteForever,
                        iconColor = palette.coral,
                        title = "重置所有数据",
                        titleColor = palette.coral,
                        subtitle = "清除本机全部订阅、节点、规则自定义与密钥",
                        showChevron = false,
                        onClick = { resetStep = 1 },
                    )
                }
            }

            // Privacy & Version Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrivacyBadge()
                Text(
                    text = "塔台 Tower · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · 离线 IP 库 (${state.ipDbVersion})",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showConfigNameDialog) {
        var nameDraft by remember(settings.configurationName) {
            mutableStateOf(settings.configurationName)
        }
        AlertDialog(
            onDismissRequest = { showConfigNameDialog = false },
            title = { Text("配置名称") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("导出配置名称（如 Tower / 塔台）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameDraft.isNotBlank(),
                    onClick = {
                        viewModel.updateSettings { it.copy(configurationName = nameDraft.trim()) }
                        viewModel.showToast("已保存配置名称", ToastTone.SUCCESS)
                        showConfigNameDialog = false
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigNameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showTailnetSheet) {
        TailnetSettingsSheet(
            tailnets = tailnets,
            onSaveTailnet = viewModel::saveTailnet,
            onToggleEnabled = viewModel::toggleTailnetEnabled,
            onDelete = viewModel::deleteTailnet,
            onDismiss = { showTailnetSheet = false },
        )
    }

    if (resetStep == 1) {
        AlertDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text("重置所有配置？") },
            text = {
                Text("将清除这台设备上的全部订阅、自有节点、规则自定义、导出偏好与 Tailscale Auth Key，并停止局域网共享。此操作无法撤销。")
            },
            confirmButton = {
                TextButton(onClick = { resetStep = 2 }) {
                    Text("继续", color = palette.coral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetStep = 0 }) {
                    Text("取消")
                }
            },
        )
    } else if (resetStep == 2) {
        AlertDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text("二次确认：确定清空全部数据？") },
            text = { Text("清空后将无法恢复，请确保已导出需要保留的备份文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetStep = 0
                        viewModel.resetAllData()
                    },
                ) {
                    Text("立即清空", color = palette.coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetStep = 0 }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconColor: Color,
    filledBadge: Boolean,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = TowerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (filledBadge) {
            TowerFilledSquircleIconBadge(
                icon = icon,
                backgroundColor = iconColor,
                size = 32.dp,
            )
        } else {
            TowerSquircleIconBadge(
                icon = icon,
                backgroundColor = iconColor,
                size = 32.dp,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = palette.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
        TowerIosSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    trailingValue: String? = null,
    titleColor: Color = TowerTheme.palette.textPrimary,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = TowerTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TowerSquircleIconBadge(
            icon = icon,
            backgroundColor = iconColor,
            size = 32.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = titleColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }
        if (trailingValue != null) {
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ThemePickerTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) palette.primaryContainer else palette.cardSubtleSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.75.dp,
            color = if (selected) palette.primary else palette.cardBorder,
        ),
        modifier = modifier
            .height(68.dp)
            .towerPressScale(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) palette.primary else palette.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) palette.primary else palette.textPrimary,
                maxLines = 1,
            )
        }
    }
}
