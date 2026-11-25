package com.jzb.tower.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.TailnetConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailnetSettingsSheet(
    tailnets: List<TailnetConnection>,
    onSaveTailnet: suspend (
        existingId: String?,
        name: String,
        controlURLString: String?,
        deviceName: String?,
        subnets: List<String>,
        magicDNSSuffix: String?,
        isEnabled: Boolean,
        authKey: String?,
        clearAuthKey: Boolean,
    ) -> Result<Unit>,
    onToggleEnabled: (TailnetConnection) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingTarget by remember { mutableStateOf<TailnetConnection?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tailscale 内网",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                TintedActionButton(
                    text = "添加内网",
                    icon = Icons.Rounded.Add,
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.height(38.dp),
                )
            }

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Text(
                    text = "开启代理时也可访问家里的电脑与局域网设备。Tailnet 不会进入代理节点或策略组，仅接收置顶的 Tailscale 地址段、MagicDNS 后缀与自填子网路由。\n" +
                        "Auth Key 使用 Android KeyStore AES-GCM 硬件级加密存储于本机，绝不写入配置快照或导出 JSON；Surge 仅写入 interactive-login。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }

            if (tailnets.isEmpty()) {
                TowerCard(contentPadding = PaddingValues(18.dp)) {
                    Text(
                        text = "尚未添加 Tailscale 内网配置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
            } else {
                tailnets.forEach { conn ->
                    TowerCard(
                        onClick = { editingTarget = conn },
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = conn.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (conn.hasAuthKey) palette.emeraldSurface else palette.cardSubtleSurface,
                                    ) {
                                        Text(
                                            text = if (conn.hasAuthKey) "已保存密钥" else "无密钥",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (conn.hasAuthKey) palette.emerald else palette.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append(conn.magicDNSSuffix ?: conn.controlURLString ?: "官方控制服务器")
                                        if (conn.subnets.isNotEmpty()) append(" · ${conn.subnets.size} 个子网")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                )
                            }
                            Switch(
                                checked = conn.isEnabled,
                                onCheckedChange = { onToggleEnabled(conn) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingTarget != null) {
        TailnetEditorDialog(
            existing = editingTarget,
            onDismiss = {
                showCreateDialog = false
                editingTarget = null
            },
            onSave = { name, controlUrl, deviceName, subnets, magicDns, authKey, clearKey ->
                onSaveTailnet(
                    editingTarget?.id,
                    name,
                    controlUrl,
                    deviceName,
                    subnets,
                    magicDns,
                    editingTarget?.isEnabled ?: true,
                    authKey,
                    clearKey,
                )
            },
            onDelete = editingTarget?.let { target ->
                {
                    onDelete(target.id)
                    editingTarget = null
                }
            },
        )
    }
}

@Composable
private fun TailnetEditorDialog(
    existing: TailnetConnection?,
    onDismiss: () -> Unit,
    onSave: suspend (
        name: String,
        controlURLString: String?,
        deviceName: String?,
        subnets: List<String>,
        magicDNSSuffix: String?,
        authKey: String?,
        clearAuthKey: Boolean,
    ) -> Result<Unit>,
    onDelete: (() -> Unit)?,
) {
    val palette = TowerTheme.palette
    val scope = rememberCoroutineScope()

    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "Tailscale") }
    var controlUrl by remember(existing?.id) { mutableStateOf(existing?.controlURLString.orEmpty()) }
    var authKeyInput by remember(existing?.id) { mutableStateOf("") }
    var clearSavedKey by remember(existing?.id) { mutableStateOf(false) }
    var revealKey by remember { mutableStateOf(false) }
    var subnetsText by remember(existing?.id) { mutableStateOf(existing?.subnets?.joinToString("\n").orEmpty()) }
    var magicDns by remember(existing?.id) { mutableStateOf(existing?.magicDNSSuffix.orEmpty()) }
    var deviceName by remember(existing?.id) { mutableStateOf(existing?.deviceName.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (existing == null) "添加 Tailscale 内网" else "编辑 Tailscale 内网") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = controlUrl,
                    onValueChange = { controlUrl = it; errorMsg = null },
                    label = { Text("控制服务器（留空为官方）") },
                    placeholder = { Text("https://headscale.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = authKeyInput,
                    onValueChange = {
                        authKeyInput = it
                        if (it.isNotEmpty()) clearSavedKey = false
                        errorMsg = null
                    },
                    label = {
                        Text(
                            if (existing?.hasAuthKey == true && !clearSavedKey) {
                                "Auth Key（留空保持已保存密钥）"
                            } else {
                                "Auth Key（可选，tskey-auth-…）"
                            },
                        )
                    },
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealKey = !revealKey }) {
                            Icon(
                                imageVector = if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (revealKey) "隐藏" else "显示",
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing?.hasAuthKey == true && !clearSavedKey) {
                    TextButton(
                        onClick = {
                            clearSavedKey = true
                            authKeyInput = ""
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("清除已保存的 Auth Key", color = palette.coral)
                    }
                }
                Text(
                    text = "Auth Key 仅存 Android KeyStore，绝不随快照导出；Surge 使用 interactive-login，不会写入 Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                OutlinedTextField(
                    value = subnetsText,
                    onValueChange = { subnetsText = it; errorMsg = null },
                    label = { Text("内网子网（每行一个，可选）") },
                    placeholder = { Text("192.168.1.0/24") },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = magicDns,
                    onValueChange = { magicDns = it; errorMsg = null },
                    label = { Text("MagicDNS 后缀（可选）") },
                    placeholder = { Text("tail1234.ts.net") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("设备名（默认 tower）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.coral,
                    )
                }

                if (onDelete != null) {
                    HorizontalDivider(color = palette.divider)
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = palette.coral)
                        Spacer(Modifier.size(4.dp))
                        Text("删除这个 Tailscale 内网", color = palette.coral)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving && name.isNotBlank(),
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorMsg = null
                        val subnets = subnetsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        val res = onSave(
                            name.trim(),
                            controlUrl.trim().takeIf { it.isNotEmpty() },
                            deviceName.trim().takeIf { it.isNotEmpty() },
                            subnets,
                            magicDns.trim().takeIf { it.isNotEmpty() },
                            authKeyInput.trim().takeIf { it.isNotEmpty() },
                            clearSavedKey,
                        )
                        isSaving = false
                        res.onSuccess { onDismiss() }
                            .onFailure { errorMsg = it.message ?: "保存失败" }
                    }
                },
            ) {
                Text(if (isSaving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 Tailscale 内网？") },
            text = { Text("导出配置将不再包含此内网，本机 KeyStore 中保存的 Auth Key 也会一并清除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = palette.coral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}
