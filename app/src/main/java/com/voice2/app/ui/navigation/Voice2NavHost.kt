package com.voice2.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.voice2.app.ui.chat.ChatScreen
import com.voice2.app.ui.chat.ChatDetailScreen
import com.voice2.app.ui.todos.TodoScreen
import com.voice2.app.ui.settings.SettingsScreen

@Composable
fun Voice2NavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(onChatClick = { id -> 
                navController.navigate(Screen.ChatDetail.createRoute(id))
            })
        }
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { })
        ) {
            ChatDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Todos.route) {
            TodoScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
