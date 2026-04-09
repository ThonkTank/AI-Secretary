package com.autosecretary.features.task.ui.list;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;

import android.util.Log;
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
import java.util.Collections;
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
    /** Fires once during construction to auto-generate today's schedule. */
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final AdjustTaskProgressUseCase adjustTaskProgressUseCase;
    private final TaskEditSessionController taskEditSessionController;
    private final TaskCalendarService taskCalendarService;
    private final TaskScheduleConfigRepository scheduleConfigRepository;

    /** Source of truth: holds all loaded task slots. Never filtered in place. */
    private final ViewSlotList masterList;
    /** Derived display list posted to the Fragment after each filter/sort pass. */
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> selectedDay = new MutableLiveData<>(LocalDate.now());
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    /** Currently active display mode (CHECKLIST or MANAGE); drives filter and sort behaviour. */
    private ListConfig activeListConfig = ListConfig.CHECKLIST;
    /** True once READ_CALENDAR permission is granted; gates calendar event injection in filterList(). */
    private boolean hasCalendarPermission = false;
    /** Expand/collapse state per task ID, used in Manage mode to show/hide child slot groups. */
    private final Map<String, Boolean> expandedByTaskId = new HashMap<>();
    /** Cached calendar events for the current day; avoids re-querying ContentResolver on every filterList(). */
    private List<ViewSlot> cachedCalendarSlots = Collections.emptyList();
    /** The day for which cachedCalendarSlots was fetched; null means cache is invalid. */
    private LocalDate cachedCalendarDay = null;

    public TaskViewModel(Application app,
                         TaskDataService taskDataService,
                         CheckOffTaskUseCase checkOffTaskUseCase,
                         RegenerateScheduleUseCase regenerateScheduleUseCase,
                         AdjustTaskProgressUseCase adjustTaskProgressUseCase,
                         TaskEditSessionController taskEditSessionController,
                         TaskCalendarService taskCalendarService,
                         TaskScheduleConfigRepository scheduleConfigRepository) {
        super(app);
        this.taskDataService = taskDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.adjustTaskProgressUseCase = adjustTaskProgressUseCase;
        this.taskEditSessionController = taskEditSessionController;
        this.taskEditSessionController.setOnTaskChanged(this::refreshList);
        this.taskCalendarService = taskCalendarService;
        this.scheduleConfigRepository = scheduleConfigRepository;

        this.masterList = new ViewSlotList();
        // Show existing data immediately while regeneration runs in background.
        refreshList();
        // Auto-regenerate on ViewModel creation (once per Activity lifecycle).
        // Previously triggered by the manual "Generieren" button.
        regenerateAndLoad();
    }

    /** Regenerates the daily schedule and refreshes the display list on completion. */
    private void regenerateAndLoad() {
        regenerateScheduleUseCase.execute(result -> {
            List<SchedulingConflict> conflicts = result.conflicts();
            if (!conflicts.isEmpty()) {
                for (SchedulingConflict c : conflicts) {
                    Log.w("TaskScheduleConflict", c.toString());
                }
            }
            refreshList();
        });
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

    /** Updates the search query and re-filters the list. Only effective in Manage mode. */
    public void setSearchQuery(String query) {
        String normalizedQuery = query == null ? "" : query;
        String currentQuery = searchQuery.getValue();

        if (normalizedQuery.equals(currentQuery)) {
            return;
        }

        searchQuery.setValue(normalizedQuery);
        filterList();
    }

    /** Called when READ_CALENDAR permission result is received; re-filters to show/hide calendar rows. */
    public void onCalendarPermissionChanged(boolean granted) {
        hasCalendarPermission = granted;
        cachedCalendarDay = null; // Invalidate so refreshCalendarCache re-queries
        refreshCalendarCache(selectedDay.getValue());
        filterList();
    }

    /** Moves the selected day forward by one, capped at {@link #MAX_DAY_OFFSET} days from today. */
    public void navigateNextDay() {
        LocalDate current = selectedDay.getValue();
        if (current != null && current.isBefore(LocalDate.now().plusDays(MAX_DAY_OFFSET))) {
            setSelectedDay(current.plusDays(1));
        }
    }

    /** Moves the selected day backward by one; today is the minimum (no past navigation). */
    public void navigatePreviousDay() {
        LocalDate current = selectedDay.getValue();
        if (current != null && current.isAfter(LocalDate.now())) {
            setSelectedDay(current.minusDays(1));
        }
    }

    private void setSelectedDay(LocalDate newDay) {
        selectedDay.setValue(newDay);
        refreshCalendarCache(newDay);
        applyPreset(activeListConfig);
    }

    public TaskEditSessionController getTaskEditSessionController() {
        return taskEditSessionController;
    }

    /** Switches to Checklist mode: scheduled slots for the selected day, sorted by time. */
    public void applyChecklistPreset() {
        applyPreset(ListConfig.CHECKLIST);
    }

    /** Switches to Manage mode: all tasks grouped by hierarchy, with search and expand/collapse. */
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

    /**
     * Rebuilds the display list from the master list in three steps:
     * 1. Filter allSlots by the active ListConfig predicate (and optionally a search query).
     * 2. Append cached calendar events (if READ_CALENDAR is granted).
     * 3. Sort and flatten using task-parent or slot-parent tree structure.
     *
     * <p>Calendar events are read from {@link #cachedCalendarSlots} rather than re-querying
     * the ContentResolver every time. The cache is refreshed when the selected day changes
     * or on {@link #refreshList()}.
     */
    private void filterList() {
        LocalDate day = selectedDay.getValue();
        String rawQuery = searchQuery.getValue();
        String normalizedSearchQuery = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);

        Predicate<ViewSlot> predicate = slot -> {
            if (!activeListConfig.matches(slot, day)) return false;
            if (activeListConfig == ListConfig.MANAGE && !normalizedSearchQuery.isEmpty()) {
                String title = slot.getItem().title == null ? "" : slot.getItem().title;
                return title.toLowerCase(Locale.ROOT).contains(normalizedSearchQuery);
            }
            return true;
        };
        masterList.filter(predicate);

        if (day != null && hasCalendarPermission && !cachedCalendarSlots.isEmpty()) {
            masterList.appendToDisplay(cachedCalendarSlots);
        }

        Comparator<ViewSlot> comparator = activeListConfig.comparator();
        if (activeListConfig.groupByTaskParent()) {
            masterList.sortByTask(comparator, slot -> expandedByTaskId.getOrDefault(slot.getItem().taskId, true));
        } else {
            masterList.sortBySlot(comparator);
        }

        displayList.setValue(masterList.getDisplaySlots());
    }

    /**
     * Queries calendar events for the given day and caches them as ViewSlots.
     * Called when the selected day changes or during a full data refresh.
     *
     * <p>If the schedule config cache is cold and we're on the main thread, Room will throw.
     * In that case we fall back to empty slots; the next refreshList() cycle will populate them
     * once the background regeneration has warmed the cache.
     */
    private void refreshCalendarCache(LocalDate day) {
        if (day == null || !hasCalendarPermission) {
            cachedCalendarSlots = Collections.emptyList();
            cachedCalendarDay = day;
            return;
        }
        if (day.equals(cachedCalendarDay)) {
            return;
        }
        cachedCalendarDay = day;
        SchedulingWindowProvider.SchedulingWindow sw;
        try {
            sw = scheduleConfigRepository.forDay(day);
        } catch (IllegalStateException e) {
            // Schedule config cache cold + main thread — skip until next cycle
            Log.w("TaskViewModel", "Calendar cache skipped: schedule config not yet warm", e);
            cachedCalendarSlots = Collections.emptyList();
            return;
        }
        ScheduleWindow window = new ScheduleWindow(
                day,
                sw.start().toLocalTime(),
                sw.end().toLocalTime()
        );
        List<TaskCalendarEvent> events = taskCalendarService.getEventsForDay(window);
        List<ViewSlot> calendarSlots = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            TaskCalendarEvent event = events.get(i);
            TaskListItem item = TaskListItem.calendarEvent(
                    "calendar-" + day + "-" + i,
                    event.title(), day, event.start(), event.end()
            );
            calendarSlots.add(new ViewSlot(item));
        }
        cachedCalendarSlots = calendarSlots;
    }

    /** Two-phase checkoff: first tap starts the slot, second tap completes it. No-op for calendar events. */
    public void checkOff(ViewSlot viewSlot) {
        if (viewSlot.getItem().isCalendarEvent()) {
            return;
        }
        checkOffTaskUseCase.execute(viewSlot.getItem(), this::refreshList);
    }

    /** Increments the progress counter for a goal-based task (e.g. 3/10 → 4/10). */
    public void incrementProgress(ViewSlot viewSlot) {
        adjustTaskProgressUseCase.execute(viewSlot.getItem(), true, this::refreshList);
    }

    /** Decrements the progress counter for a goal-based task (e.g. 4/10 → 3/10). */
    public void decrementProgress(ViewSlot viewSlot) {
        adjustTaskProgressUseCase.execute(viewSlot.getItem(), false, this::refreshList);
    }

    /** Toggles expand/collapse for a parent task in Manage mode. No-op in Checklist mode or for leaf tasks. */
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

    /** Reloads all tasks from the DB into the master list, re-filters, and notifies the widget. */
    private void refreshList() {
        taskDataService.loadAllMapped(items -> {
            masterList.fromList(items);
            cachedCalendarDay = null; // Invalidate so filterList uses fresh calendar data
            refreshCalendarCache(selectedDay.getValue());
            filterList();
            TaskWidgetProvider.notifyWidgetUpdate(getApplication());
        });
    }

}
