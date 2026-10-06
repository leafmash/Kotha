package com.kotha.app.data.session

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val uid: String, val needsTerms: Boolean) : SessionState
}

enum class VerifyResult { Verified, NotYet, Failed, Unchanged }
