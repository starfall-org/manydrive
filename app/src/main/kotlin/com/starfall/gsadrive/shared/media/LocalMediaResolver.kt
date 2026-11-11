package com.starfall.gsadrive.shared.media

import com.starfall.gsadrive.R

import android.net.Uri
import android.provider.OpenableColumns
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.shared.i18n.localized

internal fun resolveLocalMedia(context: android.content.Context, uri: Uri, declaredType: String?): DriveFile {
    var name = uri.lastPathSegment ?: localized(R.string.media)
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0)
            }
        }
    }

    val resolvedType = declaredType?.takeUnless {
        it.isBlank() || '*' in it || it == "application/octet-stream"
    } ?: context.contentResolver.getType(uri)
    val mime = localFileMimeType(name, resolvedType)
    require(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")) {
        localized(R.string.this_file_type_cannot_be_previewed)
    }

    val id = if (uri.scheme == "file") requireNotNull(uri.path) else uri.toString()
    return DriveFile(id, name, mime, null)
}
