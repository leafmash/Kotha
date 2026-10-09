package com.kotha.app.data.call

import com.kotha.app.core.ApplicationScope
import com.kotha.app.data.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class CallCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    private val repository: CallRepository,
    private val manager: CallManager,
    @ApplicationScope private val scope: CoroutineScope
) {

    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            authRepository.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else repository.observeIncoming(uid) }
                .collect { docs -> docs.forEach { manager.onIncoming(it) } }
        }
    }
}
