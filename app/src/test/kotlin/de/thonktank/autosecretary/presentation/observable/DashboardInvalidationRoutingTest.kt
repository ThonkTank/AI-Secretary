package de.thonktank.autosecretary.presentation.observable

import de.thonktank.autosecretary.data.observable.ClockInvalidationReason
import de.thonktank.autosecretary.data.observable.ClockSnapshot
import de.thonktank.autosecretary.data.preferences.DisplayPreferences
import de.thonktank.autosecretary.data.preferences.FocusStepLimit
import de.thonktank.autosecretary.data.preferences.UiThemeMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardInvalidationRoutingTest {
    private val day = LocalDate.of(2026, 8, 24)

    @Test
    fun contentRoutingKeepsDomainChangesAndOnlyReadsMinuteTicksAcrossDayBoundary() = runBlocking {
        val loadedDate = AtomicReference(day)
        val events = listOf(
            event(PresentationInvalidationCause.INITIAL),
            event(PresentationInvalidationCause.DATABASE),
            event(PresentationInvalidationCause.CALENDAR),
            displayEvent(),
            event(PresentationInvalidationCause.CALENDAR_POLICY),
            clockEvent(day, ClockInvalidationReason.MINUTE_TICK),
            clockEvent(day.plusDays(1), ClockInvalidationReason.MINUTE_TICK),
            clockEvent(day, ClockInvalidationReason.FOREGROUND),
        )
        val routing = DashboardInvalidationRouting(events.asFlow(), loadedDate::get)

        val content = routing.contentChanges.toList()

        assertEquals(
            listOf(
                PresentationInvalidationCause.INITIAL,
                PresentationInvalidationCause.DATABASE,
                PresentationInvalidationCause.CALENDAR,
                PresentationInvalidationCause.CALENDAR_POLICY,
                PresentationInvalidationCause.CLOCK,
                PresentationInvalidationCause.CLOCK,
            ),
            content.map(PresentationInvalidation::cause),
        )
        assertEquals(day.plusDays(1), content[4].clock?.date)
        assertEquals(ClockInvalidationReason.FOREGROUND, content[5].clock?.reason)
    }

    @Test
    fun appearanceRoutingContainsOnlyDisplayAndClockPayloads() = runBlocking {
        val display = displayEvent()
        val clock = clockEvent(day, ClockInvalidationReason.MINUTE_TICK)
        val routing = DashboardInvalidationRouting(
            listOf(event(PresentationInvalidationCause.DATABASE), display, clock).asFlow(),
            LoadedDashboardDate { day },
        )

        assertEquals(listOf(display, clock), routing.appearanceChanges.toList())
        assertEquals(listOf(display), routing.todayPreferenceChanges.toList())
    }

    @Test
    fun optionsRoutingExcludesDatabaseWorkAndSeparatesAppearanceFromCalendar() = runBlocking {
        val initial = event(PresentationInvalidationCause.INITIAL)
        val database = event(PresentationInvalidationCause.DATABASE)
        val display = displayEvent()
        val clock = clockEvent(day, ClockInvalidationReason.MINUTE_TICK)
        val calendar = event(PresentationInvalidationCause.CALENDAR)
        val policy = event(PresentationInvalidationCause.CALENDAR_POLICY)
        val routing = OptionsInvalidationRouting(
            listOf(initial, database, display, clock, calendar, policy).asFlow(),
        )

        assertEquals(
            listOf(initial, display, clock),
            routing.appearanceChanges.toList(),
        )
        assertEquals(
            listOf(initial, calendar, policy),
            routing.calendarChanges.toList(),
        )
    }

    @Test
    fun missingClockPayloadAndUnknownLoadedDateFailSafeToContentRead() {
        val missingPayload = event(PresentationInvalidationCause.CLOCK)
        val sameDayTick = clockEvent(day, ClockInvalidationReason.MINUTE_TICK)

        assertTrue(DashboardInvalidationRouting.requiresContentRead(missingPayload, day))
        assertTrue(DashboardInvalidationRouting.requiresContentRead(sameDayTick, null))
        assertFalse(DashboardInvalidationRouting.requiresContentRead(sameDayTick, day))
    }

    @Test
    fun cosmeticEventDoesNotCancelAnActiveContentRead() = runBlocking {
        val readStarted = CountDownLatch(1)
        val cosmeticProcessed = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val publications = Channel<PresentationInvalidationCause>(Channel.UNLIMITED)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val readDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val inputs = flow {
            emit(event(PresentationInvalidationCause.DATABASE))
            emit(displayEvent())
            cosmeticProcessed.countDown()
        }
        val routing = DashboardInvalidationRouting(inputs, LoadedDashboardDate { day })
        val pipeline = LatestReadPipeline(
            inputs = routing.contentChanges,
            read = LatestRead { event ->
                readStarted.countDown()
                try {
                    releaseRead.await()
                } catch (error: InterruptedException) {
                    interrupted.set(true)
                    throw error
                }
                event.cause
            },
            publish = LatestReadPublication { publications.trySend(it) },
            failure = LatestReadFailure { failures.add(it) },
            readDispatcher = readDispatcher,
        )
        try {
            withTimeout(5_000) { runInterruptible { readStarted.await() } }
            withTimeout(5_000) { runInterruptible { cosmeticProcessed.await() } }

            releaseRead.countDown()

            assertEquals(
                PresentationInvalidationCause.DATABASE,
                withTimeout(5_000) { publications.receive() },
            )
            assertFalse(interrupted.get())
            assertTrue(failures.isEmpty())
        } finally {
            releaseRead.countDown()
            pipeline.close()
            readDispatcher.close()
        }
    }

    private fun event(cause: PresentationInvalidationCause) = PresentationInvalidation(
        cause = cause,
        targets = setOf(PresentationInvalidationTarget.DASHBOARD),
    )

    private fun displayEvent() = PresentationInvalidation(
        cause = PresentationInvalidationCause.DISPLAY_PREFERENCES,
        targets = setOf(PresentationInvalidationTarget.DASHBOARD),
        displayPreferences = DisplayPreferences(UiThemeMode.DARK, FocusStepLimit.THREE),
    )

    private fun clockEvent(
        date: LocalDate,
        reason: ClockInvalidationReason,
    ) = PresentationInvalidation(
        cause = PresentationInvalidationCause.CLOCK,
        targets = setOf(PresentationInvalidationTarget.DASHBOARD),
        clock = ClockSnapshot(date, LocalTime.NOON, reason),
    )
}
