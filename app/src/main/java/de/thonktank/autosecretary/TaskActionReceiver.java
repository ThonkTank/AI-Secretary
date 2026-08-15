package de.thonktank.autosecretary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskActionReceiver extends BroadcastReceiver {
    public static final String COMPLETE = "de.thonktank.autosecretary.COMPLETE";
    public static final String LATER = "de.thonktank.autosecretary.LATER";
    public static final String TOGGLE_STEP = "de.thonktank.autosecretary.TOGGLE_STEP";
    public static final String CLOSE = "de.thonktank.autosecretary.CLOSE";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync(); Context app = context.getApplicationContext(); String action = intent.getAction();
        WORKER.execute(() -> { try { TaskService tasks = new TaskService(DatabaseProvider.get(app));
            if (COMPLETE.equals(action)) tasks.complete(intent.getStringExtra("occurrence_id"));
            else if (LATER.equals(action)) tasks.defer(intent.getStringExtra("occurrence_id"));
            else if (TOGGLE_STEP.equals(action)) tasks.toggleStep(intent.getStringExtra("step_id"));
            else if (CLOSE.equals(action)) tasks.closeOngoingTask(intent.getStringExtra("task_id"));
            TaskWidgetProvider.updateAll(app); } finally { pending.finish(); } });
    }
}
