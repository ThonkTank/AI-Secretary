package com.autosecretary.features.task.ui.list;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.app.Preferences;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;

/**
 * Manual {@link ViewModelProvider.Factory} for {@link TaskViewModel}.
 *
 * <p>Receives all long-lived dependencies via constructor injection from {@code AppCompositionRoot}.
 * {@link TaskEditSessionController} and {@link com.autosecretary.app.Preferences Preferences} are
 * created inside {@link #create} rather than injected because they are ViewModel-scoped: they must
 * be recreated each time a new {@link TaskViewModel} instance is created and must not be shared
 * across ViewModel instances.
 */
public class TaskViewModelFactory implements ViewModelProvider.Factory {
    private final Application app;
    private final TaskDataService taskDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final AdjustTaskProgressUseCase adjustTaskProgressUseCase;
    private final TaskCalendarService taskCalendarService;

    public TaskViewModelFactory(Application app,
                                TaskDataService taskDataService,
                                CheckOffTaskUseCase checkOffTaskUseCase,
                                RegenerateScheduleUseCase regenerateScheduleUseCase,
                                AdjustTaskProgressUseCase adjustTaskProgressUseCase,
                                TaskCalendarService taskCalendarService) {
        this.app = app;
        this.taskDataService = taskDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.adjustTaskProgressUseCase = adjustTaskProgressUseCase;
        this.taskCalendarService = taskCalendarService;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskViewModel.class)) {
            // Created here (not injected) so its lifecycle is tied to the ViewModel instance.
            TaskEditSessionController taskEditSessionController = new TaskEditSessionController(
                    taskDataService
            );

            return (T) new TaskViewModel(
                    app,
                    taskDataService,
                    checkOffTaskUseCase,
                    regenerateScheduleUseCase,
                    adjustTaskProgressUseCase,
                    taskEditSessionController,
                    taskCalendarService,
                    new Preferences(app) // ViewModel-scoped; not shared with other consumers
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
