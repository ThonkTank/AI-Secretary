package com.autosecretary.background;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class BackgroundSchedulerTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration.Builder()
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);
    }

    @Test
    public void installRegistersDailyAndCalendarWidgetSafetyWork() throws Exception {
        BackgroundScheduler.install(context, java.time.LocalTime.of(7, 0));

        var daily = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BackgroundScheduler.DAILY_WORK).get();
        var periodic = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BackgroundScheduler.PERIODIC_WORK).get();

        assertEquals(1, daily.size());
        assertEquals(WorkInfo.State.ENQUEUED, daily.get(0).getState());
        assertEquals(1, periodic.size());
        assertFalse(periodic.get(0).getState().isFinished());
    }

    @Test
    public void configuredStartIsWallClockAlignedAcrossDstChange() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZonedDateTime beforeSpringChange = ZonedDateTime.of(
                2026, 3, 28, 8, 0, 0, 0, berlin);

        Duration delay = BackgroundScheduler.delayUntilNext(
                beforeSpringChange, LocalTime.of(7, 0));

        assertEquals(Duration.ofHours(22), delay);
        assertEquals(ZonedDateTime.of(2026, 3, 29, 7, 0, 0, 0, berlin),
                beforeSpringChange.plus(delay));
    }
}
