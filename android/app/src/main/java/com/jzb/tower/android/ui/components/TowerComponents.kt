package com.jzb.tower.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.NodeLatencyMeasurement
import com.jzb.tower.core.model.QrMatrix
import java.util.concurrent.ConcurrentHashMap

enum class ToastTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
}

@Immutable
data class ToastMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val tone: ToastTone = ToastTone.NEUTRAL,
)

/**
 * Signature responsive press-scale + alpha micro-interaction matching ResponsivePressButtonStyle.
 */
fun Modifier.towerPressScale(
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) TowerMotion.PRESS_SCALE else 1f,
        animationSpec = if (isPressed) TowerMotion.pressInSpec() else TowerMotion.pressReleaseSpec(),
        label = "towerPressScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (enabled && isPressed) TowerMotion.PRESS_ALPHA else 1f,
        animationSpec = if (isPressed) TowerMotion.pressInSpec() else TowerMotion.pressReleaseSpec(),
        label = "towerPressAlpha",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = pressAlpha
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
}

@Composable
fun Modifier.towerPressVisual(isPressed: Boolean, enabled: Boolean = true): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) TowerMotion.PRESS_SCALE else 1f,
        animationSpec = if (isPressed) TowerMotion.pressInSpec() else TowerMotion.pressReleaseSpec(),
        label = "towerPressVisualScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (enabled && isPressed) TowerMotion.PRESS_ALPHA else 1f,
        animationSpec = if (isPressed) TowerMotion.pressInSpec() else TowerMotion.pressReleaseSpec(),
        label = "towerPressVisualAlpha",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = pressAlpha
    }
}

/**
 * Full-page ambient background matching TowerTheme.background in SwiftUI.
 */
@Composable
fun TowerPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = TowerTheme.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.groupedBackground)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.ambientWash,
                        Color.Transparent,
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        content()
    }
}

/**
 * Elevated Tower surface card with 22dp continuous rounded corners, 0.75dp hairline border,
 * and soft shape-bound ambient shadow matching TowerCardModifier in SwiftUI.
 */
@Composable
fun TowerCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = TowerDimens.CardCornerRadius,
    containerColor: Color = TowerTheme.palette.cardSurface,
    borderColor: Color = TowerTheme.palette.cardBorder,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = TowerTheme.palette
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shadowModifier = if (palette.isDark) {
        Modifier
    } else {
        Modifier.shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.045f),
            spotColor = Color.Black.copy(alpha = 0.055f),
        )
    }

    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.towerPressVisual(isPressed) else Modifier)
            .then(shadowModifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(TowerDimens.CardBorderWidth, borderColor),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Circular 25dp checkmark toggle inside a 44dp touch target matching CheckmarkToggleStyle in SwiftUI.
 */
@Composable
fun TowerSelectionCheckmark(
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val palette = TowerTheme.palette
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val fillColor by animateColorAsState(
        targetValue = if (selected) palette.primary else Color.Transparent,
        animationSpec = TowerMotion.selectionSpec(),
        label = "checkFill",
    )
    val strokeColor by animateColorAsState(
        targetValue = if (selected) palette.primary else palette.textTertiary.copy(alpha = 0.42f),
        animationSpec = TowerMotion.selectionSpec(),
        label = "checkStroke",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = TowerMotion.selectionSpec(),
        label = "checkAlpha",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else TowerMotion.SELECTION_SYMBOL_SCALE,
        animationSpec = TowerMotion.selectionSpec(),
        label = "checkScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = if (isPressed) TowerMotion.pressInSpec() else TowerMotion.pressReleaseSpec(),
        label = "checkPressAlpha",
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .alpha(pressAlpha)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(1.5.dp, strokeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        alpha = checkAlpha
                        scaleX = checkScale
                        scaleY = checkScale
                    },
            )
        }
    }
}

/**
 * Sleek 48x28dp spring-animated iOS-style toggle switch.
 */
@Composable
fun TowerIosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = TowerTheme.palette
    val haptic = LocalHapticFeedback.current
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> palette.switchOffTrack.copy(alpha = 0.5f)
            checked -> palette.primary
            else -> palette.switchOffTrack
        },
        animationSpec = TowerMotion.selectionSpec(),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = TowerMotion.pillSpring(),
        label = "switchThumb",
    )

    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(!checked)
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.2f),
                )
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * iOS-style pill segmented control with animated sliding white thumb matching iphone-3.jpg.
 */
@Composable
fun TowerSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val palette = TowerTheme.palette
    val haptic = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(CircleShape)
            .background(palette.segmentedTrack)
            .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * safeIndex,
            animationSpec = TowerMotion.pillSpring(),
            label = "segmentedThumb",
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (palette.isDark) 0.dp else 3.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.10f),
                )
                .clip(CircleShape)
                .background(palette.segmentedThumb),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { idx, label ->
                val isSelected = idx == safeIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) palette.textPrimary else palette.textSecondary,
                    animationSpec = TowerMotion.selectionSpec(),
                    label = "segText_$idx",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (idx != safeIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(idx)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Rolling vertical number text matching SwiftUI .contentTransition(.numericText).
 */
@Composable
fun TowerNumericText(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (targetState >= initialState) {
                (slideInVertically(TowerMotion.selectionSpec()) { it / 2 } + fadeIn(TowerMotion.selectionSpec())) togetherWith
                    (slideOutVertically(TowerMotion.selectionSpec()) { -it / 2 } + fadeOut(TowerMotion.selectionSpec()))
            } else {
                (slideInVertically(TowerMotion.selectionSpec()) { -it / 2 } + fadeIn(TowerMotion.selectionSpec())) togetherWith
                    (slideOutVertically(TowerMotion.selectionSpec()) { it / 2 } + fadeOut(TowerMotion.selectionSpec()))
            }
        },
        modifier = modifier,
        label = "towerNumericText",
    ) { num ->
        Text(
            text = if (suffix.isEmpty()) num.toString() else "$num$suffix",
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * Fixed 58dp min-width right-aligned latency slot so node names never jitter during speed tests.
 */
@Composable
fun TowerLatencyBadge(
    measurement: NodeLatencyMeasurement?,
    isTesting: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val stateKey = when {
        isTesting -> -2
        measurement == null -> -1
        !measurement.isApplicable -> -3
        measurement.milliseconds == null -> -4
        else -> measurement.milliseconds ?: -1
    }

    Box(
        modifier = modifier.widthIn(min = 58.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedContent(
            targetState = stateKey,
            transitionSpec = {
                fadeIn(TowerMotion.selectionSpec()) togetherWith fadeOut(TowerMotion.pressInSpec())
            },
            label = "latencySlot",
        ) { key ->
            when (key) {
                -2 -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = palette.primary,
                    modifier = Modifier.size(14.dp),
                )
                -1 -> Text(
                    text = "",
                    style = MaterialTheme.typography.labelSmall,
                )
                -3 -> Text(
                    text = "不支持",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    maxLines = 1,
                )
                -4 -> Text(
                    text = "超时",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.unreachableRose,
                    maxLines = 1,
                )
                else -> {
                    val color = FormatUtils.latencyColor(key, isApplicable = true)
                    Text(
                        text = "$key ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = color,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * Elevated floating pill button matching top bar chrome in iphone-1/2/3/4/5.jpg & Mobbin Aave.
 */
@Composable
fun TowerFloatingPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textColor: Color = TowerTheme.palette.primary,
    enabled: Boolean = true,
) {
    val palette = TowerTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = palette.cardSurface.copy(alpha = 0.96f),
        border = BorderStroke(0.75.dp, palette.cardBorder),
        modifier = modifier
            .height(38.dp)
            .towerPressVisual(isPressed, enabled)
            .then(
                if (palette.isDark) Modifier else Modifier.shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                ),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) textColor else palette.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (enabled) textColor else palette.textTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * Elevated floating circular icon button matching "+" in iphone-1.jpg & copy in iphone-4.jpg.
 */
@Composable
fun TowerFloatingCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TowerTheme.palette.primary,
    size: Dp = 38.dp,
    enabled: Boolean = true,
) {
    val palette = TowerTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = palette.cardSurface.copy(alpha = 0.96f),
        border = BorderStroke(0.75.dp, palette.cardBorder),
        modifier = modifier
            .size(size)
            .towerPressVisual(isPressed, enabled)
            .then(
                if (palette.isDark) Modifier else Modifier.shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                ),
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else palette.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Colorful 32dp squircle icon badge matching Settings in iphone-5.jpg and Mobbin Luma.
 */
@Composable
fun TowerSquircleIconBadge(
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconTint: Color = Color.White,
) {
    val cornerRadius = size * 0.28f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = backgroundColor,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/**
 * Vibrant filled squircle icon tile matching Mobbin Luma Settings style.
 */
@Composable
fun TowerFilledSquircleIconBadge(
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val cornerRadius = size * 0.28f
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.85f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

@Composable
fun SectionHeading(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = TowerTheme.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            trailing()
        } else if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = palette.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PrivacyBadge(
    modifier: Modifier = Modifier,
    text: String = "全部在这台设备上处理",
) {
    val palette = TowerTheme.palette
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = palette.emeraldSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = palette.emerald,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = palette.emerald,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MetricPill(
    value: Int,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    total: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = TowerTheme.palette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.towerPressVisual(isPressed) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            TowerNumericText(
                value = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 23.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = palette.textPrimary,
            )
            if (total != null) {
                Text(
                    text = "/$total",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = palette.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = TowerTheme.palette.primary,
    contentColor: Color = Color.White,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(TowerDimens.CompactCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = contentColor.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .height(TowerDimens.ActionButtonHeight)
            .towerPressVisual(isPressed, enabled),
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TintedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = TowerTheme.palette.primary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(TowerDimens.CompactCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = tint.copy(alpha = 0.12f),
            contentColor = tint,
            disabledContainerColor = tint.copy(alpha = 0.05f),
            disabledContentColor = tint.copy(alpha = 0.4f),
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier
            .height(TowerDimens.ActionButtonHeight)
            .towerPressVisual(isPressed, enabled),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Floating pill toast matching StatusSurface + StatusBadge in TowerTheme.swift.
 */
@Composable
fun ToastHost(
    toast: ToastMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(TowerMotion.surfaceSpring()) + scaleIn(
                initialScale = TowerMotion.SURFACE_ENTRY_SCALE,
                animationSpec = TowerMotion.surfaceSpring(),
            ),
            exit = fadeOut(TowerMotion.selectionSpec()) + scaleOut(
                targetScale = TowerMotion.SURFACE_ENTRY_SCALE,
                animationSpec = TowerMotion.selectionSpec(),
            ),
        ) {
            if (toast != null) {
                val accent = when (toast.tone) {
                    ToastTone.SUCCESS -> palette.emerald
                    ToastTone.WARNING -> palette.amber
                    ToastTone.ERROR -> palette.coral
                    ToastTone.NEUTRAL -> palette.primary
                }
                val icon = when (toast.tone) {
                    ToastTone.SUCCESS -> Icons.Rounded.CheckCircle
                    ToastTone.WARNING -> Icons.Rounded.ErrorOutline
                    ToastTone.ERROR -> Icons.Rounded.ErrorOutline
                    ToastTone.NEUTRAL -> Icons.Rounded.Info
                }
                val tintAlpha = if (toast.tone == ToastTone.SUCCESS) 0.14f else 0.05f
                val borderAlpha = if (toast.tone == ToastTone.SUCCESS) 0.50f else 0.24f

                Surface(
                    shape = RoundedCornerShape(17.dp),
                    color = palette.cardSurface,
                    border = BorderStroke(1.dp, accent.copy(alpha = borderAlpha)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .clickable { onDismiss() },
                ) {
                    Box(
                        modifier = Modifier.background(accent.copy(alpha = tintAlpha)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(29.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(accent, accent.copy(alpha = 0.82f)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = toast.text,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private object AssetBitmapCache {
    private val cache = ConcurrentHashMap<String, Bitmap?>()

    fun loadAssetBitmap(context: Context, assetPath: String): Bitmap? {
        cache[assetPath]?.let { return it }
        val loaded = try {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
        if (loaded != null) {
            cache[assetPath] = loaded
        }
        return loaded
    }
}

@Composable
fun TowerAppIcon(
    size: Dp = 64.dp,
    cornerRadius: Dp = size * 0.24f,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember {
        AssetBitmapCache.loadAssetBitmap(context, "tower_icon.png")
    }
    val shape = RoundedCornerShape(cornerRadius)
    val shadowMod = if (TowerTheme.palette.isDark) Modifier else Modifier.shadow(
        elevation = 10.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.10f),
        spotColor = Color.Black.copy(alpha = 0.12f),
    )
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "塔台应用图标",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .then(shadowMod)
                .clip(shape)
                .border(0.75.dp, TowerTheme.palette.cardBorder, shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .then(shadowMod)
                .clip(shape)
                .background(TowerTheme.palette.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "塔",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

@Composable
fun ClientIcon(
    iconFile: String,
    name: String,
    brandColorHex: Long,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    withShadow: Boolean = false,
) {
    val context = LocalContext.current
    val palette = TowerTheme.palette
    val bitmap = remember(iconFile) {
        AssetBitmapCache.loadAssetBitmap(context, "tower_clients/$iconFile")
    }
    val shape = RoundedCornerShape(size * 0.23f)
    val shadowMod = if (withShadow && !palette.isDark) {
        Modifier.shadow(
            elevation = 6.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.08f),
            spotColor = Color.Black.copy(alpha = 0.10f),
        )
    } else {
        Modifier
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .then(shadowMod)
                .clip(shape)
                .border(0.6.dp, palette.cardBorder, shape),
        )
    } else {
        val fallbackColor = Color(brandColorHex)
        Box(
            modifier = modifier
                .size(size)
                .then(shadowMod)
                .clip(shape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

@Composable
fun QrCodeView(
    matrix: QrMatrix?,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val palette = TowerTheme.palette
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(0.75.dp, palette.cardBorder),
        shadowElevation = 4.dp,
    ) {
        if (matrix == null || matrix.size <= 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "无法生成二维码",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .aspectRatio(1f),
            ) {
                val n = matrix.size
                val cell = this.size.minDimension / n.toFloat()
                val darkColor = Color(0xFF0F172A)
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = darkColor,
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.35f, cell + 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }
}
