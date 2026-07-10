package com.autosecretary.features.task.ui;

import com.autosecretary.features.task.ui.edit.TaskEditViewModelFactory;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;

public interface TaskUiDependencies {
    TaskViewModelFactory getTaskViewModelFactory();

    TaskEditViewModelFactory getTaskEditViewModelFactory();

    TaskScheduleConfigViewModelFactory getTaskScheduleConfigViewModelFactory();
}
