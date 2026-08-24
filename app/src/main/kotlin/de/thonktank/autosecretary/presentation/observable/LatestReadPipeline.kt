package de.thonktank.autosecretary.presentation.observable

import java.io.Closeable
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

fun interface LatestRead<I, O> {
    fun read(input: I): O
}

fun interface LatestReadPublication<O> {
    fun publish(value: O)
}

fun interface LatestReadFailure {
    fun fail(error: Throwable)
}

/** Runs synchronous reads interruptibly and guarantees that only the latest result is published. */
class LatestReadPipeline<I, O> @JvmOverloads constructor(
    inputs: Flow<I>,
    read: LatestRead<I, O>,
    publish: LatestReadPublication<O>,
    failure: LatestReadFailure,
    readDispatcher: CoroutineDispatcher = Dispatchers.IO,
    collectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + collectionDispatcher)

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            inputs.collectLatest { input ->
                try {
                    val result = runInterruptible(readDispatcher) { read.read(input) }
                    ensureActive()
                    publish.publish(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    failure.fail(error)
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
