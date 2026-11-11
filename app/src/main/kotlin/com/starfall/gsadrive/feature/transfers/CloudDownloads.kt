package com.starfall.gsadrive.feature.transfers

import com.starfall.gsadrive.R

import android.content.Context
import android.widget.Toast
import com.starfall.gsadrive.data.DocumentDownloads
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.accounts.openStorage
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class CloudDownloads(private val context: Context, private val scope: CoroutineScope) {
    fun download(file: DriveFile, session: StorageSession) {
        Toast.makeText(context, localized(R.string.adding_to_the_download_queue), Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = ioResult {
                val storage = session.openStorage()
                DocumentDownloads(
                    context,
                    list = { storage.list(it.id) },
                    source = { storage.downloadSource(it.id) }
                ).download(file)
            }
            Toast.makeText(context,
                if (result.isSuccess) localized(R.string.sent_arg_1_files_to_android_download_manager, result.getOrNull())
                else localized(R.string.unable_to_add_all_files_to_the_download_list_added_files_continue_to_load_on_android),
                Toast.LENGTH_LONG).show()
        }
    }
}
