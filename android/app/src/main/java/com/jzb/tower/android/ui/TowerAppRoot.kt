package com.jzb.tower.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jzb.tower.android.TowerViewModel
import com.jzb.tower.android.ui.components.ToastHost
import com.jzb.tower.android.ui.components.TowerPageBackground
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

private val TowerTabs = listOf(
    TabItem(AppTab.SUBSCRIPTIONS, "订阅", Icons.Rounded.RssFeed),
    TabItem(AppTab.RULES, "规则", Icons.AutoMirrored.Rounded.AltRoute),
    TabItem(AppTab.EXPORT, "导出", Icons.Rounded.Outbox),
    TabItem(AppTab.SETTINGS, "设置", Icons.Rounded.Settings),
)

@Composable
fun TowerAppRoot(
    viewModel: TowerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val hasCompletedOnboarding = state.snapshot.settings.hasCompletedOnboarding

    TowerTheme(themePreference = state.snapshot.settings.theme) {
        val palette = TowerTheme.palette
        Box(modifier = modifier.fillMaxSize()) {
            if (!hasCompletedOnboarding) {
                WelcomeScreen(
                    onStart = { viewModel.completeOnboarding() },
                )
            } else {
                TowerPageBackground {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            NavigationBar(
                                containerColor = palette.cardSurface,
                                tonalElevation = 0.dp,
                            ) {
                                TowerTabs.forEach { item ->
                                    val selected = selectedTab == item.tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(item.tab) },
                                        icon = {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                            )
                                        },
                                        label = { Text(item.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = palette.primary,
                                            selectedTextColor = palette.primary,
                                            indicatorColor = palette.primaryContainer,
                                            unselectedIconColor = palette.textSecondary,
                                            unselectedTextColor = palette.textSecondary,
                                        ),
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            Crossfade(
                                targetState = selectedTab,
                                animationSpec = TowerMotion.selectionSpec(),
                                label = "tabCrossfade",
                            ) { tab ->
                                when (tab) {
                                    AppTab.SUBSCRIPTIONS -> SubscriptionsScreen(viewModel = viewModel)
                                    AppTab.RULES -> RulesScreen(viewModel = viewModel)
                                    AppTab.EXPORT -> ExportScreen(viewModel = viewModel)
                                    AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
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
