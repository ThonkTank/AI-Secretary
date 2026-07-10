package com.autosecretary.features.task.ui.edit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.edit.TaskEditReferenceData;
import com.autosecretary.features.task.application.edit.TaskEditReferenceDataUseCase;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * ViewModel owner for the task-edit surface.
 */
public class TaskEditViewModel extends ViewModel {
    private final TaskDataService taskDataService;
    private final TaskEditReferenceDataUseCase referenceDataUseCase;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    private final MutableLiveData<TaskEditState> selectedTask = new MutableLiveData<>();
    private final MutableLiveData<Task> selectedBaseTask = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNewTask = new MutableLiveData<>(false);
    private final MutableLiveData<Long> changeVersion = new MutableLiveData<>(0L);

    public TaskEditViewModel(
            TaskDataService taskDataService,
            TaskEditReferenceDataUseCase referenceDataUseCase,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.taskDataService = taskDataService;
        this.referenceDataUseCase = referenceDataUseCase;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public LiveData<Long> getChangeVersion() {
        return changeVersion;
    }

    public boolean isNewTask() {
        return Boolean.TRUE.equals(isNewTask.getValue());
    }

    public TaskEditState getSelectedTask() {
        return selectedTask.getValue();
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

    public void beginEditTask(String taskId, Runnable onReady) {
        taskDataService.loadTask(taskId, task -> {
            selectedBaseTask.postValue(task);
            selectedTask.postValue(TaskEditStateMapper.fromTask(task));
            isNewTask.postValue(false);
            if (onReady != null) {
                callbackDispatcher.execute(onReady);
            }
        });
    }

    public void createNewTask() {
        Task task = new Task();
        task.prefSlots.add(TaskPrefSlotFactory.createDefault(task.core.id));

        selectedBaseTask.setValue(task);
        selectedTask.setValue(TaskEditStateMapper.fromTask(task));
        isNewTask.setValue(true);
    }

    public void loadReferenceData(Consumer<TaskEditReferenceData> onLoaded) {
        workerExecutor.execute(() -> {
            String currentTaskId = requireSelectedTask().id;
            TaskEditReferenceData referenceData = referenceDataUseCase.load(currentTaskId);
            callbackDispatcher.execute(() -> onLoaded.accept(referenceData));
        });
    }

    public void saveEditedTask() {
        Task mappedTask = TaskEditStateMapper.toTask(requireSelectedTask(), requireSelectedBaseTask());
        taskDataService.saveTask(mappedTask, () -> {
            isNewTask.postValue(false);
            incrementChangeVersion();
        });
    }

    public void deleteSelectedTask(Runnable onDeleted) {
        String taskId = requireSelectedBaseTask().core.id;
        taskDataService.deleteTask(taskId, () -> {
            incrementChangeVersion();
            if (onDeleted != null) {
                onDeleted.run();
            }
        });
    }

    private void incrementChangeVersion() {
        Long currentValue = changeVersion.getValue();
        changeVersion.postValue(currentValue == null ? 1L : currentValue + 1L);
    }

}
