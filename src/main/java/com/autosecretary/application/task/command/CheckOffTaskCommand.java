package com.autosecretary.application.task.command;

import com.autosecretary.application.task.model.checkoff.CheckOffResult;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskSlot;
import com.autosecretary.services.TaskCompletionService;
import com.autosecretary.services.TaskCompletionService.CompletionPhase;
import com.autosecretary.services.TaskLifecycleManager;

public class CheckOffTaskCommand {
    private final TaskCompletionService completionService;
    private final TaskLifecycleManager lifecycleManager;

    public CheckOffTaskCommand(TaskCompletionService completionService, TaskLifecycleManager lifecycleManager) {
        this.completionService = completionService;
        this.lifecycleManager = lifecycleManager;
    }

    public CheckOffResult execute(Task task, TaskSlot slot) {
        CompletionPhase phase = completionService.checkOff(task, slot, lifecycleManager);
        return new CheckOffResult(phase, task, slot);
    }
}
