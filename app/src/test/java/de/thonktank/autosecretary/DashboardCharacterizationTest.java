package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import androidx.test.core.app.ApplicationProvider;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.TaskSlot;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DashboardCharacterizationTest {
    @Test public void emptyDashboardHasNoFocus() {
        assertNull(DashboardFixtures.emptyDashboard().firstOpen());
    }

    @Test public void completedTaskKeepsItsPositionButIsSkippedAsFocus() {
        TaskSnapshot done = DashboardFixtures.completedTodayTask();
        TaskSnapshot open = DashboardFixtures.overdueTask();
        TodayUiModel state = DashboardFixtures.today(10, java.util.Arrays.asList(done, open));

        assertSame(open, state.firstOpen());
        assertTrue(state.tasks.get(0).done);
    }

    @Test public void fullFixtureCoversTheDashboardStatesFromTheHandoff() {
        TodayUiModel state = DashboardFixtures.fullDashboard();

        assertEquals(120, state.xp);
        assertEquals(5, state.tasks.size());
        assertTrue(state.tasks.stream().anyMatch(task -> task.overdue));
        assertTrue(state.tasks.stream().anyMatch(TaskSnapshot::routine));
        assertTrue(state.tasks.stream().anyMatch(task -> task.ongoing));
        assertTrue(state.tasks.stream().anyMatch(task -> task.done));
    }

    @Test public void taskActionsAreDerivedFromTheCurrentReadModel() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        assertEquals("erledigen", DashboardFixtures.simpleTask().actionLabel(context));
        assertEquals("Rest erledigen", DashboardFixtures.taskWithSteps().actionLabel(context));
        assertEquals("Bedingung erfüllt", DashboardFixtures.ongoingTask().actionLabel(context));
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

    @Test public void timelineKeepsDoneItemsAssignsAfterAcrossCalendarAndCapsAtThree() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                new NoOpActions(), "test");
        List<CalendarEventSnapshot> events = Collections.singletonList(
                new CalendarEventSnapshot("12:00", "Termin", 12 * 60));
        TodayUiModel dashboardState = DashboardFixtures.today(10, java.util.Arrays.asList(
                DashboardFixtures.simpleTask(), DashboardFixtures.completedTodayTask(),
                DashboardFixtures.recurringTask(), DashboardFixtures.ongoingTask()));
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.AUTO);
        renderer.render(new DashboardUiState(NavigationDestination.TODAY,
                        TodayUiModel.compose(dashboardState, events),
                        CalendarUiState.from(new CalendarResult.Success(events)), palette,
                        CalendarPermissionStatus.GRANTED, false, Collections.emptySet(),
                        EditorUiState.closed()));

        LinearLayout timeline = content.findViewById(R.id.dashboard_timeline);
        List<String> text = new ArrayList<>();
        collectText(timeline, text);
        assertEquals(3, timeline.getChildCount());
        assertTrue(text.contains(context.getString(R.string.marker_done)));
        assertTrue(text.contains(context.getString(R.string.marker_after)));
    }

    private static void collectText(View view, List<String> result) {
        if (view instanceof TextView) result.add(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), result);
        }
    }

    private static final class NoOpActions extends FocusTestActions {
        @Override public void onAddTask() { }
        @Override public void onTaskAction(TimelineTaskUiModel task) { }
        @Override public void onTaskMenu(TimelineTaskUiModel task) { }
        @Override public void onTheme(UiThemeMode mode) { }
        @Override public void onFocusStepLimit(
                de.thonktank.autosecretary.data.preferences.FocusStepLimit limit) { }
        @Override public void onCalendarPermission() { }
        @Override public void onUpdates() { }
    }
}
