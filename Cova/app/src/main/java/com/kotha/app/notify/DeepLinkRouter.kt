package com.kotha.app.notify

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DeepLinkRouter @Inject constructor() {

    private val mutable = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = mutable.asStateFlow()

    fun publish(link: DeepLink) {
        mutable.value = link
    }

    fun consume(link: DeepLink) {
        mutable.compareAndSet(link, null)
    }
}
