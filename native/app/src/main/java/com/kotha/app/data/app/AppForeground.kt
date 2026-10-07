package com.kotha.app.data.app

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppForeground @Inject constructor() {

    private val mutable = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = mutable.asStateFlow()

    fun set(value: Boolean) {
        mutable.value = value
    }
}
