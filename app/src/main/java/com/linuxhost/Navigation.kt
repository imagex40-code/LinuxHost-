package com.linuxhost

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Install : Screen("install", "", Icons.Filled.Dashboard)
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    data object Terminal : Screen("terminal", "Terminal", Icons.Filled.Terminal)
    data object Manager : Screen("manager", "Manager", Icons.Filled.BugReport)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

private val mainScreens = listOf(
    Screen.Dashboard,
    Screen.Terminal,
    Screen.Manager,
    Screen.Settings,
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val engine = koinInject<ProotEngine>()
    val status by engine.status.collectAsState()

    var previousStatus by remember { mutableStateOf(status) }
    val successStates = listOf(InstanceStatus.INSTALLED, InstanceStatus.RUNNING, InstanceStatus.STOPPED)
    LaunchedEffect(status) {
        if (previousStatus in listOf(InstanceStatus.INSTALLING) && status in successStates) {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Install.route) { inclusive = true }
            }
        }
        previousStatus = status
    }

    val startRoute = when (status) {
        InstanceStatus.NOT_INSTALLED, InstanceStatus.INSTALLING, InstanceStatus.INTERRUPTED -> Screen.Install.route
        else -> Screen.Dashboard.route
    }

    val showBottomBar = currentDestination?.route in mainScreens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    mainScreens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Install.route) {
                InstallScreen(
                    onLaunchDashboard = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Install.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Terminal.route) { TerminalScreen() }
            composable(Screen.Manager.route) { ManagerScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
