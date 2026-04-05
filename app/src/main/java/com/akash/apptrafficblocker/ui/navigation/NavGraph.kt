package com.akash.apptrafficblocker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.akash.apptrafficblocker.service.BlockerService
import com.akash.apptrafficblocker.ui.blocklist.BlocklistScreen
import com.akash.apptrafficblocker.ui.dnslog.DnsLogScreen
import com.akash.apptrafficblocker.ui.home.HomeScreen
import com.akash.apptrafficblocker.ui.picker.AppPickerScreen
import com.akash.apptrafficblocker.ui.profiles.ProfilesScreen
import com.akash.apptrafficblocker.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val APP_PICKER = "app_picker"
    const val SETTINGS = "settings"
    const val BLOCKLIST = "blocklist"
    const val DNS_LOG = "dns_log"
    const val PROFILES = "profiles"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    blockerService: BlockerService?,
    onRequestVpnPermission: () -> Unit
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                blockerService = blockerService,
                onNavigateToAppPicker = { navController.navigate(Routes.APP_PICKER) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToBlocklist = { navController.navigate(Routes.BLOCKLIST) },
                onNavigateToDnsLog = { navController.navigate(Routes.DNS_LOG) },
                onNavigateToProfiles = { navController.navigate(Routes.PROFILES) },
                onRequestVpnPermission = onRequestVpnPermission
            )
        }
        composable(Routes.APP_PICKER) {
            AppPickerScreen(
                onAppSelected = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BLOCKLIST) {
            BlocklistScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DNS_LOG) {
            DnsLogScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PROFILES) {
            ProfilesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
