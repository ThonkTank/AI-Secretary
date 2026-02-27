package com.autosecretary.features.task.ui.edit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.util.ArrayList;

/**
 * Owns the task-editing session lifecycle independently from list concerns.
 */
public class TaskEditSessionController {
    private final TaskDataService taskDataService;
    private Runnable onTaskChanged = () -> { };
    private final TaskEditStateMapper taskEditStateMapper = new TaskEditStateMapper();

    private final MutableLiveData<TaskEditState> selectedTask = new MutableLiveData<>();
    private final MutableLiveData<Task> selectedBaseTask = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNewTask = new MutableLiveData<>(false);

    public TaskEditSessionController(TaskDataService taskDataService) {
        this.taskDataService = taskDataService;
    }

    public void setOnTaskChanged(Runnable onTaskChanged) {
        this.onTaskChanged = onTaskChanged == null ? () -> { } : onTaskChanged;
    }

    public LiveData<TaskEditState> getSelectedTask() {
        return selectedTask;
    }

    public boolean isNewTask() {
        Boolean value = isNewTask.getValue();
        return value != null && value;
    }

    public TaskEditState requireSelectedTask() {
        TaskEditState task = selectedTask.getValue();
        if (task == null) {
            throw new IllegalStateException("No task selected for editing.");
        }
        return task;
    }

    public Task requireSelectedBaseTask() {
        Task task = selectedBaseTask.getValue();
        if (task == null) {
            throw new IllegalStateException("No base task selected for editing.");
        }
        return task;
    }

    public void beginEditTask(String taskId) {
        taskDataService.loadTask(taskId, task -> {
            selectedBaseTask.postValue(task);
            selectedTask.postValue(taskEditStateMapper.fromTask(task));
            isNewTask.postValue(false);
        });
    }

    public void createNewTask() {
        Task task = new Task();
        task.core = new TaskCore();
        task.slots = new ArrayList<>();
        task.prefSlots = new ArrayList<>();
        task.parents = new ArrayList<>();
        task.prerequisites = new ArrayList<>();

        task.prefSlots.add(TaskPrefSlotFactory.createDefault(task.core.id));

        selectedBaseTask.setValue(task);
        selectedTask.setValue(taskEditStateMapper.fromTask(task));
        isNewTask.setValue(true);
    }

    public void saveEditedTask(Task mappedTask) {
        taskDataService.saveTask(mappedTask, () -> {
            isNewTask.postValue(false);
            onTaskChanged.run();
        });
    }

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
