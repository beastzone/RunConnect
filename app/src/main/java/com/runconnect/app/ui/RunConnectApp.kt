package com.runconnect.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.runconnect.app.ui.navigation.Screen
import com.runconnect.app.ui.navigation.bottomNavItems
import com.runconnect.app.ui.screens.activities.ActivitiesScreen
import com.runconnect.app.ui.screens.activitydetail.ActivityDetailScreen
import com.runconnect.app.ui.screens.bodymetrics.BodyMetricsScreen
import com.runconnect.app.ui.screens.dashboard.DashboardScreen
import com.runconnect.app.ui.screens.heartrate.HeartRateScreen
import com.runconnect.app.ui.screens.settings.SettingsScreen
import com.runconnect.app.ui.screens.sleep.SleepDetailScreen
import com.runconnect.app.ui.screens.sleep.SleepScreen
import com.runconnect.app.ui.theme.Background
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextSecondary

@Composable
fun RunConnectApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                NavigationBar(
                    containerColor = CardDark,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = CardDark,
                            ),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onActivityClick = { id ->
                        navController.navigate(Screen.ActivityDetail.createRoute(id))
                    },
                    onSeeAllActivities = {
                        navController.navigate(Screen.Activities.route)
                    }
                )
            }
            composable(Screen.Activities.route) {
                ActivitiesScreen(
                    onActivityClick = { id ->
                        navController.navigate(Screen.ActivityDetail.createRoute(id))
                    }
                )
            }
            composable(Screen.ActivityDetail.route) { backStackEntry ->
                val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
                ActivityDetailScreen(
                    activityId = activityId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Sleep.route) {
                SleepScreen(
                    onSessionClick = { id ->
                        navController.navigate(Screen.SleepDetail.createRoute(id))
                    }
                )
            }
            composable(Screen.SleepDetail.route) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                SleepDetailScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.HeartRate.route) {
                HeartRateScreen()
            }
            composable(Screen.BodyMetrics.route) {
                BodyMetricsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
