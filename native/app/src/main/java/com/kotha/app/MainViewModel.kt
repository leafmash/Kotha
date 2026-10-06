package com.kotha.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take

@HiltViewModel
class MainViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {

    private val routes: Flow<String?> = authRepository.currentUser
        .map { user -> if (user == null) Routes.AUTH else Routes.HOME }
        .take(1)

    val startRoute: StateFlow<String?> = routes.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
