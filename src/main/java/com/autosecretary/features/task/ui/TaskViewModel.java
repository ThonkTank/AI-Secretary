package com.autosecretary.features.task.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.app.Preferences;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.DecrementTaskProgressUseCase;
import com.autosecretary.features.task.application.DeleteTaskUseCase;
import com.autosecretary.features.task.application.IncrementTaskProgressUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskAsyncDataService;
import com.autosecretary.features.task.application.TaskListItem;
import com.autosecretary.features.task.application.internal.calendar.CalendarEvent;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;
import com.autosecretary.features.task.ui.state.ViewSlotList;
import com.autosecretary.features.task.ui.state.ViewSlotList.ViewSlot;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class TaskViewModel extends AndroidViewModel {
    private static final int MAX_DAY_OFFSET = 6;

    private final TaskAsyncDataService taskAsyncDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final IncrementTaskProgressUseCase incrementTaskProgressUseCase;
    private final DecrementTaskProgressUseCase decrementTaskProgressUseCase;
    private final TaskEditSessionController taskEditSessionController;
    private final Preferences preferences;
    private final CalendarReader calendarReader;

    private final ViewSlotList masterList;
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> selectedDay = new MutableLiveData<>(LocalDate.now());

    private LocalDate day;
    private ListConfig activeListConfig = ListConfig.CHECKLIST;
    private boolean hasCalendarPermission = false;
    private final Map<String, Boolean> expandedByTaskId = new HashMap<>();

    public TaskViewModel(Application app,
                         TaskAsyncDataService taskAsyncDataService,
                         CheckOffTaskUseCase checkOffTaskUseCase,
                         RegenerateScheduleUseCase regenerateScheduleUseCase,
                         DeleteTaskUseCase deleteTaskUseCase,
                         CalendarReader calendarReader,
                         IncrementTaskProgressUseCase incrementTaskProgressUseCase,
                         DecrementTaskProgressUseCase decrementTaskProgressUseCase) {
        super(app);
        this.taskAsyncDataService = taskAsyncDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.incrementTaskProgressUseCase = incrementTaskProgressUseCase;
        this.decrementTaskProgressUseCase = decrementTaskProgressUseCase;
        this.taskEditSessionController = new TaskEditSessionController(
                taskAsyncDataService,
                deleteTaskUseCase,
                this::refreshList
        );
        this.calendarReader = calendarReader;
        this.preferences = new Preferences(app);

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
        applyPreset(newDay, activeListConfig);
    }

    public TaskEditSessionController getTaskEditSessionController() {
        return taskEditSessionController;
    }

    public void applyChecklistPreset() {
        applyPreset(selectedDay.getValue(), ListConfig.CHECKLIST);
    }

    public void applyManagePreset() {
        applyPreset(selectedDay.getValue(), ListConfig.MANAGE);
    }

    private void applyPreset(LocalDate day, ListConfig config) {
        this.day = day;
        this.activeListConfig = config;
        filterList();
    }

    public boolean isManageMode() {
        return activeListConfig == ListConfig.MANAGE;
    }

    public void updateList() {
        regenerateScheduleUseCase.execute(this::refreshList);
    }

    public void filterList() {
        Predicate<ViewSlot> predicate = slot -> activeListConfig.matches(slot, day);
        masterList.filter(predicate);

        if (day != null && hasCalendarPermission) {
            List<CalendarEvent> events = calendarReader.getEventsForDay(
                    getApplication(),
                    day,
                    preferences.readPrefTime(day, true),
                    preferences.readPrefTime(day, false)
            );
            List<ViewSlot> merged = new ArrayList<>(masterList.displaySlots);
            int index = 0;
            for (CalendarEvent event : events) {
                TaskListItem item = TaskListItem.calendarEvent(
                        "calendar-" + day + "-" + index,
                        event.title(),
                        day,
                        event.start(),
                        event.end()
                );
                merged.add(new ViewSlot(item));
                index++;
            }
            masterList.displaySlots = merged;
        }

        sortList();
    }

    public void sortList() {
        Comparator<ViewSlot> comparator = activeListConfig.comparator();

        if (activeListConfig.groupByTaskParent) {
            masterList.sortByTask(comparator, slot -> expandedByTaskId.getOrDefault(slot.item.taskId, true));
        } else {
            masterList.sortBySlot(comparator);
        }
        displayList.setValue(masterList.displaySlots);
    }

    public void checkOff(ViewSlot viewSlot) {
        if (viewSlot.item.isCalendarEvent()) {
            return;
        }
        checkOffTaskUseCase.execute(viewSlot.item, this::refreshList);
    }

    public void incrementProgress(ViewSlot viewSlot) {
        incrementTaskProgressUseCase.execute(viewSlot.item, this::refreshList);
    }

    public void decrementProgress(ViewSlot viewSlot) {
        decrementTaskProgressUseCase.execute(viewSlot.item, this::refreshList);
    }

    public void startTimer(String slotId) {
        if (slotId == null) {
            return;
        }
        taskAsyncDataService.startTimer(slotId, this::refreshList);
    }

    public void stopTimer(String slotId) {
        if (slotId == null) {
            return;
        }
        taskAsyncDataService.stopTimer(slotId, this::refreshList);
    }

    public void toggleExpanded(ViewSlot viewSlot) {
        if (activeListConfig != ListConfig.MANAGE || !viewSlot.hasChildren) {
            return;
        }
        String taskId = viewSlot.item.taskId;
        boolean currentlyExpanded = expandedByTaskId.getOrDefault(taskId, true);
        expandedByTaskId.put(taskId, !currentlyExpanded);
        sortList();
    }

    public boolean isExpanded(ViewSlot viewSlot) {
        return expandedByTaskId.getOrDefault(viewSlot.item.taskId, true);
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
        CHECKLIST(false) {
            @Override
            boolean matches(ViewSlot slot, LocalDate day) {
                if (slot.item.isCalendarEvent()) {
                    return isOnDay(slot, day);
                }
                return isOnDay(slot, day) && slot.item.start != null;
            }

            @Override
            Comparator<ViewSlot> comparator() {
                return Comparator.comparing(
                        (ViewSlot slot) -> slot.item.start,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
            }
        },
        MANAGE(true) {
            @Override
            boolean matches(ViewSlot slot, LocalDate day) {
                return isOnDay(slot, day);
            }

            @Override
            Comparator<ViewSlot> comparator() {
                return Comparator
                        .comparing((ViewSlot slot) -> slot.item.isCalendarEvent() ? 1 : 0)
                        .thenComparing(slot -> slot.item.title, Comparator.naturalOrder());
            }
        };

        private final boolean groupByTaskParent;

        ListConfig(boolean groupByTaskParent) {
            this.groupByTaskParent = groupByTaskParent;
        }

        abstract boolean matches(ViewSlot slot, LocalDate day);

        abstract Comparator<ViewSlot> comparator();
    }
}
