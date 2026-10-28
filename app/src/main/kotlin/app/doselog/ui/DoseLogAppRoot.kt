package app.doselog.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.doselog.ui.components.LocalFormats
import app.doselog.ui.components.rememberDoseLogFormats
import app.doselog.ui.export.ExportScreen
import app.doselog.ui.log.LogScreen
import app.doselog.ui.manage.ItemEditScreen
import app.doselog.ui.manage.ManageScreen
import app.doselog.ui.navigation.ExportRoute
import app.doselog.ui.navigation.ItemEditRoute
import app.doselog.ui.navigation.LogRoute
import app.doselog.ui.navigation.ManageRoute
import app.doselog.ui.navigation.ReportsRoute
import app.doselog.ui.navigation.ScoreRoute
import app.doselog.ui.navigation.TopLevelDestination
import app.doselog.ui.reports.ReportsScreen
import app.doselog.ui.score.ScoreScreen

@Composable
fun DoseLogAppRoot(modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalFormats provides rememberDoseLogFormats()) {
        AppScaffold(modifier)
    }
}

@Composable
private fun AppScaffold(modifier: Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { topLevel ->
        destination?.hierarchy?.any { it.hasRoute(topLevel.routeClass) } == true
    }

    Scaffold(
        modifier = modifier,
        // Each screen handles its own status-bar insets through its top app bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { topLevel ->
                        val selected = topLevel == currentTopLevel
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(topLevel) },
                            icon = {
                                Icon(
                                    painter = painterResource(if (selected) topLevel.selectedIcon else topLevel.icon),
                                    contentDescription = null,
                                )
                            },
                            label = { Text(topLevel.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = LogRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable<LogRoute> {
                LogScreen(
                    onOpenManage = { navController.navigateToTopLevel(TopLevelDestination.MANAGE) },
                    onOpenExport = { navController.navigate(ExportRoute) },
                )
            }
            composable<ScoreRoute> {
                ScoreScreen(onOpenExport = { navController.navigate(ExportRoute) })
            }
            composable<ReportsRoute> {
                ReportsScreen(onOpenExport = { navController.navigate(ExportRoute) })
            }
            composable<ManageRoute> {
                ManageScreen(
                    onAddItem = { navController.navigate(ItemEditRoute()) },
                    onEditItem = { id -> navController.navigate(ItemEditRoute(id)) },
                    onOpenExport = { navController.navigate(ExportRoute) },
                )
            }
            composable<ItemEditRoute> {
                ItemEditScreen(onDone = { navController.popBackStack() })
            }
            composable<ExportRoute> {
                ExportScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
