package com.jzb.tower.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.BuildConfig
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.SectionHeading
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.ToastTone
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.DnsProtectionMode
import com.jzb.tower.core.model.NodeLatencyTestMode
import com.jzb.tower.core.model.ThemePreference
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: TowerViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val settings = state.snapshot.settings
    val tailnets = state.snapshot.tailnets

    var configNameDraft by remember(settings.configurationName) {
        mutableStateOf(settings.configurationName)
    }
    var showTailnetSheet by remember { mutableStateOf(false) }
    var resetStep by remember { mutableStateOf(0) } // 0 = none, 1 = first confirm, 2 = final confirm

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TowerDimens.PagePadding,
            end = TowerDimens.PagePadding,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "settings_header") {
            Column {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.textPrimary,
                )
                Text(
                    text = "配置生成、测速、DNS 保护与本机备份",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }

        // Configuration Name & Node / Export options
        item(key = "node_export_card") {
            TowerCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeading(title = "节点与配置", detail = "默认保持原始订阅")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = configNameDraft,
                            onValueChange = { configNameDraft = it },
                            label = { Text("配置名称") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (configNameDraft.trim() != settings.configurationName && configNameDraft.isNotBlank()) {
                            TintedActionButton(
                                text = "保存",
                                onClick = {
                                    viewModel.updateSettings {
                                        it.copy(configurationName = configNameDraft.trim())
                                    }
                                    viewModel.showToast("已保存配置名称", ToastTone.SUCCESS)
                                },
                                modifier = Modifier.height(48.dp),
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "节点附加订阅名称",
                        subtitle = "节点名后显示来源名称，便于区分多个订阅服务。",
                        checked = settings.appendSubscriptionNameToNodes,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(appendSubscriptionNameToNodes = v) }
                        },
                    )
                    HorizontalDivider(color = palette.divider)

                    SettingsSwitchRow(
                        title = "过滤订阅节点信息",
                        subtitle = "开启后自动隐藏流量、到期、官网与客服等信息节点。",
                        checked = settings.filterSubscriptionInfoNodes,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(filterSubscriptionInfoNodes = v) }
                        },
                    )
                    HorizontalDivider(color = palette.divider)

                    SettingsSwitchRow(
                        title = "打开塔台时自动更新订阅",
                        subtitle = "关闭时仅在手动刷新时联网拉取订阅。",
                        checked = settings.autoRefreshOnOpen,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(autoRefreshOnOpen = v) }
                        },
                    )
                    HorizontalDivider(color = palette.divider)

                    SettingsSwitchRow(
                        title = "优先使用规则集",
                        subtitle = "兼容时引用远程规则集，不兼容的客户端自动保留内联本地规则。",
                        checked = settings.preferRuleSets,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(preferRuleSets = v) }
                        },
                    )
                    HorizontalDivider(color = palette.divider)

                    SettingsSwitchRow(
                        title = "代理集合（嵌入远程订阅链接）",
                        subtitle = "将原始订阅链接写入配置文件交由客户端更新（不支持 Hiddify / V2Box / Shadowrocket）。",
                        checked = settings.embedRemoteSubscriptionLinks,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(embedRemoteSubscriptionLinks = v) }
                        },
                    )
                }
            }
        }

        // DNS, Latency & Theme
        item(key = "modes_card") {
            TowerCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeading(title = "网络与外观")

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = palette.primary, modifier = Modifier.size(18.dp))
                            Text("DNS 保护模式", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            DnsProtectionMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = settings.dnsProtectionMode == mode,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(dnsProtectionMode = mode) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(idx, DnsProtectionMode.entries.size),
                                ) {
                                    Text(FormatUtils.dnsProtectionTitle(mode), maxLines = 1)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = palette.emerald, modifier = Modifier.size(18.dp))
                            Text("延迟测速方式", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            NodeLatencyTestMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = settings.latencyTestMode == mode,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(latencyTestMode = mode) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(idx, NodeLatencyTestMode.entries.size),
                                ) {
                                    Text(
                                        when (mode) {
                                            NodeLatencyTestMode.AUTOMATIC -> "自动"
                                            NodeLatencyTestMode.ICMP -> "ICMP"
                                            NodeLatencyTestMode.TCP -> "TCP"
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.divider)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Palette, contentDescription = null, tint = palette.indigo, modifier = Modifier.size(18.dp))
                            Text("外观主题", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemePreference.entries.forEachIndexed { idx, theme ->
                                SegmentedButton(
                                    selected = settings.theme == theme,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(theme = theme) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(idx, ThemePreference.entries.size),
                                ) {
                                    Text(
                                        when (theme) {
                                            ThemePreference.SYSTEM -> "跟随系统"
                                            ThemePreference.LIGHT -> "浅色"
                                            ThemePreference.DARK -> "深色"
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tailscale Inner Network
        item(key = "tailnet_card") {
            val enabledCount = tailnets.count { it.isEnabled }
            TowerCard(
                onClick = { showTailnetSheet = true },
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsIconBox(icon = Icons.Rounded.Hub, tint = palette.indigo)
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

        // Backup & Restore
        item(key = "backup_card") {
            TowerCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading(title = "备份与恢复", detail = "本地 JSON 文件")
                    Text(
                        text = "导出与恢复所有订阅、节点、规则定制与偏好设置（出于安全原因，Tailscale Auth Key 永不导出）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TintedActionButton(
                            text = "导出备份 JSON",
                            icon = Icons.Rounded.Upload,
                            onClick = { exportBackupLauncher.launch("tower-backup.json") },
                            modifier = Modifier.weight(1f),
                        )
                        TintedActionButton(
                            text = "导入备份 JSON",
                            icon = Icons.Rounded.Download,
                            onClick = {
                                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Replay Onboarding & Reset All Data
        item(key = "replay_reset_card") {
            TowerCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.replayOnboarding() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsIconBox(icon = Icons.Rounded.AutoAwesome, tint = palette.amber)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("重看使用引导", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                            Text("再次查看本机隐私与工作流介绍", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textTertiary)
                    }

                    HorizontalDivider(color = palette.divider)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { resetStep = 1 }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsIconBox(icon = Icons.Rounded.DeleteForever, tint = palette.coral)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("重置所有数据", style = MaterialTheme.typography.titleSmall, color = palette.coral)
                            Text("清除本机全部订阅、节点、规则自定义与密钥", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                        }
                    }
                }
            }
        }

        // Privacy & Version Footer
        item(key = "settings_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrivacyBadge()
                Text(
                    text = "塔台 Tower · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · 离线 IP 库 (${state.ipDbVersion})",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary,
                )
            }
        }
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
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = TowerTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsIconBox(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
