package com.presencestudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.presencestudio.MainViewModel
import com.presencestudio.ui.components.AnimatedBackground
import com.presencestudio.ui.screens.DiscordLoginScreen
import com.presencestudio.ui.screens.HomeScreen
import com.presencestudio.ui.screens.ProfilesScreen
import com.presencestudio.ui.screens.SettingsScreen
import com.presencestudio.ui.screens.TemplatesScreen
import com.presencestudio.ui.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Profiles : Screen("profiles", "Profiles", Icons.Default.Folder)
    data object Templates : Screen("templates", "Templates", Icons.Default.Style)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun PresenceApp(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showDiscordLogin by remember { mutableStateOf(false) }

    val screens = listOf(Screen.Home, Screen.Profiles, Screen.Templates, Screen.Settings)

    if (showDiscordLogin) {
        DiscordLoginScreen(
            onTokenReceived = { token ->
                viewModel.updateToken(token)
                showDiscordLogin = false
            },
            onCancel = { showDiscordLogin = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground()

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Surface,
                    tonalElevation = 0.dp
                ) {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary,
                                selectedTextColor = Primary,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = Primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when (currentScreen) {
                Screen.Home -> HomeScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
                Screen.Profiles -> ProfilesScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
                Screen.Templates -> TemplatesScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onLoginClick = { showDiscordLogin = true },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
