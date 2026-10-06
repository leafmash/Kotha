package com.kotha.app.data.auth

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.kotha.app.R

@StringRes
fun Throwable.authMessage(@StringRes fallback: Int = R.string.auth_err_generic): Int = when (this) {
    is FirebaseNetworkException -> R.string.auth_err_network_request_failed
    is FirebaseTooManyRequestsException -> R.string.auth_err_too_many_requests
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_CREDENTIAL" -> R.string.auth_err_invalid_credential
        "ERROR_WRONG_PASSWORD" -> R.string.auth_err_wrong_password
        "ERROR_USER_NOT_FOUND" -> R.string.auth_err_user_not_found
        "ERROR_INVALID_EMAIL" -> R.string.auth_err_invalid_email
        "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.auth_err_email_already_in_use
        "ERROR_WEAK_PASSWORD" -> R.string.auth_err_weak_password
        "ERROR_USER_DISABLED" -> R.string.auth_err_user_disabled
        "ERROR_TOO_MANY_REQUESTS" -> R.string.auth_err_too_many_requests
        else -> fallback
    }
    else -> fallback
}
