package com.lcdr.assistant.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.lcdr.assistant.ui.chat.ChatScreen
import com.lcdr.assistant.ui.device.DeviceScreen
import com.lcdr.assistant.ui.hub.HubScreen
import com.lcdr.assistant.ui.login.LoginScreen
import com.lcdr.assistant.ui.memory.MemoryScreen
import com.lcdr.assistant.ui.settings.SettingsScreen
import com.lcdr.assistant.ui.voice.VoiceScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(startedWithToken: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startedWithToken) Screen.Chat.route else Screen.Login.route

    val topLevelRoutes = listOf(
        Screen.Chat.route,
        Screen.Device.route,
        Screen.Hub.route,
        Screen.Memory.route,
        Screen.Settings.route
    )

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Chat.route,
                        onClick = { navController.navigateTopLevel(Screen.Chat.route) },
                        icon = { Icon(Icons.Default.Chat, "Chat") },
                        label = { Text("LCDR") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Device.route,
                        onClick = { navController.navigateTopLevel(Screen.Device.route) },
                        icon = { Icon(Icons.Default.PhoneAndroid, "Device") },
                        label = { Text("Device") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Hub.route,
                        onClick = { navController.navigateTopLevel(Screen.Hub.route) },
                        icon = { Icon(Icons.Default.Hub, "Hub") },
                        label = { Text("Hub") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Memory.route,
                        onClick = { navController.navigateTopLevel(Screen.Memory.route) },
                        icon = { Icon(Icons.Default.Memory, "Memory") },
                        label = { Text("Memory") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = { navController.navigateTopLevel(Screen.Settings.route) },
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Chat.route) {
                ChatScreen(
                    onNavigateToVoice = { navController.navigate(Screen.Voice.route) }
                )
            }
            composable(Screen.Voice.route) {
                VoiceScreen(onDismiss = { navController.popBackStack() })
            }
            composable(Screen.Hub.route) {
                HubScreen()
            }
            composable(Screen.Memory.route) {
                MemoryScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Device.route) {
                DeviceScreen(
                    onNavigateToChat = { navController.navigateTopLevel(Screen.Chat.route) }
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
