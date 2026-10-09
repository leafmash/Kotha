package com.kotha.app.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen

fun DocumentReference.snapshotFlow(includeMetadata: Boolean = false): Flow<DocumentSnapshot> = callbackFlow {
    val changes = if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE
    val registration = addSnapshotListener(changes) { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

fun Query.snapshotFlow(includeMetadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
    val changes = if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE
    val registration = addSnapshotListener(changes) { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

fun <T> Flow<T>.resilient(): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException || attempt >= 4) {
        false
    } else {
        delay(1_000L * (attempt + 1))
        true
    }
}.catch { }
