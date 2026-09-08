package com.starfall.gsadrive.data

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** REST client for the Google Photos Picker API (photospicker.googleapis.com). */
object GooglePhotosPickerApi {
    private const val BASE = "https://photospicker.googleapis.com/v1"

    data class Session(
        val id: String,
        val pickerUri: String,
        val mediaItemsSet: Boolean,
        val pollIntervalMillis: Long,
        val timeoutMillis: Long
    )

    data class PickedItem(
        val id: String,
        val filename: String,
        val mimeType: String,
        val baseUrl: String,
        val createTime: String?
    )

    fun createSession(accessToken: String, maxItemCount: Int = 200): Session {
        val body = JSONObject()
            .put("pickingConfig", JSONObject().put("maxItemCount", maxItemCount.coerceIn(1, 2000).toString()))
        val response = JSONObject(request(accessToken, "POST", "$BASE/sessions", body.toString().toByteArray()).decodeToString())
        return response.toSession()
    }

    fun getSession(accessToken: String, sessionId: String): Session {
        val response = JSONObject(request(accessToken, "GET", "$BASE/sessions/${encodePath(sessionId)}").decodeToString())
        return response.toSession()
    }

    fun listPickedItems(accessToken: String, sessionId: String): List<PickedItem> {
        val result = mutableListOf<PickedItem>()
        var pageToken: String? = null
        do {
            val query = buildString {
                append("?sessionId=").append(URLEncoder.encode(sessionId, "UTF-8"))
                append("&pageSize=100")
                pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val response = JSONObject(request(accessToken, "GET", "$BASE/mediaItems$query").decodeToString())
            response.optJSONArray("mediaItems")?.let { items ->
                repeat(items.length()) { index ->
                    val item = items.optJSONObject(index) ?: return@repeat
                    val mediaFile = item.optJSONObject("mediaFile") ?: return@repeat
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@repeat
                    val baseUrl = mediaFile.optString("baseUrl").takeIf { it.isNotBlank() } ?: return@repeat
                    result += PickedItem(
                        id = id,
                        filename = mediaFile.optString("filename").ifBlank { "Google Photos media" },
                        mimeType = mediaFile.optString("mimeType").ifBlank { "application/octet-stream" },
                        baseUrl = baseUrl,
                        createTime = item.optString("createTime").ifBlank { null }
                    )
                }
            }
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return result
    }

    fun download(accessToken: String, item: PickedItem, target: File) {
        val suffix = if (item.mimeType.startsWith("video/")) "=dv" else "=d"
        val connection = (URL(item.baseUrl + suffix).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 180_000
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Google Photos Picker ($status): $message")
            }
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) }
            }
        } finally {
            connection.disconnect()
        }
    }

    fun deleteSession(accessToken: String, sessionId: String) {
        request(accessToken, "DELETE", "$BASE/sessions/${encodePath(sessionId)}")
    }

    private fun request(
        accessToken: String,
        method: String,
        address: String,
        payload: ByteArray? = null
    ): ByteArray {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 60_000
            if (payload != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                outputStream.use { it.write(payload) }
            }
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes() } ?: ByteArray(0)
            if (status !in 200..299) error("Google Photos Picker API ($status): ${body.decodeToString()}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toSession(): Session {
        val polling = optJSONObject("pollingConfig")
        return Session(
            id = getString("id"),
            pickerUri = optString("pickerUri"),
            mediaItemsSet = optBoolean("mediaItemsSet", false),
            pollIntervalMillis = parseDurationMillis(polling?.optString("pollInterval"), 2_000L).coerceAtLeast(500L),
            timeoutMillis = parseDurationMillis(polling?.optString("timeoutIn"), 10 * 60_000L).coerceAtLeast(0L)
        )
    }

    private fun parseDurationMillis(value: String?, fallback: Long): Long {
        if (value.isNullOrBlank() || !value.endsWith('s')) return fallback
        return value.dropLast(1).toDoubleOrNull()?.times(1000.0)?.toLong() ?: fallback
    }

    private fun encodePath(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
