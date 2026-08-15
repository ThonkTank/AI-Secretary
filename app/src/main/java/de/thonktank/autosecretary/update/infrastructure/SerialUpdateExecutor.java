package de.thonktank.autosecretary.update.infrastructure;

import de.thonktank.autosecretary.update.application.UpdateExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Production serial queue owned by one UpdateViewModel. */
public final class SerialUpdateExecutor implements UpdateExecutor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auto-secretary-update");
        thread.setDaemon(true);
        return thread;
    });

    @Override public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override public void close() {
        executor.shutdownNow();
    }
}
