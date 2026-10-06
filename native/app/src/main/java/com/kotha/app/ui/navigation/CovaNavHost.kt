package com.kotha.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kotha.app.ui.screens.auth.AuthScreen
import com.kotha.app.ui.screens.home.HomeScreen

@Composable
fun CovaNavHost(startRoute: String) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = startRoute,
        enterTransition = { fadeIn(tween(240)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(160)) }
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                onContinue = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
