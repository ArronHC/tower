package com.jzb.tower.android.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jzb.tower.android.ui.theme.TowerDimens
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
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
 * Elevated Tower surface card with 22dp continuous rounded corners and subtle 0.75dp border.
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
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) TowerMotion.PRESS_SCALE else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) TowerMotion.PRESS_IN_MS else TowerMotion.PRESS_RELEASE_MS,
        ),
        label = "cardPressScale",
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
        shadowElevation = if (TowerTheme.palette.isDark) 0.dp else 1.5.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
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
                modifier = Modifier.size(15.dp),
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
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) TowerMotion.PRESS_SCALE else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "metricScale",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                },
                label = "metricCount",
            ) { num ->
                Text(
                    text = num.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                )
            }
            if (total != null) {
                Text(
                    text = "/$total",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = palette.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp),
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
    enabled: Boolean = true,
    containerColor: Color = TowerTheme.palette.primary,
    contentColor: Color = Color.White,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) TowerMotion.PRESS_SCALE else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) TowerMotion.PRESS_IN_MS else TowerMotion.PRESS_RELEASE_MS,
        ),
        label = "primaryBtnScale",
    )

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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (icon != null) {
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
    enabled: Boolean = true,
    tint: Color = TowerTheme.palette.primary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) TowerMotion.PRESS_SCALE else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "tintedBtnScale",
    )

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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
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
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.95f, animationSpec = tween(180)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)),
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
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = palette.cardSurface,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .clickable { onDismiss() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent),
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
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
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
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "塔台应用图标",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(0.75.dp, TowerTheme.palette.cardBorder, shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
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
) {
    val context = LocalContext.current
    val bitmap = remember(iconFile) {
        AssetBitmapCache.loadAssetBitmap(context, "tower_clients/$iconFile")
    }
    val shape = RoundedCornerShape(size * 0.24f)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(0.6.dp, TowerTheme.palette.cardBorder, shape),
        )
    } else {
        val fallbackColor = Color(brandColorHex)
        Box(
            modifier = modifier
                .size(size)
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
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(0.75.dp, palette.cardBorder),
        shadowElevation = 2.dp,
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
