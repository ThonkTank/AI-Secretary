package com.autosecretary.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public final class AppExecutorsTest {
    private AppExecutors executors;

    @Before public void setUp() { executors = new AppExecutors(Runnable::run); }
    @After public void tearDown() { executors.close(); }

    @Test
    public void databaseLaneExecutesCommandsSerially() throws Exception {
        AtomicInteger next = new AtomicInteger();
        var first = executors.database().submit(next::incrementAndGet);
        var second = executors.database().submit(next::incrementAndGet);

        assertEquals(1, first.get().intValue());
        assertEquals(2, second.get().intValue());
    }

    @Test
    public void callDatabaseIsSafeFromInsideDatabaseLane() throws Exception {
        int result = executors.database().submit(() -> executors.callDatabase(() -> 7)).get();
        assertEquals(7, result);
        assertFalse(executors.database().isShutdown());
    }
}
