package com.kotha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

object FirestoreRest {

    enum class Outcome { SUCCESS, RETRY, FAILURE }

    private class Response(val code: Int, val body: String)

    private const val TIMEOUT_MS = 15000
    private const val PUSH_ATTEMPTS = 3
    private const val PUSH_RETRY_MS = 1500L
    private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
    private const val FIRESTORE_URL = "https://firestore.googleapis.com/v1"

    fun sendMessage(context: Context, session: Session, chatId: String, text: String): Outcome {
        val token = when (val result = refreshIdToken(context, session)) {
            is TokenResult.Ok -> result.token
            TokenResult.Retry -> return Outcome.RETRY
            TokenResult.Rejected -> return Outcome.FAILURE
        }

        val database = "projects/${session.projectId}/databases/(default)/documents"
        val members = fetchMembers(token, database, chatId) ?: return Outcome.RETRY
        if (!members.contains(session.uid)) return Outcome.FAILURE

        val messageId = UUID.randomUUID().toString().replace("-", "").take(20)
        val commit = JSONObject().put(
            "writes",
            JSONArray()
                .put(messageWrite(database, chatId, session.uid, text, messageId))
                .put(chatWrite(database, chatId, session.uid, members, text))
        )
        val response = request(
            "$FIRESTORE_URL/$database:commit",
            "POST",
            commit.toString(),
            "application/json",
            token
        ) ?: return Outcome.RETRY

        return when {
            response.code in 200..299 -> {
                triggerPush(session, token, chatId, messageId)
                Outcome.SUCCESS
            }
            response.code == 429 || response.code >= 500 -> Outcome.RETRY
            else -> Outcome.FAILURE
        }
    }

    private fun triggerPush(session: Session, token: String, chatId: String, messageId: String) {
        if (session.apiBase.isBlank()) return
        val body = JSONObject()
            .put("type", "message")
            .put("chatId", chatId)
            .put("messageId", messageId)
            .toString()
        for (attempt in 0 until PUSH_ATTEMPTS) {
            if (attempt > 0) Thread.sleep(PUSH_RETRY_MS * attempt)
            val response = request(session.apiBase + "/api/notify", "POST", body, "application/json", token)
            if (response != null) {
                val code = response.code
                if (code in 200..299) return
                if (code in 400..499 && code != 408 && code != 429) return
            }
        }
    }

    private sealed class TokenResult {
        class Ok(val token: String) : TokenResult()
        object Retry : TokenResult()
        object Rejected : TokenResult()
    }

    private fun refreshIdToken(context: Context, session: Session): TokenResult {
        val form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(session.refreshToken, "UTF-8")
        val response = request(
            "$TOKEN_URL?key=" + URLEncoder.encode(session.apiKey, "UTF-8"),
            "POST",
            form,
            "application/x-www-form-urlencoded",
            null
        ) ?: return TokenResult.Retry

        if (response.code == 429 || response.code >= 500) return TokenResult.Retry
        if (response.code !in 200..299) return TokenResult.Rejected

        val json = JSONObject(response.body)
        val idToken = json.optString("id_token")
        if (idToken.isEmpty()) return TokenResult.Rejected
        val rotated = json.optString("refresh_token")
        if (rotated.isNotEmpty() && rotated != session.refreshToken) {
            SessionStore.updateRefreshToken(context, rotated)
        }
        return TokenResult.Ok(idToken)
    }

    private fun fetchMembers(token: String, database: String, chatId: String): List<String>? {
        val response = request("$FIRESTORE_URL/$database/chats/$chatId", "GET", null, null, token) ?: return null
        if (response.code !in 200..299) return null
        val values = JSONObject(response.body)
            .optJSONObject("fields")
            ?.optJSONObject("members")
            ?.optJSONObject("arrayValue")
            ?.optJSONArray("values") ?: return emptyList()
        return (0 until values.length()).mapNotNull { values.getJSONObject(it).optString("stringValue").takeIf { v -> v.isNotEmpty() } }
    }

    private fun stringField(value: String) = JSONObject().put("stringValue", value)

    private fun serverTime(path: String) = JSONObject().put("fieldPath", path).put("setToServerValue", "REQUEST_TIME")

    private fun quoted(key: String) = "`" + key.replace("\\", "\\\\").replace("`", "\\`") + "`"

    private fun messageWrite(database: String, chatId: String, uid: String, text: String, id: String): JSONObject {
        val fields = JSONObject()
            .put("from", stringField(uid))
            .put("type", stringField("text"))
            .put("text", stringField(text))
            .put("status", stringField("sent"))
        return JSONObject()
            .put("update", JSONObject().put("name", "$database/chats/$chatId/messages/$id").put("fields", fields))
            .put("updateTransforms", JSONArray().put(serverTime("at")))
            .put("currentDocument", JSONObject().put("exists", false))
    }

    private fun chatWrite(database: String, chatId: String, uid: String, members: List<String>, text: String): JSONObject {
        val typing = JSONObject().put("mapValue", JSONObject().put("fields", JSONObject().put(uid, JSONObject().put("booleanValue", false))))
        val unread = JSONObject().put("mapValue", JSONObject().put("fields", JSONObject().put(uid, JSONObject().put("integerValue", "0"))))
        val fields = JSONObject()
            .put("lastMessage", stringField(text))
            .put("lastFrom", stringField(uid))
            .put("typing", typing)
            .put("unread", unread)

        val mask = JSONArray()
            .put("lastMessage")
            .put("lastFrom")
            .put("typing." + quoted(uid))
            .put("unread." + quoted(uid))

        val transforms = JSONArray().put(serverTime("lastAt"))
        members.filter { it != uid }.forEach {
            transforms.put(
                JSONObject()
                    .put("fieldPath", "unread." + quoted(it))
                    .put("increment", JSONObject().put("integerValue", "1"))
            )
        }

        return JSONObject()
            .put("update", JSONObject().put("name", "$database/chats/$chatId").put("fields", fields))
            .put("updateMask", JSONObject().put("fieldPaths", mask))
            .put("updateTransforms", transforms)
            .put("currentDocument", JSONObject().put("exists", true))
    }

    private fun request(url: String, method: String, body: String?, contentType: String?, bearer: String?): Response? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            Response(code, text)
        } catch (e: Exception) {
            null
        }
    }
}
