package com.autosecretary.features.task.domain.internal.scoring;

import com.autosecretary.features.task.data.TaskPrefSlot;
import java.util.List;

public final class PreferenceFitState {
    private final List<TaskPrefSlot> todayPrefSlots;
    private final boolean hasDayConstraints;

    public PreferenceFitState(List<TaskPrefSlot> todayPrefSlots, boolean hasDayConstraints) {
        this.todayPrefSlots = List.copyOf(todayPrefSlots);
        this.hasDayConstraints = hasDayConstraints;
    }

    public List<TaskPrefSlot> todayPrefSlots() {
        return todayPrefSlots;
    }

    public boolean hasDayConstraints() {
        return hasDayConstraints;
    }
}
