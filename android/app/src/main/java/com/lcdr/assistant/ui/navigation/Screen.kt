package com.lcdr.assistant.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Chat : Screen("chat")
    object Voice : Screen("voice")
    object Hub : Screen("hub")
    object Memory : Screen("memory")
    object Settings : Screen("settings")
    object Device : Screen("device")
    object ActionHistory : Screen("action_history")
}
