package com.runconnect.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Activities : Screen("activities")
    object ActivityDetail : Screen("activity_detail/{activityId}") {
        fun createRoute(activityId: String) = "activity_detail/$activityId"
    }
    object Sleep : Screen("sleep")
    object SleepDetail : Screen("sleep_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "sleep_detail/$sessionId"
    }
    object HeartRate : Screen("heart_rate")
    object BodyMetrics : Screen("body_metrics")
    object Settings : Screen("settings")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Activities, "Activities", Icons.Filled.DirectionsRun, Icons.Outlined.DirectionsRun),
    BottomNavItem(Screen.Sleep, "Sleep", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    BottomNavItem(Screen.HeartRate, "Heart", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    BottomNavItem(Screen.BodyMetrics, "Body", Icons.Filled.MonitorWeight, Icons.Outlined.MonitorWeight),
    BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)
