package de.thonktank.autosecretary.presentation.observable

import de.thonktank.autosecretary.calendar.CalendarPolicy
import de.thonktank.autosecretary.data.observable.ClockInvalidationReason
import de.thonktank.autosecretary.data.observable.ClockSnapshot
import de.thonktank.autosecretary.data.preferences.DisplayPreferences
import de.thonktank.autosecretary.data.preferences.FocusStepLimit
import de.thonktank.autosecretary.data.preferences.UiThemeMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationInvalidationSourceTest {
    @Test
    fun targetCollectorsShareOneUpstreamAndStopItAfterTheLastCollector() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val inputs = Inputs()
        val source = inputs.source(dispatcher)
        try {
            val dashboard = source.dashboardChanges.produceIn(this)
            val catalog = source.catalogChanges.produceIn(this)

            assertInitial(dashboard.receive(), PresentationInvalidationTarget.DASHBOARD)
            assertInitial(catalog.receive(), PresentationInvalidationTarget.CATALOG)
            await { inputs.allStarts(1) }

            dashboard.cancel()
            yield()
            assertTrue(inputs.allStops(0))

            catalog.cancel()
            await { inputs.allStops(1) }

            val widgets = source.widgetChanges.produceIn(this)
            assertInitial(widgets.receive(), PresentationInvalidationTarget.WIDGETS)
            await { inputs.allStarts(2) }
            widgets.cancel()
            await { inputs.allStops(2) }
        } finally {
            source.close()
            dispatcher.close()
        }
    }

    @Test
    fun everyTargetGetsOwnInitialAndOnlyMatchingLaterEvents() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val inputs = Inputs()
        val source = inputs.source(dispatcher)
        try {
            val dashboard = source.dashboardChanges.produceIn(this)
            val catalog = source.catalogChanges.produceIn(this)
            assertInitial(dashboard.receive(), PresentationInvalidationTarget.DASHBOARD)
            assertInitial(catalog.receive(), PresentationInvalidationTarget.CATALOG)
            await { inputs.allStarts(1) }
            assertEquals(
                setOf(
                    PresentationInvalidationCause.DATABASE,
                    PresentationInvalidationCause.CALENDAR,
                    PresentationInvalidationCause.DISPLAY_PREFERENCES,
                    PresentationInvalidationCause.CALENDAR_POLICY,
                    PresentationInvalidationCause.CLOCK,
                ),
                (1..5).map { dashboard.receive().cause }.toSet(),
            )
            assertEquals(PresentationInvalidationCause.DATABASE, catalog.receive().cause)

            inputs.database.emit(setOf("tasks"))
            assertEquals(PresentationInvalidationCause.DATABASE, dashboard.receive().cause)
            val databaseEvent = catalog.receive()
            assertEquals(PresentationInvalidationCause.DATABASE, databaseEvent.cause)
            assertEquals(setOf("tasks"), databaseEvent.changedTables)

            inputs.calendar.emit(Unit)
            assertEquals(PresentationInvalidationCause.CALENDAR, dashboard.receive().cause)
            yield()
            assertTrue(catalog.tryReceive().isFailure)

            val widgets = source.widgetChanges.produceIn(this)
            assertInitial(widgets.receive(), PresentationInvalidationTarget.WIDGETS)
            assertTrue(widgets.tryReceive().isFailure)

            val preferences = DisplayPreferences(UiThemeMode.DARK, FocusStepLimit.THREE)
            inputs.display.emit(preferences)
            val displayEvent = dashboard.receive()
            assertEquals(PresentationInvalidationCause.DISPLAY_PREFERENCES, displayEvent.cause)
            assertEquals(preferences, displayEvent.displayPreferences)
            assertEquals(preferences, widgets.receive().displayPreferences)

            val clock = ClockSnapshot(
                LocalDate.of(2026, 8, 25),
                LocalTime.MIDNIGHT,
                ClockInvalidationReason.MINUTE_TICK,
            )
            inputs.clock.emit(clock)
            assertEquals(clock, dashboard.receive().clock)
            assertEquals(clock, widgets.receive().clock)
            assertTrue(catalog.tryReceive().isFailure)

            dashboard.cancel()
            catalog.cancel()
            widgets.cancel()
        } finally {
            source.close()
            dispatcher.close()
        }
    }

    @Test
    fun closingSourceCompletesTargetCollectorsAndStopsUpstream() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val inputs = Inputs()
        val source = inputs.source(dispatcher)
        try {
            val dashboard = source.dashboardChanges.produceIn(this)
            assertInitial(dashboard.receive(), PresentationInvalidationTarget.DASHBOARD)
            await { inputs.allStarts(1) }

            source.close()

            withTimeout(5_000) {
                while (dashboard.receiveCatching().isSuccess) {
                    // Drain any already queued startup invalidations before observing completion.
                }
            }
            await { inputs.allStops(1) }
        } finally {
            source.close()
            dispatcher.close()
        }
    }

    @Test
    fun widgetHostMaterializationTargetsOnlyActiveWidgetCollectors() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val inputs = Inputs()
        val source = inputs.source(dispatcher)
        try {
            val dashboard = source.dashboardChanges.produceIn(this)
            val widgets = source.widgetChanges.produceIn(this)
            assertInitial(dashboard.receive(), PresentationInvalidationTarget.DASHBOARD)
            assertInitial(widgets.receive(), PresentationInvalidationTarget.WIDGETS)
            await { inputs.allStarts(1) }
            repeat(5) { dashboard.receive() }
            repeat(5) { widgets.receive() }

            source.materializeWidgetHostChange()

            assertEquals(PresentationInvalidationCause.WIDGET_HOST, widgets.receive().cause)
            yield()
            assertTrue(dashboard.tryReceive().isFailure)
            dashboard.cancel()
            widgets.cancel()
        } finally {
            source.close()
            dispatcher.close()
        }
    }

    private fun assertInitial(
        event: PresentationInvalidation,
        target: PresentationInvalidationTarget,
    ) {
        assertEquals(PresentationInvalidationCause.INITIAL, event.cause)
        assertEquals(setOf(target), event.targets)
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) yield()
        }
    }

    private class Inputs {
        val database = TrackedFlow<Set<String>>(emptySet())
        val calendar = TrackedFlow(Unit)
        val display = TrackedFlow(
            DisplayPreferences(UiThemeMode.AUTO, FocusStepLimit.THREE),
        )
        val policy = TrackedFlow(CalendarPolicy.ALL_VISIBLE)
        val clock = TrackedFlow(
            ClockSnapshot(
                LocalDate.of(2026, 8, 24),
                LocalTime.NOON,
                ClockInvalidationReason.INITIAL,
            ),
        )

        fun source(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
            PresentationInvalidationSource(
                databaseChanges = database.flow,
                calendarChanges = calendar.flow,
                displayPreferenceChanges = display.flow,
                calendarPolicyChanges = policy.flow,
                clockChanges = clock.flow,
                sharingDispatcher = dispatcher,
            )

        fun allStarts(expected: Int) = all().all { it.starts.get() == expected }
        fun allStops(expected: Int) = all().all { it.stops.get() == expected }

        private fun all(): List<TrackedFlow<*>> =
            listOf(database, calendar, display, policy, clock)
    }

    private class TrackedFlow<T>(initial: T) {
        private val values = MutableSharedFlow<T>(replay = 1).apply { tryEmit(initial) }
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        val flow: Flow<T> = values
            .onStart { starts.incrementAndGet() }
            .onCompletion { stops.incrementAndGet() }

        suspend fun emit(value: T) {
            values.emit(value)
        }
    }
}
