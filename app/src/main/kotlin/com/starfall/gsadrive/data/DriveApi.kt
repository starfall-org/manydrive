package com.starfall.gsadrive.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
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
    val trashed: Boolean = false,
    val sharedWithMeTime: String? = null
) {
    val isFolder get() = mimeType == "application/vnd.google-apps.folder"
    val description get() = if (isFolder) "Thư mục" else "Đã sửa đổi ${modifiedTime?.replace("T", " ")?.substringBefore(".") ?: "gần đây"}"
}

data class DriveUserProfile(val displayName: String?, val photoLink: String?)

data class DrivePermission(
    val id: String,
    val type: String,
    val role: String,
    val emailAddress: String?,
    val displayName: String?
)


object DriveApi {
    fun accountProfile(accessToken: String): DriveUserProfile {
        val user = JSONObject(request(accessToken, "GET",
            "https://www.googleapis.com/drive/v3/about?fields=user(displayName,photoLink)").decodeToString())
            .optJSONObject("user")
        return DriveUserProfile(
            displayName = user?.optString("displayName")?.trim()?.takeIf { it.isNotEmpty() },
            photoLink = user?.optString("photoLink")?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    fun displayName(accessToken: String): String? = accountProfile(accessToken).displayName

    fun listFiles(accessToken: String, sharedWithMe: Boolean = false, trashed: Boolean = false, parentId: String? = null): List<DriveFile> {
        val query = when {
            trashed -> "trashed = true"
            parentId != null -> "'${parentId.replace("\\", "\\\\").replace("'", "\\'")}' in parents and trashed = false"
            sharedWithMe -> "sharedWithMe = true and trashed = false"
            else -> "'root' in parents and 'me' in owners and trashed = false"
        }
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val fields = "nextPageToken,files(id,name,mimeType,modifiedTime,size,thumbnailLink,webViewLink,parents,trashed,sharedWithMeTime)"
            val page = pageToken?.let { "&pageToken=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val address = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}&supportsAllDrives=true&includeItemsFromAllDrives=true&orderBy=folder,modifiedTime%20desc&pageSize=100&fields=$fields$page"
            val response = JSONObject(request(accessToken, "GET", address).decodeToString())
            val items = response.optJSONArray("files")
            if (items != null) repeat(items.length()) { files += items.getJSONObject(it).toDriveFile() }
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return files
    }

    /** Global Drive search. No parent constraint: results can come from any folder depth or shared content. */
    fun searchFiles(accessToken: String, text: String): List<DriveFile> {
        val term = text.trim()
        if (term.isEmpty()) return emptyList()
        val escaped = term.replace("\\", "\\\\").replace("'", "\\'")
        val query = "name contains '$escaped' and trashed = false"
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val fields = "nextPageToken,files(id,name,mimeType,modifiedTime,size,thumbnailLink,webViewLink,parents,trashed,sharedWithMeTime)"
            val page = pageToken?.let { "&pageToken=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val address = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&spaces=drive&corpora=user&supportsAllDrives=true&includeItemsFromAllDrives=true" +
                "&orderBy=folder,name_natural&pageSize=100&fields=$fields$page"
            val response = JSONObject(request(accessToken, "GET", address).decodeToString())
            val items = response.optJSONArray("files")
            if (items != null) repeat(items.length()) { files += items.getJSONObject(it).toDriveFile() }
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return files
    }

    fun createFolder(accessToken: String, name: String, parentId: String? = null): String {
        val body = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
        parentId?.let { body.put("parents", JSONArray().put(it)) }
        return JSONObject(request(accessToken, "POST", "https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id", body.toString().toByteArray(), "application/json").decodeToString()).getString("id")
    }

    fun moveToTrash(accessToken: String, fileId: String) {
        request(accessToken, "PATCH", "https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true", "{\"trashed\":true}".toByteArray(), "application/json")
    }

    fun move(accessToken: String, file: DriveFile, newParentId: String) {
        val removeParents = file.parents.joinToString(",")
        request(accessToken, "PATCH", "https://www.googleapis.com/drive/v3/files/${file.id}?supportsAllDrives=true&addParents=${URLEncoder.encode(newParentId, "UTF-8")}&removeParents=${URLEncoder.encode(removeParents, "UTF-8")}", ByteArray(0), null)
    }

    fun copy(accessToken: String, file: DriveFile, newParentId: String) {
        val metadata = JSONObject().put("name", file.name).put("parents", JSONArray().put(newParentId))
        request(accessToken, "POST", "https://www.googleapis.com/drive/v3/files/${file.id}/copy", metadata.toString().toByteArray(), "application/json")
    }

    fun share(accessToken: String, fileId: String, email: String, role: String) {
        val permission = JSONObject().put("type", "user").put("role", role).put("emailAddress", email)
        request(accessToken, "POST", "https://www.googleapis.com/drive/v3/files/$fileId/permissions?sendNotificationEmail=true", permission.toString().toByteArray(), "application/json")
    }

    fun rename(accessToken: String, fileId: String, newName: String) {
        val body = JSONObject().put("name", newName.trim())
        request(accessToken, "PATCH",
            "https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true",
            body.toString().toByteArray(), "application/json")
    }

    fun listPermissions(accessToken: String, fileId: String): List<DrivePermission> {
        val body = JSONObject(request(accessToken, "GET",
            "https://www.googleapis.com/drive/v3/files/$fileId/permissions?supportsAllDrives=true&fields=permissions(id,type,role,emailAddress,displayName)").decodeToString())
        val items = body.optJSONArray("permissions") ?: return emptyList()
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            DrivePermission(
                id = item.getString("id"),
                type = item.optString("type"),
                role = item.optString("role"),
                emailAddress = item.optString("emailAddress").ifBlank { null },
                displayName = item.optString("displayName").ifBlank { null }
            )
        }
    }

    fun deletePermission(accessToken: String, fileId: String, permissionId: String) {
        request(accessToken, "DELETE",
            "https://www.googleapis.com/drive/v3/files/$fileId/permissions/$permissionId?supportsAllDrives=true")
    }

    fun restore(accessToken: String, fileId: String) {
        request(accessToken, "PATCH", "https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true",
            "{\"trashed\":false}".toByteArray(), "application/json")
    }

    fun upload(accessToken: String, name: String, mimeType: String, input: InputStream, parentId: String? = null) {
        val boundary = "ManyDrive${java.util.UUID.randomUUID()}"
        val metadata = JSONObject().put("name", name).apply { parentId?.let { put("parents", JSONArray().put(it)) } }
        val connection = (URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            connection.outputStream.use { output ->
                fun text(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
                text("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n")
                text("--$boundary\r\nContent-Type: $mimeType\r\n\r\n")
                input.copyTo(output, 64 * 1024)
                text("\r\n--$boundary--\r\n")
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Drive API ($status): $body" }
        } finally { connection.disconnect() }
    }

    fun download(accessToken: String, fileId: String): ByteArray = request(accessToken, "GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media")

    fun downloadTo(accessToken: String, fileId: String, target: File) {
        val connection = (URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 120_000
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Drive API ($status): $message")
            }
            target.parentFile?.mkdirs()
            connection.inputStream.use { input -> target.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) } }
        } finally { connection.disconnect() }
    }

    fun updateContent(accessToken: String, fileId: String, mimeType: String, content: ByteArray) {
        val connection = (URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&supportsAllDrives=true")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", mimeType.ifBlank { "text/plain; charset=UTF-8" })
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setFixedLengthStreamingMode(content.size)
        }
        try {
            connection.outputStream.use { it.write(content) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Drive API ($status): $body" }
        } finally { connection.disconnect() }
    }

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
        parents = optJSONArray("parents")?.let { parents -> List(parents.length()) { parents.getString(it) } } ?: emptyList(),
        trashed = optBoolean("trashed"), sharedWithMeTime = optString("sharedWithMeTime").ifBlank { null }
    )
}
