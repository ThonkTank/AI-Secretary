package com.autosecretary.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.Task;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TodayTimelineTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.of(2026, 8, 14, 10, 0);
    private static final Instant NOW = LOCAL_NOW.atZone(ZONE).toInstant();

    @Test
    public void keepsOnlyRemainingTodayEntriesAndInterleavesThemChronologically() {
        PlanAssignment ongoingTask = assignment("ongoing", "Laufende Aufgabe",
                LOCAL_NOW.minusMinutes(30), LOCAL_NOW.plusMinutes(30));
        PlanAssignment laterTask = assignment("later", "Späte Aufgabe",
                LOCAL_NOW.plusHours(2), LOCAL_NOW.plusHours(3));
        CalendarOccurrence ongoing = occurrence(1, "Laufender Termin",
                NOW.minusSeconds(3600), NOW.plusSeconds(600));
        CalendarOccurrence next = occurrence(2, "Nächster Termin",
                NOW.plusSeconds(3600), NOW.plusSeconds(5400));
        CalendarOccurrence past = occurrence(3, "Vergangen",
                NOW.minusSeconds(7200), NOW.minusSeconds(3600));

        TodayTimeline timeline = query(dashboard(
                List.of(ongoingTask, laterTask), List.of(next, past, ongoing)));

        assertEquals(List.of("Laufender Termin", "Laufende Aufgabe",
                        "Nächster Termin", "Späte Aufgabe"),
                timeline.entries().stream().map(value -> value.title().orElse("privat")).toList());
    }

    @Test
    public void fixedCalendarCommitmentWinsAnEqualStartTime() {
        LocalDateTime start = LOCAL_NOW.plusHours(1);
        TodayTimeline timeline = query(dashboard(
                List.of(assignment("tie", "Aufgabe", start, start.plusMinutes(30))),
                List.of(occurrence(1, "Termin", start.atZone(ZONE).toInstant(),
                        start.plusMinutes(45).atZone(ZONE).toInstant()))));

        assertTrue(timeline.entries().get(0) instanceof TodayEntry.Calendar);
        assertTrue(timeline.entries().get(1) instanceof TodayEntry.Focus);
    }

    @Test
    public void includesOvernightOccurrenceAndChoosesMaximumPrecedingEnd() {
        LocalDateTime taskStart = LOCAL_NOW.plusHours(2);
        CalendarOccurrence overnight = occurrence(1, "Nachtschicht",
                LOCAL_NOW.toLocalDate().minusDays(1).atTime(23, 30).atZone(ZONE).toInstant(),
                NOW.plusSeconds(1800));
        CalendarOccurrence early = occurrence(2, "Früh", NOW, NOW.plusSeconds(1800));
        CalendarOccurrence closest = occurrence(3, "Direkt davor", NOW.plusSeconds(600),
                taskStart.minusMinutes(5).atZone(ZONE).toInstant());
        TodayTimeline timeline = query(dashboard(List.of(assignment(
                "after", "Nachbereitung", taskStart, taskStart.plusMinutes(30))),
                List.of(closest, overnight, early)));

        TodayEntry.Focus focus = (TodayEntry.Focus) timeline.entries().stream()
                .filter(value -> value instanceof TodayEntry.Focus).findFirst().orElseThrow();
        assertSame(closest, focus.precedingCalendar());
        assertTrue(timeline.entries().stream().anyMatch(value -> value.title()
                .orElse("").equals("Nachtschicht")));
    }

    @Test
    public void stableIdentityDeduplicatesOneInstanceButKeepsEqualLookingEventsDistinct() {
        CalendarOccurrence first = occurrence(1, "Gleich", NOW.plusSeconds(3600),
                NOW.plusSeconds(5400));
        CalendarOccurrence second = occurrence(2, "Gleich", NOW.plusSeconds(3600),
                NOW.plusSeconds(5400));
        TodayTimeline timeline = query(dashboard(List.of(), List.of(first, first, second)));

        assertEquals(2, timeline.entries().size());
        assertEquals(first.start(), timeline.nextRefreshAt());
    }

    @Test
    public void todayAppliesCalendarPolicyBeforeRenderingRawOccurrences() {
        CalendarOccurrence busy = occurrence(1, "Bleibt", NOW.plusSeconds(600),
                NOW.plusSeconds(1200));
        CalendarOccurrence free = new CalendarOccurrence(
                new CalendarOccurrenceId(1, 2, NOW.plusSeconds(700)),
                NOW.plusSeconds(700), NOW.plusSeconds(1300), false,
                CalendarAvailability.FREE, CalendarStatus.CONFIRMED,
                CalendarParticipation.ACCEPTED, CalendarVisibility.VISIBLE,
                Optional.of("Frei"));
        CalendarOccurrence canceled = new CalendarOccurrence(
                new CalendarOccurrenceId(1, 3, NOW.plusSeconds(800)),
                NOW.plusSeconds(800), NOW.plusSeconds(1400), false,
                CalendarAvailability.BUSY, CalendarStatus.CANCELED,
                CalendarParticipation.ACCEPTED, CalendarVisibility.VISIBLE,
                Optional.of("Abgesagt"));
        CalendarOccurrence declined = new CalendarOccurrence(
                new CalendarOccurrenceId(1, 4, NOW.plusSeconds(900)),
                NOW.plusSeconds(900), NOW.plusSeconds(1500), false,
                CalendarAvailability.BUSY, CalendarStatus.CONFIRMED,
                CalendarParticipation.DECLINED, CalendarVisibility.VISIBLE,
                Optional.of("Abgelehnt"));
        CalendarOccurrence allDay = new CalendarOccurrence(
                new CalendarOccurrenceId(1, 5, NOW.plusSeconds(1000)),
                NOW.plusSeconds(1000), NOW.plusSeconds(1600), true,
                CalendarAvailability.BUSY, CalendarStatus.CONFIRMED,
                CalendarParticipation.ACCEPTED, CalendarVisibility.VISIBLE,
                Optional.of("Ganztägig"));

        TodayTimeline timeline = query(dashboard(
                List.of(), List.of(free, canceled, declined, allDay, busy)));

        assertEquals(List.of("Bleibt"), timeline.entries().stream()
                .map(value -> value.title().orElse("privat")).toList());
    }

    @Test
    public void nextDayBoundaryUsesInjectedZoneAcrossDstStart() {
        Instant beforeDst = Instant.parse("2026-03-28T23:30:00Z");
        TimeProvider time = fixed(beforeDst, ZONE);
        TodayTimeline timeline = new GetTodayTimeline(time).execute(dashboard(List.of(), List.of()));

        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), timeline.nextRefreshAt());
    }

    @Test
    public void nextDayBoundaryUsesInjectedZoneAcrossDstEnd() {
        Instant beforeOverlap = Instant.parse("2026-10-24T22:30:00Z");
        TodayTimeline timeline = new GetTodayTimeline(fixed(beforeOverlap, ZONE))
                .execute(dashboard(List.of(), List.of()));

        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), timeline.nextRefreshAt());
    }

    private static TodayTimeline query(DashboardData dashboard) {
        return new GetTodayTimeline(fixed(NOW, ZONE)).execute(dashboard);
    }

    private static TimeProvider fixed(Instant now, ZoneId zone) {
        return new TimeProvider() {
            @Override public Instant now() { return now; }
            @Override public ZoneId zone() { return zone; }
        };
    }

    private static PlanAssignment assignment(
            String id, String title, LocalDateTime start, LocalDateTime end) {
        String uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
        Task task = new Task(uuid, title, 30, null, null, true, List.of(),
                LOCAL_NOW.minusDays(1), false, CompletionStats.empty(), 0);
        return new PlanAssignment(task, "TASK", start, end);
    }

    private static CalendarOccurrence occurrence(
            long eventId, String title, Instant start, Instant end) {
        return new CalendarOccurrence(new CalendarOccurrenceId(1, eventId, start), start, end,
                false, CalendarAvailability.BUSY, CalendarStatus.CONFIRMED,
                CalendarParticipation.ACCEPTED, CalendarVisibility.VISIBLE,
                Optional.ofNullable(title));
    }

    private static DashboardData dashboard(
            List<PlanAssignment> focus, List<CalendarOccurrence> calendar) {
        List<BusyInterval> busy = new CalendarPolicy().busyIntervals(calendar, ZONE);
        return new DashboardData(focus, List.of(), calendar, busy, false,
                List.of(), List.of(), List.of(), null);
    }
}
