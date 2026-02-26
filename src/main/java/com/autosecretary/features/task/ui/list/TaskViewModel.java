package com.autosecretary.features.task.ui.list;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.autosecretary.features.task.application.AdjustTaskProgressUseCase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.domain.SchedulingConflict;
import com.autosecretary.features.task.ui.edit.TaskEditSessionController;
import com.autosecretary.features.task.ui.list.state.ViewSlotList;
import com.autosecretary.features.task.ui.list.state.ViewSlotList.ViewSlot;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskViewModel extends AndroidViewModel {
    static final int MAX_DAY_OFFSET = 6;

    private final TaskDataService taskDataService;
    private final CheckOffTaskUseCase checkOffTaskUseCase;
    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final AdjustTaskProgressUseCase adjustTaskProgressUseCase;
    private final TaskEditSessionController taskEditSessionController;
    private final TaskListProjectionService taskListProjectionService;

    private final ViewSlotList masterList;
    private final MutableLiveData<List<ViewSlot>> displayList = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> selectedDay = new MutableLiveData<>(LocalDate.now());
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<List<SchedulingConflict>> scheduleConflicts = new MutableLiveData<>();

    private ListConfig activeListConfig = ListConfig.CHECKLIST;
    private boolean hasCalendarPermission = false;
    private final Map<String, Boolean> expandedByTaskId = new HashMap<>();

    public TaskViewModel(Application app,
                         TaskDataService taskDataService,
                         CheckOffTaskUseCase checkOffTaskUseCase,
                         RegenerateScheduleUseCase regenerateScheduleUseCase,
                         AdjustTaskProgressUseCase adjustTaskProgressUseCase,
                         TaskEditSessionController taskEditSessionController,
                         TaskListProjectionService taskListProjectionService) {
        super(app);
        this.taskDataService = taskDataService;
        this.checkOffTaskUseCase = checkOffTaskUseCase;
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.adjustTaskProgressUseCase = adjustTaskProgressUseCase;
        this.taskEditSessionController = taskEditSessionController;
        this.taskEditSessionController.setOnTaskChanged(this::refreshList);
        this.taskListProjectionService = taskListProjectionService;

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

    public void filterList() {
        taskListProjectionService.project(
                masterList,
                activeListConfig,
                selectedDay.getValue(),
                searchQuery.getValue(),
                hasCalendarPermission,
                expandedByTaskId
        );
        displayList.setValue(masterList.getDisplaySlots());
    }

    public void checkOff(ViewSlot viewSlot) {
        if (viewSlot.item.isCalendarEvent()) {
            return;
        }
        checkOffTaskUseCase.execute(viewSlot.item, this::refreshList);
    }

    public void incrementProgress(ViewSlot viewSlot) {
        adjustTaskProgressUseCase.execute(viewSlot.item, true, this::refreshList);
    }

    public void decrementProgress(ViewSlot viewSlot) {
        adjustTaskProgressUseCase.execute(viewSlot.item, false, this::refreshList);
    }

    public void startTimer(String slotId) {
        if (slotId == null) {
            return;
        }
        taskDataService.startTimer(slotId, this::refreshList);
    }

    public void stopTimer(String slotId) {
        if (slotId == null) {
            return;
        }
        taskDataService.stopTimer(slotId, this::refreshList);
    }

    public void toggleExpanded(ViewSlot viewSlot) {
        if (activeListConfig != ListConfig.MANAGE || !viewSlot.hasChildren()) {
            return;
        }
        String taskId = viewSlot.item.taskId;
        boolean currentlyExpanded = expandedByTaskId.getOrDefault(taskId, true);
        expandedByTaskId.put(taskId, !currentlyExpanded);
        filterList();
    }

    public boolean isExpanded(ViewSlot viewSlot) {
        return expandedByTaskId.getOrDefault(viewSlot.item.taskId, true);
    }

    private void refreshList() {
        taskDataService.loadAllMapped(items -> {
            masterList.fromList(items);
            filterList();
            TaskWidgetProvider.notifyWidgetUpdate(getApplication());
        });
    }

    private static boolean isOnDay(ViewSlot viewSlot, LocalDate day) {
        return day == null || viewSlot.item.day.equals(day);
    }

    enum ListConfig {
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

        boolean groupByTaskParent() {
            return groupByTaskParent;
        }
    }
}
