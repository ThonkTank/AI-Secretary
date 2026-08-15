package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.TaskSlot;

public final class DashboardCharacterizationTest {
    @Test public void emptyDashboardHasNoFocus() {
        assertNull(DashboardFixtures.emptyDashboard().firstOpen());
    }

    @Test public void completedTaskKeepsItsPositionButIsSkippedAsFocus() {
        TaskSnapshot done = DashboardFixtures.completedTodayTask();
        TaskSnapshot open = DashboardFixtures.overdueTask();
        DashboardState state = new DashboardState(10, java.util.Arrays.asList(done, open));

        assertSame(open, state.firstOpen());
        assertTrue(state.tasks.get(0).done);
    }

    @Test public void fullFixtureCoversTheDashboardStatesFromTheHandoff() {
        DashboardState state = DashboardFixtures.fullDashboard();

        assertEquals(120, state.xp);
        assertEquals(5, state.tasks.size());
        assertTrue(state.tasks.stream().anyMatch(task -> task.overdue));
        assertTrue(state.tasks.stream().anyMatch(TaskSnapshot::routine));
        assertTrue(state.tasks.stream().anyMatch(task -> task.ongoing));
        assertTrue(state.tasks.stream().anyMatch(task -> task.done));
    }

    @Test public void taskActionsAreDerivedFromTheCurrentReadModel() {
        assertEquals("erledigen", DashboardFixtures.simpleTask().actionLabel());
        assertEquals("Rest erledigen", DashboardFixtures.taskWithSteps().actionLabel());
        assertEquals("Bedingung erfüllt", DashboardFixtures.ongoingTask().actionLabel());
    }

    @Test public void slotsHaveTheEstablishedDayOrder() {
        assertEquals(0, TaskSlot.MORNING.rank);
        assertEquals(1, TaskSlot.MIDDAY.rank);
        assertEquals(2, TaskSlot.EVENING.rank);
        assertEquals(3, TaskSlot.LATER.rank);
    }

    @Test public void calendarFixtureKeepsAllDayBeforeTimedEvents() {
        java.util.List<CalendarEventSnapshot> events = DashboardFixtures.calendarEvents();

        assertEquals("ganztägig", events.get(0).time);
        assertEquals(0, events.get(0).minuteOfDay);
        assertEquals(10 * 60 + 15, events.get(1).minuteOfDay);
        assertFalse(events.get(1).title.isEmpty());
    }
}
