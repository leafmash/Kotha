package com.kotha.app.data.auth

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingProfile @Inject constructor() {
    @Volatile
    var name: String? = null
}
