package com.starfall.gsadrive.data

import com.starfall.gsadrive.R

import com.starfall.gsadrive.shared.i18n.localized

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Keep API diagnostics without exposing response bodies, URLs or credentials. */
internal fun driveLoadError(failure: Throwable): String {
    val causes = generateSequence(failure) { it.cause }.take(12).toList()
    val http = causes.filterIsInstance<HttpResponseException>().firstOrNull()
    if (http != null) {
        val reasons = (http as? GoogleJsonResponseException)?.details?.errors.orEmpty()
            .mapNotNull { it.reason?.takeIf { reason -> reason.matches(Regex("[A-Za-z0-9_.-]{1,80}")) } }
            .distinct()
        val detail = when {
            reasons.any { it in setOf("accessNotConfigured", "serviceDisabled") } ->
                localized(R.string.the_google_drive_api_is_not_enabled_for_your_app_s_project)
            reasons.any { it in setOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded") } || http.statusCode == 429 ->
                localized(R.string.google_drive_is_limiting_the_number_of_visits_please_try_again_later)
            http.statusCode == 401 -> localized(R.string.google_refuses_access_token_please_re_authorize_your_account)
            http.statusCode == 403 -> localized(R.string.google_denies_access_to_drive_need_to_check_app_permissions_and_account_policies)
            http.statusCode == 404 -> localized(R.string.the_folder_no_longer_exists_or_the_account_doesn_t_have_access_rights)
            http.statusCode >= 500 -> localized(R.string.the_google_drive_server_is_experiencing_an_error_please_try_again_later)
            else -> localized(R.string.request_to_download_drive_file_list_failed)
        }
        return "$detail (HTTP ${http.statusCode}${if (reasons.isEmpty()) "" else ": ${reasons.joinToString() }"})"
    }
    return when {
        causes.any { it is UnknownHostException } -> localized(R.string.google_drive_address_cannot_be_resolved_please_check_your_network_connection_or_dns)
        causes.any { it is SocketTimeoutException } -> localized(R.string.google_drive_connection_timed_out_let_s_try_something_new)
        causes.any { it is SSLException } -> localized(R.string.unable_to_establish_a_secure_connection_to_google_drive)
        causes.any { it is LinkageError } -> localized(R.string.google_library_compatibility_error_arg_1, causes.first { it is LinkageError }.javaClass.simpleName)
        else -> localized(R.string.unable_to_load_drive_file_list_arg_1, failure.javaClass.simpleName)
    }
}
