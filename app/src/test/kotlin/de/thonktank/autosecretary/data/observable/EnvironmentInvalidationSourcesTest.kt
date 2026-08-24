package de.thonktank.autosecretary.data.observable

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import de.thonktank.autosecretary.Clock
import de.thonktank.autosecretary.calendar.CalendarDataSource
import de.thonktank.autosecretary.calendar.CalendarPolicy
import de.thonktank.autosecretary.calendar.CalendarResult
import de.thonktank.autosecretary.data.preferences.FocusStepLimit
import de.thonktank.autosecretary.data.preferences.UiPreferences
import de.thonktank.autosecretary.data.preferences.UiThemeMode
import de.thonktank.autosecretary.infrastructure.AppLogger
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class EnvironmentInvalidationSourcesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun calendarFlowStartsOnceSignalsChangesAndStopsOnCancellation() = runBlocking {
        val calendar = FakeCalendarDataSource()
        val emissions = CalendarInvalidationSource(calendar).changes.produceIn(this)

        assertEquals(Unit, emissions.next())
        assertEquals(1, calendar.observerCount)
        calendar.signalChange()
        assertEquals(Unit, emissions.next())

        emissions.cancel()
        yield()
        assertEquals(0, calendar.observerCount)
    }

    @Test
    fun preferenceFlowsExposeDisplayAndCalendarPolicyChanges() = runBlocking {
        val preferences = UiPreferences(context, NoOpLogger)
        val source = PreferenceInvalidationSource(preferences)
        val displays = source.displayPreferences.produceIn(this)
        val policies = source.calendarPolicy.produceIn(this)

        val initialDisplay = displays.next()
        assertEquals(UiThemeMode.AUTO, initialDisplay.themeMode)
        assertEquals(FocusStepLimit.AUTO, initialDisplay.focusStepLimit)
        assertEquals(CalendarPolicy.ALL_VISIBLE, policies.next())

        preferences.setThemeMode(UiThemeMode.DARK)
        assertEquals(UiThemeMode.DARK, displays.next().themeMode)
        preferences.setFocusStepLimit(FocusStepLimit.THREE)
        assertEquals(FocusStepLimit.THREE, displays.next().focusStepLimit)
        preferences.setCalendarPolicy(CalendarPolicy.GOOGLE_ONLY)
        assertEquals(CalendarPolicy.GOOGLE_ONLY, policies.next())

        displays.cancel()
        policies.cancel()
    }

    @Test
    fun clockFlowMaterializesForegroundAndCrossesTheDayBoundary() = runBlocking {
        val clock = MutableClock(
            date = LocalDate.of(2026, 8, 24),
            time = LocalTime.of(23, 59),
        )
        val ticker = ManualMinuteTicker()
        val source = ClockInvalidationSource(clock, ticker)
        val emissions = source.changes.produceIn(this)

        assertEquals(
            ClockSnapshot(clock.today(), clock.time(), ClockInvalidationReason.INITIAL),
            emissions.next(),
        )
        assertEquals(1, ticker.observerCount)

        clock.time = LocalTime.of(23, 59, 30)
        source.materializeForeground()
        assertEquals(
            ClockSnapshot(clock.today(), clock.time(), ClockInvalidationReason.FOREGROUND),
            emissions.next(),
        )

        clock.date = LocalDate.of(2026, 8, 25)
        clock.time = LocalTime.MIDNIGHT
        ticker.signal()
        assertEquals(
            ClockSnapshot(clock.today(), clock.time(), ClockInvalidationReason.MINUTE_TICK),
            emissions.next(),
        )

        emissions.cancel()
        yield()
        assertEquals(0, ticker.observerCount)
    }

    @Test
    fun foregroundWithoutACollectorDoesNotStartTheMinuteTicker() {
        val ticker = ManualMinuteTicker()
        val source = ClockInvalidationSource(
            MutableClock(LocalDate.of(2026, 8, 24), LocalTime.NOON),
            ticker,
        )

        source.materializeForeground()

        assertEquals(0, ticker.observerCount)
    }

    @Test
    fun androidTickerAlignsToTheMinuteBoundaryAndStopsExactly() {
        val ticker = AndroidMinuteTicker(
            Handler(Looper.getMainLooper()),
            currentTimeMillis = { 59_999L },
        )
        var signals = 0
        val subscription = ticker.subscribe { signals++ }

        assertEquals(0, signals)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(1, signals)

        subscription.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertEquals(1, signals)
    }

    private suspend fun <T> ReceiveChannel<T>.next(): T = withTimeout(5_000) { receive() }

    private class FakeCalendarDataSource : CalendarDataSource {
        private val observers = linkedSetOf<Runnable>()
        val observerCount: Int get() = observers.size

        override fun loadToday(): CalendarResult = CalendarResult.Success(emptyList())

        override fun observeChanges(observer: Runnable): CalendarDataSource.Subscription {
            observers += observer
            return CalendarDataSource.Subscription { observers -= observer }
        }

        fun signalChange() {
            observers.toList().forEach(Runnable::run)
        }
    }

    private class MutableClock(
        var date: LocalDate,
        var time: LocalTime,
    ) : Clock {
        override fun today(): LocalDate = date
        override fun time(): LocalTime = time
    }

    private class ManualMinuteTicker : MinuteTicker {
        private val observers = linkedSetOf<Runnable>()
        val observerCount: Int get() = observers.size

        override fun subscribe(observer: Runnable): TimeSignalSubscription {
            observers += observer
            return TimeSignalSubscription { observers -= observer }
        }

        fun signal() {
            observers.toList().forEach(Runnable::run)
        }
    }

    private object NoOpLogger : AppLogger {
        override fun info(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, error: Throwable) = Unit
    }

    private companion object {
        const val PREFERENCES_FILE = "forest_ui"
    }
}
