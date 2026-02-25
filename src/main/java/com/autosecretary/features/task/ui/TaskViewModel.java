package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;
import com.autosecretary.features.task.ui.state.ViewSlotList;
import com.autosecretary.features.task.ui.state.ViewSlotList.ViewSlot;
import com.autosecretary.features.task.widget.TaskWidgetProvider;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
    private static final int MAX_DAY_OFFSET = 6;

    private final TaskAsyncDataService taskAsyncDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final TaskEditSessionController taskEditSessionController;

    private final ViewSlotList masterList;
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> selectedDay = new MutableLiveData<>(LocalDate.now());

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

    public LiveData<LocalDate> getSelectedDay() {
        return selectedDay;
    }

    public void navigateNextDay() {
        LocalDate current = selectedDay.getValue();
        if (current != null && current.isBefore(LocalDate.now().plusDays(MAX_DAY_OFFSET))) {
            setSelectedDay(current.plusDays(1));
        }
    }

    public void navigatePreviousDay() {
        LocalDate current = selectedDay.getValue();
        if (current != null && current.isAfter(LocalDate.now())) {
            setSelectedDay(current.minusDays(1));
        }
    }

    private void setSelectedDay(LocalDate newDay) {
        selectedDay.setValue(newDay);
        applyPreset(newDay, activeListConfig);
    }

    public TaskEditSessionController getTaskEditSessionController() {
        return taskEditSessionController;
    }


    /**
     * Applies the checklist browsing preset.
     * <p>
     * Exact semantics:
     * <ul>
     *     <li>Filter to tasks on the currently selected day.</li>
     *     <li>Hide unscheduled tasks ({@code start == null}).</li>
     *     <li>Do not group by parent task.</li>
     *     <li>Sort by time only (ascending, nulls last).</li>
     * </ul>
     * </p>
     */
    public void applyChecklistPreset() {
        applyPreset(selectedDay.getValue(), ListConfig.CHECKLIST);
    }

    /**
     * Applies the management browsing preset.
     * <p>
     * Exact semantics:
     * <ul>
     *     <li>Filter to tasks on the currently selected day.</li>
     *     <li>Include unscheduled tasks.</li>
     *     <li>Group by parent task.</li>
     *     <li>Sort by title only (natural ascending order).</li>
     * </ul>
     * </p>
     */
    public void applyManagePreset() {
        applyPreset(selectedDay.getValue(), ListConfig.MANAGE);
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
        Predicate<ViewSlot> predicate = activeListConfig.buildPredicate(day);
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
        Comparator<ViewSlot> comparator = activeListConfig.getComparator();
        boolean groupByTaskParent = activeListConfig.groupByTaskParent;

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
            TaskWidgetProvider.notifyWidgetUpdate(getApplication());
        });
    }

    private static boolean isOnDay(ViewSlot viewSlot, LocalDate day) {
        return day == null || viewSlot.item.day.equals(day);
    }

    private enum ListConfig {
        CHECKLIST(
                day -> vs -> isOnDay(vs, day) && vs.item.start != null,
                () -> Comparator.comparing(
                        (ViewSlot vs) -> vs.item.start,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ),
                false
        ),
        MANAGE(
                day -> vs -> isOnDay(vs, day),
                () -> Comparator.comparing(vs -> vs.item.title, Comparator.naturalOrder()),
                true
        );

        private final Function<LocalDate, Predicate<ViewSlot>> dayAwarePredicateBuilder;
        private final Supplier<Comparator<ViewSlot>> comparatorProvider;
        private final boolean groupByTaskParent;

        ListConfig(Function<LocalDate, Predicate<ViewSlot>> dayAwarePredicateBuilder,
                   Supplier<Comparator<ViewSlot>> comparatorProvider,
                   boolean groupByTaskParent) {
            this.dayAwarePredicateBuilder = dayAwarePredicateBuilder;
            this.comparatorProvider = comparatorProvider;
            this.groupByTaskParent = groupByTaskParent;
        }

        private Predicate<ViewSlot> buildPredicate(LocalDate day) {
            return dayAwarePredicateBuilder.apply(day);
        }

        private Comparator<ViewSlot> getComparator() {
            return comparatorProvider.get();
        }
    }
}
