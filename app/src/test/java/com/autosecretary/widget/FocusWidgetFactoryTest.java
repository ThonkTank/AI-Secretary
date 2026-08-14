package com.autosecretary.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.TodayTimeline;
import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.Task;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

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
                new BusyInterval(now.plusMinutes(30), now.plusHours(1), "Termin A"),
                new BusyInterval(now.plusHours(1), now.plusHours(1).plusMinutes(30), "Termin B"),
                new BusyInterval(now.plusHours(3), now.plusHours(4), "Termin C")),
                List.of(), List.of(), List.of(), null);

        List<TodayTimeline.Entry> entries =
                FocusWidgetFactory.orderedEntries(dashboard, now, 3);

        assertEquals(List.of("Termin A", "Termin B", "Aufgabe"),
                entries.stream().map(TodayTimeline.Entry::title).toList());
        assertTrue(entries.get(0) instanceof TodayTimeline.Calendar);
        assertTrue(entries.get(2) instanceof TodayTimeline.Assignment);
    }
}
