package com.autosecretary.ui;

import com.autosecretary.application.CalendarOccurrence;
import com.autosecretary.application.DashboardData;
import com.autosecretary.application.TodayEntry;
import com.autosecretary.application.TodayTimeline;
import com.autosecretary.domain.PlanAssignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Maps the canonical timeline once; app and widget only differ in technical rendering. */
public final class TodayPresenter {
    public TodayViewData present(
            DashboardData data,
            TodayTimeline timeline,
            ZoneId zone,
            String hiddenCalendarTitle) {
        LocalDate today = timeline.day();
        List<TodayRow> rows = timeline.entries().stream()
                .map(entry -> (TodayRow) (entry instanceof TodayEntry.Focus focus
                        ? new TodayRow.Focus(focusRow(focus, data, today, hiddenCalendarTitle))
                        : new TodayRow.Calendar(calendarRow(
                                ((TodayEntry.Calendar) entry).value(), zone,
                                hiddenCalendarTitle))))
                .collect(java.util.stream.Collectors.toList());
        return new TodayViewData(today, timeline.generatedAt(), timeline.nextRefreshAt(), rows,
                timeline.undoAvailable(), data.calendarPermissionMissing());
    }

    private static CalendarRow calendarRow(
            CalendarOccurrence item, ZoneId zone, String hiddenTitle) {
        boolean hidden = item.title().isEmpty();
        return new CalendarRow(item.id().stableValue(),
                LocalDateTime.ofInstant(item.start(), zone),
                LocalDateTime.ofInstant(item.end(), zone),
                item.title().orElse(hiddenTitle), hidden);
    }

    private static FocusRow focusRow(
            TodayEntry.Focus entry,
            DashboardData data,
            LocalDate today,
            String hiddenCalendarTitle) {
        PlanAssignment assignment = entry.value();
        var item = assignment.workItem();
        List<StepRow> steps = UiModelMapper.stepRows(item, assignment.occurrenceKey(),
                assignment.start().toLocalDate(), data.stepCompletions());
        CalendarOccurrence preceding = entry.precedingCalendar();
        String precedingTitle = preceding == null ? null
                : preceding.title().orElse(hiddenCalendarTitle);
        return new FocusRow(item.id(), item.title(), item.durationMinutes(),
                assignment.start(), assignment.end(), steps, precedingTitle,
                item instanceof com.autosecretary.domain.Routine,
                item instanceof com.autosecretary.domain.Routine routine
                        && routine.nextDueDate().isBefore(today)
                        || item.deadlineAt() != null
                        && item.deadlineAt().toLocalDate().isBefore(today));
    }
}
