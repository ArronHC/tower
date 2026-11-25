package com.jzb.tower.android.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.TintedActionButton
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.GeneratedConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationViewerSheet(
    configuration: GeneratedConfiguration,
    onCopyUnmasked: (editedOverride: String?) -> Unit,
    onShareUnmasked: (editedOverride: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember(configuration.content) { mutableStateOf(configuration.content) }
    var searchQuery by remember { mutableStateOf("") }

    val hasEdits = editedText != configuration.content

    val displayedText = remember(editedText, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) editedText
        else editedText.lines()
            .filter { it.contains(q, ignoreCase = true) }
            .joinToString("\n")
    }

    val highlighted = remember(displayedText, palette.isDark) {
        buildSyntaxHighlightedConfig(displayedText, palette.isDark)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.groupedBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = configuration.fileName,
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "${configuration.content.lines().size} 行 · 预览中敏感密钥已打码",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                TintedActionButton(
                    text = if (isEditing) "语法预览" else "编辑",
                    icon = if (isEditing) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier.height(38.dp),
                )
            }

            if (!isEditing) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("在配置中筛选行（如 DIRECT、OPENAI）") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                )
            } else {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.cardSurface)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    Text(
                        text = highlighted,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TintedActionButton(
                    text = "复制",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { onCopyUnmasked(if (hasEdits) editedText else null) },
                    modifier = Modifier.weight(1f),
                )
                TintedActionButton(
                    text = "分享文件",
                    icon = Icons.Rounded.Share,
                    onClick = { onShareUnmasked(if (hasEdits) editedText else null) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun buildSyntaxHighlightedConfig(text: String, isDark: Boolean): AnnotatedString {
    val sectionColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val keyColor = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
    val commentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    val stringColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val baseColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")

    // Limit heavy highlighting on massive files to avoid frame drops while keeping full content
    val lines = text.lines()
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("//") -> {
                    pushStyle(SpanStyle(color = commentColor))
                    append(line)
                    pop()
                }
                trimmed.startsWith("[") && trimmed.contains("]") -> {
                    pushStyle(SpanStyle(color = sectionColor, fontWeight = FontWeight.Bold))
                    append(line)
                    pop()
                }
                else -> {
                    val colonIdx = line.indexOf(':')
                    val eqIdx = line.indexOf('=')
                    val sepIdx = when {
                        colonIdx > 0 && (eqIdx < 0 || colonIdx < eqIdx) && !line.substring(0, colonIdx).contains("://") -> colonIdx
                        eqIdx > 0 -> eqIdx
                        else -> -1
                    }
                    if (sepIdx > 0 && sepIdx < 60) {
                        pushStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.SemiBold))
                        append(line.substring(0, sepIdx))
                        pop()
                        val rest = line.substring(sepIdx)
                        appendStyledValues(rest, baseColor, stringColor, stringRegex)
                    } else {
                        appendStyledValues(line, baseColor, stringColor, stringRegex)
                    }
                }
            }
            if (index < lines.lastIndex) append('\n')
        }
    }
}

private fun AnnotatedString.Builder.appendStyledValues(
    segment: String,
    baseColor: Color,
    stringColor: Color,
    stringRegex: Regex,
) {
    var cursor = 0
    for (match in stringRegex.findAll(segment)) {
        if (match.range.first > cursor) {
            pushStyle(SpanStyle(color = baseColor))
            append(segment.substring(cursor, match.range.first))
            pop()
        }
        pushStyle(SpanStyle(color = stringColor))
        append(match.value)
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < segment.length) {
        pushStyle(SpanStyle(color = baseColor))
        append(segment.substring(cursor))
        pop()
    }
}
