package de.thonktank.autosecretary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application-lifetime serial queue for ordered widget reads and actions. */
public final class AppExecutors {
    public final ExecutorService widgetSerial = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auto-secretary-widget");
        thread.setDaemon(true);
        return thread;
    });
}
