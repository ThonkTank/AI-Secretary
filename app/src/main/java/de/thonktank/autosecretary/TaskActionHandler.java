package de.thonktank.autosecretary;

import android.content.Intent;

/** Validates and executes the non-destructive task actions allowed directly from widgets. */
final class TaskActionHandler {
    interface Actions {
        void complete(String occurrenceId);
        void defer(String occurrenceId);
        void toggleStep(String stepId);
        void materializeDue();
    }

    private final Actions actions;

    TaskActionHandler(AppContainer container) {
        this(new Actions() {
            @Override public void complete(String occurrenceId) {
                container.today.complete.execute(occurrenceId);
            }
            @Override public void defer(String occurrenceId) {
                container.today.defer.execute(occurrenceId);
            }
            @Override public void toggleStep(String stepId) {
                container.today.toggleStep.execute(stepId);
            }
            @Override public void materializeDue() {
                container.today.materializeDue.execute();
            }
        });
    }

    TaskActionHandler(Actions actions) {
        this.actions = actions;
    }

    void handle(Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (TaskActionReceiver.COMPLETE.equals(action)) {
            actions.complete(required(intent, TaskActionReceiver.EXTRA_OCCURRENCE_ID));
        } else if (TaskActionReceiver.LATER.equals(action)) {
            actions.defer(required(intent, TaskActionReceiver.EXTRA_OCCURRENCE_ID));
        } else if (TaskActionReceiver.TOGGLE_STEP.equals(action)) {
            actions.toggleStep(required(intent, TaskActionReceiver.EXTRA_STEP_ID));
        } else {
            throw new IllegalArgumentException("Unsupported widget action: " + action);
        }
        actions.materializeDue();
    }

    private static String required(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Missing widget action extra: " + key);
        return value;
    }
}
