package com.scamslayer.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scamslayer.app.ui.screens.CreatePersonaScreen
import com.scamslayer.app.ui.screens.EditPersonaScreen
import com.scamslayer.app.ui.screens.HomeScreen
import com.scamslayer.app.ui.screens.RecordingsScreen
import com.scamslayer.app.ui.screens.SettingsScreen
import com.scamslayer.app.ui.theme.ScamRed

sealed class Screen(
    val route: String,
    val titleKey: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    val title: String get() = when (titleKey) {
        "home" -> L.s.navHome
        "recordings" -> L.s.navRecordings
        "guide" -> L.s.navGuide
        else -> titleKey
    }
    data object Home : Screen("home", "home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Recordings : Screen("recordings", "recordings", Icons.Filled.Mic, Icons.Outlined.Mic)
    data object Settings : Screen("settings", "guide", Icons.Filled.MenuBook, Icons.Outlined.MenuBook)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Recordings,
    Screen.Settings
)

@Composable
fun ScamSlayerNavigation(viewModel: MainViewModel, initialRoute: String? = null) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            navController.navigate(initialRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // Pop back to main nav graph first (cancels edit/create)
                            navController.popBackStack(navController.graph.findStartDestination().id, false)
                            if (screen.route != Screen.Home.route) {
                                navController.navigate(screen.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(text = screen.title)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScamRed,
                            selectedTextColor = ScamRed,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onCreatePersona = {
                        navController.navigate("create_persona")
                    },
                    onEditPersona = { personaId ->
                        navController.navigate("edit_persona/$personaId")
                    }
                )
            }
            composable(Screen.Recordings.route) {
                RecordingsScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable("create_persona") {
                CreatePersonaScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("edit_persona/{personaId}") { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: return@composable
                EditPersonaScreen(
                    viewModel = viewModel,
                    personaId = personaId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
