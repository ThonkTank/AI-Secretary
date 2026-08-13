package com.autosecretary.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppExecutorsTest {
    private AppExecutors executors;

    @Before public void setUp() { executors = new AppExecutors(Runnable::run); }
    @After public void tearDown() { executors.close(); }

    @Test
    public void failedPreparationPermanentlyRejectsDatabaseCommands() {
        CompletableFuture<Void> preparation = new CompletableFuture<>();
        preparation.completeExceptionally(new IllegalStateException("backup failed"));
        executors.setDatabaseGate(preparation);
        AtomicInteger mutations = new AtomicInteger();

        ExecutionException first = assertThrows(ExecutionException.class,
                () -> executors.database().submit(mutations::incrementAndGet).get());
        ExecutionException second = assertThrows(ExecutionException.class,
                () -> executors.database().submit(mutations::incrementAndGet).get());

        assertEquals(0, mutations.get());
        assertEquals("Datenbankvorbereitung ist fehlgeschlagen",
                first.getCause().getMessage());
        assertEquals("Datenbankvorbereitung ist fehlgeschlagen",
                second.getCause().getMessage());
    }

    @Test
    public void completedPreparationAllowsDatabaseCommands() throws Exception {
        executors.setDatabaseGate(CompletableFuture.completedFuture(null));
        assertEquals(7, executors.database().submit(() -> 7).get().intValue());
        assertFalse(executors.database().isShutdown());
    }
}
