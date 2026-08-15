package com.autosecretary.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.GetTodayTimeline;
import com.autosecretary.application.LocationPort;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.application.WorkItemRepository;
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
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
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

        TimeProvider time = new TimeProvider() {
            @Override public Instant now() { return now.toInstant(ZoneOffset.UTC); }
            @Override public java.time.ZoneId zone() { return ZoneOffset.UTC; }
        };
        WidgetDependencies dependencies = new TestDependencies(dashboard, time);
        FocusWidgetFactory factory = new FocusWidgetFactory(
                ApplicationProvider.getApplicationContext(), dependencies);
        factory.onDataSetChanged();

        assertEquals(3, factory.getCount());
        assertEquals(("1:1:" + now.plusMinutes(30).toInstant(ZoneOffset.UTC)).hashCode(),
                factory.getItemId(0));
        assertEquals(("1:2:" + now.plusHours(1).toInstant(ZoneOffset.UTC)).hashCode(),
                factory.getItemId(1));
        assertEquals(task.id().hashCode(), factory.getItemId(2));
        assertNotNull(factory.getViewAt(0));
        assertNotNull(factory.getViewAt(2));
    }

    private static CalendarOccurrence occurrence(
            long id, LocalDateTime start, LocalDateTime end, String title) {
        Instant instant = start.toInstant(ZoneOffset.UTC);
        return new CalendarOccurrence(new CalendarOccurrenceId(1, id, instant), instant,
                end.toInstant(ZoneOffset.UTC), false, CalendarAvailability.BUSY,
                CalendarStatus.CONFIRMED, CalendarParticipation.ACCEPTED,
                CalendarVisibility.VISIBLE, Optional.of(title));
    }

    private record TestDependencies(
            DashboardData dashboard, TimeProvider time) implements WidgetDependencies {
        @Override public DashboardData loadDashboard() { return dashboard; }
        @Override public com.autosecretary.application.TodayTimeline today(
                DashboardData value) {
            return new GetTodayTimeline(time).execute(value);
        }
        @Override public WorkItemRepository workItems() { throw new UnsupportedOperationException(); }
        @Override public MoveWorkItemUseCase moveWorkItem() {
            throw new UnsupportedOperationException();
        }
        @Override public LocationPort location() {
            return new LocationPort() {
                @Override public Position lastKnown() { return null; }
                @Override public void start(java.util.function.Consumer<Position> listener) { }
                @Override public void stop() { }
            };
        }
        @Override public void executeDatabase(Runnable action) { action.run(); }
        @Override public void refreshWidgets() { }
    }
}
