package com.starfall.gsadrive.feature.accounts

import com.starfall.gsadrive.R

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starfall.gsadrive.data.AccountStore
import com.starfall.gsadrive.data.DriveApi
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.S3Account
import com.starfall.gsadrive.data.S3AccountStore
import com.starfall.gsadrive.data.S3Accounts
import com.starfall.gsadrive.data.S3Api
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.data.ServiceAccessToken
import com.starfall.gsadrive.data.ServiceAccountApi
import com.starfall.gsadrive.data.ServiceAccountCredentials
import com.starfall.gsadrive.data.ServiceAccountStore
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saved account connections and profiles. Android pickers remain in the Activity. */
internal class AccountController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val selection: android.content.SharedPreferences,
    private val listingCache: FileListCache,
    private val onAdded: (AccountEntry, List<DriveFile>, String?) -> Unit,
    private val onDisplayName: (String) -> Unit,
    private val onServiceToken: (String, ServiceAccessToken) -> Unit,
    private val onServiceRemoved: (String) -> Unit,
    private val onSignOut: () -> Unit
) {
    private val googleStore by lazy { AccountStore(context) }
    private val s3Store by lazy { S3AccountStore(context) }
    private val serviceStore by lazy { ServiceAccountStore(context) }
    var s3Accounts = S3Accounts()
        private set
    var serviceAccounts = emptyList<ServiceAccountCredentials>()
        private set
    private var s3Available = true
    private var serviceAvailable = true
    var ui by mutableStateOf(AccountUi())

    fun restore(): AccountEntry? {
        runCatching { s3Store.load() }
            .onSuccess { s3Accounts = it }
            .onFailure { s3Available = false; accountError(localized(R.string.unable_to_read_saved_s3_account)) }
        runCatching { serviceStore.load() }
            .onSuccess { serviceAccounts = it }
            .onFailure { serviceAvailable = false; accountError(localized(R.string.unable_to_read_saved_service_account)) }
        refreshAccounts()
        val restoredKey = if (selection.contains("active")) selection.getString("active", "")
            else googleStore.active()?.let { "GOOGLE:$it" }
        ui.entries.find { it.key == restoredKey }?.let { entry ->
            ui = ui.copy(active = entry)
        }
        return ui.active
    }

    fun selectGoogle(email: String) {
        googleStore.select(email)
        refreshAccounts()
        ui = ui.copy(revision = ui.revision + 1, message = null)
    }

    fun clearActiveGoogle() = googleStore.clearActive()
    fun accountError(message: String) { ui = ui.copy(busy = false, message = message) }

    fun refreshAccounts() {
        ui = ui.copy(entries =
            googleStore.accounts().map { email ->
                AccountEntry(AccountType.GOOGLE, email, storedAccountName(AccountType.GOOGLE, email),
                    avatarUrl = selection.getString("avatar:GOOGLE:$email", null))
            } + s3Accounts.accounts.map { AccountEntry(AccountType.S3, it.id, it.name, it.config.bucket, endpointUrl = it.config.endpoint) } +
                serviceAccounts.map { AccountEntry(AccountType.SERVICE, it.id, storedAccountName(AccountType.SERVICE, it.email),
                    avatarUrl = selection.getString("avatar:SERVICE:${it.id}", null)) })
    }

    fun storedAccountName(type: AccountType, email: String): String =
        selection.getString("name:${type.name}:$email", null)
            ?.takeIf { it.isNotBlank() && '@' !in it } ?: email.substringBefore('@')

    fun refreshAccountName(token: String) {
        val entry = ui.active ?: return
        if (entry.type == AccountType.S3 || selection.getBoolean("profile:${entry.key}", false)) return
        scope.launch {
            val profile = ioResult { DriveApi.accountProfile(token) }.getOrNull()
                ?: return@launch
            if (ui.entries.none { it.key == entry.key }) return@launch
            val name = profile.displayName?.takeIf { it.isNotBlank() && '@' !in it }
            selection.edit().apply {
                name?.let { putString("name:${entry.key}", it) }
                if (profile.photoLink != null) putString("avatar:${entry.key}", profile.photoLink) else remove("avatar:${entry.key}")
                putBoolean("profile:${entry.key}", true)
            }.apply()
            refreshAccounts()
            if (ui.active?.key == entry.key) {
                val refreshed = ui.entries.firstOrNull { it.key == entry.key } ?: entry
                ui = ui.copy(active = refreshed)
                if (name != null) onDisplayName(name)
            }
        }
    }

    fun connectS3(name: String, config: S3Config) {
        if (!s3Available) return accountError(localized(R.string.unable_to_open_s3_account_vault_on_device))
        val account = S3Account(name = name.trim(), config = config)
        ui = ui.copy(busy = true, message = null)
        scope.launch {
            ioResult { S3Api.list(config) }
                .onSuccess { files ->
                    val saved = s3Accounts.copy(accounts = s3Accounts.accounts + account, activeId = account.id)
                    runCatching { s3Store.save(saved) }
                        .onSuccess {
                            s3Accounts = saved
                            refreshAccounts()
                            onAdded(AccountEntry(AccountType.S3, account.id, account.name, config.bucket, endpointUrl = config.endpoint), files, null)
                        }.onFailure { accountError(localized(R.string.unable_to_save_s3_account)) }
                }.onFailure { accountError(localized(R.string.unable_to_connect_s3_check_information_bucket_and_network_permissions)) }
        }
    }

    fun importService(uri: Uri) {
        if (!serviceAvailable) return accountError(localized(R.string.unable_to_open_service_account_repository_on_the_device))
        ui = ui.copy(busy = true, message = null)
        scope.launch {
            ioResult {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= ServiceAccountCredentials.MAX_JSON_BYTES) { localized(R.string.json_file_is_too_large) }
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                } ?: throw IllegalArgumentException(localized(R.string.cannot_read_the_selected_json_file))
                val credentials = ServiceAccountCredentials.parse(text)
                val token = ServiceAccountApi.accessToken(credentials)
                Triple(credentials, token, DriveApi.listFiles(token.value))
            }.onSuccess { (credentials, token, files) ->
                val saved = serviceAccounts.filterNot { it.id == credentials.id } + credentials
                runCatching { serviceStore.save(saved) }
                    .onSuccess {
                        serviceAccounts = saved
                        onServiceToken(credentials.id, token)
                        refreshAccounts()
                        onAdded(ui.entries.first { it.type == AccountType.SERVICE && it.id == credentials.id }, files, token.value)
                    }.onFailure { accountError(localized(R.string.service_account_cannot_be_saved_on_the_device)) }
            }.onFailure {
                accountError(if (it is IllegalArgumentException) it.message ?: localized(R.string.invalid_json_file)
                    else localized(R.string.unable_to_connect_service_account_check_the_json_key_drive_api_and_network_then_re_import_the_file))
            }
        }
    }

    fun removeAccount(entry: AccountEntry) {
        runCatching {
            when (entry.type) {
                AccountType.GOOGLE -> googleStore.remove(entry.id)
                AccountType.S3 -> {
                    val saved = s3Accounts.copy(accounts = s3Accounts.accounts.filterNot { it.id == entry.id },
                        activeId = s3Accounts.activeId?.takeUnless { it == entry.id })
                    s3Store.save(saved)
                    s3Accounts = saved
                }
                AccountType.SERVICE -> {
                    val saved = serviceAccounts.filterNot { it.id == entry.id }
                    serviceStore.save(saved)
                    serviceAccounts = saved
                    onServiceRemoved(entry.id)
                }
            }
        }.onSuccess {
            scope.launch(Dispatchers.IO) { listingCache.clear(entry.key) }
            selection.edit().remove("name:${entry.key}").remove("avatar:${entry.key}").remove("profile:${entry.key}").apply()
            if (ui.active?.key == entry.key) onSignOut()
            refreshAccounts()
        }.onFailure { accountError(localized(R.string.saved_accounts_cannot_be_deleted)) }
    }

}
