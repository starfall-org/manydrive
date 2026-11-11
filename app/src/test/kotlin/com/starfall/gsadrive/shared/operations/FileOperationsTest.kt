package com.starfall.gsadrive.shared.operations

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class FileOperationsTest {
    @Test fun batchProcessesEachIdOnceAndContinuesAfterFailures() = runBlocking {
        val visited = mutableListOf<Int>()
        val failure = IOException("denied")
        val outcome = listOf(1, 2, 1, 3, 2).runFileBatch({ it }) {
            visited.add(it)
            if (it != 2) throw failure
        }
        assertEquals(listOf(1, 2, 3), visited)
        assertEquals(3, outcome.total)
        assertEquals(2, outcome.failed)
        assertSame(failure, outcome.firstFailure)
    }

    @Test fun emptyBatchHasNoFailures() = runBlocking {
        val outcome = emptyList<Int>().runFileBatch({ it }) { error("Must not run") }
        assertEquals(BatchOutcome(0, 0, null), outcome)
    }

    @Test fun cancellationStopsBatchWithoutProcessingRemainingFiles() = runBlocking {
        val visited = mutableListOf<Int>()
        val cancelled = CancellationException("stop")
        try {
            listOf(1, 2, 3).runFileBatch({ it }) {
                visited.add(it)
                if (it == 2) throw cancelled
            }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        assertEquals(listOf(1, 2), visited)
    }

    @Test fun cancelledJobStopsEvenWhenActionsDoNotSuspend() = runBlocking {
        val job = Job()
        val visited = mutableListOf<Int>()
        try {
            withContext(job) {
                listOf(1, 2, 3).runFileBatch({ it }) {
                    visited.add(it)
                    job.cancel()
                }
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(listOf(1), visited)
    }

    @Test fun ioResultReturnsOrdinaryFailures() = runBlocking {
        val failure = IOException("offline")
        val result = ioResult<Unit> { throw failure }
        // Coroutine stack-trace recovery may copy exceptions across dispatcher boundaries.
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(failure.message, result.exceptionOrNull()?.message)
        assertEquals(42, ioResult { 42 }.getOrThrow())
    }

    @Test fun ioResultDoesNotDeliverCompletionAfterCancellation() = runBlocking {
        var completed = false
        try {
            ioResult<Unit> { throw CancellationException("stop") }
            completed = true
        } catch (_: CancellationException) { }
        assertFalse(completed)
    }
}
