package de.thonktank.autosecretary;

import androidx.annotation.NonNull;

public final class TaskStepSnapshot {
    @NonNull public final String id;
    @NonNull public final String label;
    public final boolean done;

    public TaskStepSnapshot(@NonNull String id, @NonNull String label, boolean done) {
        this.id = id; this.label = label; this.done = done;
    }
}
