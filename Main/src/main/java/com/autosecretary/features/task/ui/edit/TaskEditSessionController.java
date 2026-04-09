package com.autosecretary.features.task.ui.edit;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.util.ArrayList;

/**
 * Owns the task-editing session lifecycle independently from list-screen concerns.
 *
 * <p>This controller lives inside {@link com.autosecretary.features.task.ui.list.TaskViewModel TaskViewModel}
 * so it survives {@link com.autosecretary.features.task.ui.edit.TaskEditDialog TaskEditDialog} rotation
 * and recreation. It tracks two modes:
 * <ul>
 *   <li><b>Create mode</b> ({@link #createNewTask()}): initialises a blank {@link TaskEditState} for a new task.
 *   <li><b>Edit mode</b> ({@link #beginEditTask(String, Runnable)}): loads an existing task from the DB and
 *       converts it to a {@link TaskEditState} for editing.
 * </ul>
 * On save, {@link #saveEditedTask(com.autosecretary.features.task.data.Task)} persists the mapped result
 * and notifies the list screen via the {@code onTaskChanged} callback. On delete,
 * {@link #deleteSelectedTask(Runnable)} removes the task and fires the same callback.
 */
public class TaskEditSessionController {
    private final TaskDataService taskDataService;
    private Runnable onTaskChanged = () -> { };

    private final MutableLiveData<TaskEditState> selectedTask = new MutableLiveData<>();
    private final MutableLiveData<Task> selectedBaseTask = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNewTask = new MutableLiveData<>(false);

    public TaskEditSessionController(TaskDataService taskDataService) {
        this.taskDataService = taskDataService;
    }

    public void setOnTaskChanged(Runnable onTaskChanged) {
        this.onTaskChanged = onTaskChanged == null ? () -> { } : onTaskChanged;
    }

    public boolean isNewTask() {
        return Boolean.TRUE.equals(isNewTask.getValue());
    }

    /** Returns the current edit state, or {@code null} if no task is selected. */
    public TaskEditState getSelectedTask() {
        return selectedTask.getValue();
    }

    /** Returns the current edit state or throws if no task is selected. Used by dialog on save. */
    public TaskEditState requireSelectedTask() {
        TaskEditState task = selectedTask.getValue();
        if (task == null) {
            throw new IllegalStateException("No task selected for editing.");
        }
        return task;
    }

    /** Returns the original Task loaded from DB, or throws if none. Used for round-trip mapping on save. */
    public Task requireSelectedBaseTask() {
        Task task = selectedBaseTask.getValue();
        if (task == null) {
            throw new IllegalStateException("No base task selected for editing.");
        }
        return task;
    }

    /** Loads an existing task from the DB and converts it to a {@link TaskEditState} for editing.
     * {@code onReady} is called on the main thread once the state is available. */
    public void beginEditTask(String taskId, Runnable onReady) {
        taskDataService.loadTask(taskId, task -> {
            selectedBaseTask.postValue(task);
            selectedTask.postValue(TaskEditStateMapper.fromTask(task));
            isNewTask.postValue(false);
            if (onReady != null) {
                new Handler(Looper.getMainLooper()).post(onReady);
            }
        });
    }

    /** Initialises a blank Task with one default pref-slot and converts it to edit state for a new task. */
    public void createNewTask() {
        Task task = new Task();
        task.core = new TaskCore();
        task.slots = new ArrayList<>();
        task.prefSlots = new ArrayList<>();
        task.parents = new ArrayList<>();
        task.prerequisites = new ArrayList<>();
        task.plannedMeals = new ArrayList<>();

        task.prefSlots.add(TaskPrefSlotFactory.createDefault(task.core.id));

        selectedBaseTask.setValue(task);
        selectedTask.setValue(TaskEditStateMapper.fromTask(task));
        isNewTask.setValue(true);
    }

    /** Persists the mapped Task to the DB and notifies the list screen via onTaskChanged callback. */
    public void saveEditedTask(Task mappedTask) {
        taskDataService.saveTask(mappedTask, () -> {
            isNewTask.postValue(false);
            onTaskChanged.run();
        });
    }

    /** Deletes the currently selected task from the DB, notifies onTaskChanged, then runs the callback. */
    public void deleteSelectedTask(Runnable onDeleted) {
        String taskId = requireSelectedBaseTask().core.id;
        taskDataService.deleteTask(taskId, () -> {
            onTaskChanged.run();
            if (onDeleted != null) {
                onDeleted.run();
            }
        });
    }
}
