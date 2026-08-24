package de.thonktank.autosecretary.data.observable

import de.thonktank.autosecretary.calendar.CalendarPolicy
import de.thonktank.autosecretary.data.preferences.DisplayPreferences
import de.thonktank.autosecretary.data.preferences.UiPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Cold preference streams; SharedPreferences listeners live only while collected. */
class PreferenceInvalidationSource(private val preferences: UiPreferences) {
    val displayPreferences: Flow<DisplayPreferences> = callbackFlow {
        val subscription = preferences.observeDisplayPreferences { trySend(it) }
        awaitClose { subscription.close() }
    }.distinctUntilChanged { previous, current ->
        previous.themeMode == current.themeMode &&
            previous.focusStepLimit == current.focusStepLimit
    }.conflate()

    val calendarPolicy: Flow<CalendarPolicy> = callbackFlow {
        val subscription = preferences.observeCalendarPolicy { trySend(it) }
        awaitClose { subscription.close() }
    }.distinctUntilChanged().conflate()
}
