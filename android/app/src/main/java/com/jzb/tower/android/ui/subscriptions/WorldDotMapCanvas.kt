package com.jzb.tower.android.ui.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.WorldDotMapData
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Immutable
data class CountryCoverageSummary(
    val code: String,
    val name: String,
    val flagEmoji: String,
    val nodeCount: Int,
    val bestOrMedianLatencyMs: Int?,
)

private enum class MapLatencyColorBand {
    UNTESTED,
    FAST,
    MEDIUM,
    SLOW,
}

private class IndexedWorldMap(
    val columns: Int,
    val rows: Int,
    val landCells: IntArray,
    val cellCountry: Array<String?>,
)

@Composable
fun WorldDotMapCanvas(
    mapData: WorldDotMapData?,
    coverageByCountry: Map<String, CountryCoverageSummary>,
    selectedCountryCode: String?,
    onSelectCountry: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette

    if (mapData == null || mapData.columns <= 0 || mapData.rows <= 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(220f / 149f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "正在加载离线点阵地图…",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
        return
    }

    val indexed = remember(mapData) {
        val total = mapData.columns * mapData.rows
        val landList = ArrayList<Int>(total / 4)
        val cellCountry = arrayOfNulls<String>(total)
        for (r in 0 until mapData.rows) {
            val rowStr = mapData.landRows.getOrNull(r) ?: continue
            val limit = minOf(mapData.columns, rowStr.length)
            for (c in 0 until limit) {
                if (rowStr[c] == '#') {
                    landList.add(r * mapData.columns + c)
                }
            }
        }
        for ((code, indices) in mapData.countryCells) {
            val upper = code.uppercase()
            for (idx in indices) {
                if (idx in 0 until total) {
                    cellCountry[idx] = upper
                }
            }
        }
        IndexedWorldMap(
            columns = mapData.columns,
            rows = mapData.rows,
            landCells = landList.toIntArray(),
            cellCountry = cellCountry,
        )
    }

    val countryColorMap = remember(coverageByCountry, palette.isDark) {
        coverageByCountry.mapValues { (_, summary) ->
            val ms = summary.bestOrMedianLatencyMs
            when {
                ms == null -> MapLatencyColorBand.UNTESTED
                ms < 100 -> MapLatencyColorBand.FAST
                ms <= 280 -> MapLatencyColorBand.MEDIUM
                else -> MapLatencyColorBand.SLOW
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffsets(newScale: Float, rawX: Float, rawY: Float): Pair<Float, Float> {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f || newScale <= 1.001f) return 0f to 0f
        val maxPanX = 0f
        val minPanX = w * (1f - newScale)
        val maxPanY = 0f
        val minPanY = h * (1f - newScale)
        return rawX.coerceIn(minPanX, maxPanX) to rawY.coerceIn(minPanY, maxPanY)
    }

    fun resetViewport() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val aspectRatio = indexed.columns.toFloat() / indexed.rows.toFloat()
    val hasLatencyResult = coverageByCountry.values.any { it.bestOrMedianLatencyMs != null }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clipToBounds()
            .onSizeChanged { size ->
                canvasSize = size
                val (cx, cy) = clampOffsets(scale, offsetX, offsetY)
                offsetX = cx
                offsetY = cy
            }
            .pointerInput(indexed, coverageByCountry, selectedCountryCode) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            resetViewport()
                        } else {
                            val newScale = 2.2f
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val (cx, cy) = clampOffsets(newScale, -w * 0.6f, -h * 0.55f)
                            scale = newScale
                            offsetX = cx
                            offsetY = cy
                        }
                    },
                    onTap = { tapPos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectTapGestures

                        val mapX = (tapPos.x - offsetX) / scale
                        val mapY = (tapPos.y - offsetY) / scale
                        val cellW = w / indexed.columns
                        val cellH = h / indexed.rows
                        val col = (mapX / cellW).toInt()
                        val row = (mapY / cellH).toInt()

                        var hitCode: String? = null
                        var bestDist = Float.MAX_VALUE
                        // Search 2-cell neighborhood so small territories (HK, SG, TW) are easy to tap
                        for (dr in -2..2) {
                            for (dc in -2..2) {
                                val nr = row + dr
                                val nc = col + dc
                                if (nr !in 0 until indexed.rows || nc !in 0 until indexed.columns) continue
                                val code = indexed.cellCountry[nr * indexed.columns + nc] ?: continue
                                if (!coverageByCountry.containsKey(code)) continue
                                val centerX = (nc + 0.5f) * cellW
                                val centerY = (nr + 0.5f) * cellH
                                val dx = mapX - centerX
                                val dy = mapY - centerY
                                val d = sqrt(dx * dx + dy * dy)
                                if (d < bestDist) {
                                    bestDist = d
                                    hitCode = code
                                }
                            }
                        }

                        if (hitCode != null) {
                            onSelectCountry(if (selectedCountryCode == hitCode) null else hitCode)
                        } else if (selectedCountryCode != null) {
                            onSelectCountry(null)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Consume pinch gestures anytime, and single-finger pan only when zoomed in
                // so vertical scrolling of SubscriptionsScreen works smoothly at 1x zoom.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val shouldHandleTransform = pointerCount >= 2 || scale > 1.02f
                        if (shouldHandleTransform) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldScale = scale
                                val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                                val scaleRatio = newScale / oldScale
                                val newRawX = centroid.x - (centroid.x - offsetX) * scaleRatio + panChange.x
                                val newRawY = centroid.y - (centroid.y - offsetY) * scaleRatio + panChange.y
                                val (clampedX, clampedY) = clampOffsets(newScale, newRawX, newRawY)
                                scale = newScale
                                offsetX = clampedX
                                offsetY = clampedY
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val neutralLandColor = palette.textPrimary.copy(alpha = if (palette.isDark) 0.17f else 0.12f)
        val fastColor = palette.emerald
        val mediumColor = palette.amber
        val slowColor = palette.coral
        val untestedColor = palette.coolSlate

        fun bandColor(band: MapLatencyColorBand, isSelected: Boolean): Color {
            val base = when (band) {
                MapLatencyColorBand.FAST -> fastColor
                MapLatencyColorBand.MEDIUM -> mediumColor
                MapLatencyColorBand.SLOW -> slowColor
                MapLatencyColorBand.UNTESTED -> untestedColor
            }
            if (!isSelected) return base
            return if (palette.isDark) {
                base.copy(
                    red = (base.red * 1.15f).coerceAtMost(1f),
                    green = (base.green * 1.15f).coerceAtMost(1f),
                    blue = (base.blue * 1.15f).coerceAtMost(1f),
                    alpha = 1f,
                )
            } else {
                Color(
                    red = base.red * 0.68f,
                    green = base.green * 0.68f,
                    blue = base.blue * 0.68f,
                    alpha = 1f,
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = (size.width / indexed.columns) * scale
            val cellH = (size.height / indexed.rows) * scale
            val baseRadius = (minOf(cellW, cellH) * 0.37f).coerceAtLeast(1.1f)
            val coveredRadius = baseRadius * 1.12f
            val selectedRadius = baseRadius * 1.32f
            val cols = indexed.columns

            for (idx in indexed.landCells) {
                val col = idx % cols
                val row = idx / cols
                val cx = offsetX + (col + 0.5f) * cellW
                val cy = offsetY + (row + 0.5f) * cellH
                if (cx < -selectedRadius || cx > size.width + selectedRadius ||
                    cy < -selectedRadius || cy > size.height + selectedRadius
                ) {
                    continue
                }

                val code = indexed.cellCountry[idx]
                val band = if (code != null) countryColorMap[code] else null
                if (band == null) {
                    drawCircle(
                        color = neutralLandColor,
                        radius = baseRadius,
                        center = Offset(cx, cy),
                    )
                } else {
                    val isSelected = code == selectedCountryCode
                    drawCircle(
                        color = bandColor(band, isSelected),
                        radius = if (isSelected) selectedRadius else coveredRadius,
                        center = Offset(cx, cy),
                    )
                }
            }
        }

        // Selected country neutral chip overlay (CLAUDE.md #8: neutral text, no color tinting on text)
        val selectedSummary = selectedCountryCode?.let { coverageByCountry[it] }
        AnimatedVisibility(
            visible = selectedSummary != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            if (selectedSummary != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.cardSurface.copy(alpha = 0.94f),
                    border = BorderStroke(0.75.dp, palette.cardBorder),
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = selectedSummary.flagEmoji,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = selectedSummary.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "${selectedSummary.nodeCount} 节点",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                        if (selectedSummary.bestOrMedianLatencyMs != null) {
                            Text(
                                text = "${selectedSummary.bestOrMedianLatencyMs} ms",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = FormatUtils.latencyColor(selectedSummary.bestOrMedianLatencyMs),
                            )
                        }
                        IconButton(
                            onClick = { onSelectCountry(null) },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清除地区筛选",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        // Latency legend pill when no country chip overlaps bottom-start, or top-start when chip is shown
        AnimatedVisibility(
            visible = hasLatencyResult && selectedSummary == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.cardSurface.copy(alpha = 0.88f),
                border = BorderStroke(0.5.dp, palette.cardBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "<100",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = palette.textSecondary,
                    )
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(palette.emerald, palette.amber, palette.coral),
                                ),
                            ),
                    )
                    Text(
                        text = "280+ ms",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = palette.textSecondary,
                    )
                }
            }
        }

        // Reset zoom button when zoomed or panned
        AnimatedVisibility(
            visible = scale > 1.03f || abs(offsetX) > 2f || abs(offsetY) > 2f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Surface(
                onClick = ::resetViewport,
                shape = CircleShape,
                color = palette.cardSurface.copy(alpha = 0.92f),
                border = BorderStroke(0.75.dp, palette.cardBorder),
                shadowElevation = 3.dp,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomOutMap,
                        contentDescription = "恢复默认",
                        tint = palette.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
