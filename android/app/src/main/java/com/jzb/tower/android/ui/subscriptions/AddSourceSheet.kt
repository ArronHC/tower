package com.jzb.tower.android.ui.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.AndroidPlatformActions
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.PrivacyBadge
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.DetectedSourceKind
import com.jzb.tower.core.model.SourceDetectionResult
import com.jzb.tower.core.model.SubscriptionRequestOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceSheet(
    initialSharedText: String?,
    detectSourceInput: (String) -> SourceDetectionResult,
    onAddSubscription: suspend (url: String, name: String?, options: SubscriptionRequestOptions?) -> Result<*>,
    onImportNodes: suspend (content: String) -> Result<*>,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sourceValue by rememberSaveable { mutableStateOf(initialSharedText.orEmpty()) }
    var customName by rememberSaveable { mutableStateOf("") }
    var customUserAgent by rememberSaveable { mutableStateOf("") }
    var dohUrl by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // CLAUDE.md #11: Automatically read system clipboard ONCE when opened and prefill ONLY if
    // detectSourceInput recognizes a supported source; never re-read repeatedly in this session.
    var didAutoReadClipboard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!didAutoReadClipboard) {
            didAutoReadClipboard = true
            if (sourceValue.isBlank()) {
                val clip = AndroidPlatformActions.readClipboardText(context)
                if (!clip.isNullOrBlank()) {
                    val detected = detectSourceInput(clip)
                    if (detected.kind != DetectedSourceKind.UNKNOWN) {
                        sourceValue = clip
                    }
                }
            }
        }
    }

    val detection = remember(sourceValue) {
        if (sourceValue.isBlank()) null else detectSourceInput(sourceValue.trim())
    }
    val isSubscriptionUrl = detection?.kind == DetectedSourceKind.SUBSCRIPTION_URL
    val canSubmit = !isSaving && sourceValue.isNotBlank() &&
        detection != null && detection.kind != DetectedSourceKind.UNKNOWN

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = palette.groupedBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "添加订阅或节点",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                TowerFloatingPillButton(
                    text = "取消",
                    enabled = !isSaving,
                    onClick = onDismiss,
                )
            }

            PrivacyBadge(text = "仅在本机处理")

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sourceValue,
                        onValueChange = {
                            sourceValue = it
                            errorMessage = null
                        },
                        label = { Text("粘贴订阅链接、节点 URI 或 Clash / Surge 配置") },
                        minLines = 3,
                        maxLines = 7,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (detection != null) {
                            val isKnown = detection.kind != DetectedSourceKind.UNKNOWN
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isKnown) palette.emeraldSurface else palette.amberSurface,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isKnown) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = if (isKnown) palette.emerald else palette.amber,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = detection.summary.ifBlank {
                                            when (detection.kind) {
                                                DetectedSourceKind.SUBSCRIPTION_URL -> "已识别为订阅链接"
                                                DetectedSourceKind.PROXY_NODES -> "已识别 ${detection.detectedNodeCount} 个节点"
                                                DetectedSourceKind.CLASH_YAML -> "已识别 Clash 配置"
                                                DetectedSourceKind.SURGE_CONFIG -> "已识别 Surge 配置"
                                                DetectedSourceKind.RULE_SCHEME -> "识别为规则方案"
                                                DetectedSourceKind.UNKNOWN -> "未识别的格式"
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isKnown) palette.emerald else palette.amber,
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.size(1.dp))
                        }

                        TintedActionButton(
                            text = "从剪贴板粘贴",
                            icon = Icons.Rounded.ContentPaste,
                            onClick = {
                                val clip = AndroidPlatformActions.readClipboardText(context)
                                if (!clip.isNullOrBlank()) {
                                    sourceValue = clip
                                    errorMessage = null
                                } else {
                                    errorMessage = "剪贴板为空"
                                }
                            },
                            modifier = Modifier.height(38.dp),
                        )
                    }

                    Text(
                        text = "支持 HTTP/HTTPS 订阅链接，以及 SS、SSR、VMess、VLESS、Trojan、Hysteria 2、TUIC、WireGuard、AnyTLS、SOCKS5 等节点协议。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }

            AnimatedVisibility(
                visible = isSubscriptionUrl,
                enter = fadeIn(TowerMotion.disclosureSpec()) + expandVertically(TowerMotion.disclosureSpec()),
                exit = fadeOut(TowerMotion.disclosureSpec()) + shrinkVertically(TowerMotion.disclosureSpec()),
            ) {
                TowerCard(contentPadding = PaddingValues(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("自定义名称（可选，留空自动命名）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showAdvanced = !showAdvanced }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "高级请求选项（UA / DoH）",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.primary,
                            )
                            Icon(
                                imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = palette.primary,
                            )
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = customUserAgent,
                                    onValueChange = { customUserAgent = it },
                                    label = { Text("自定义 User-Agent（可选）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = dohUrl,
                                    onValueChange = { dohUrl = it },
                                    label = { Text("DNS-over-HTTPS 地址（可选）") },
                                    placeholder = { Text("https://dns.alidns.com/dns-query") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.coralSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = palette.coral)
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.coral,
                        )
                    }
                }
            }

            PrimaryActionButton(
                text = when {
                    isSaving -> "正在导入…"
                    isSubscriptionUrl -> "添加订阅"
                    else -> "导入节点"
                },
                icon = if (isSubscriptionUrl) Icons.Rounded.Link else Icons.Rounded.Add,
                enabled = canSubmit,
                onClick = {
                    val trimmed = sourceValue.trim()
                    scope.launch {
                        isSaving = true
                        errorMessage = null
                        val result = if (isSubscriptionUrl) {
                            val opts = SubscriptionRequestOptions(
                                userAgent = customUserAgent.trim().takeIf { it.isNotEmpty() },
                                dnsOverHTTPSURL = dohUrl.trim().takeIf { it.isNotEmpty() },
                            ).takeIf { !it.isEmpty }
                            onAddSubscription(
                                detection?.normalizedURL ?: trimmed,
                                customName.trim().takeIf { it.isNotEmpty() },
                                opts,
                            )
                        } else {
                            onImportNodes(trimmed)
                        }
                        isSaving = false
                        result.onSuccess {
                            onDismiss()
                        }.onFailure { err ->
                            errorMessage = err.message ?: "导入失败，请检查内容"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isSaving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = palette.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "正在本机解析并识别地区…",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
