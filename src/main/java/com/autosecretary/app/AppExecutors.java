package com.autosecretary.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** The only production owner of thread pools. */
public final class AppExecutors {
    private final AtomicReference<Thread> databaseThread = new AtomicReference<>();
    private final ExecutorService rawDatabase;
    private final ExecutorService database;
    private final ExecutorService io;
    private final ExecutorService ai;
    private final Executor main;

    public AppExecutors(Executor main) {
        this.main = main;
        rawDatabase = Executors.newSingleThreadExecutor(work -> new Thread(() -> {
            databaseThread.set(Thread.currentThread());
            work.run();
        }, "autosecretary-db"));
        database = new GatedExecutorService(rawDatabase);
        io = Executors.newFixedThreadPool(2);
        ai = Executors.newSingleThreadExecutor();
    }

    public ExecutorService database() { return database; }
    public ExecutorService io() { return io; }
    public ExecutorService ai() { return ai; }
    public Executor main() { return main; }

    public void setDatabaseGate(Future<?> preparation) {
        ((GatedExecutorService) database).setGate(preparation);
    }

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

    /** Every DB command rechecks the same preparation result; a failed backup never opens Room. */
    private static final class GatedExecutorService extends AbstractExecutorService {
        private final ExecutorService delegate;
        private volatile Future<?> gate = CompletableFuture.completedFuture(null);

        GatedExecutorService(ExecutorService delegate) { this.delegate = delegate; }

        void setGate(Future<?> gate) {
            this.gate = java.util.Objects.requireNonNull(gate);
        }

        @Override public void execute(Runnable command) {
            delegate.execute(() -> {
                awaitGate();
                command.run();
            });
        }

        @Override public Future<?> submit(Runnable task) {
            return delegate.submit(() -> {
                awaitGate();
                task.run();
            });
        }

        @Override public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(() -> {
                awaitGate();
                task.run();
            }, result);
        }

        @Override public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(() -> {
                awaitGate();
                return task.call();
            });
        }

        private void awaitGate() {
            try {
                gate.get();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Datenbankvorbereitung wurde unterbrochen", error);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                throw new IllegalStateException("Datenbankvorbereitung ist fehlgeschlagen", cause);
            }
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit)
                throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
