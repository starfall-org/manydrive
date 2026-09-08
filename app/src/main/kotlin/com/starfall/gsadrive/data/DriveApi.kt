package com.starfall.gsadrive.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String?,
    val size: Long? = null,
    val thumbnailUrl: String? = null,
    val webViewUrl: String? = null,
    val parents: List<String> = emptyList(),
    val trashed: Boolean = false
) {
    val isFolder get() = mimeType == "application/vnd.google-apps.folder"
    val description get() = if (isFolder) "Thư mục" else "Đã sửa đổi ${modifiedTime?.replace("T", " ")?.substringBefore(".") ?: "gần đây"}"
}

object DriveApi {
    fun listFiles(accessToken: String, sharedWithMe: Boolean = false, trashed: Boolean = false, parentId: String? = null): List<DriveFile> {
        val query = when {
            trashed -> "trashed = true"
            sharedWithMe -> "sharedWithMe = true and trashed = false"
            parentId != null -> "'$parentId' in parents and trashed = false"
            else -> "'root' in parents and trashed = false"
        }
        val fields = "files(id,name,mimeType,modifiedTime,size,thumbnailLink,webViewLink,parents,trashed)"
        val url = URL("https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}&orderBy=folder,modifiedTime%20desc&pageSize=100&fields=$fields")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; setRequestProperty("Authorization", "Bearer $accessToken"); setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000; readTimeout = 15_000
        }
        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("Drive API (${connection.responseCode}): $body")
        val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
        return List(files.length()) { index -> files.getJSONObject(index).toDriveFile() }
    }

    fun createFolder(accessToken: String, name: String, parentId: String? = null) {
        val body = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
        parentId?.let { body.put("parents", JSONArray().put(it)) }
        request(accessToken, "POST", "https://www.googleapis.com/drive/v3/files", body.toString().toByteArray(), "application/json")
    }

    fun moveToTrash(accessToken: String, fileId: String) {
        request(accessToken, "PATCH", "https://www.googleapis.com/drive/v3/files/$fileId", "{\"trashed\":true}".toByteArray(), "application/json")
    }

    fun move(accessToken: String, file: DriveFile, newParentId: String) {
        val removeParents = file.parents.joinToString(",")
        request(accessToken, "PATCH", "https://www.googleapis.com/drive/v3/files/${file.id}?addParents=$newParentId&removeParents=${URLEncoder.encode(removeParents, "UTF-8")}", ByteArray(0), null)
    }

    fun copy(accessToken: String, file: DriveFile, newParentId: String) {
        val metadata = JSONObject().put("name", file.name).put("parents", JSONArray().put(newParentId))
        request(accessToken, "POST", "https://www.googleapis.com/drive/v3/files/${file.id}/copy", metadata.toString().toByteArray(), "application/json")
    }

    fun share(accessToken: String, fileId: String, email: String, role: String) {
        val permission = JSONObject().put("type", "user").put("role", role).put("emailAddress", email)
        request(accessToken, "POST", "https://www.googleapis.com/drive/v3/files/$fileId/permissions?sendNotificationEmail=true", permission.toString().toByteArray(), "application/json")
    }

    fun upload(accessToken: String, name: String, mimeType: String, bytes: ByteArray, parentId: String? = null) {
        val boundary = "ManyDrive${System.currentTimeMillis()}"
        val metadata = JSONObject().put("name", name).apply { parentId?.let { put("parents", JSONArray().put(it)) } }
        val output = ByteArrayOutputStream()
        fun text(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
        text("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n")
        text("--$boundary\r\nContent-Type: $mimeType\r\n\r\n")
        output.write(bytes)
        text("\r\n--$boundary--\r\n")
        request(accessToken, "POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", output.toByteArray(), "multipart/related; boundary=$boundary")
    }

    fun download(accessToken: String, fileId: String): ByteArray = request(accessToken, "GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media")

    private fun request(accessToken: String, method: String, address: String, payload: ByteArray? = null, contentType: String? = null): ByteArray {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            contentType?.let { setRequestProperty("Content-Type", it) }
            if (payload != null) { doOutput = true; outputStream.use { it.write(payload) } }
            connectTimeout = 15_000; readTimeout = 30_000
        }
        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).use { it?.readBytes() ?: ByteArray(0) }
        if (connection.responseCode !in 200..299) error("Drive API (${connection.responseCode}): ${body.decodeToString()}")
        return body
    }

    private fun JSONObject.toDriveFile() = DriveFile(
        id = getString("id"), name = optString("name", "Không tên"), mimeType = optString("mimeType"),
        modifiedTime = optString("modifiedTime").ifBlank { null }, size = optString("size").toLongOrNull(),
        thumbnailUrl = optString("thumbnailLink").ifBlank { null }, webViewUrl = optString("webViewLink").ifBlank { null },
        parents = optJSONArray("parents")?.let { parents -> List(parents.length()) { parents.getString(it) } } ?: emptyList(), trashed = optBoolean("trashed")
    )
}
