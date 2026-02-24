package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.LoadTaskListUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.SaveTaskUseCase;
import com.autosecretary.features.task.application.TaskUseCaseFactory;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.mapper.TaskEditStateMapper;
import com.autosecretary.features.task.ui.model.TaskEditState;
import com.autosecretary.views.models.ViewSlotList;
import com.autosecretary.views.models.ViewSlotList.ViewSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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

    private LocalDate day;
    private boolean displayUnscheduled;
    private boolean byTaskParent;
    private boolean byScore;
    private boolean byTime;
    private boolean byTitle;

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

        task.prefSlots.add(TaskPrefSlotFactory.createDefault(task.core.id));

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
        day = LocalDate.now();
        displayUnscheduled = false;
        byTaskParent = false;
        byScore = false;
        byTime = true;
        byTitle = false;
        filterList();
    }

    public void applyManagePreset() {
        day = LocalDate.now();
        displayUnscheduled = true;
        byTaskParent = true;
        byScore = false;
        byTime = false;
        byTitle = true;
        filterList();
    }

    public void updateList() {
        regenerateScheduleUseCase.execute(this::refreshList);
    }

    public void filterList() {
        Predicate<ViewSlot> predicate = buildPredicate(day, displayUnscheduled);
        masterList.filter(predicate);
        sortList();
    }

    public void sortList() {
        Comparator<ViewSlot> comparator = buildComparator(byScore, byTime, byTitle);
        masterList.sort(byTaskParent, comparator);
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

    private static Predicate<ViewSlot> buildPredicate(LocalDate day, boolean displayUnscheduled) {
        Predicate<ViewSlot> predicate = vs -> true;

        if (day != null) {
            predicate = predicate.and(vs -> vs.item.day.equals(day));
        }
        if (!displayUnscheduled) {
            predicate = predicate.and(vs -> vs.item.start != null);
        }
        return predicate;
    }

    private static Comparator<ViewSlot> buildComparator(boolean byScore, boolean byTime, boolean byTitle) {
        Comparator<ViewSlot> comparator = (a, b) -> 0;

        if (byScore) {
            comparator = comparator.thenComparing(
                    Comparator.comparingInt((ViewSlot vs) -> vs.item.score).reversed()
            );
        }
        if (byTime) {
            comparator = comparator.thenComparing(
                    vs -> vs.item.start,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        }
        if (byTitle) {
            comparator = comparator.thenComparing(vs -> vs.item.title, Comparator.naturalOrder());
        }
        return comparator;
    }
}
