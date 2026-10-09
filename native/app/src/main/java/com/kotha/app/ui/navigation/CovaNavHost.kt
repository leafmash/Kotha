package com.kotha.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import com.kotha.app.ui.components.NotificationOnboarding
import com.kotha.app.ui.screens.chat.ChatScreen
import com.kotha.app.ui.screens.group.GroupInfoScreen
import com.kotha.app.ui.screens.home.HomeScreen
import com.kotha.app.ui.screens.profile.MyProfileScreen
import com.kotha.app.ui.screens.profile.UserProfileScreen
import com.kotha.app.ui.screens.settings.BlockedScreen
import com.kotha.app.ui.screens.settings.SettingsScreen

private const val SLIDE_MS = 300

private val slideIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(SLIDE_MS)) { it } + fadeIn(tween(SLIDE_MS))
}

private val slideOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(SLIDE_MS)) { it } + fadeOut(tween(SLIDE_MS))
}

private fun NavHostController.openChatFresh(chatId: String) {
    navigate(Routes.chat(chatId)) { popUpTo(Routes.HOME) }
}

private fun NavHostController.openChatFromLink(chatId: String) {
    val entry = currentBackStackEntry
    val route = entry?.destination?.route
    val current = entry?.arguments?.getString("chatId")
    if (route == Routes.CHAT && current == chatId) return
    navigate(Routes.chat(chatId)) {
        popUpTo(Routes.HOME)
        launchSingleTop = true
    }
}

@Composable
fun CovaNavHost(navHostViewModel: NavHostViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val pending by navHostViewModel.pending.collectAsStateWithLifecycle()
    val ready by navHostViewModel.ready.collectAsStateWithLifecycle()
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
            HomeScreen(
                onOpenChat = { navController.navigate(Routes.chat(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            enterTransition = slideIn,
            popExitTransition = slideOut
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenGroup = { navController.navigate(Routes.group(it)) },
                onOpenUser = { uid, chatId -> navController.navigate(Routes.user(uid, chatId)) }
            )
        }
        composable(
            route = Routes.GROUP,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            enterTransition = slideIn,
            popExitTransition = slideOut
        ) {
            GroupInfoScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.openChatFresh(it) },
                onOpenUser = { uid, chatId -> navController.navigate(Routes.user(uid, chatId)) },
                onLeft = { navController.popBackStack(Routes.HOME, false) }
            )
        }
        composable(
            route = Routes.USER,
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("chatId") { type = NavType.StringType }
            ),
            enterTransition = slideIn,
            popExitTransition = slideOut
        ) {
            UserProfileScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { navController.openChatFresh(it) }
            )
        }
        composable(
            route = Routes.PROFILE,
            enterTransition = slideIn,
            popExitTransition = slideOut
        ) {
            MyProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SETTINGS,
            enterTransition = slideIn,
            popExitTransition = slideOut
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenBlocked = { navController.navigate(Routes.BLOCKED) }
            )
        }
        composable(
            route = Routes.BLOCKED,
            enterTransition = slideIn,
            popExitTransition = slideOut
        ) {
            BlockedScreen(onBack = { navController.popBackStack() })
        }
    }

    LaunchedEffect(pending, ready) {
        val link = pending
        if (link == null || !ready) return@LaunchedEffect
        navController.currentBackStackEntryFlow.first()
        navHostViewModel.consume(link)
        navController.openChatFromLink(link.chatId)
    }

    NotificationOnboarding(
        enabled = ready,
        onPermissionResult = { navHostViewModel.syncPush() }
    )

    LaunchedEffect(ready) {
        if (ready) navHostViewModel.syncPush()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ready) navHostViewModel.syncPush()
    }
}
