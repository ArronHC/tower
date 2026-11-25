package com.jzb.tower.android.ui.export

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.jzb.tower.android.ui.components.TowerIosSwitch
import com.jzb.tower.android.ui.components.TowerSegmentedControl
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.ExportContentMode
import com.jzb.tower.core.model.LanSharingStatus
import com.jzb.tower.core.model.LanSubscriptionFormat
import com.jzb.tower.core.model.QrMatrix

/**
 * Inline LAN sharing destination card displayed in ExportScreen when the LAN router icon is selected.
 */
@Composable
fun InlineLanSharingCard(
    lanStatus: LanSharingStatus,
    onToggleSharing: (Boolean) -> Unit,
    onRotateToken: () -> Unit,
    buildUrl: (LanSubscriptionFormat, ExportContentMode, String?) -> String,
    generateQr: (String) -> QrMatrix?,
    onCopiedToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current

    var selectedFormat by remember { mutableStateOf(LanSubscriptionFormat.AUTO) }
    var contentMode by remember { mutableStateOf(ExportContentMode.FULL_CONFIGURATION) }
    var showRotateConfirm by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TowerCard(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
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
                        Column {
                            Text(
                                text = if (lanStatus.enabled) "局域网共享已开启" else "局域网共享",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.textPrimary,
                            )
                            Text(
                                text = if (lanStatus.enabled) "端口 ${lanStatus.port} · 同 Wi-Fi 设备可扫码拉取" else "开启后可向同局域网设备提供订阅",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                    }
                    TowerIosSwitch(
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

                AnimatedVisibility(
                    visible = lanStatus.enabled,
                    enter = fadeIn(TowerMotion.disclosureSpec()) + expandVertically(TowerMotion.disclosureSpec()),
                    exit = fadeOut(TowerMotion.disclosureSpec()) + shrinkVertically(TowerMotion.disclosureSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = palette.divider, thickness = 0.5.dp)

                        TowerSegmentedControl(
                            options = listOf("完整配置", "仅节点"),
                            selectedIndex = if (contentMode == ExportContentMode.FULL_CONFIGURATION) 0 else 1,
                            onSelect = { idx ->
                                contentMode = if (idx == 0) ExportContentMode.FULL_CONFIGURATION else ExportContentMode.NODES_ONLY
                            },
                        )

                        Text(
                            text = "链接格式",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
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

                        if (activeUrl.isNotBlank()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                QrCodeView(matrix = qrMatrix, size = 204.dp)
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
                                    .padding(11.dp),
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
                            HorizontalDivider(color = palette.divider, thickness = 0.5.dp)
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

                        TintedActionButton(
                            text = "更换访问密钥（轮换 Token）",
                            icon = Icons.Rounded.Key,
                            tint = palette.amber,
                            onClick = { showRotateConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        TowerCard(contentPadding = PaddingValues(14.dp)) {
            Text(
                text = "提示：请确保接收设备与本机在同一局域网（Wi-Fi 或热点），Android 将保持前台通知以确保服务不被系统后台挂起。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
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
