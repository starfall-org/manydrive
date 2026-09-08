package com.starfall.gsadrive.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PhotoItem(
    val id: String,
    val filename: String,
    val mimeType: String,
    val baseUrl: String?,
    val localPath: String? = null,
    val createTime: String? = null
)

/** Google Photos Library API now exposes only media created by this application. */
object PhotosApi {
    fun listAppCreatedMedia(accessToken: String): List<PhotoItem> {
        val connection = (URL("https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=100").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("Google Photos API (${connection.responseCode}): $body")
        val items = JSONObject(body).optJSONArray("mediaItems") ?: return emptyList()
        return List(items.length()) { index ->
            items.getJSONObject(index).let {
                PhotoItem(
                    id = it.getString("id"),
                    filename = it.optString("filename", "Không tên"),
                    mimeType = it.optString("mimeType"),
                    baseUrl = it.optString("baseUrl").ifBlank { null },
                    createTime = it.optJSONObject("mediaMetadata")?.optString("creationTime")?.ifBlank { null }
                )
            }
        }
    }
}
