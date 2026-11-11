package com.starfall.gsadrive.feature.photos

import com.starfall.gsadrive.R

import android.accounts.Account
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.starfall.gsadrive.data.FileListCache
import com.starfall.gsadrive.data.PhotosApi
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.shared.operations.ioResult
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Photos read authorization stays separate from Drive and Photos upload tokens. */
internal class PhotosLibraryController(
    private val activity: ComponentActivity,
    private val account: () -> AccountEntry?,
    private val update: (String, Model) -> Unit
) {
    private val authorization = Identity.getAuthorizationClient(activity)
    private val cache = FileListCache(File(activity.cacheDir, "google-photos-list"))
    private var revision = 0
    private var pending: Pair<String, Int>? = null
    private var tokenAccount: String? = null
    private var token: String? = null
    private var snapshot = Model()
    val accessToken: String? get() = token.takeIf { tokenAccount == account()?.key }

    private val resolution = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val request = pending
        pending = null
        if (request != null && current(request)) {
            if (result.resultCode != android.app.Activity.RESULT_OK) fail(request, localized(R.string.google_photos_permission_has_been_revoked))
            else runCatching { authorization.getAuthorizationResultFromIntent(result.data).accessToken }
                .onSuccess { value ->
                    if (value == null) fail(request, localized(R.string.google_doesn_t_return_photos_permission))
                    else load(request, value)
                }.onFailure { fail(request, localized(R.string.unable_to_grant_google_photos_permission)) }
        }
    }

    /** Capture the account now, so a queued source cannot switch to a different account. */
    fun tokenProvider(expectedAccount: String): (() -> String)? {
        val entry = account()?.takeIf { it.key == tokenAccount && it.key == expectedAccount } ?: return null
        val context = activity.applicationContext
        return {
            val result = com.google.android.gms.tasks.Tasks.await(
                Identity.getAuthorizationClient(context).authorize(AuthorizationRequest.builder()
                    .setAccount(Account(entry.id, "com.google"))
                    .setRequestedScopes(listOf(Scope(PhotosApi.READ_SCOPE))).build()),
                30, java.util.concurrent.TimeUnit.SECONDS)
            check(!result.hasResolution()) { localized(R.string.google_photos_authorization_is_required) }
            result.accessToken ?: error(localized(R.string.google_doesn_t_return_photos_permission))
        }
    }

    private fun current(request: Pair<String, Int>) = request.first == account()?.key && request.second == revision
    private fun publish(accountKey: String, next: Model) {
        snapshot = next
        update(accountKey, next)
    }

    private fun fail(request: Pair<String, Int>, message: String) {
        if (current(request)) {
            publish(request.first, snapshot.copy(loading = false, message = message))
        }
    }

    fun reset() {
        revision++
        token = null
        tokenAccount = null
        pending = null
        snapshot = Model()
    }

    fun refresh() {
        val entry = account()?.takeIf { it.type == AccountType.GOOGLE } ?: return
        if (pending != null) return
        val request = entry.key to ++revision
        publish(entry.key, snapshot.copy(loading = true, message = null))

        activity.lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.read(entry.key, CACHE_LOCATION) }
            if (!current(request)) return@launch
            if (snapshot.files.isEmpty() && !cached.isNullOrEmpty()) {
                publish(entry.key, snapshot.copy(files = cached, loading = true, message = null))
            }
            authorize(request, entry)
        }
    }

    private fun authorize(request: Pair<String, Int>, entry: AccountEntry) {
        if (!current(request)) return
        authorization.authorize(AuthorizationRequest.builder()
            .setAccount(Account(entry.id, "com.google"))
            .setRequestedScopes(listOf(Scope(PhotosApi.READ_SCOPE))).build())
            .addOnSuccessListener { result ->
                if (!current(request)) return@addOnSuccessListener
                if (result.hasResolution()) {
                    val intent = result.pendingIntent
                    if (intent == null) fail(request, localized(R.string.unable_to_open_google_photos_permissions))
                    else {
                        pending = request
                        resolution.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    }
                } else {
                    val value = result.accessToken
                    if (value == null) fail(request, localized(R.string.google_doesn_t_return_photos_permission))
                    else load(request, value)
                }
            }.addOnFailureListener { fail(request, localized(R.string.unable_to_grant_google_photos_permission)) }
    }

    private fun load(request: Pair<String, Int>, value: String) {
        if (!current(request)) return
        tokenAccount = request.first
        token = value
        activity.lifecycleScope.launch {
            val result = ioResult { PhotosApi.list(value) }
            if (!current(request)) return@launch
            result.onSuccess { files ->
                publish(request.first, snapshot.copy(files = files, loading = false, message = null))
                withContext(Dispatchers.IO) { cache.write(request.first, CACHE_LOCATION, files) }
            }.onFailure { fail(request, it.message ?: localized(R.string.could_not_load_google_photos)) }
        }
    }

    private companion object {
        const val CACHE_LOCATION = "google-photos"
    }
}
