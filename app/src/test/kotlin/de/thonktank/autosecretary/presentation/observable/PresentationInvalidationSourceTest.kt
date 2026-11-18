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
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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

            assertInitial(dashboard, PresentationInvalidationTarget.DASHBOARD)
            assertInitial(catalog, PresentationInvalidationTarget.CATALOG)
            await { inputs.allStarts(1) }

            dashboard.cancel()
            yield()
            assertTrue(inputs.allStops(0))

            catalog.cancel()
            await { inputs.allStops(1) }

            val widgets = source.widgetChanges.produceIn(this)
            assertInitial(widgets, PresentationInvalidationTarget.WIDGETS)
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
            val flowRuns = source.flowRunChanges.produceIn(this)
            assertInitial(dashboard, PresentationInvalidationTarget.DASHBOARD)
            assertInitial(catalog, PresentationInvalidationTarget.CATALOG)
            assertInitial(flowRuns, PresentationInvalidationTarget.FLOW_RUNS)
            await { inputs.allStarts(1) }
            inputs.emitInitialValues()
            assertUpstreamCauses(dashboard, PresentationInvalidationTarget.DASHBOARD)
            assertEquals(PresentationInvalidationCause.DATABASE, catalog.next("catalog startup database event").cause)
            assertEquals(PresentationInvalidationCause.CLOCK, flowRuns.next("flow runs startup clock").cause)

            inputs.database.emit(setOf("tasks"))
            assertEquals(PresentationInvalidationCause.DATABASE, dashboard.next("dashboard database change").cause)
            val databaseEvent = catalog.next("catalog database change")
            assertEquals(PresentationInvalidationCause.DATABASE, databaseEvent.cause)
            assertEquals(setOf("tasks"), databaseEvent.changedTables)

            inputs.calendar.emit(Unit)
            assertEquals(PresentationInvalidationCause.CALENDAR, dashboard.next("dashboard calendar change").cause)
            yield()
            assertTrue(catalog.tryReceive().isFailure)

            val widgets = source.widgetChanges.produceIn(this)
            assertInitial(widgets, PresentationInvalidationTarget.WIDGETS)
            assertTrue(widgets.tryReceive().isFailure)

            val preferences = DisplayPreferences(UiThemeMode.DARK, FocusStepLimit.THREE)
            inputs.display.emit(preferences)
            val displayEvent = dashboard.next("dashboard display preferences")
            assertEquals(PresentationInvalidationCause.DISPLAY_PREFERENCES, displayEvent.cause)
            assertEquals(preferences, displayEvent.displayPreferences)
            assertEquals(preferences, widgets.next("widget display preferences").displayPreferences)

            val clock = ClockSnapshot(
                LocalDate.of(2026, 8, 25),
                LocalTime.MIDNIGHT,
                ClockInvalidationReason.MINUTE_TICK,
                0L,
            )
            inputs.clock.emit(clock)
            assertEquals(clock, dashboard.next("dashboard clock").clock)
            assertEquals(clock, widgets.next("widget clock").clock)
            assertEquals(clock, flowRuns.next("flow runs clock").clock)
            assertTrue(catalog.tryReceive().isFailure)

            dashboard.cancel()
            catalog.cancel()
            widgets.cancel()
            flowRuns.cancel()
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
            assertInitial(dashboard, PresentationInvalidationTarget.DASHBOARD)
            await { inputs.allStarts(1) }

            inputs.database.emit(setOf("tasks"))
            source.close()

            withTimeout(5_000) {
                while (dashboard.receiveCatching().isSuccess) {
                    // Drain any queued invalidation before observing completion.
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
            assertInitial(dashboard, PresentationInvalidationTarget.DASHBOARD)
            assertInitial(widgets, PresentationInvalidationTarget.WIDGETS)
            await { inputs.allStarts(1) }
            inputs.emitInitialValues()
            assertUpstreamCauses(dashboard, PresentationInvalidationTarget.DASHBOARD)
            assertUpstreamCauses(widgets, PresentationInvalidationTarget.WIDGETS)

            source.materializeWidgetHostChange()

            assertEquals(PresentationInvalidationCause.WIDGET_HOST, widgets.next("widget host change").cause)
            inputs.calendar.emit(Unit)
            assertEquals(PresentationInvalidationCause.CALENDAR, dashboard.next("calendar after widget host").cause)
            dashboard.cancel()
            widgets.cancel()
        } finally {
            source.close()
            dispatcher.close()
        }
    }

    private suspend fun ReceiveChannel<PresentationInvalidation>.next(expected: String) =
        withTimeoutOrNull(5_000) { receive() }
            ?: throw AssertionError("Timed out waiting for $expected")

    private suspend fun assertInitial(
        channel: ReceiveChannel<PresentationInvalidation>,
        target: PresentationInvalidationTarget,
    ) {
        val event = channel.next("$target initial invalidation")
        assertEquals(PresentationInvalidationCause.INITIAL, event.cause)
        assertEquals(setOf(target), event.targets)
    }

    private suspend fun assertUpstreamCauses(
        channel: ReceiveChannel<PresentationInvalidation>,
        target: PresentationInvalidationTarget,
    ) {
        val expected = setOf(
            PresentationInvalidationCause.DATABASE,
            PresentationInvalidationCause.CALENDAR,
            PresentationInvalidationCause.DISPLAY_PREFERENCES,
            PresentationInvalidationCause.CALENDAR_POLICY,
            PresentationInvalidationCause.CLOCK,
        )
        val received = List(expected.size) { index -> channel.next("$target upstream event ${index + 1}").cause }
        assertEquals(expected, received.toSet())
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
                0L,
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

        suspend fun emitInitialValues() {
            all().forEach { it.emitInitial() }
        }

        fun allStarts(expected: Int) = all().all { it.starts.get() == expected }
        fun allStops(expected: Int) = all().all { it.stops.get() == expected }

        private fun all(): List<TrackedFlow<*>> =
            listOf(database, calendar, display, policy, clock)
    }

    private class TrackedFlow<T>(private val initial: T) {
        private val values = MutableSharedFlow<T>()
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        val flow: Flow<T> = values
            .onSubscription { starts.incrementAndGet() }
            .onCompletion { stops.incrementAndGet() }

        suspend fun emitInitial() = emit(initial)

        suspend fun emit(value: T) {
            withTimeout(5_000) { values.emit(value) }
        }
    }
}
