package com.autosecretary.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.domain.FocusPlanner;
import com.autosecretary.domain.PlanningSettings;
import com.autosecretary.domain.WorkItem;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public final class PlanFocusCalendarResultTest {
    private static final Instant NOW = Instant.parse("2026-03-29T00:30:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    @Test public void permissionMissingIsDistinctFromAnAvailableEmptyCalendar() {
        DashboardData missing = execute(range -> new CalendarReadResult.PermissionMissing());
        DashboardData empty = execute(range -> new CalendarReadResult.Available(List.of()));

        assertTrue(missing.calendarPermissionMissing());
        assertFalse(empty.calendarPermissionMissing());
        assertTrue(missing.calendarOccurrences().isEmpty());
        assertTrue(empty.calendarOccurrences().isEmpty());
    }

    @Test public void calendarHorizonUsesInjectedBerlinBoundariesAcrossDst() {
        TimeRange[] captured = new TimeRange[1];
        execute(range -> {
            captured[0] = range;
            return new CalendarReadResult.Available(List.of());
        });

        assertEquals(Instant.parse("2026-03-28T23:00:00Z"),
                captured[0].startInclusive());
        assertEquals(Instant.parse("2026-04-04T22:00:00Z"),
                captured[0].endExclusive());
    }

    private static DashboardData execute(CalendarPort calendar) {
        TimeProvider time = new TimeProvider() {
            @Override public Instant now() { return NOW; }
            @Override public ZoneId zone() { return ZONE; }
        };
        return new PlanFocusUseCase(new EmptyRepository(), calendar,
                new PlanningSettingsRepository() {
                    @Override public PlanningSettings load() {
                        return PlanningSettings.defaults();
                    }
                    @Override public void save(PlanningSettings value) { }
                }, time, new FocusPlanner()).execute(3);
    }

    private static final class EmptyRepository implements WorkItemRepository {
        @Override public FocusSnapshot loadSnapshot() {
            return new FocusSnapshot(List.of(), List.of(), List.of());
        }
        @Override public WorkItem find(String id) { return null; }
        @Override public void save(WorkItem item) { }
        @Override public void delete(String id) { }
        @Override public void deleteAll(List<String> ids) { }
        @Override public WorkItem complete(String id, LocalDateTime at) { return null; }
        @Override public WorkItem setStepCompleted(
                String workItemId, String stepId, boolean completed, LocalDateTime at) {
            return null;
        }
        @Override public List<DayPlanDirective> directives(LocalDate day) { return List.of(); }
        @Override public void saveDirective(DayPlanDirective directive, String undoLabel) { }
        @Override public String latestUndoLabel() { return null; }
        @Override public boolean undoLatest(LocalDateTime at) { return false; }
        @Override public void applyChangeSet(
                List<BulkChange> changes, String label, LocalDateTime at) { }
    }
}
