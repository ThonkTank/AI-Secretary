package com.autosecretary.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** The only production owner of thread pools. */
public final class AppExecutors {
    private final AtomicReference<Thread> databaseThread = new AtomicReference<>();
    private final ExecutorService database;
    private final ExecutorService io;
    private final ExecutorService ai;
    private final Executor main;

    public AppExecutors(Executor main) {
        this.main = main;
        database = Executors.newSingleThreadExecutor(work -> new Thread(() -> {
            databaseThread.set(Thread.currentThread());
            work.run();
        }, "autosecretary-db"));
        io = Executors.newFixedThreadPool(2);
        ai = Executors.newSingleThreadExecutor();
    }

    public ExecutorService database() { return database; }
    public ExecutorService io() { return io; }
    public ExecutorService ai() { return ai; }
    public Executor main() { return main; }

    /** Runs synchronously on the database lane and is safe when already executing there. */
    public <T> T callDatabase(Callable<T> action)
            throws InterruptedException, ExecutionException {
        if (Thread.currentThread() == databaseThread.get()) {
            try { return action.call(); }
            catch (Exception error) { throw new ExecutionException(error); }
        }
        return database.submit(action).get();
    }

    public void close() {
        database.shutdownNow();
        io.shutdownNow();
        ai.shutdownNow();
    }

}
