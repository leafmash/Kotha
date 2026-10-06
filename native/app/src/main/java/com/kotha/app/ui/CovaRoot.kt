package com.kotha.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kotha.app.data.session.SessionState
import com.kotha.app.ui.navigation.CovaNavHost
import com.kotha.app.ui.screens.auth.AuthScreen

private enum class RootKind { Loading, Auth, Main }

@Composable
fun CovaRoot(session: SessionState) {
    val kind = when (session) {
        SessionState.Loading -> RootKind.Loading
        SessionState.SignedOut -> RootKind.Auth
        is SessionState.SignedIn -> RootKind.Main
    }
    Crossfade(targetState = kind, label = "root") { target ->
        when (target) {
            RootKind.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            RootKind.Auth -> AuthScreen()
            RootKind.Main -> CovaNavHost()
        }
    }
}
