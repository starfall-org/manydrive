package com.starfall.gsadrive.app.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

internal class AndroidDocumentsMultiPicker : ActivityResultContract<Array<String>, List<Uri>>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val mimeTypes = input.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val androidPicker = ComponentName("com.android.documentsui", "com.android.documentsui.picker.PickActivity")
        val available = runCatching {
            context.packageManager.getActivityInfo(androidPicker, 0)
        }.isSuccess
        if (available) intent.component = androidPicker
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val result = LinkedHashSet<Uri>()
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) result += clip.getItemAt(index).uri
        }
        intent.data?.let(result::add)
        return result.toList()
    }
}
