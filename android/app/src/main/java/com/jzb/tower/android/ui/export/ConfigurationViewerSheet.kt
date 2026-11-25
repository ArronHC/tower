package com.jzb.tower.android.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
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
import androidx.compose.material3.Surface
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
import com.jzb.tower.android.ui.components.TowerCard
import com.jzb.tower.android.ui.components.TowerFloatingCircleButton
import com.jzb.tower.android.ui.components.TowerFloatingPillButton
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.ClientCatalog
import com.jzb.tower.core.model.GeneratedConfiguration

/**
 * Full-screen syntax-highlighted configuration viewer & editor matching iphone-4.jpg.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationViewerSheet(
    configuration: GeneratedConfiguration,
    onCopyUnmasked: (editedOverride: String?) -> Unit,
    onShareUnmasked: (editedOverride: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = TowerTheme.palette
    val clientMeta = remember(configuration.target) { ClientCatalog.meta(configuration.target) }
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
        dragHandle = null,
        containerColor = palette.groupedBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar matching iphone-4.jpg: "完成" pill (left) | Client Name (center) | Copy circle (right)
            Box(modifier = Modifier.fillMaxWidth()) {
                TowerFloatingPillButton(
                    text = "完成",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = clientMeta.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = palette.textPrimary,
                    )
                    Text(
                        text = "${configuration.fileName} · ${configuration.content.lines().size} 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TowerFloatingCircleButton(
                        icon = if (isEditing) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                        contentDescription = if (isEditing) "语法预览" else "编辑",
                        onClick = { isEditing = !isEditing },
                    )
                    TowerFloatingCircleButton(
                        icon = Icons.Rounded.ContentCopy,
                        contentDescription = "复制完整配置",
                        onClick = { onCopyUnmasked(if (hasEdits) editedText else null) },
                    )
                }
            }

            if (!isEditing) {
                Surface(
                    shape = CircleShape,
                    color = palette.cardSurface,
                    border = BorderStroke(0.75.dp, palette.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = palette.textPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "在配置中筛选行（如 DIRECT、OPENAI）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textTertiary,
                                    )
                                }
                                inner()
                            },
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "清除",
                                tint = palette.textSecondary,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clickable { searchQuery = "" },
                            )
                        }
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp),
                )
            } else {
                TowerCard(
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp),
                ) {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            text = highlighted,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TintedActionButton(
                    text = "复制配置",
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
    val keyColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
    val commentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    val stringColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val numberColor = if (isDark) Color(0xFFFB923C) else Color(0xFFC2410C)
    val boolColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
    val baseColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")

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
                        pushStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.Medium))
                        append(line.substring(0, sepIdx))
                        pop()
                        val rest = line.substring(sepIdx)
                        appendStyledValues(rest, baseColor, stringColor, numberColor, boolColor, stringRegex)
                    } else {
                        appendStyledValues(line, baseColor, stringColor, numberColor, boolColor, stringRegex)
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
    numberColor: Color,
    boolColor: Color,
    stringRegex: Regex,
) {
    var cursor = 0
    for (match in stringRegex.findAll(segment)) {
        if (match.range.first > cursor) {
            appendScalarSegment(segment.substring(cursor, match.range.first), baseColor, numberColor, boolColor)
        }
        pushStyle(SpanStyle(color = stringColor))
        append(match.value)
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < segment.length) {
        appendScalarSegment(segment.substring(cursor), baseColor, numberColor, boolColor)
    }
}

private fun AnnotatedString.Builder.appendScalarSegment(
    segment: String,
    baseColor: Color,
    numberColor: Color,
    boolColor: Color,
) {
    val trimmed = segment.trim()
    val color = when {
        trimmed == ": true" || trimmed == ": false" || trimmed == "= true" || trimmed == "= false" ||
            trimmed == "true" || trimmed == "false" -> boolColor
        trimmed.removePrefix(":").removePrefix("=").trim().toDoubleOrNull() != null -> numberColor
        else -> baseColor
    }
    pushStyle(SpanStyle(color = color))
    append(segment)
    pop()
}
