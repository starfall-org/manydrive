package com.starfall.gsadrive.feature.viewer

import com.starfall.gsadrive.data.DriveFile

internal data class ViewerState(
    val file: DriveFile,
    val localPath: String? = null,
    val text: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val minimized: Boolean = false,
    val swipeQueue: List<DriveFile> = emptyList(),
    val swipeIndex: Int = -1,
    val previewPaths: Map<String, String> = emptyMap()
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

internal fun isSwipePreview(file: DriveFile): Boolean =
    file.mimeType.startsWith("image/") || isMediaPreview(file)

internal fun isPreviewable(file: DriveFile): Boolean =
    !file.isFolder && (file.mimeType.startsWith("image/") || isMediaPreview(file) || isTextPreview(file))
