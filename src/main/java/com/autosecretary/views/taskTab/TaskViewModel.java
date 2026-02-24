package com.autosecretary.views.taskTab;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.application.task.CheckOffTaskUseCase;
import com.autosecretary.application.task.LoadTaskListUseCase;
import com.autosecretary.application.task.RegenerateScheduleUseCase;
import com.autosecretary.application.task.SaveTaskUseCase;
import com.autosecretary.application.task.TaskUseCaseFactory;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskCore;
import com.autosecretary.database.task.TaskPrefSlot;
import com.autosecretary.views.taskTab.mapper.TaskEditStateMapper;
import com.autosecretary.views.taskTab.model.TaskEditState;
import com.autosecretary.views.models.ViewSlotList;
import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

public class TaskViewModel extends AndroidViewModel {
    private final LoadTaskListUseCase loadTaskListUseCase;
    private final SaveTaskUseCase saveTaskUseCase;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;

    private final ViewSlotList masterList;
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    private final MutableLiveData<TaskEditState> selectedTask = new MutableLiveData<>();
    private final MutableLiveData<Task> selectedBaseTask = new MutableLiveData<>();
    private final TaskEditStateMapper taskEditStateMapper = new TaskEditStateMapper();
    private final MutableLiveData<Boolean> isNewTask = new MutableLiveData<>(false);

    private final TaskListFilter filters = new TaskListFilter();
    private final TaskListSort sorters = new TaskListSort();

    public TaskViewModel(Application app, TaskUseCaseFactory.Bundle bundle) {
        super(app);
        this.loadTaskListUseCase = bundle.loadTaskListUseCase;
        this.saveTaskUseCase = bundle.saveTaskUseCase;
        this.checkOffTaskUseCase = bundle.checkOffTaskUseCase;
        this.regenerateScheduleUseCase = bundle.regenerateScheduleUseCase;

        this.masterList = new ViewSlotList();
        refreshList();
    }

    public LiveData<List<ViewSlot>> getList() {
        return displayList;
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

    public void beginEditTask(String taskId) {
        loadTaskListUseCase.loadTask(taskId, task -> {
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

        TaskPrefSlot defaultSlot = new TaskPrefSlot();
        defaultSlot.taskId = task.core.id;
        defaultSlot.days = EnumSet.allOf(DayOfWeek.class);
        defaultSlot.start = LocalTime.of(6, 0);
        task.prefSlots.add(defaultSlot);

        selectedBaseTask.setValue(task);
        selectedTask.setValue(taskEditStateMapper.fromTask(task));
        isNewTask.setValue(true);
    }

    public void saveEditedTask(Task mappedTask) {
        saveTaskUseCase.execute(mappedTask, () -> {
            isNewTask.postValue(false);
            refreshList();
        });
    }

    public void applyChecklistPreset() {
        filters.day = LocalDate.now();
        filters.displayUnscheduled = false;
        sorters.byTaskParent = false;
        sorters.byScore = false;
        sorters.byTime = true;
        sorters.byTitle = false;
        filterList();
    }

    public void applyManagePreset() {
        filters.day = LocalDate.now();
        filters.displayUnscheduled = true;
        sorters.byTaskParent = true;
        sorters.byScore = false;
        sorters.byTime = false;
        sorters.byTitle = true;
        filterList();
    }

    public void updateList() {
        regenerateScheduleUseCase.execute(this::refreshList);
    }

    public void filterList() {
        Predicate<ViewSlot> predicate = TaskViewSlotQuery.buildPredicate(filters);
        masterList.filter(predicate);
        sortList();
    }

    public void sortList() {
        Comparator<ViewSlot> comparator = TaskViewSlotQuery.buildComparator(sorters);
        masterList.sort(sorters.byTaskParent, comparator);
        displayList.postValue(masterList.displaySlots);
    }

    public Task requireSelectedBaseTask() {
        Task task = selectedBaseTask.getValue();
        if (task == null) {
            throw new IllegalStateException("No base task selected for editing.");
        }
        return task;
    }

    public void checkOff(ViewSlot viewSlot) {
        checkOffTaskUseCase.execute(viewSlot.item, this::refreshList);
    }

    private void refreshList() {
        loadTaskListUseCase.execute(items -> {
            masterList.fromList(items);
            filterList();
        });
    }
}
