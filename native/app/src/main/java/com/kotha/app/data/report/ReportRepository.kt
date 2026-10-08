package com.kotha.app.data.report

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kotha.app.core.AppConfig
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.block.BlockRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class ReportTarget(
    val type: String,
    val chatId: String,
    val reportedUid: String = "",
    val messageId: String = "",
    val content: String = "",
    val contentType: String = ""
)

val ReportReasons = listOf("spam", "harassment", "hate", "sexual", "violence", "scam", "other")

@Singleton
class ReportRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val blockRepository: BlockRepository
) {

    suspend fun submit(target: ReportTarget, reason: String, note: String, alsoBlock: Boolean): Boolean {
        val uid = authRepository.user?.uid ?: return false
        val data = mutableMapOf<String, Any>(
            "reporter" to uid,
            "type" to target.type,
            "reason" to reason,
            "chatId" to target.chatId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val trimmed = note.trim().take(AppConfig.REPORT_NOTE_MAX)
        if (trimmed.isNotEmpty()) data["note"] = trimmed
        if (target.reportedUid.isNotEmpty()) data["reportedUid"] = target.reportedUid
        if (target.messageId.isNotEmpty()) data["messageId"] = target.messageId
        if (target.content.isNotEmpty()) data["content"] = target.content.take(REPORT_CONTENT_MAX)
        if (target.contentType.isNotEmpty()) data["contentType"] = target.contentType
        return try {
            firestore.collection("reports").add(data).await()
            if (alsoBlock && target.reportedUid.isNotEmpty()) blockRepository.block(target.reportedUid)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val REPORT_CONTENT_MAX = 1500
    }
}
