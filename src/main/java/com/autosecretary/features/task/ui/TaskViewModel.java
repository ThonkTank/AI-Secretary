package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.LoadTaskListUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.SaveTaskUseCase;
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
    private ListConfig activeListConfig = ListConfig.DEFAULT;

    public TaskViewModel(Application app,
                         LoadTaskListUseCase loadTaskListUseCase,
                         SaveTaskUseCase saveTaskUseCase,
                         CheckOffTaskUseCase checkOffTaskUseCase,
                         RegenerateScheduleUseCase regenerateScheduleUseCase) {
        super(app);
        this.loadTaskListUseCase = loadTaskListUseCase;
        this.saveTaskUseCase = saveTaskUseCase;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;

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
        Predicate<ViewSlot> predicate = buildPredicate(day, activeListConfig);
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
        Comparator<ViewSlot> comparator = buildComparator(activeListConfig);
        masterList.sort(activeListConfig.isGroupByTaskParent(), comparator);
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

    /**
     * Builds the filter predicate in fixed order:
     * <ol>
     *     <li>If {@code day != null}, require {@code vs.item.day.equals(day)}.</li>
     *     <li>If {@code !displayUnscheduled}, additionally require {@code vs.item.start != null}.</li>
     * </ol>
     * These conditions are AND-combined.
     */
    private static Predicate<ViewSlot> buildPredicate(LocalDate day, ListConfig config) {
        Predicate<ViewSlot> predicate = vs -> true;

        if (day != null) {
            predicate = predicate.and(vs -> vs.item.day.equals(day));
        }
        if (!config.isDisplayUnscheduled()) {
            predicate = predicate.and(vs -> vs.item.start != null);
        }
        return predicate;
    }

    /**
     * Builds the comparator by appending optional tie-breakers in this exact order:
     * score (descending), then start time (ascending, nulls last), then title (ascending).
     * Disabled sort dimensions are skipped.
     */
    private static Comparator<ViewSlot> buildComparator(ListConfig config) {
        Comparator<ViewSlot> comparator = (a, b) -> 0;

        if (config.isSortByScore()) {
            comparator = comparator.thenComparing(
                    Comparator.comparingInt((ViewSlot vs) -> vs.item.score).reversed()
            );
        }
        if (config.isSortByTime()) {
            comparator = comparator.thenComparing(
                    vs -> vs.item.start,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        }
        if (config.isSortByTitle()) {
            comparator = comparator.thenComparing(vs -> vs.item.title, Comparator.naturalOrder());
        }
        return comparator;
    }

    private enum ListConfig {
        DEFAULT(false, false, false, false, false),
        CHECKLIST(false, false, false, true, false),
        MANAGE(true, true, false, false, true);

        private final boolean displayUnscheduled;
        private final boolean groupByTaskParent;
        private final boolean sortByScore;
        private final boolean sortByTime;
        private final boolean sortByTitle;

        ListConfig(boolean displayUnscheduled, boolean groupByTaskParent, boolean sortByScore, boolean sortByTime, boolean sortByTitle) {
            this.displayUnscheduled = displayUnscheduled;
            this.groupByTaskParent = groupByTaskParent;
            this.sortByScore = sortByScore;
            this.sortByTime = sortByTime;
            this.sortByTitle = sortByTitle;
        }

        boolean isDisplayUnscheduled() {
            return displayUnscheduled;
        }

        boolean isGroupByTaskParent() {
            return groupByTaskParent;
        }

        boolean isSortByScore() {
            return sortByScore;
        }

        boolean isSortByTime() {
            return sortByTime;
        }

        boolean isSortByTitle() {
            return sortByTitle;
        }
    }
}
