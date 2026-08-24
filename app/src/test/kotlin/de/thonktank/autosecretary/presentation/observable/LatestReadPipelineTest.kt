package de.thonktank.autosecretary.presentation.observable

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestReadPipelineTest {
    @Test
    fun newerInputInterruptsOldReadAndOnlyPublishesNewestResult() = runBlocking {
        val inputs = Channel<Int>(Channel.UNLIMITED)
        val oldStarted = CountDownLatch(1)
        val oldInterrupted = AtomicBoolean()
        val publications = Channel<String>(Channel.UNLIMITED)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val readDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val pipeline = LatestReadPipeline(
            inputs = inputs.receiveAsFlow(),
            read = LatestRead { input ->
                if (input == 1) {
                    oldStarted.countDown()
                    try {
                        CountDownLatch(1).await()
                    } catch (interrupted: InterruptedException) {
                        oldInterrupted.set(true)
                        throw interrupted
                    }
                    "old"
                } else {
                    "new-$input"
                }
            },
            publish = LatestReadPublication { publications.trySend(it) },
            failure = LatestReadFailure { failures.add(it) },
            readDispatcher = readDispatcher,
        )
        try {
            assertTrue(inputs.trySend(1).isSuccess)
            withTimeout(5_000) { runInterruptible { oldStarted.await() } }
            assertTrue(inputs.trySend(2).isSuccess)

            assertEquals("new-2", withTimeout(5_000) { publications.receive() })
            assertTrue(publications.tryReceive().isFailure)
            assertTrue(oldInterrupted.get())
            assertTrue(failures.isEmpty())
        } finally {
            pipeline.close()
            readDispatcher.close()
        }
    }

    @Test
    fun readFailureIsReportedAndDoesNotStopLaterReads() = runBlocking {
        val inputs = Channel<Int>(Channel.UNLIMITED)
        val publications = Channel<Int>(Channel.UNLIMITED)
        val failures = Channel<Throwable>(Channel.UNLIMITED)
        val readDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val pipeline = LatestReadPipeline(
            inputs = inputs.receiveAsFlow(),
            read = LatestRead { input ->
                if (input == 1) throw IllegalStateException("failed read")
                input
            },
            publish = LatestReadPublication { publications.trySend(it) },
            failure = LatestReadFailure { failures.trySend(it) },
            readDispatcher = readDispatcher,
        )
        try {
            assertTrue(inputs.trySend(1).isSuccess)
            assertEquals("failed read", withTimeout(5_000) { failures.receive() }.message)

            assertTrue(inputs.trySend(2).isSuccess)
            assertEquals(2, withTimeout(5_000) { publications.receive() })
        } finally {
            pipeline.close()
            readDispatcher.close()
        }
    }

    @Test
    fun closingPipelineInterruptsActiveReadWithoutReportingFailure() = runBlocking {
        val inputs = Channel<Int>(Channel.UNLIMITED)
        val readStarted = CountDownLatch(1)
        val readInterrupted = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val readDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val pipeline = LatestReadPipeline(
            inputs = inputs.receiveAsFlow(),
            read = LatestRead<Int, Int> { input ->
                readStarted.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (interrupted: InterruptedException) {
                    readInterrupted.countDown()
                    throw interrupted
                }
                input
            },
            publish = LatestReadPublication { throw AssertionError("must not publish") },
            failure = LatestReadFailure { failures.add(it) },
            readDispatcher = readDispatcher,
        )
        try {
            assertTrue(inputs.trySend(1).isSuccess)
            withTimeout(5_000) { runInterruptible { readStarted.await() } }

            pipeline.close()

            withTimeout(5_000) { runInterruptible { readInterrupted.await() } }
            assertTrue(failures.isEmpty())
        } finally {
            pipeline.close()
            readDispatcher.close()
        }
    }
}
