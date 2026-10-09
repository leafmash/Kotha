package com.kotha.app.data.call

import com.kotha.app.BuildConfig
import com.kotha.app.data.auth.AuthRepository
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class IceServerProvider @Inject constructor(private val authRepository: AuthRepository) {

    private var cached: List<IceServerSpec> = emptyList()
    private var expiresAt = 0L

    suspend fun load(): List<IceServerSpec> {
        if (cached.isNotEmpty() && expiresAt > System.currentTimeMillis()) return cached
        return try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FALLBACK
        }
    }

    private suspend fun fetch(): List<IceServerSpec> {
        val token = authRepository.idToken(false)
        val body = withContext(Dispatchers.IO) {
            val connection = URL(BuildConfig.API_BASE + "/api/turn").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (connection.responseCode !in 200..299) return@withContext ""
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        if (body.isEmpty()) return FALLBACK
        val json = JSONObject(body)
        val servers = parse(json.optJSONArray("iceServers"))
        if (servers.isEmpty()) return FALLBACK
        if (json.optBoolean("relay", false)) {
            cached = servers
            expiresAt = System.currentTimeMillis() + CACHE_MS
        }
        return servers
    }

    private fun parse(array: JSONArray?): List<IceServerSpec> {
        if (array == null) return emptyList()
        val out = ArrayList<IceServerSpec>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val urls = ArrayList<String>()
            val raw = item.opt("urls")
            if (raw is JSONArray) {
                for (i in 0 until raw.length()) raw.optString(i).takeIf { it.isNotBlank() }?.let { urls.add(it) }
            } else if (raw is String && raw.isNotBlank()) {
                urls.add(raw)
            }
            if (urls.isEmpty()) continue
            out.add(IceServerSpec(urls, item.optString("username"), item.optString("credential")))
        }
        return out
    }

    private companion object {
        const val TIMEOUT_MS = 6_000
        const val CACHE_MS = 30 * 60_000L
        val FALLBACK = listOf(IceServerSpec(listOf("stun:stun.l.google.com:19302"), "", ""))
    }
}
