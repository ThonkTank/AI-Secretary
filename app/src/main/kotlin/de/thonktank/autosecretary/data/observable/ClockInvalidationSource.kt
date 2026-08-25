package de.thonktank.autosecretary.data.observable

import android.os.Handler
import android.os.Looper
import de.thonktank.autosecretary.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

enum class ClockInvalidationReason {
    INITIAL,
    MINUTE_TICK,
    FOREGROUND,
}

data class ClockSnapshot(
    val date: LocalDate,
    val time: LocalTime,
    val reason: ClockInvalidationReason,
)

fun interface TimeSignalSubscription {
    fun close()
}

fun interface MinuteTicker {
    /** Starts future minute signals. Implementations must not signal synchronously here. */
    fun subscribe(observer: Runnable): TimeSignalSubscription
}

/** Main-thread ticker aligned to wall-clock minute boundaries. */
class AndroidMinuteTicker internal constructor(
    private val handler: Handler,
    private val currentTimeMillis: () -> Long,
) : MinuteTicker {
    constructor() : this(Handler(Looper.getMainLooper()), System::currentTimeMillis)

    override fun subscribe(observer: Runnable): TimeSignalSubscription {
        val active = AtomicBoolean(true)
        lateinit var tick: Runnable
        tick = Runnable {
            if (!active.get()) return@Runnable
            observer.run()
            schedule(tick)
        }
        schedule(tick)
        return TimeSignalSubscription {
            if (active.compareAndSet(true, false)) handler.removeCallbacks(tick)
        }
    }

    private fun schedule(tick: Runnable) {
        val remainder = Math.floorMod(currentTimeMillis(), MINUTE_MILLIS)
        handler.postDelayed(tick, MINUTE_MILLIS - remainder)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

/** Cold clock snapshots for initial load, minute boundaries and explicit foreground refreshes. */
class ClockInvalidationSource(
    private val clock: Clock,
    private val ticker: MinuteTicker,
) {
    private val foregroundSignals = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val changes: Flow<ClockSnapshot> = callbackFlow {
        val foregroundCollection = launch(start = CoroutineStart.UNDISPATCHED) {
            foregroundSignals.collect {
                trySend(snapshot(ClockInvalidationReason.FOREGROUND))
            }
        }
        trySend(snapshot(ClockInvalidationReason.INITIAL))
        val minuteSubscription = ticker.subscribe {
            trySend(snapshot(ClockInvalidationReason.MINUTE_TICK))
        }
        awaitClose {
            minuteSubscription.close()
            foregroundCollection.cancel()
        }
    }.conflate()

    fun materializeForeground() {
        foregroundSignals.tryEmit(Unit)
    }

    private fun snapshot(reason: ClockInvalidationReason): ClockSnapshot {
        val now = clock.now()
        return ClockSnapshot(
            date = now.toLocalDate(),
            time = now.toLocalTime(),
            reason = reason,
        )
    }
}
