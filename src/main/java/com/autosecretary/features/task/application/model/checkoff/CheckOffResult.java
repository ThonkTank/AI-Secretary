package com.autosecretary.features.task.application.model.checkoff;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService.CompletionPhase;

public class CheckOffResult {
    public final CompletionPhase phase;
    public final Task updatedTask;
    public final TaskSlot updatedSlot;

    public CheckOffResult(CompletionPhase phase, Task updatedTask, TaskSlot updatedSlot) {
        this.phase = phase;
        this.updatedTask = updatedTask;
        this.updatedSlot = updatedSlot;
    }
}
