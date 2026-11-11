package com.starfall.gsadrive.feature.transfers

import com.starfall.gsadrive.R

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.starfall.gsadrive.data.DocumentDownloads
import com.starfall.gsadrive.data.DownloadSource
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.StorageSession
import com.starfall.gsadrive.shared.i18n.localized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CloudDownloads(private val context: Context, private val scope: CoroutineScope) {
    fun download(file: DriveFile, session: StorageSession) {
        val account = session.account
        val token = session.token
        val service = session.service
        val config = session.s3
        Toast.makeText(context, localized(R.string.adding_to_the_download_queue), Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val access = if (account.type == AccountType.SERVICE)
                        ServiceAccountApi.accessToken(requireNotNull(service)).value else token
                    com.starfall.gsadrive.data.DocumentDownloads(
                        context,
                        list = { folder ->
                            if (account.type == AccountType.S3) S3Api.list(requireNotNull(config), folder.id)
                            else DriveApi.listFiles(requireNotNull(access), parentId = folder.id)
                        },
                        source = { item ->
                            if (account.type == AccountType.S3) S3Api.downloadSource(requireNotNull(config), item.id)
                            else com.starfall.gsadrive.data.DownloadSource(
                                "https://www.googleapis.com/drive/v3/files/${Uri.encode(item.id)}?alt=media&supportsAllDrives=true",
                                mapOf("Authorization" to "Bearer ${requireNotNull(access)}")
                            )
                        }
                    ).download(file)
                }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            Toast.makeText(context,
                if (result.isSuccess) localized(R.string.sent_arg_1_files_to_android_download_manager, result.getOrNull())
                else localized(R.string.unable_to_add_all_files_to_the_download_list_added_files_continue_to_load_on_android),
                Toast.LENGTH_LONG).show()
        }
    }
}
