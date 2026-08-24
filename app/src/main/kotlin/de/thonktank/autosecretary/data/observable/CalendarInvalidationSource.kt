package de.thonktank.autosecretary.data.observable

import de.thonktank.autosecretary.calendar.CalendarDataSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge

/** Cold reload impulses backed by the existing calendar provider subscription. */
class CalendarInvalidationSource(private val calendar: CalendarDataSource) {
    private val externalChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val providerChanges: Flow<Unit> = callbackFlow {
        val subscription = calendar.observeChanges { trySend(Unit) }
        trySend(Unit)
        awaitClose { subscription.close() }
    }.conflate()

    val changes: Flow<Unit> = merge(providerChanges, externalChanges).conflate()

    /** Materializes permission or other external capability changes into the common stream. */
    fun materializeExternalChange() {
        externalChanges.tryEmit(Unit)
    }
}
