package com.jzb.tower.android.ui.subscriptions

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jzb.tower.android.ui.components.FormatUtils
import com.jzb.tower.android.ui.components.towerPressScale
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.WorldDotMapData
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

@Immutable
data class CountryCoverageSummary(
    val code: String,
    val name: String,
    val flagEmoji: String,
    val nodeCount: Int,
    val bestOrMedianLatencyMs: Int?,
    val allFailed: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

private class IndexedWorldMap(
    val columns: Int,
    val rows: Int,
    val landCells: IntArray,
    val cellCountry: Array<String?>,
    val countryCentroids: Map<String, Pair<Float, Float>>, // normalized (0..1, 0..1)
)

private data class PlacedMapLabel(
    val summary: CountryCoverageSummary,
    val xPx: Float,
    val yPx: Float,
    val isSelected: Boolean,
)

@Composable
fun WorldDotMapCanvas(
    mapData: WorldDotMapData?,
    coverageByCountry: Map<String, CountryCoverageSummary>,
    selectedCountryCode: String?,
    isTestingLatency: Boolean,
    latencyProgress: Pair<Int, Int>?,
    latencyBatchCompletedTick: Int,
    hasTestableNodes: Boolean,
    onSelectCountry: (String?) -> Unit,
    onToggleLatencyTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val density = LocalDensity.current

    var lastCompletedTick by remember { mutableIntStateOf(latencyBatchCompletedTick) }
    LaunchedEffect(latencyBatchCompletedTick) {
        if (latencyBatchCompletedTick > lastCompletedTick) {
            lastCompletedTick = latencyBatchCompletedTick
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

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
        val centroids = HashMap<String, Pair<Float, Float>>()
        for ((code, indices) in mapData.countryCells) {
            val upper = code.uppercase()
            var sumX = 0.0
            var sumY = 0.0
            var count = 0
            for (idx in indices) {
                if (idx in 0 until total) {
                    cellCountry[idx] = upper
                    val c = idx % mapData.columns
                    val r = idx / mapData.columns
                    sumX += (c + 0.5) / mapData.columns
                    sumY += (r + 0.5) / mapData.rows
                    count++
                }
            }
            if (count > 0) {
                centroids[upper] = (sumX / count).toFloat() to (sumY / count).toFloat()
            }
        }
        IndexedWorldMap(
            columns = mapData.columns,
            rows = mapData.rows,
            landCells = landList.toIntArray(),
            cellCountry = cellCountry,
            countryCentroids = centroids,
        )
    }

    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffsets(newScale: Float, rawX: Float, rawY: Float): Pair<Float, Float> {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f || newScale <= 1.001f) return 0f to 0f
        val minPanX = w * (1f - newScale)
        val minPanY = h * (1f - newScale)
        return rawX.coerceIn(minPanX, 0f) to rawY.coerceIn(minPanY, 0f)
    }

    fun normalizedPointForCountry(code: String): Pair<Float, Float>? {
        indexed.countryCentroids[code]?.let { return it }
        val summary = coverageByCountry[code] ?: return null
        val lat = summary.latitude ?: return null
        val lon = summary.longitude ?: return null
        return mercatorUnitPoint(lat, lon)
    }

    fun animateViewportTo(targetScale: Float, targetX: Float, targetY: Float) {
        val (clampedX, clampedY) = clampOffsets(targetScale, targetX, targetY)
        scope.launch {
            launch { scaleAnim.animateTo(targetScale, TowerMotion.mapSpring()) }
            launch { offsetXAnim.animateTo(clampedX, TowerMotion.mapSpring()) }
            launch { offsetYAnim.animateTo(clampedY, TowerMotion.mapSpring()) }
        }
    }

    fun recenterOnCountry(code: String) {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val unit = normalizedPointForCountry(code) ?: return
        val targetScale = maxOf(scaleAnim.value, 1.8f).coerceAtMost(3.2f)
        val targetX = w * 0.5f - unit.first * w * targetScale
        val targetY = h * 0.5f - unit.second * h * targetScale
        animateViewportTo(targetScale, targetX, targetY)
    }

    fun resetViewport() {
        animateViewportTo(1f, 0f, 0f)
    }

    val aspectRatio = indexed.columns.toFloat() / indexed.rows.toFloat()
    val hasLatencyResult = coverageByCountry.values.any { it.bestOrMedianLatencyMs != null || it.allFailed }
    val scale = scaleAnim.value
    val offsetX = offsetXAnim.value
    val offsetY = offsetYAnim.value

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clipToBounds()
            .onSizeChanged { size ->
                canvasSize = size
                val (cx, cy) = clampOffsets(scaleAnim.value, offsetXAnim.value, offsetYAnim.value)
                scope.launch {
                    offsetXAnim.snapTo(cx)
                    offsetYAnim.snapTo(cy)
                }
            }
            .pointerInput(indexed, coverageByCountry, selectedCountryCode) {
                detectTapGestures(
                    onDoubleTap = { tapPos ->
                        if (scaleAnim.value > 1.08f) {
                            resetViewport()
                        } else {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val newScale = 2.0f
                            val tx = w * 0.5f - tapPos.x * newScale
                            val ty = h * 0.5f - tapPos.y * newScale
                            animateViewportTo(newScale, tx, ty)
                        }
                    },
                    onTap = { tapPos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectTapGestures

                        val mapX = (tapPos.x - offsetXAnim.value) / scaleAnim.value
                        val mapY = (tapPos.y - offsetYAnim.value) / scaleAnim.value
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

                        // Fallback: check proximity to covered country centroid/mercator point
                        if (hitCode == null) {
                            val hitRadius = minOf(w, h) * 0.08f / scaleAnim.value
                            for ((code, _) in coverageByCountry) {
                                val unit = normalizedPointForCountry(code) ?: continue
                                val dx = mapX - unit.first * w
                                val dy = mapY - unit.second * h
                                val d = sqrt(dx * dx + dy * dy)
                                if (d < hitRadius && d < bestDist) {
                                    bestDist = d
                                    hitCode = code
                                }
                            }
                        }

                        if (hitCode != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val next = if (selectedCountryCode == hitCode) null else hitCode
                            onSelectCountry(next)
                            if (next != null) {
                                recenterOnCountry(next)
                            }
                        } else if (selectedCountryCode != null) {
                            onSelectCountry(null)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val shouldHandleTransform = pointerCount >= 2 || scaleAnim.value > 1.02f
                        if (shouldHandleTransform) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldScale = scaleAnim.value
                                val newScale = (oldScale * zoomChange).coerceIn(1f, 4.5f)
                                val scaleRatio = newScale / oldScale
                                val newRawX = centroid.x - (centroid.x - offsetXAnim.value) * scaleRatio + panChange.x
                                val newRawY = centroid.y - (centroid.y - offsetYAnim.value) * scaleRatio + panChange.y
                                val (clampedX, clampedY) = clampOffsets(newScale, newRawX, newRawY)
                                scope.launch {
                                    scaleAnim.snapTo(newScale)
                                    offsetXAnim.snapTo(clampedX)
                                    offsetYAnim.snapTo(clampedY)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        // Land dot color matching WorldDotMapView / ANDROID_UI_REDESIGN_SPEC.md
        val neutralLandColor = if (palette.isDark) Color(0xFF273145) else Color(0xFFDDE2EC)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = (size.width / indexed.columns) * scale
            val cellH = (size.height / indexed.rows) * scale
            val cellMin = minOf(cellW, cellH)
            val baseRadius = (cellMin * 0.31f).coerceAtLeast(1.05f)
            val coveredRadius = (cellMin * 0.36f).coerceAtLeast(1.15f)
            val selectedRadius = (cellMin * 0.45f).coerceAtLeast(1.35f)
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
                val summary = if (code != null) coverageByCountry[code] else null
                if (summary == null) {
                    drawCircle(
                        color = neutralLandColor,
                        radius = baseRadius,
                        center = Offset(cx, cy),
                    )
                } else {
                    val isSelected = code == selectedCountryCode
                    val dotColor = FormatUtils.mapBandColor(
                        milliseconds = summary.bestOrMedianLatencyMs,
                        isApplicable = !summary.allFailed,
                        isDark = palette.isDark,
                        selected = isSelected,
                    )
                    drawCircle(
                        color = dotColor,
                        radius = if (isSelected) selectedRadius else coveredRadius,
                        center = Offset(cx, cy),
                    )
                }
            }
        }

        // Inline floating country name pills with lightweight collision avoidance (iphone-1.jpg)
        val placedLabels = remember(coverageByCountry, selectedCountryCode, canvasSize, scale, offsetX, offsetY) {
            if (canvasSize.width <= 0 || canvasSize.height <= 0 || coverageByCountry.isEmpty()) {
                emptyList()
            } else {
                val w = canvasSize.width.toFloat()
                val h = canvasSize.height.toFloat()
                val densityScale = density.density
                val maxLabels = if (scale > 1.4f) 14 else 7
                val sorted = coverageByCountry.values.sortedWith(
                    compareByDescending<CountryCoverageSummary> { it.code == selectedCountryCode }
                        .thenByDescending { it.nodeCount }
                        .thenBy { it.code },
                )

                val placed = ArrayList<PlacedMapLabel>(maxLabels)
                val boxes = ArrayList<FloatArray>(maxLabels) // [left, top, right, bottom]
                val margin = 5f * densityScale
                val labelH = 19f * densityScale

                // Reserve top-right speed test pill area & bottom-left legend area
                val topRightMinX = w - 92f * densityScale
                val topRightMaxY = 42f * densityScale

                for (summary in sorted) {
                    if (placed.size >= maxLabels) break
                    val unit = normalizedPointForCountry(summary.code) ?: continue
                    val px = offsetX + unit.first * w * scale
                    val py = offsetY + unit.second * h * scale
                    if (px < 16f * densityScale || px > w - 16f * densityScale ||
                        py < 14f * densityScale || py > h - 14f * densityScale
                    ) {
                        continue
                    }

                    val estWidth = (summary.name.length * 10.5f + 14f) * densityScale
                    val left = (px - estWidth / 2f).coerceIn(4f * densityScale, w - estWidth - 4f * densityScale)
                    val top = (py - labelH / 2f).coerceIn(4f * densityScale, h - labelH - 4f * densityScale)
                    val right = left + estWidth
                    val bottom = top + labelH

                    if (right > topRightMinX && top < topRightMaxY) continue

                    val overlaps = boxes.any { b ->
                        left < b[2] + margin && right + margin > b[0] &&
                            top < b[3] + margin && bottom + margin > b[1]
                    }
                    if (!overlaps) {
                        boxes.add(floatArrayOf(left, top, right, bottom))
                        placed.add(
                            PlacedMapLabel(
                                summary = summary,
                                xPx = left,
                                yPx = top,
                                isSelected = summary.code == selectedCountryCode,
                            ),
                        )
                    }
                }
                placed
            }
        }

        placedLabels.forEach { item ->
            val selected = item.isSelected
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.cardSurface.copy(alpha = if (selected) 0.96f else 0.84f),
                border = BorderStroke(
                    width = if (selected) 0.75.dp else 0.5.dp,
                    color = palette.textPrimary.copy(alpha = if (selected) 0.18f else 0.08f),
                ),
                modifier = Modifier
                    .offset { IntOffset(item.xPx.roundToInt(), item.yPx.roundToInt()) }
                    .zIndex(if (selected) 1f else 0f),
            ) {
                Text(
                    text = item.summary.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = if (selected) {
                        palette.textPrimary
                    } else {
                        palette.textPrimary.copy(alpha = 0.74f)
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Top-right floating "测速" / "x/n · 停止" capsule (iphone-1.jpg)
        val pillShape = RoundedCornerShape(50)
        Surface(
            onClick = {
                if (hasTestableNodes || isTestingLatency) {
                    onToggleLatencyTest()
                }
            },
            enabled = hasTestableNodes || isTestingLatency,
            shape = pillShape,
            color = palette.pillNavSurface,
            border = BorderStroke(0.75.dp, palette.pillNavBorder),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(32.dp)
                .animateContentSize(TowerMotion.disclosureSpec())
                .towerPressScale(enabled = hasTestableNodes || isTestingLatency)
                .then(
                    if (palette.isDark) Modifier else Modifier.shadow(
                        elevation = 5.dp,
                        shape = pillShape,
                        ambientColor = Color.Black.copy(alpha = 0.07f),
                        spotColor = Color.Black.copy(alpha = 0.09f),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AnimatedContent(
                    targetState = isTestingLatency,
                    transitionSpec = { fadeIn(TowerMotion.selectionSpec()) togetherWith fadeOut(TowerMotion.pressInSpec()) },
                    label = "mapLatencyIcon",
                ) { testing ->
                    if (testing) {
                        CircularProgressIndicator(
                            strokeWidth = 1.8.dp,
                            color = palette.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "测速",
                            tint = if (hasTestableNodes) palette.primary else palette.textTertiary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                val labelText = if (isTestingLatency && latencyProgress != null) {
                    "${latencyProgress.first}/${latencyProgress.second} · 停止"
                } else if (isTestingLatency) {
                    "停止"
                } else {
                    "测速"
                }

                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (hasTestableNodes || isTestingLatency) palette.primary else palette.textTertiary,
                    maxLines = 1,
                )
            }
        }

        // Bottom-left latency gradient legend: "100 ━━━ 350 ms" (iphone-1.jpg)
        AnimatedVisibility(
            visible = hasLatencyResult,
            enter = fadeIn(TowerMotion.selectionSpec()),
            exit = fadeOut(TowerMotion.selectionSpec()),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.cardSurface.copy(alpha = 0.90f),
                border = BorderStroke(0.5.dp, palette.cardBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "100",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = palette.textSecondary,
                    )
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        palette.emerald,
                                        palette.lakeBlue,
                                        palette.amber,
                                        palette.coral,
                                    ),
                                ),
                            ),
                    )
                    Text(
                        text = "350 ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = palette.textSecondary,
                    )
                }
            }
        }

        // Bottom-right floating "复原" pill when zoomed or panned
        AnimatedVisibility(
            visible = scale > 1.05f || abs(offsetX) > 3f || abs(offsetY) > 3f,
            enter = fadeIn(TowerMotion.selectionSpec()),
            exit = fadeOut(TowerMotion.selectionSpec()),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Surface(
                onClick = ::resetViewport,
                shape = CircleShape,
                color = palette.pillNavSurface,
                border = BorderStroke(0.75.dp, palette.pillNavBorder),
                modifier = Modifier
                    .height(32.dp)
                    .towerPressScale(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomOutMap,
                        contentDescription = "复原",
                        tint = palette.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "复原",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.primary,
                    )
                }
            }
        }
    }
}

private fun mercatorUnitPoint(latitude: Double, longitude: Double): Pair<Float, Float> {
    fun mercatorY(lat: Double): Double {
        val clamped = lat.coerceIn(-85.0, 85.0)
        return ln(tan(PI / 4.0 + clamped * PI / 360.0))
    }
    val x = ((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0)
    val top = mercatorY(84.0)
    val bottom = mercatorY(-60.0)
    val y = ((top - mercatorY(latitude)) / (top - bottom)).coerceIn(0.0, 1.0)
    return x.toFloat() to y.toFloat()
}
