package com.starfall.gsadrive.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.starfall.gsadrive.shared.cache.cacheKeyHash
import com.starfall.gsadrive.shared.cache.trimFileCache
import com.starfall.gsadrive.shared.cache.writeCacheFile

/** Disposable metadata only: isolated by account, tab and parent folder. */
class FileListCache(private val directory: File) {
    private fun accountDirectory(account: String) = File(directory, cacheKeyHash(account))
    private fun file(account: String, location: String) = File(accountDirectory(account), cacheKeyHash(location) + ".json")

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
            writeCacheFile(target) { temporary ->
                temporary.writeText(JSONObject().put("version", 2).put("files", values).toString())
            }
            trimFileCache(directory.walkTopDown().filter { it.isFile }.asIterable(), 20L * 1024L * 1024L)
        }
    }

    @Synchronized fun clear(account: String) { accountDirectory(account).deleteRecursively() }
    @Synchronized fun clearAll() { directory.deleteRecursively() }
}
