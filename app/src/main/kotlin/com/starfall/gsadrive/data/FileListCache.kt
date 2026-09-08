package com.starfall.gsadrive.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Disposable metadata only: isolated by account, tab and parent folder. */
class FileListCache(private val directory: File) {
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun accountDirectory(account: String) = File(directory, hash(account))
    private fun file(account: String, location: String) = File(accountDirectory(account), hash(location) + ".json")

    @Synchronized fun read(account: String, location: String): List<DriveFile>? = runCatching {
        val file = file(account, location)
        if (!file.isFile || file.length() > 20 * 1024 * 1024) return null
        val json = JSONObject(file.readText())
        if (json.getInt("version") != 2) return null
        val items = json.getJSONArray("files")
        List(items.length()) { i ->
            val item = items.getJSONObject(i)
            DriveFile(item.getString("id"), item.getString("name"), item.getString("mimeType"),
                item.optString("modifiedTime").ifBlank { null }, item.optString("size").toLongOrNull(),
                item.optString("thumbnailUrl").ifBlank { null }, item.optString("webViewUrl").ifBlank { null },
                item.getJSONArray("parents").let { parents -> List(parents.length()) { parents.getString(it) } },
                item.optBoolean("trashed"), item.optString("sharedWithMeTime").ifBlank { null })
        }
    }.getOrNull()

    @Synchronized fun write(account: String, location: String, files: List<DriveFile>) {
        runCatching {
            val target = file(account, location)
            target.parentFile?.mkdirs()
            val values = JSONArray()
            files.forEach { item -> values.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("mimeType", item.mimeType).put("modifiedTime", item.modifiedTime)
                .put("size", item.size).put("thumbnailUrl", item.thumbnailUrl).put("webViewUrl", item.webViewUrl)
                .put("parents", JSONArray(item.parents)).put("trashed", item.trashed)
                .put("sharedWithMeTime", item.sharedWithMeTime)) }
            val temporary = File(target.path + ".tmp")
            temporary.writeText(JSONObject().put("version", 2).put("files", values).toString())
            check(temporary.renameTo(target))
            var bytes = 0L
            directory.walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.forEach {
                bytes += it.length()
                if (bytes > 20 * 1024 * 1024) it.delete()
            }
        }
    }

    @Synchronized fun clear(account: String) { accountDirectory(account).deleteRecursively() }
    @Synchronized fun clearAll() { directory.deleteRecursively() }
}
