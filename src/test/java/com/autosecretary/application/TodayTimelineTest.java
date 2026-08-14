package com.autosecretary.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.Task;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class TodayTimelineTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 10, 0);

    @Test
    public void keepsOnlyRemainingTodayEntriesAndInterleavesThemChronologically() {
        PlanAssignment ongoingTask = assignment("ongoing", "Laufende Aufgabe",
                NOW.minusMinutes(30), NOW.plusMinutes(30));
        PlanAssignment laterTask = assignment("later", "Späte Aufgabe",
                NOW.plusHours(2), NOW.plusHours(3));
        PlanAssignment tomorrowTask = assignment("tomorrow", "Morgen",
                NOW.plusDays(1), NOW.plusDays(1).plusMinutes(30));
        BusyInterval ongoingCalendar = new BusyInterval(
                NOW.minusHours(1), NOW.plusMinutes(10), "Laufender Termin");
        BusyInterval nextCalendar = new BusyInterval(
                NOW.plusHours(1), NOW.plusHours(1).plusMinutes(30), "Nächster Termin");
        BusyInterval pastCalendar = new BusyInterval(
                NOW.minusHours(2), NOW.minusHours(1), "Vergangen");
        BusyInterval tomorrowCalendar = new BusyInterval(
                NOW.plusDays(1), NOW.plusDays(1).plusHours(1), "Morgen");

        TodayTimeline timeline = TodayTimeline.from(dashboard(
                List.of(ongoingTask, laterTask, tomorrowTask),
                List.of(tomorrowCalendar, nextCalendar, pastCalendar, ongoingCalendar)), NOW);

        assertEquals(List.of("Laufender Termin", "Laufende Aufgabe",
                        "Nächster Termin", "Späte Aufgabe"),
                timeline.entries().stream().map(TodayTimeline.Entry::title).toList());
    }

    @Test
    public void fixedCalendarCommitmentWinsAnEqualStartTime() {
        LocalDateTime start = NOW.plusHours(1);
        PlanAssignment task = assignment("tie", "Aufgabe", start, start.plusMinutes(30));
        BusyInterval calendar = new BusyInterval(start, start.plusMinutes(45), "Termin");

        TodayTimeline timeline = TodayTimeline.from(
                dashboard(List.of(task), List.of(calendar)), NOW);

        assertTrue(timeline.entries().get(0) instanceof TodayTimeline.Calendar);
        assertTrue(timeline.entries().get(1) instanceof TodayTimeline.Assignment);
    }

    @Test
    public void includesTimedEventThatStartedBeforeMidnightAndIsStillRunning() {
        BusyInterval overnight = new BusyInterval(NOW.toLocalDate().minusDays(1).atTime(23, 30),
                NOW.toLocalDate().atTime(10, 30), "Nachtschicht");

        TodayTimeline timeline = TodayTimeline.from(
                dashboard(List.of(), List.of(overnight)), NOW);

        assertEquals(List.of("Nachtschicht"),
                timeline.entries().stream().map(TodayTimeline.Entry::title).toList());
    }

    private static PlanAssignment assignment(
            String id, String title, LocalDateTime start, LocalDateTime end) {
        String uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
        Task task = new Task(uuid, title, 30, null, null, true, List.of(),
                NOW.minusDays(1), false, CompletionStats.empty(), 0);
        return new PlanAssignment(task, "TASK", start, end);
    }

    private static DashboardData dashboard(
            List<PlanAssignment> focus, List<BusyInterval> calendar) {
        return new DashboardData(focus, List.of(), calendar, List.of(), List.of(), List.of(), null);
    }
}
