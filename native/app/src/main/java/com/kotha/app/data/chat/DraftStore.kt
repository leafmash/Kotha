package com.kotha.app.data.chat

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class DraftStore @Inject constructor() {

    private val mutable = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = mutable.asStateFlow()

    fun get(chatId: String): String = mutable.value[chatId].orEmpty()

    fun set(chatId: String, text: String) {
        mutable.update { if (text.isBlank()) it - chatId else it + (chatId to text) }
    }
}
