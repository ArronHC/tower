package com.jzb.tower.android.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.PrimaryActionButton
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.components.TowerSegmentedControl
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.RuleScheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSchemeSheet(
    onImport: suspend (name: String?, url: String?, content: String?) -> Result<RuleScheme>,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var modeIndex by remember { mutableIntStateOf(0) } // 0 = URL, 1 = Text
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!isImporting) onDismiss() },
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
                    text = "导入规则方案",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.textPrimary,
                )
                TowerFloatingPillButton(
                    text = "取消",
                    enabled = !isImporting,
                    onClick = onDismiss,
                )
            }

            TowerSegmentedControl(
                options = listOf("HTTPS 链接", "粘贴配置文本"),
                selectedIndex = modeIndex,
                onSelect = { modeIndex = it },
            )

            TowerCard(contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("方案名称（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (modeIndex == 0) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it; errorMsg = null },
                            label = { Text("规则配置地址（仅支持 HTTPS）") },
                            placeholder = { Text("https://example.com/acl4ssr.ini") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it; errorMsg = null },
                            label = { Text("INI / Surge / Clash 规则文本") },
                            minLines = 5,
                            maxLines = 10,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = "只会在你点击导入或刷新时联网获取规则，生成配置时不依赖远程转换服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.coral,
                )
            }

            val canSubmit = !isImporting && if (modeIndex == 0) url.isNotBlank() else content.isNotBlank()
            PrimaryActionButton(
                text = if (isImporting) "正在导入…" else "导入方案",
                icon = Icons.Rounded.Download,
                enabled = canSubmit,
                onClick = {
                    scope.launch {
                        isImporting = true
                        errorMsg = null
                        val res = onImport(
                            name.trim().takeIf { it.isNotEmpty() },
                            if (modeIndex == 0) url.trim() else null,
                            if (modeIndex == 1) content else null,
                        )
                        isImporting = false
                        res.onSuccess { onDismiss() }
                            .onFailure { errorMsg = it.message ?: "导入失败" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
