package com.edgeclaw.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.edgeclaw.mobile.ui.screens.*

/**
 * Navigation routes
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val DISCOVERY = "discovery"
    const val DEVICE_DETAIL = "device/{peerId}"
    const val SETTINGS = "settings"
    const val SECURITY = "security"

    fun deviceDetail(peerId: String) = "device/$peerId"
}

/**
 * Main navigation host
 */
@Composable
fun EdgeClawNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToDiscovery = { navController.navigate(Routes.DISCOVERY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSecurity = { navController.navigate(Routes.SECURITY) },
                onNavigateToDevice = { peerId ->
                    navController.navigate(Routes.deviceDetail(peerId))
                }
            )
        }

        composable(Routes.DISCOVERY) {
            DiscoveryScreen(
                onNavigateBack = { navController.popBackStack() },
                onDeviceSelected = { peerId ->
                    navController.navigate(Routes.deviceDetail(peerId))
                }
            )
        }

        composable(Routes.DEVICE_DETAIL) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            DeviceDetailScreen(
                peerId = peerId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SECURITY) {
            SecurityScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
