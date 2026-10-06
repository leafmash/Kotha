package com.kotha.app

import androidx.lifecycle.ViewModel
import com.kotha.app.data.session.SessionRepository
import com.kotha.app.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MainViewModel @Inject constructor(sessionRepository: SessionRepository) : ViewModel() {

    val session: StateFlow<SessionState> = sessionRepository.state
}
