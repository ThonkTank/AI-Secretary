package com.autosecretary.features.task.domain;

import java.util.ArrayList;
import java.util.List;

public class TaskSlotGenerationResult {
    public final int createdSlots;
    public final List<SchedulingConflict> conflicts;

    public TaskSlotGenerationResult(int createdSlots, List<SchedulingConflict> conflicts) {
        this.createdSlots = createdSlots;
        this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
    }
}
