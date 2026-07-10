package com.autosecretary.features.task.ui.list;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.UndoTaskCheckOffUseCase;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;

/**
 * Manual {@link ViewModelProvider.Factory} for {@link TaskViewModel}.
 *
 * <p>Receives all long-lived dependencies via constructor injection from {@code AppCompositionRoot}.
 */
public class TaskViewModelFactory implements ViewModelProvider.Factory {
    private final TaskDataService taskDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final UndoTaskCheckOffUseCase undoTaskCheckOffUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final AdjustTaskProgressUseCase adjustTaskProgressUseCase;
    private final TaskCalendarService taskCalendarService;
    private final TaskScheduleConfigRepository scheduleConfigRepository;
    private final WidgetRefreshNotifier widgetRefreshNotifier;

    public TaskViewModelFactory(TaskDataService taskDataService,
                                CheckOffTaskUseCase checkOffTaskUseCase,
                                UndoTaskCheckOffUseCase undoTaskCheckOffUseCase,
                                RegenerateScheduleUseCase regenerateScheduleUseCase,
                                AdjustTaskProgressUseCase adjustTaskProgressUseCase,
                                TaskCalendarService taskCalendarService,
                                TaskScheduleConfigRepository scheduleConfigRepository,
                                WidgetRefreshNotifier widgetRefreshNotifier) {
        this.taskDataService = taskDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.undoTaskCheckOffUseCase = undoTaskCheckOffUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.adjustTaskProgressUseCase = adjustTaskProgressUseCase;
        this.taskCalendarService = taskCalendarService;
        this.scheduleConfigRepository = scheduleConfigRepository;
        this.widgetRefreshNotifier = widgetRefreshNotifier;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskViewModel.class)) {
            return (T) new TaskViewModel(
                    taskDataService,
                    checkOffTaskUseCase,
                    undoTaskCheckOffUseCase,
                    regenerateScheduleUseCase,
                    adjustTaskProgressUseCase,
                    taskCalendarService,
                    scheduleConfigRepository,
                    widgetRefreshNotifier
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
