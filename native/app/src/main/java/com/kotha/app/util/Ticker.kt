package com.kotha.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun ticker(periodMs: Long): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMs)
    }
}
