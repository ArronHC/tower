package com.jzb.tower.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.Outbound
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.ToastHost
import com.jzb.tower.android.ui.components.TowerPageBackground
import com.jzb.tower.android.ui.components.towerPressScale
import com.jzb.tower.android.ui.export.ExportScreen
import com.jzb.tower.android.ui.onboarding.WelcomeScreen
import com.jzb.tower.android.ui.rules.RulesScreen
import com.jzb.tower.android.ui.settings.SettingsScreen
import com.jzb.tower.android.ui.subscriptions.SubscriptionsScreen
import com.jzb.tower.android.ui.theme.TowerMotion
import com.jzb.tower.android.ui.theme.TowerTheme
import com.jzb.tower.core.model.AppTab

private data class TabItem(
    val tab: AppTab,
    val label: String,
    val icon: ImageVector,
)

private val TowerMainTabs = listOf(
    TabItem(AppTab.SUBSCRIPTIONS, "订阅", Icons.Rounded.Sensors),
    TabItem(AppTab.RULES, "规则", Icons.AutoMirrored.Rounded.AltRoute),
    TabItem(AppTab.EXPORT, "导出", Icons.AutoMirrored.Rounded.Outbound),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowerAppRoot(
    viewModel: TowerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val showSettingsSheet by viewModel.showSettingsSheet.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val hasCompletedOnboarding = state.snapshot.settings.hasCompletedOnboarding

    TowerTheme(themePreference = state.snapshot.settings.theme) {
        val palette = TowerTheme.palette
        val effectiveTab = if (selectedTab == AppTab.SETTINGS) AppTab.EXPORT else selectedTab

        Box(modifier = modifier.fillMaxSize()) {
            if (!hasCompletedOnboarding) {
                WelcomeScreen(
                    onStart = { viewModel.completeOnboarding() },
                )
            } else {
                TowerPageBackground {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Crossfade(
                            targetState = effectiveTab,
                            animationSpec = TowerMotion.selectionSpec(),
                            label = "tabCrossfade",
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                        ) { tab ->
                            when (tab) {
                                AppTab.SUBSCRIPTIONS -> SubscriptionsScreen(viewModel = viewModel)
                                AppTab.RULES -> RulesScreen(viewModel = viewModel)
                                AppTab.EXPORT, AppTab.SETTINGS -> ExportScreen(viewModel = viewModel)
                            }
                        }

                        // Non-blocking floating batch refresh capsule above the floating tab bar
                        AnimatedVisibility(
                            visible = isRefreshing,
                            enter = slideInVertically(TowerMotion.capsuleSpring()) { it } + fadeIn(TowerMotion.selectionSpec()),
                            exit = slideOutVertically(TowerMotion.capsuleSpring()) { it } + fadeOut(TowerMotion.selectionSpec()),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 82.dp),
                        ) {
                            FloatingRefreshCapsule(
                                onCancel = { viewModel.cancelRefreshAll() },
                            )
                        }

                        // Floating 3-Tab Glass Pill Navigation matching iphone-1/3.jpg & Mobbin Luma/Lightyear
                        FloatingPillTabBar(
                            selectedTab = effectiveTab,
                            onSelectTab = { viewModel.selectTab(it) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 10.dp),
                        )
                    }
                }
            }

            if (showSettingsSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { viewModel.closeSettings() },
                    sheetState = sheetState,
                    dragHandle = null,
                    containerColor = palette.groupedBackground,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onClose = { viewModel.closeSettings() },
                    )
                }
            }

            ToastHost(
                toast = toastMessage,
                onDismiss = { viewModel.dismissToast() },
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }
}

@Composable
private fun FloatingPillTabBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val haptic = LocalHapticFeedback.current
    val selectedIndex = TowerMainTabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    val pillShape = RoundedCornerShape(32.dp)

    Surface(
        shape = pillShape,
        color = palette.pillNavSurface,
        border = BorderStroke(0.75.dp, palette.pillNavBorder),
        modifier = modifier
            .width(248.dp)
            .height(60.dp)
            .shadow(
                elevation = 14.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = if (palette.isDark) 0.35f else 0.09f),
                spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.40f else 0.13f),
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        ) {
            val segmentWidth = maxWidth / TowerMainTabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = TowerMotion.pillSpring(),
                label = "floatingTabIndicator",
            )

            // Animated sliding active pill indicator
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(palette.pillIndicator),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TowerMainTabs.forEach { item ->
                    val isSelected = item.tab == selectedTab
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) palette.primary else palette.textPrimary.copy(alpha = 0.72f),
                        animationSpec = TowerMotion.selectionSpec(),
                        label = "tabTint_${item.label}",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.92f,
                        animationSpec = TowerMotion.selectionSpec(),
                        label = "tabScale_${item.label}",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (!isSelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectTab(item.tab)
                                    }
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingRefreshCapsule(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TowerTheme.palette
    val shape = CircleShape
    Surface(
        shape = shape,
        color = palette.pillNavSurface,
        border = BorderStroke(0.75.dp, palette.pillNavBorder),
        modifier = modifier
            .height(42.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = palette.primary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "正在刷新订阅…",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = palette.textPrimary,
            )
            VerticalDivider(
                color = palette.divider,
                modifier = Modifier.height(16.dp),
            )
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = palette.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .towerPressScale(onClick = onCancel)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
