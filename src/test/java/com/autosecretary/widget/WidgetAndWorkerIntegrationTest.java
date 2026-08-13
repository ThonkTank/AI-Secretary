package com.autosecretary.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.background.FocusRefreshWorker;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.PlanningSettings;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.TimeWindow;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Callable;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = AutoSecretaryApplication.class)
public final class WidgetAndWorkerIntegrationTest {
    private static final String ID = "50000000-0000-0000-0000-000000000001";
    private AutoSecretaryApplication app;

    @Before public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        app.deleteDatabase("autosecretary.db");
        app.graph().planningSettings().save(new PlanningSettings(
                new TimeWindow(LocalTime.MIN, LocalTime.MAX),
                new TimeWindow(LocalTime.MIN, LocalTime.of(8, 0)),
                new TimeWindow(LocalTime.of(8, 0), LocalTime.of(16, 0)),
                new TimeWindow(LocalTime.of(16, 0), LocalTime.MAX),
                0, 0, 0, 1));
        database(() -> {
            app.graph().workItems().save(new Task(ID, "Widget-Aufgabe", 5, null, null,
                    true, List.of(), LocalDateTime.now().minusDays(1), false,
                    CompletionStats.empty(), 0));
            return null;
        });
    }

    @After public void tearDown() {
        database(() -> { app.graph().database().clearAllTables(); return null; });
        app.onTerminate();
    }

    @Test
    public void workerBuildsPlanAndWidgetFactoryRendersIt() {
        ListenableWorker.Result result = database(() -> FocusRefreshWorker.refresh(app, 0));
        assertTrue(result instanceof ListenableWorker.Result.Success);

        FocusWidgetFactory factory = new FocusWidgetFactory(app, app.graph());
        database(() -> { factory.onDataSetChanged(); return null; });
        int expectedTodayRows = LocalTime.now().isBefore(LocalTime.of(23, 54)) ? 1 : 0;
        assertEquals(expectedTodayRows, factory.getCount());
        if (expectedTodayRows > 0) assertTrue(factory.getViewAt(0) != null);
    }

    @Test
    public void widgetCompletionUsesApplicationCommandTransaction() {
        Intent complete = new Intent(app, FocusWidgetProvider.class)
                .setComponent(new ComponentName(app, FocusWidgetProvider.class))
                .setAction(FocusWidgetProvider.ACTION_COMPLETE)
                .putExtra(FocusWidgetProvider.EXTRA_ID, ID);
        app.sendBroadcast(complete);

        await(() -> database(() -> ((Task) app.graph().workItems().find(ID)).completed()));
        assertTrue(database(() -> ((Task) app.graph().workItems().find(ID)).completed()));
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(10); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("Widget-Aktion wurde nicht ausgeführt");
    }

    private <T> T database(Callable<T> action) {
        try { return app.graph().executors().database().submit(action).get(); }
        catch (Exception error) { throw new AssertionError(error); }
    }
}
