package com.starfall.gsadrive.shared.operations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Recover ordinary failures, but never turn coroutine cancellation into a user-facing error. */
internal suspend fun <T> ioResult(block: suspend () -> T): Result<T> = try {
    Result.success(withContext(Dispatchers.IO) { block() })
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

internal data class BatchOutcome(val total: Int, val failed: Int, val firstFailure: Exception?)

/** Continue after individual failures; stop immediately on cancellation. */
internal suspend fun <T, K> Iterable<T>.runFileBatch(
    key: (T) -> K,
    action: suspend (T) -> Unit
): BatchOutcome {
    val unique = distinctBy(key)
    var failed = 0
    var firstFailure: Exception? = null
    for (item in unique) {
        currentCoroutineContext().ensureActive()
        try {
            action(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failed++
            if (firstFailure == null) firstFailure = failure
        }
    }
    return BatchOutcome(unique.size, failed, firstFailure)
}
