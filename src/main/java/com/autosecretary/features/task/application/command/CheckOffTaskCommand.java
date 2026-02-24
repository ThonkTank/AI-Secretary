package com.autosecretary.features.task.application.command;

import com.autosecretary.features.task.application.model.checkoff.CheckOffResult;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskCompletionService.CompletionPhase;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

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
