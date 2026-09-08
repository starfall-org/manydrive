package com.starfall.gsadrive.data

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
                "Google Drive API chưa được bật cho dự án của ứng dụng."
            reasons.any { it in setOf("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded") } || http.statusCode == 429 ->
                "Google Drive đang giới hạn số lượt truy cập. Hãy thử lại sau."
            http.statusCode == 401 -> "Google từ chối access token. Hãy cấp quyền lại cho tài khoản."
            http.statusCode == 403 -> "Google từ chối quyền truy cập Drive. Cần kiểm tra quyền ứng dụng và chính sách tài khoản."
            http.statusCode == 404 -> "Thư mục không còn tồn tại hoặc tài khoản không có quyền truy cập."
            http.statusCode >= 500 -> "Máy chủ Google Drive đang gặp lỗi. Hãy thử lại sau."
            else -> "Yêu cầu tải danh sách tệp Drive thất bại."
        }
        return "$detail (HTTP ${http.statusCode}${if (reasons.isEmpty()) "" else ": ${reasons.joinToString() }"})"
    }
    return when {
        causes.any { it is UnknownHostException } -> "Không phân giải được địa chỉ Google Drive. Hãy kiểm tra kết nối mạng hoặc DNS."
        causes.any { it is SocketTimeoutException } -> "Kết nối Google Drive quá thời gian chờ. Hãy thử làm mới."
        causes.any { it is SSLException } -> "Không thiết lập được kết nối bảo mật tới Google Drive."
        causes.any { it is LinkageError } -> "Thư viện Google gặp lỗi tương thích (${causes.first { it is LinkageError }.javaClass.simpleName})."
        else -> "Không thể tải danh sách tệp Drive (${failure.javaClass.simpleName})."
    }
}
