package com.autosecretary.features.task.ui.widget;

import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotToggleMutation;

import java.util.concurrent.ExecutorService;

public interface TaskWidgetDependencies {
    ExecutorService getDbExecutor();

    LoadTaskWidgetItemsUseCase createLoadTaskWidgetItemsUseCase();

    TaskSlotToggleMutation getTaskSlotToggleMutation();
}
