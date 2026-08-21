package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksView;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

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
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.thonktank.autosecretary.calendar.CalendarResult;

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

    @Test public void timelineExcludesDoneItemsAssignsAfterAcrossCalendarAndCapsAtThree() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        DashboardEventRecorder recorded = new DashboardEventRecorder();
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                recorded, "test", new RewardAnchorRegistry(), new AllTasksView.Listener() { });
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
                        EditorUiState.closed()), AllTasksUiState.empty());

        LinearLayout timeline = content.findViewById(R.id.dashboard_timeline);
        List<String> text = new ArrayList<>();
        collectText(timeline, text);
        assertEquals(3, timeline.getChildCount());
        assertFalse(text.contains(context.getString(R.string.marker_done)));
        assertTrue(text.contains(context.getString(R.string.marker_after)));
        CompletedTodayView completed = content.findViewById(R.id.dashboard_completed_today);
        assertEquals(View.VISIBLE, completed.getVisibility());
        assertEquals(View.GONE, completed.getChildAt(1).getVisibility());
        assertEquals("Heute erledigt (1)",
                ((TextView) completed.getChildAt(0)).getText().toString());
        assertTrue(completed.getChildAt(0).performClick());
        LinearLayout completedRows = (LinearLayout) completed.getChildAt(1);
        assertEquals(View.VISIBLE, completedRows.getVisibility());
        LinearLayout completedRow = (LinearLayout) completedRows.getChildAt(0);
        View undo = completedRow.getChildAt(2);
        assertEquals("Rückgängig", ((TextView) undo).getText().toString());
        AccessibilityNodeInfo undoInfo = undo.createAccessibilityNodeInfo();
        assertEquals(android.widget.Button.class.getName(), undoInfo.getClassName());
        assertTrue(undo.getContentDescription().toString()
                .contains(DashboardFixtures.completedTodayTask().title));
        assertTrue(undo.getContentDescription().toString().contains("10 XP"));
        undoInfo.recycle();
        assertTrue(undo.performClick());
        assertEquals(DashboardFixtures.completedTodayTask().occurrenceId,
                recorded.last(DashboardEvent.UndoCompleted.class).occurrenceId);
    }

    @Test public void completedHistoryFollowsTheEmptyStateWhenNothingIsOpen() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                event -> { }, "test", new RewardAnchorRegistry(), new AllTasksView.Listener() { });
        TodayUiModel today = DashboardFixtures.today(10,
                Collections.singletonList(DashboardFixtures.completedTodayTask()));
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.AUTO);

        renderer.render(new DashboardUiState(NavigationDestination.TODAY,
                        today.withCalendar(Collections.emptyList()), CalendarUiState.empty(),
                        palette, CalendarPermissionStatus.GRANTED, false,
                        Collections.emptySet(), EditorUiState.closed()),
                AllTasksUiState.empty());

        View empty = findDirectChild(content, EmptyStateView.class);
        CompletedTodayView completed = content.findViewById(R.id.dashboard_completed_today);
        assertEquals(View.VISIBLE, empty.getVisibility());
        assertEquals(View.VISIBLE, completed.getVisibility());
        assertTrue(content.indexOfChild(completed) > content.indexOfChild(empty));
    }

    private static View findDirectChild(ViewGroup parent, Class<? extends View> type) {
        for (int index = 0; index < parent.getChildCount(); index++)
            if (type.isInstance(parent.getChildAt(index))) return parent.getChildAt(index);
        return null;
    }

    private static void collectText(View view, List<String> result) {
        if (view instanceof TextView) result.add(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), result);
        }
    }
}
