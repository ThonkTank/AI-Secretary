package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.ui.mapper.TaskEditStateMapper;
import com.autosecretary.features.task.ui.model.TaskEditState;
import com.autosecretary.features.task.ui.model.ViewSlotList;
import com.autosecretary.features.task.ui.model.ViewSlotList.ViewSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Coordinates task-list presentation state for the task screen.
 * <p>
 * Raw task slots are loaded into {@link #masterList}, then transformed through a two-step pipeline:
 * filtering ({@link #filterList()}) and sorting ({@link #sortList()}). The resulting
 * {@link ViewSlotList#displaySlots} are published to {@link #displayList} for the UI.
 * </p>
 * <p>
 * This ViewModel also manages task-selection/editing state ({@link #selectedTask},
 * {@link #selectedBaseTask}, {@link #isNewTask}) and delegates persistence/scheduling actions to
 * use cases.
 * </p>
 */
public class TaskViewModel extends AndroidViewModel {
    private final TaskAsyncDataService taskAsyncDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;

    private final ViewSlotList masterList;
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    private final MutableLiveData<TaskEditState> selectedTask = new MutableLiveData<>();
    private final MutableLiveData<Task> selectedBaseTask = new MutableLiveData<>();
    private final TaskEditStateMapper taskEditStateMapper = new TaskEditStateMapper();
    private final MutableLiveData<Boolean> isNewTask = new MutableLiveData<>(false);

    private LocalDate day;
    private ListConfig activeListConfig = ListConfig.CHECKLIST;

    public TaskViewModel(Application app,
                         TaskAsyncDataService taskAsyncDataService,
                         CheckOffTaskUseCase checkOffTaskUseCase,
                         RegenerateScheduleUseCase regenerateScheduleUseCase) {
        super(app);
        this.taskAsyncDataService = taskAsyncDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;

        this.masterList = new ViewSlotList();
        applyChecklistPreset();
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
        taskAsyncDataService.loadTask(taskId, task -> {
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
        taskAsyncDataService.saveTask(mappedTask, () -> {
            isNewTask.postValue(false);
            refreshList();
        });
    }

    /**
     * Applies the checklist browsing preset.
     * <p>
     * Exact semantics:
     * <ul>
     *     <li>Filter to tasks on {@code LocalDate.now()}.</li>
     *     <li>Hide unscheduled tasks ({@code start == null}).</li>
     *     <li>Do not group by parent task.</li>
     *     <li>Sort by time only (ascending, nulls last).</li>
     * </ul>
     * Note: both built-in presets currently target {@code LocalDate.now()}.
     * </p>
     */
    public void applyChecklistPreset() {
        applyPreset(LocalDate.now(), ListConfig.CHECKLIST);
    }

    /**
     * Applies the management browsing preset.
     * <p>
     * Exact semantics:
     * <ul>
     *     <li>Filter to tasks on {@code LocalDate.now()}.</li>
     *     <li>Include unscheduled tasks.</li>
     *     <li>Group by parent task.</li>
     *     <li>Sort by title only (natural ascending order).</li>
     * </ul>
     * </p>
     */
    public void applyManagePreset() {
        applyPreset(LocalDate.now(), ListConfig.MANAGE);
    }

    private void applyPreset(LocalDate day, ListConfig config) {
        this.day = day;
        this.activeListConfig = config;
        filterList();
    }

    public void updateList() {
        regenerateScheduleUseCase.execute(this::refreshList);
    }

    /**
     * Rebuilds the displayed list using the current filter controls.
     * <p>
     * Invariant: this method always applies filtering before sorting so that ordering is performed
     * over the already-filtered subset.
     * </p>
     */
    public void filterList() {
        Predicate<ViewSlot> predicate;
        switch (activeListConfig) {
            case CHECKLIST:
                predicate = buildChecklistPredicate(day);
                break;
            case MANAGE:
                predicate = buildManagePredicate(day);
                break;
            default:
                throw new IllegalStateException("Unsupported list mode: " + activeListConfig);
        }
        masterList.filter(predicate);
        sortList();
    }

    /**
     * Re-sorts the current filtered list and publishes it to observers.
     * <p>
     * Invariant: this method does not change filter membership; it only updates order/grouping and
     * then posts {@link ViewSlotList#displaySlots} to {@link #displayList}.
     * </p>
     */
    public void sortList() {
        Comparator<ViewSlot> comparator;
        boolean groupByTaskParent;

        switch (activeListConfig) {
            case CHECKLIST:
                comparator = buildChecklistComparator();
                groupByTaskParent = false;
                break;
            case MANAGE:
                comparator = buildManageComparator();
                groupByTaskParent = true;
                break;
            default:
                throw new IllegalStateException("Unsupported list mode: " + activeListConfig);
        }

        masterList.sort(groupByTaskParent, comparator);
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
        taskAsyncDataService.loadAllMapped(items -> {
            masterList.fromList(items);
            filterList();
        });
    }

    /**
     * Builds the checklist predicate in fixed order:
     * <ol>
     *     <li>If {@code day != null}, require {@code vs.item.day.equals(day)}.</li>
     *     <li>Always require {@code vs.item.start != null} to hide unscheduled items.</li>
     * </ol>
     */
    private static Predicate<ViewSlot> buildChecklistPredicate(LocalDate day) {
        Predicate<ViewSlot> predicate = vs -> true;

        if (day != null) {
            predicate = predicate.and(vs -> vs.item.day.equals(day));
        }
        predicate = predicate.and(vs -> vs.item.start != null);
        return predicate;
    }

    private static Predicate<ViewSlot> buildManagePredicate(LocalDate day) {
        Predicate<ViewSlot> predicate = vs -> true;

        if (day != null) {
            predicate = predicate.and(vs -> vs.item.day.equals(day));
        }
        return predicate;
    }

    /**
     * Checklist sorts by start time only (ascending, nulls last).
     */
    private static Comparator<ViewSlot> buildChecklistComparator() {
        return Comparator.comparing(
                (ViewSlot vs) -> vs.item.start,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
    }

    private static Comparator<ViewSlot> buildManageComparator() {
        return Comparator.comparing(vs -> vs.item.title, Comparator.naturalOrder());
    }

    private enum ListConfig {
        CHECKLIST,
        MANAGE
    }
}
