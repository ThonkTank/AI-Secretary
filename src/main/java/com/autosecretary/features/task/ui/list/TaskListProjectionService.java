package com.autosecretary.features.task.ui.list;

import com.autosecretary.app.Preferences;
import com.autosecretary.features.task.application.calendar.TaskCalendarEvent;
import com.autosecretary.features.task.application.calendar.TaskCalendarService;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.ui.list.state.ViewSlotList;
import com.autosecretary.features.task.ui.list.state.ViewSlotList.ViewSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

class TaskListProjectionService {
    private final TaskCalendarService taskCalendarService;
    private final Preferences preferences;

    TaskListProjectionService(TaskCalendarService taskCalendarService, Preferences preferences) {
        this.taskCalendarService = taskCalendarService;
        this.preferences = preferences;
    }

    void project(ViewSlotList masterList,
                 TaskViewModel.ListConfig listConfig,
                 LocalDate day,
                 String searchQuery,
                 boolean hasCalendarPermission,
                 Map<String, Boolean> expandedByTaskId) {
        String normalizedSearchQuery = normalizeSearchQuery(searchQuery);
        filterMasterSlots(masterList, listConfig, day, normalizedSearchQuery);

        if (day != null && hasCalendarPermission) {
            mergeCalendarEvents(masterList, day);
        }

        sortList(masterList, listConfig, expandedByTaskId);
    }

    private void filterMasterSlots(ViewSlotList masterList,
                                   TaskViewModel.ListConfig listConfig,
                                   LocalDate day,
                                   String normalizedSearchQuery) {
        Predicate<ViewSlot> predicate = slot -> {
            if (!listConfig.matches(slot, day)) {
                return false;
            }
            if (listConfig != TaskViewModel.ListConfig.MANAGE || normalizedSearchQuery.isEmpty()) {
                return true;
            }

            String title = slot.item.title == null ? "" : slot.item.title;
            return title.toLowerCase(Locale.ROOT).contains(normalizedSearchQuery);
        };

        masterList.filter(predicate);
    }

    private void mergeCalendarEvents(ViewSlotList masterList, LocalDate day) {
        List<TaskCalendarEvent> events = taskCalendarService.getEventsForDay(
                day,
                preferences.readPrefTime(day, true),
                preferences.readPrefTime(day, false)
        );

        List<ViewSlot> mergedSlots = new ArrayList<>(masterList.displaySlots);
        int index = 0;
        for (TaskCalendarEvent event : events) {
            TaskListItem item = TaskListItem.calendarEvent(
                    "calendar-" + day + "-" + index,
                    event.title(),
                    day,
                    event.start(),
                    event.end()
            );
            mergedSlots.add(new ViewSlot(item));
            index++;
        }

        masterList.displaySlots = mergedSlots;
    }

    private void sortList(ViewSlotList masterList,
                          TaskViewModel.ListConfig listConfig,
                          Map<String, Boolean> expandedByTaskId) {
        Comparator<ViewSlot> comparator = listConfig.comparator();

        if (listConfig.groupByTaskParent()) {
            masterList.sortByTask(comparator, slot -> expandedByTaskId.getOrDefault(slot.item.taskId, true));
            return;
        }
        masterList.sortBySlot(comparator);
    }

    private static String normalizeSearchQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }
}
