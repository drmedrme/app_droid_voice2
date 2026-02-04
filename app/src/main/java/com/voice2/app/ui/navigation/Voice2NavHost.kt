package com.voice2.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.voice2.app.ui.chat.ChatDetailScreen
import com.voice2.app.ui.chat.ChatScreen
import com.voice2.app.ui.chat.ChatViewModel
import com.voice2.app.ui.settings.SettingsScreen
import com.voice2.app.ui.todos.TodoScreen

private const val SLIDE_DURATION = 300
private const val FADE_DURATION = 250

@Composable
fun Voice2NavHost(
    navController: NavHostController,
    chatViewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier,
        // Default transitions for tab switches: crossfade
        enterTransition = { fadeIn(tween(FADE_DURATION)) },
        exitTransition = { fadeOut(tween(FADE_DURATION)) },
        popEnterTransition = { fadeIn(tween(FADE_DURATION)) },
        popExitTransition = { fadeOut(tween(FADE_DURATION)) }
    ) {
        composable(
            route = Screen.Chat.route,
            // Going to ChatDetail: slide the list slightly left
            exitTransition = {
                if (targetState.destination.route == Screen.ChatDetail.route) {
                    slideOutHorizontally(tween(SLIDE_DURATION)) { -it / 4 } + fadeOut(tween(SLIDE_DURATION))
                } else {
                    fadeOut(tween(FADE_DURATION))
                }
            },
            // Returning from ChatDetail: slide the list back in from left
            popEnterTransition = {
                if (initialState.destination.route == Screen.ChatDetail.route) {
                    slideInHorizontally(tween(SLIDE_DURATION)) { -it / 4 } + fadeIn(tween(SLIDE_DURATION))
                } else {
                    fadeIn(tween(FADE_DURATION))
                }
            }
        ) {
            ChatScreen(
                onChatClick = { id ->
                    navController.navigate(Screen.ChatDetail.createRoute(id))
                },
                viewModel = chatViewModel
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { }),
            // Slide in from right when opening a chat
            enterTransition = {
                slideInHorizontally(tween(SLIDE_DURATION)) { it } + fadeIn(tween(SLIDE_DURATION / 2))
            },
            // Slide out to right when going back
            popExitTransition = {
                slideOutHorizontally(tween(SLIDE_DURATION)) { it } + fadeOut(tween(SLIDE_DURATION / 2))
            }
        ) {
            ChatDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { id ->
                    navController.navigate(Screen.ChatDetail.createRoute(id))
                }
            )
        }

        composable(Screen.Todos.route) {
            TodoScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
