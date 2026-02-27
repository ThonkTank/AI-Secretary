package com.autosecretary.features.task.domain.scheduling;

import java.util.ArrayList;
import java.util.List;

public record TaskSlotGenerationResult(int createdSlots, List<SchedulingConflict> conflicts) {
    public TaskSlotGenerationResult {
        conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
    }
}
