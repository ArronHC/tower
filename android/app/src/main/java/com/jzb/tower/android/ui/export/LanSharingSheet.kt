package com.jzb.tower.android.ui.export

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jzb.tower.android.ui.AndroidPlatformActions
import com.jzb.tower.android.ui.components.QrCodeView
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.ExportContentMode
import com.jzb.tower.core.model.LanSharingStatus
import com.jzb.tower.core.model.LanSubscriptionFormat
import com.jzb.tower.core.model.QrMatrix

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanSharingSheet(
    lanStatus: LanSharingStatus,
    onToggleSharing: (Boolean) -> Unit,
    onRotateToken: () -> Unit,
    buildUrl: (LanSubscriptionFormat, ExportContentMode, String?) -> String,
    generateQr: (String) -> QrMatrix?,
    onCopiedToast: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFormat by remember { mutableStateOf(LanSubscriptionFormat.AUTO) }
    var contentMode by remember { mutableStateOf(ExportContentMode.FULL_CONFIGURATION) }
    var showRotateConfirm by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Even if notification is denied, start the LAN sharing service
        onToggleSharing(true)
    }

    val activeUrl = remember(lanStatus, selectedFormat, contentMode) {
        if (!lanStatus.enabled) "" else {
            val base = lanStatus.primaryURL.takeIf { it.isNotBlank() } ?: lanStatus.urls.firstOrNull()
            buildUrl(selectedFormat, contentMode, base)
        }
    }
    val qrMatrix = remember(activeUrl) {
        if (activeUrl.isNotBlank()) generateQr(activeUrl) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.groupedBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "局域网共享订阅",
                style = MaterialTheme.typography.headlineSmall,
                color = palette.textPrimary,
            )

            TowerCard(contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiTethering,
                            contentDescription = null,
                            tint = if (lanStatus.enabled) palette.emerald else palette.textSecondary,
                            modifier = Modifier.size(26.dp),
                        )
                        Column {
                            Text(
                                text = if (lanStatus.enabled) "正在局域网共享（端口 ${lanStatus.port}）" else "共享服务已关闭",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = if (lanStatus.enabled) "同一 Wi-Fi 下的设备可扫描二维码或拉取订阅" else "仅在主动开启后监听局域网请求",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                    }
                    Switch(
                        checked = lanStatus.enabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onToggleSharing(true)
                                }
                            } else {
                                onToggleSharing(enable)
                            }
                        },
                    )
                }
            }

            if (lanStatus.enabled) {
                TowerCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "链接格式",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ClientCatalog.LAN_FORMAT_OPTIONS.forEach { option ->
                                FilterChip(
                                    selected = selectedFormat == option.id,
                                    onClick = { selectedFormat = option.id },
                                    label = { Text(option.label) },
                                )
                            }
                        }

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = contentMode == ExportContentMode.FULL_CONFIGURATION,
                                onClick = { contentMode = ExportContentMode.FULL_CONFIGURATION },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("完整配置") }
                            SegmentedButton(
                                selected = contentMode == ExportContentMode.NODES_ONLY,
                                onClick = { contentMode = ExportContentMode.NODES_ONLY },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("仅节点") }
                        }

                        if (activeUrl.isNotBlank()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                QrCodeView(matrix = qrMatrix, size = 200.dp)
                                Text(
                                    text = "使用其他设备客户端扫码添加",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }

                            Text(
                                text = activeUrl,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                ),
                                color = palette.textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
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
                                        AndroidPlatformActions.copyToClipboard(context, "塔台局域网订阅", activeUrl)
                                        onCopiedToast("已复制局域网订阅链接")
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                TintedActionButton(
                                    text = "分享链接",
                                    icon = Icons.Rounded.Share,
                                    onClick = {
                                        AndroidPlatformActions.shareText(context, "塔台局域网订阅", activeUrl)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (lanStatus.urls.size > 1) {
                            Text(
                                text = "其他可用本机地址",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textSecondary,
                            )
                            lanStatus.urls.forEach { base ->
                                val full = buildUrl(selectedFormat, contentMode, base)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = full,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            AndroidPlatformActions.copyToClipboard(context, "局域网订阅", full)
                                            onCopiedToast("已复制链接")
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "复制",
                                            tint = palette.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TintedActionButton(
                    text = "更换访问密钥（轮换 Token）",
                    icon = Icons.Rounded.Key,
                    tint = palette.amber,
                    onClick = { showRotateConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Text(
                    text = "提示：请确保接收设备与本机在同一局域网（Wi-Fi 或热点），Android 将保持前台通知以确保服务不被系统后台挂起。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }
    }

    if (showRotateConfirm) {
        AlertDialog(
            onDismissRequest = { showRotateConfirm = false },
            title = { Text("更换访问密钥？") },
            text = { Text("更换后将生成新的随机 Token，所有此前添加到其他设备的旧订阅链接都会立即失效。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRotateConfirm = false
                        onRotateToken()
                    },
                ) {
                    Text("更换密钥", color = palette.amber, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotateConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}
