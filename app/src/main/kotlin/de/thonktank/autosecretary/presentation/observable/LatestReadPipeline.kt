package de.thonktank.autosecretary.presentation.observable

import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

fun interface LatestRead<I, O> {
    fun read(input: I): O
}

fun interface LatestReadPublication<O> {
    fun publish(value: O)
}

fun interface LatestReadFailure {
    fun fail(error: Throwable)
}

fun interface LatestReadPreparation<I> {
    fun prepare(input: I)
}

/** Runs synchronous reads interruptibly and guarantees that only the latest result is published. */
class LatestReadPipeline<I, O> @JvmOverloads constructor(
    inputs: Flow<I>,
    read: LatestRead<I, O>,
    publish: LatestReadPublication<O>,
    failure: LatestReadFailure,
    readDispatcher: CoroutineDispatcher = Dispatchers.IO,
    collectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    preparation: LatestReadPreparation<I>? = null,
    preparationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + collectionDispatcher)
    private val collection = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        inputs.collectLatest { input ->
            try {
                if (preparation != null) {
                    withContext(NonCancellable + preparationDispatcher) {
                        preparation.prepare(input)
                    }
                    ensureActive()
                }
                val result = runInterruptible(readDispatcher) { read.read(input) }
                ensureActive()
                publish.publish(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                failure.fail(error)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    internal suspend fun awaitStopped() {
        collection.join()
    }

    companion object {
        /** Java-friendly prepared pipeline without transferring ownership of the executor. */
        @JvmStatic
        fun <I, O> prepared(
            inputs: Flow<I>,
            preparationExecutor: Executor,
            preparation: LatestReadPreparation<I>,
            read: LatestRead<I, O>,
            publish: LatestReadPublication<O>,
            failure: LatestReadFailure,
        ): LatestReadPipeline<I, O> = LatestReadPipeline(
            inputs = inputs,
            read = read,
            publish = publish,
            failure = failure,
            preparation = preparation,
            preparationDispatcher = preparationExecutor.asCoroutineDispatcher(),
        )
    }
}
