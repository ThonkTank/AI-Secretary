package de.thonktank.autosecretary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskActionReceiver extends BroadcastReceiver {
    public static final String COMPLETE = "de.thonktank.autosecretary.COMPLETE";
    public static final String LATER = "de.thonktank.autosecretary.LATER";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync(); Context app = context.getApplicationContext(); String id = intent.getStringExtra("occurrence_id"); String action = intent.getAction();
        WORKER.execute(() -> { try { TaskService tasks = new TaskService(DatabaseProvider.get(app)); if (COMPLETE.equals(action)) tasks.completeNextStep(id); else if (LATER.equals(action)) tasks.defer(id); TaskWidgetProvider.updateAll(app); } finally { pending.finish(); } });
    }
}
