package com.autosecretary.application.task.model.checkoff;

import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskSlot;
import com.autosecretary.services.TaskCompletionService.CompletionPhase;

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
