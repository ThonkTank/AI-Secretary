package com.autosecretary.features.task.ui.list;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.app.Preferences;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.calendar.ScheduleWindow;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import com.autosecretary.features.task.domain.TaskCalendarEvent;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;
import com.autosecretary.features.task.ui.list.state.ViewSlotList;
import com.autosecretary.features.task.ui.list.state.ViewSlot;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * ViewModel for the task list screen ({@link TaskListFragment}).
 *
 * <p>Owns the entire display state for the list: the master slot list, the current display mode
 * (CHECKLIST or MANAGE), day navigation, search query, and expand/collapse state for task parents.
 *
 * <p>Data flow on every update:
 * <ol>
 *   <li>{@link #refreshList()} loads all mapped items from {@link TaskDataService} into
 *       {@link #masterList} (the source of truth).</li>
 *   <li>{@link #filterList()} filters, optionally appends calendar events, then sorts and
 *       flattens the list via {@link ViewSlotList}.</li>
 *   <li>The result is posted to {@link #displayList}, observed by the Fragment.</li>
 * </ol>
 *
 * <p>See {@code README.md} in this package for the full data-flow diagram.
 */
public class TaskViewModel extends AndroidViewModel {
    /** Maximum number of days ahead navigable in both list and widget views. */
    public static final int MAX_DAY_OFFSET = 6;

    private final TaskDataService taskDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final AdjustTaskProgressUseCase adjustTaskProgressUseCase;
    private final TaskEditSessionController taskEditSessionController;
    private final TaskCalendarService taskCalendarService;
    private final Preferences preferences;

    /** Source of truth: holds all loaded task slots. Never filtered in place. */
    private final ViewSlotList masterList;
    /** Derived display list posted to the Fragment after each filter/sort pass. */
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> selectedDay = new MutableLiveData<>(LocalDate.now());
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<List<SchedulingConflict>> scheduleConflicts = new MutableLiveData<>();

    /** Currently active display mode (CHECKLIST or MANAGE); drives filter and sort behaviour. */
    private ListConfig activeListConfig = ListConfig.CHECKLIST;
    /** True once READ_CALENDAR permission is granted; gates calendar event injection in filterList(). */
    private boolean hasCalendarPermission = false;
    /** Expand/collapse state per task ID, used in Manage mode to show/hide child slot groups. */
    private final Map<String, Boolean> expandedByTaskId = new HashMap<>();

    public TaskViewModel(Application app,
                         TaskDataService taskDataService,
                         CheckOffTaskUseCase checkOffTaskUseCase,
                         RegenerateScheduleUseCase regenerateScheduleUseCase,
                         AdjustTaskProgressUseCase adjustTaskProgressUseCase,
                         TaskEditSessionController taskEditSessionController,
                         TaskCalendarService taskCalendarService,
                         Preferences preferences) {
        super(app);
        this.taskDataService = taskDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.adjustTaskProgressUseCase = adjustTaskProgressUseCase;
        this.taskEditSessionController = taskEditSessionController;
        this.taskEditSessionController.setOnTaskChanged(this::refreshList);
        this.taskCalendarService = taskCalendarService;
        this.preferences = preferences;

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

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public LiveData<List<SchedulingConflict>> getScheduleConflicts() {
        return scheduleConflicts;
    }

    public void setSearchQuery(String query) {
        String normalizedQuery = query == null ? "" : query;
        String currentQuery = searchQuery.getValue();

        if (normalizedQuery.equals(currentQuery)) {
            return;
        }

        searchQuery.setValue(normalizedQuery);
        filterList();
    }

    public void onCalendarPermissionChanged(boolean granted) {
        hasCalendarPermission = granted;
        filterList();
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
        applyPreset(activeListConfig);
    }

    public TaskEditSessionController getTaskEditSessionController() {
        return taskEditSessionController;
    }

    public void applyChecklistPreset() {
        applyPreset(ListConfig.CHECKLIST);
    }

    public void applyManagePreset() {
        applyPreset(ListConfig.MANAGE);
    }

    private void applyPreset(ListConfig config) {
        this.activeListConfig = config;
        filterList();
    }

    public boolean isManageMode() {
        return activeListConfig == ListConfig.MANAGE;
    }

    public void updateList() {
        regenerateScheduleUseCase.execute(result -> {
            scheduleConflicts.postValue(result.conflicts);
            refreshList();
        });
    }

    /**
     * Rebuilds the display list from the master list in three steps:
     * 1. Filter allSlots by the active ListConfig predicate (and optionally a search query).
     * 2. Append calendar events from the device calendar (if READ_CALENDAR is granted).
     * 3. Sort and flatten using task-parent or slot-parent tree structure.
     */
    private void filterList() {
        LocalDate day = selectedDay.getValue();
        String rawQuery = searchQuery.getValue();
        String normalizedSearchQuery = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);

        Predicate<ViewSlot> predicate = slot -> {
            if (!activeListConfig.matches(slot, day)) {
                return false;
            }
            if (activeListConfig != ListConfig.MANAGE || normalizedSearchQuery.isEmpty()) {
                return true;
            }
            String title = slot.getItem().title == null ? "" : slot.getItem().title;
            return title.toLowerCase(Locale.ROOT).contains(normalizedSearchQuery);
        };
        masterList.filter(predicate);

        if (day != null && hasCalendarPermission) {
            ScheduleWindow window = new ScheduleWindow(
                    day,
                    preferences.readDayStartTime(day.getDayOfWeek()),
                    preferences.readDayEndTime(day.getDayOfWeek())
            );
            List<TaskCalendarEvent> events = taskCalendarService.getEventsForDay(window);
            List<ViewSlot> calendarSlots = new ArrayList<>();
            int index = 0;
            for (TaskCalendarEvent event : events) {
                TaskListItem item = TaskListItem.calendarEvent(
                        "calendar-" + day + "-" + index,
                        event.title(), day, event.start(), event.end()
                );
                calendarSlots.add(new ViewSlot(item));
                index++;
            }
            masterList.appendToDisplay(calendarSlots);
        }

        Comparator<ViewSlot> comparator = activeListConfig.comparator();
        if (activeListConfig.groupByTaskParent()) {
            masterList.sortByTask(comparator, slot -> expandedByTaskId.getOrDefault(slot.getItem().taskId, true));
        } else {
            masterList.sortBySlot(comparator);
        }

        displayList.setValue(masterList.getDisplaySlots());
    }

    public void checkOff(ViewSlot viewSlot) {
        if (viewSlot.getItem().isCalendarEvent()) {
            return;
        }
        checkOffTaskUseCase.execute(viewSlot.getItem(), this::refreshList);
    }

    public void incrementProgress(ViewSlot viewSlot) {
        adjustTaskProgressUseCase.execute(viewSlot.getItem(), true, this::refreshList);
    }

    public void decrementProgress(ViewSlot viewSlot) {
        adjustTaskProgressUseCase.execute(viewSlot.getItem(), false, this::refreshList);
    }

    public void toggleTimer(ViewSlot viewSlot) {
        if (viewSlot.getItem().slotId == null) {
            return;
        }
        if (viewSlot.getItem().inProgress) {
            taskDataService.stopTimer(viewSlot.getItem().slotId, this::refreshList);
        } else {
            taskDataService.startTimer(viewSlot.getItem().slotId, this::refreshList);
        }
    }

    public void toggleExpanded(ViewSlot viewSlot) {
        if (activeListConfig != ListConfig.MANAGE || viewSlot.getChildren().isEmpty()) {
            return;
        }
        String taskId = viewSlot.getItem().taskId;
        boolean currentlyExpanded = expandedByTaskId.getOrDefault(taskId, true);
        expandedByTaskId.put(taskId, !currentlyExpanded);
        filterList();
    }

    public boolean isExpanded(ViewSlot viewSlot) {
        return expandedByTaskId.getOrDefault(viewSlot.getItem().taskId, true);
    }

    private void refreshList() {
        taskDataService.loadAllMapped(items -> {
            masterList.fromList(items);
            filterList();
            TaskWidgetProvider.notifyWidgetUpdate(getApplication());
        });
    }

}
