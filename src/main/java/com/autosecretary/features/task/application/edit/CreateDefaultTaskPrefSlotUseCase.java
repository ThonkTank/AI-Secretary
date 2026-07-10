package com.autosecretary.features.task.application.edit;

import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskPrefSlotFactory;

public class CreateDefaultTaskPrefSlotUseCase {
    public TaskPrefSlot execute(String taskId) {
        return TaskPrefSlotFactory.createDefault(taskId);
    }
}
