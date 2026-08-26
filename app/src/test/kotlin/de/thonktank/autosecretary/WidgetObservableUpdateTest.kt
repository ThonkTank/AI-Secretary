package de.thonktank.autosecretary

import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import de.thonktank.autosecretary.calendar.CalendarResult
import de.thonktank.autosecretary.infrastructure.AppLogger
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationCause
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationTarget
import de.thonktank.autosecretary.data.observable.ClockInvalidationReason
import de.thonktank.autosecretary.data.observable.ClockSnapshot
import de.thonktank.autosecretary.widget.WidgetPresenter
import java.time.LocalTime
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetObservableUpdateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun noInstalledWidgetsNeverStartsTheObservableSources() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.host.ids = intArrayOf()

            fixture.coordinator.reconcileInstalledWidgets()
            yield()

            assertFalse(fixture.coordinator.observingForTest())
            assertEquals(0, fixture.invalidations.starts.get())
            assertEquals(0, fixture.loads.get())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun installedWidgetStartsOnceAndFinalRemovalStopsTheSources() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.host.ids = intArrayOf(7)
            fixture.coordinator.reconcileInstalledWidgets()
            await { fixture.host.updates.get() == 1 }

            assertTrue(fixture.coordinator.observingForTest())
            assertEquals(1, fixture.invalidations.starts.get())
            assertEquals(1, fixture.loads.get())

            fixture.host.ids = intArrayOf()
            fixture.coordinator.reconcileInstalledWidgets()
            await { fixture.invalidations.stops.get() == 1 }
            fixture.invalidations.databaseChanged()
            yield()

            assertFalse(fixture.coordinator.observingForTest())
            assertEquals(1, fixture.host.updates.get())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun providerRequestFinishesAfterTheLatestProjectionIsPublished() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.host.ids = intArrayOf(4)
            fixture.coordinator.reconcileInstalledWidgets()
            await { fixture.host.updates.get() == 1 }
            val completions = AtomicInteger()

            fixture.coordinator.update(null, intArrayOf(4), completions::incrementAndGet)
            await { completions.get() == 1 }

            assertEquals(1, completions.get())
            assertEquals(2, fixture.host.updates.get())
            assertEquals(2, fixture.loads.get())
            assertEquals(1, fixture.invalidations.starts.get())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun newerInvalidationInterruptsTheOldReadAndOnlyPublishesTheLatest() = runBlocking {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val loads = AtomicInteger()
        val fixture = Fixture(loader = {
            if (loads.incrementAndGet() == 1) {
                started.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (error: InterruptedException) {
                    interrupted.countDown()
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("cancelled old widget read", error)
                }
            }
            data()
        })
        try {
            fixture.host.ids = intArrayOf(9)
            fixture.coordinator.reconcileInstalledWidgets()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertTrue(fixture.invalidations.eventsSubscribed.await(5, TimeUnit.SECONDS))

            fixture.invalidations.databaseChanged()
            await { fixture.host.updates.get() == 1 }

            assertTrue(interrupted.await(5, TimeUnit.SECONDS))
            assertEquals(2, loads.get())
            assertEquals(0, fixture.logger.errors.get())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dayBoundarySnapshotReachesTheWidgetReadUnchanged() = runBlocking {
        var received: PresentationInvalidation? = null
        val fixture = Fixture(latestLoader = { invalidation ->
            received = invalidation
            data()
        })
        try {
            fixture.host.ids = intArrayOf(12)
            fixture.coordinator.reconcileInstalledWidgets()
            await { fixture.host.updates.get() == 1 }
            val snapshot = ClockSnapshot(
                LocalDate.of(2026, 8, 26),
                LocalTime.MIDNIGHT,
                ClockInvalidationReason.MINUTE_TICK,
            )

            fixture.invalidations.clockChanged(snapshot)
            await { fixture.host.updates.get() == 2 }

            assertEquals(snapshot, received?.clock)
        } finally {
            fixture.close()
        }
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) yield()
        }
    }

    private inner class Fixture(
        loader: () -> WidgetPresenter.CycleData = { data() },
        latestLoader: ((PresentationInvalidation) -> WidgetPresenter.CycleData)? = null,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        val host = RecordingHost()
        val invalidations = FakeInvalidations()
        val logger = RecordingLogger()
        val loads = AtomicInteger()
        val coordinator = WidgetUpdateCoordinator(
            executor,
            logger,
            WidgetSizeClassifier(),
            {},
            {
                loads.incrementAndGet()
                loader()
            },
            { invalidation ->
                loads.incrementAndGet()
                latestLoader?.invoke(invalidation) ?: loader()
            },
            WidgetPresenter(context)::present,
            { RemoteViews(context.packageName, R.layout.task_widget) },
            host,
            invalidations,
        )

        fun close() {
            coordinator.stopObserving()
            executor.shutdownNow()
        }
    }

    private class RecordingHost : WidgetUpdateCoordinator.InstalledHost {
        @Volatile var ids = intArrayOf()
        val updates = AtomicInteger()
        override fun installedIds(): IntArray = ids.copyOf()
        override fun options(widgetId: Int) = Bundle()
        override fun update(widgetId: Int, views: RemoteViews) {
            updates.incrementAndGet()
        }
    }

    private class FakeInvalidations : WidgetUpdateCoordinator.Invalidations {
        private val events = MutableSharedFlow<PresentationInvalidation>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        val eventsSubscribed = CountDownLatch(1)
        override fun changes(): Flow<PresentationInvalidation> = flow {
            starts.incrementAndGet()
            try {
                emit(event(PresentationInvalidationCause.INITIAL))
                eventsSubscribed.countDown()
                events.collect { emit(it) }
            } finally {
                stops.incrementAndGet()
            }
        }
        override fun materializeHostChange() {
            events.tryEmit(event(PresentationInvalidationCause.WIDGET_HOST))
        }
        fun databaseChanged() {
            events.tryEmit(event(PresentationInvalidationCause.DATABASE))
        }
        fun clockChanged(snapshot: ClockSnapshot) {
            events.tryEmit(
                PresentationInvalidation(
                    PresentationInvalidationCause.CLOCK,
                    setOf(PresentationInvalidationTarget.WIDGETS),
                    clock = snapshot,
                ),
            )
        }

        private fun event(cause: PresentationInvalidationCause) = PresentationInvalidation(
            cause,
            setOf(PresentationInvalidationTarget.WIDGETS),
        )
    }

    private class RecordingLogger : AppLogger {
        val errors = AtomicInteger()
        override fun info(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, error: Throwable) {
            errors.incrementAndGet()
        }
    }

    private fun data() = WidgetPresenter.CycleData(
        DashboardFixtures.emptyWidgetDashboard(),
        CalendarResult.Success(Collections.emptyList()),
        DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
    )
}
