package com.autosecretary.features.assistant.application.internal;

import com.autosecretary.shared.ClaudeApiException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Runs a DB read on the {@code dbExecutor} and blocks for its result. The chat loop runs on the io
 * executor; all Room access must hop to the db executor. This does not deadlock: both executors are
 * single-threaded and DB tasks never dispatch back onto the io executor.
 */
public final class DbCalls {

    private final ExecutorService dbExecutor;

    public DbCalls(ExecutorService dbExecutor) {
        this.dbExecutor = dbExecutor;
    }

    public <T> T call(Callable<T> task) {
        try {
            return dbExecutor.submit(task).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ClaudeApiException("Datenbankfehler: " + message(cause), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClaudeApiException("Datenbankzugriff unterbrochen", e);
        }
    }

    private static String message(Throwable throwable) {
        if (throwable == null) {
            return "Unbekannter Fehler";
        }
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }
}
