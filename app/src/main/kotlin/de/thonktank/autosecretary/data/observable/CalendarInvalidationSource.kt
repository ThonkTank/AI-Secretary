package de.thonktank.autosecretary.data.observable

import de.thonktank.autosecretary.calendar.CalendarDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Cold reload impulses backed by the existing calendar provider subscription. */
class CalendarInvalidationSource(private val calendar: CalendarDataSource) {
    val changes: Flow<Unit> = callbackFlow {
        val subscription = calendar.observeChanges { trySend(Unit) }
        trySend(Unit)
        awaitClose { subscription.close() }
    }.conflate()
}
