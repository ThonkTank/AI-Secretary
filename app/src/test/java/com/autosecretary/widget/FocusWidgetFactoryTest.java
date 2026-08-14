package com.autosecretary.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.TodayTimeline;
import com.autosecretary.application.TodayEntry;
import com.autosecretary.application.CalendarOccurrence;
import com.autosecretary.application.CalendarOccurrenceId;
import com.autosecretary.application.CalendarAvailability;
import com.autosecretary.application.CalendarStatus;
import com.autosecretary.application.CalendarParticipation;
import com.autosecretary.application.CalendarVisibility;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.Task;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class FocusWidgetFactoryTest {
    @Test
    public void widgetUsesSharedChronologicalTopThreeAcrossTasksAndCalendar() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 10, 0);
        Task task = new Task("10000000-0000-0000-0000-000000000001", "Aufgabe", 30,
                null, null, true, List.of(), now.minusDays(1), false,
                CompletionStats.empty(), 0);
        PlanAssignment assignment = new PlanAssignment(task, "TASK",
                now.plusHours(2), now.plusHours(2).plusMinutes(30));
        DashboardData dashboard = new DashboardData(List.of(assignment), List.of(task), List.of(
                occurrence(1, now.plusMinutes(30), now.plusHours(1), "Termin A"),
                occurrence(2, now.plusHours(1), now.plusHours(1).plusMinutes(30), "Termin B"),
                occurrence(3, now.plusHours(3), now.plusHours(4), "Termin C")),
                List.of(), false, List.of(), List.of(), List.of(), null);

        List<TodayEntry> entries =
                FocusWidgetFactory.orderedEntries(dashboard, now, 3);

        assertEquals(List.of("Termin A", "Termin B", "Aufgabe"),
                entries.stream().map(value -> value.title().orElse("privat")).toList());
        assertTrue(entries.get(0) instanceof TodayEntry.Calendar);
        assertTrue(entries.get(2) instanceof TodayEntry.Focus);
    }

    private static CalendarOccurrence occurrence(
            long id, LocalDateTime start, LocalDateTime end, String title) {
        Instant instant = start.toInstant(ZoneOffset.UTC);
        return new CalendarOccurrence(new CalendarOccurrenceId(1, id, instant), instant,
                end.toInstant(ZoneOffset.UTC), false, CalendarAvailability.BUSY,
                CalendarStatus.CONFIRMED, CalendarParticipation.ACCEPTED,
                CalendarVisibility.VISIBLE, Optional.of(title));
    }
}
