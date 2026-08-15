package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.infrastructure.AppLogger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class WidgetUpdateCoordinatorTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test public void oneCycleLoadsOnceForAllWidgetIds() {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        List<WidgetSizeClassifier.Size> projected = new ArrayList<>();
        List<Integer> updated = new ArrayList<>();
        WidgetPresenter.CycleData data = data();
        WidgetPresenter presenter = new WidgetPresenter(context);
        WidgetUpdateCoordinator coordinator = new WidgetUpdateCoordinator(context, Runnable::run,
                new RecordingLogger(), new WidgetSizeClassifier(), () -> {
                    loads.incrementAndGet();
                    return data;
                }, (cycle, size) -> {
                    projected.add(size);
                    return presenter.present(cycle, size);
                }, model -> new RemoteViews(context.getPackageName(), R.layout.task_widget));
        Bundle[] options = {WidgetSizeClassifierTest.options(160, 160),
                WidgetSizeClassifierTest.options(300, 160),
                WidgetSizeClassifierTest.options(250, 300),
                WidgetSizeClassifierTest.options(350, 300)};

        coordinator.runCycle(new int[]{10, 11, 12, 13}, new WidgetUpdateCoordinator.Host() {
            @Override public Bundle options(int id) { return options[id - 10]; }
            @Override public void update(int id, RemoteViews views) { updated.add(id); }
        }, completions::incrementAndGet);

        assertEquals(1, loads.get());
        assertEquals(java.util.Arrays.asList(WidgetSizeClassifier.Size.SMALL,
                WidgetSizeClassifier.Size.WIDE, WidgetSizeClassifier.Size.TALL,
                WidgetSizeClassifier.Size.LARGE), projected);
        assertEquals(java.util.Arrays.asList(10, 11, 12, 13), updated);
        assertEquals(1, completions.get());
    }

    @Test public void oneBrokenWidgetDoesNotBlockOthersAndCompletionIsGuaranteed() {
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        RecordingLogger logger = new RecordingLogger();
        WidgetPresenter presenter = new WidgetPresenter(context);
        WidgetUpdateCoordinator coordinator = new WidgetUpdateCoordinator(context, Runnable::run,
                logger, new WidgetSizeClassifier(), this::data, presenter::present,
                model -> new RemoteViews(context.getPackageName(), R.layout.task_widget));

        coordinator.runCycle(new int[]{1, 2}, new WidgetUpdateCoordinator.Host() {
            @Override public Bundle options(int id) { return new Bundle(); }
            @Override public void update(int id, RemoteViews views) {
                if (id == 1) throw new IllegalStateException("launcher unavailable");
                updates.incrementAndGet();
            }
        }, completions::incrementAndGet);

        assertEquals(1, updates.get());
        assertEquals(1, completions.get());
        assertEquals(1, logger.errors);
    }

    @Test public void loadFailureStillFinishesTheBroadcast() {
        AtomicInteger completions = new AtomicInteger();
        RecordingLogger logger = new RecordingLogger();
        WidgetUpdateCoordinator coordinator = new WidgetUpdateCoordinator(context, Runnable::run,
                logger, new WidgetSizeClassifier(), () -> {
                    throw new IllegalStateException("database unavailable");
                }, (data, size) -> null, model -> null);

        coordinator.runCycle(new int[]{1}, new NoOpHost(), completions::incrementAndGet);

        assertEquals(1, completions.get());
        assertEquals(1, logger.errors);
    }

    private WidgetPresenter.CycleData data() {
        return new WidgetPresenter.CycleData(DashboardFixtures.emptyDashboard(),
                new CalendarResult.Success(Collections.emptyList()),
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO));
    }

    private static final class NoOpHost implements WidgetUpdateCoordinator.Host {
        @Override public Bundle options(int id) { return new Bundle(); }
        @Override public void update(int id, RemoteViews views) { }
    }

    private static final class RecordingLogger implements AppLogger {
        int errors;
        @Override public void info(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable error) { errors++; }
    }
}
