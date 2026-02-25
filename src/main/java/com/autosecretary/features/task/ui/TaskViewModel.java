package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.ui.model.ViewSlotList;
import com.autosecretary.features.task.ui.model.ViewSlotList.ViewSlot;

import java.time.LocalDate;
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
 * Editing lifecycle is delegated to {@link TaskEditSessionController} while this ViewModel keeps
 * list-oriented concerns and scheduling actions.
 * </p>
 */
public class TaskViewModel extends AndroidViewModel {
    private final TaskAsyncDataService taskAsyncDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final TaskEditSessionController taskEditSessionController;

    private final ViewSlotList masterList;
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();

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
        this.taskEditSessionController = new TaskEditSessionController(taskAsyncDataService, this::refreshList);

        this.masterList = new ViewSlotList();
        applyChecklistPreset();
        refreshList();
    }

    public LiveData<List<ViewSlot>> getList() {
        return displayList;
    }

    public TaskEditSessionController getTaskEditSessionController() {
        return taskEditSessionController;
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
        displayList.setValue(masterList.displaySlots);
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
        return vs -> isOnDay(vs, day) && vs.item.start != null;
    }

    private static Predicate<ViewSlot> buildManagePredicate(LocalDate day) {
        return vs -> isOnDay(vs, day);
    }

    private static boolean isOnDay(ViewSlot viewSlot, LocalDate day) {
        return day == null || viewSlot.item.day.equals(day);
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
