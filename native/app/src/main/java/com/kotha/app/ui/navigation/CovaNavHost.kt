package com.kotha.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kotha.app.ui.screens.chat.ChatScreen
import com.kotha.app.ui.screens.home.HomeScreen

private const val SLIDE_MS = 300

@Composable
fun CovaNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(240)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(160)) }
    ) {
        composable(
            route = Routes.HOME,
            exitTransition = { slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeOut(tween(SLIDE_MS)) },
            popEnterTransition = { slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeIn(tween(SLIDE_MS)) }
        ) {
            HomeScreen(onOpenChat = { navController.navigate(Routes.chat(it)) })
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(SLIDE_MS)) { it } + fadeIn(tween(SLIDE_MS)) },
            popExitTransition = { slideOutHorizontally(tween(SLIDE_MS)) { it } + fadeOut(tween(SLIDE_MS)) }
        ) {
            ChatScreen(onBack = { navController.popBackStack() })
        }
    }
}
