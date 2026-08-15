package de.thonktank.autosecretary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TaskActionReceiver extends BroadcastReceiver {
    public static final String COMPLETE = "de.thonktank.autosecretary.COMPLETE";
    public static final String LATER = "de.thonktank.autosecretary.LATER";
    public static final String CONDITION = "de.thonktank.autosecretary.CONDITION";

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("task_id");
        if (id == null) return;
        TaskRepository store = new TaskRepository(context);
        String action = intent.getAction();
        if (COMPLETE.equals(action)) store.completeNextStep(id);
        else if (LATER.equals(action)) store.later(id);
        else if (CONDITION.equals(action)) store.fulfilCondition(id);
        TaskWidgetProvider.updateAll(context);
    }
}
