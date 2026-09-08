package com.starfall.gsadrive

import androidx.compose.ui.graphics.vector.ImageVector
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.PhotoItem

internal data class Model(
    val user: String? = null, val token: String? = null,
    val files: List<DriveFile> = emptyList(), val photos: List<PhotoItem> = emptyList(),
    val loading: Boolean = false, val message: String? = null,
    val path: List<DriveFile> = emptyList(),
    val fromCache: Boolean = false, val uploading: Boolean = false
)

internal data class ViewerState(
    val file: DriveFile,
    val localPath: String? = null,
    val text: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val minimized: Boolean = false,
    val mediaQueue: List<DriveFile> = emptyList(),
    val mediaIndex: Int = -1
)

internal const val MAX_TEXT_PREVIEW_BYTES = 4L * 1024 * 1024
internal const val MAX_PREVIEW_CACHE_BYTES = 512L * 1024 * 1024

internal fun isTextPreview(file: DriveFile): Boolean {
    if (file.mimeType.startsWith("text/")) return true
    return file.name.substringAfterLast('.', "").lowercase() in setOf(
        "txt", "md", "markdown", "log", "csv", "json", "xml", "yaml", "yml",
        "kt", "kts", "java", "js", "jsx", "ts", "tsx", "html", "htm", "css",
        "py", "sh", "bash", "zsh", "toml", "ini", "conf", "properties", "gradle"
    )
}

internal fun isMediaPreview(file: DriveFile): Boolean =
    file.mimeType.startsWith("video/") || file.mimeType.startsWith("audio/")

internal fun isPreviewable(file: DriveFile): Boolean =
    !file.isFolder && (file.mimeType.startsWith("image/") || isMediaPreview(file) || isTextPreview(file))

internal enum class AccountType(val label: String) { GOOGLE("Google"), S3("S3"), SERVICE("Service Account") }
internal fun isTabEnabled(type: AccountType?, index: Int): Boolean = when (index) {
    0 -> true
    1 -> type == AccountType.GOOGLE || type == AccountType.SERVICE
    2 -> type == AccountType.GOOGLE
    3 -> type == AccountType.GOOGLE || type == AccountType.SERVICE
    else -> false
}

internal data class AccountEntry(val type: AccountType, val id: String, val name: String, val detail: String = "") {
    val key: String get() = "${type.name}:$id"
    val title: String get() = if (type == AccountType.S3) detail.ifBlank { name } else name.substringBefore('@')
    val subtitle: String get() = if (type == AccountType.S3) name else id
}
internal data class AccountUi(
    val entries: List<AccountEntry> = emptyList(),
    val active: AccountEntry? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val revision: Int = 0
)

internal data class Tab(val label: String, val icon: ImageVector)

