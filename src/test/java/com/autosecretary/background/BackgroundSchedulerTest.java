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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class BackgroundSchedulerTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
    }

    @Test
    public void installRegistersExactlyOnePeriodicRefreshPath() throws Exception {
        BackgroundScheduler.install(context);
        BackgroundScheduler.install(context);

        var work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BackgroundScheduler.PERIODIC_WORK).get();

        assertEquals(1, work.size());
        assertFalse(work.get(0).getState().isFinished());
        assertEquals(WorkInfo.State.ENQUEUED, work.get(0).getState());
    }
}
