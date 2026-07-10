package com.autosecretary.features.task.ui.edit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.edit.internal.TaskEditStateMapper;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * ViewModel owner for the task-edit surface.
 */
public class TaskEditViewModel extends ViewModel {
    private final TaskDataService taskDataService;
    private final BudgetRepository budgetRepository;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    private final MutableLiveData<TaskEditState> selectedTask = new MutableLiveData<>();
    private final MutableLiveData<Task> selectedBaseTask = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNewTask = new MutableLiveData<>(false);
    private final MutableLiveData<Long> changeVersion = new MutableLiveData<>(0L);

    public TaskEditViewModel(
            TaskDataService taskDataService,
            BudgetRepository budgetRepository,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.taskDataService = taskDataService;
        this.budgetRepository = budgetRepository;
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
            List<TaskEditOption> parentTasks = taskDataService.readAllSync().stream()
                    .filter(task -> task.core != null
                            && task.core.id != null
                            && task.core.title != null
                            && !task.core.id.equals(currentTaskId))
                    .map(task -> new TaskEditOption(task.core.id, task.core.title))
                    .toList();
            List<TaskEditOption> budgetAccounts = budgetRepository.findActiveAccounts().stream()
                    .map(account -> new TaskEditOption(account.id(), account.name()))
                    .toList();
            List<TaskEditOption> budgetCategories = budgetRepository.findActiveCategories().stream()
                    .map(category -> new TaskEditOption(category.id(), formatBudgetCategoryLabel(category)))
                    .toList();
            callbackDispatcher.execute(() -> onLoaded.accept(new TaskEditReferenceData(
                    parentTasks,
                    budgetAccounts,
                    budgetCategories)));
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

    private static String formatBudgetCategoryLabel(BudgetCategory category) {
        if (category.icon() == null || category.icon().isBlank()) {
            return category.name();
        }
        return category.icon() + " " + category.name();
    }
}
