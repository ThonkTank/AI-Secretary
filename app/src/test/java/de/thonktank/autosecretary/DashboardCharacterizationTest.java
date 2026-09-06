package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.CalendarEventSnapshot;

import de.thonktank.autosecretary.ui.today.CompletedTodayView;

import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksAction;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeFixture;
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeHostView;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.CompletedTaskUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineItemUiModel;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.model.TaskId;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;

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
        assertNull(DashboardFixtures.emptyDashboard().focus);
    }

    @Test public void completedTaskIsPartitionedAwayFromFocusAndTimeline() {
        CompletedTaskUiModel done = DashboardFixtures.completedTodayTask();
        FocusTaskUiModel open = DashboardFixtures.overdueTask();
        TodayUiModel state = new TodayUiModel(new XpProgress(10), open,
                Collections.emptyList(), Collections.singletonList(done));

        assertSame(open, state.focus);
        assertSame(done, state.completedToday.get(0));
        assertTrue(state.timeline.isEmpty());
    }

    @Test public void fullFixtureCoversTheDashboardStatesFromTheHandoff() {
        TodayUiModel state = DashboardFixtures.fullDashboard();

        assertEquals(120, state.xpProgress.total);
        assertEquals(1, state.completedToday.size());
        assertEquals(3, state.timeline.size());
        assertTrue(state.timeline.stream().anyMatch(item -> item.task.overdue));
        assertTrue(state.timeline.stream().anyMatch(item -> item.task.actionTarget.routine));
        assertTrue(state.timeline.stream().anyMatch(item ->
                item.task.actionTarget.taskId.equals("ongoing")));
    }

    @Test public void taskActionsUseExplicitTargetMetadata() {
        assertFalse(DashboardFixtures.simpleTask().terminalCondition());
        assertFalse(DashboardFixtures.taskWithSteps().steps.isEmpty());
        assertTrue(DashboardFixtures.ongoingTask().terminalCondition());
    }

    @Test public void slotsHaveTheEstablishedDayOrder() {
        assertEquals(0, TaskSlot.MORNING.rank);
        assertEquals(1, TaskSlot.MIDDAY.rank);
        assertEquals(2, TaskSlot.EVENING.rank);
        assertEquals(3, TaskSlot.LATER.rank);
    }

    @Test public void allTasksDestinationMountsComposeAndPublishesTypedActions() {
        Context context = ApplicationProvider.getApplicationContext();
        LinearLayout shell = new LinearLayout(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        List<AllTasksAction> actions = new ArrayList<>();
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                action -> { }, action -> { }, "test", new RewardAnchorRegistry(), actions::add);
        AllTasksUiState state = AllTasksComposeFixture.state(true).toggleExpanded(
                AllTasksUiState.cardKey("morning", TaskSlot.MORNING)).toggleExpanded(
                AllTasksUiState.cardKey("bed", TaskSlot.MORNING));
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);

        renderer.render(TodayScreenStateFixtures.shell(NavigationDestination.ALL_TASKS, palette),
                TodayScreenStateFixtures.today(DashboardFixtures.fullDashboard()), state,
                options(palette));

        assertEquals(View.GONE, scroll.getVisibility());
        assertTrue(shell.getChildAt(1) instanceof AllTasksComposeHostView);
        AllTasksComposeHostView host = (AllTasksComposeHostView) shell.getChildAt(1);
        assertEquals(R.id.all_tasks_compose_host, host.getId());
        assertTrue(host.dispatchDropForTest(
                "step:morning|MORNING:morning-step-0", "task:bed|MORNING"));
        assertTrue(actions.get(0) instanceof AllTasksAction.StepMoved);
    }

    @Test public void runningFlowsLiveInAllTasksAndNoLongerConsumeTodaySpace() {
        Context context = ApplicationProvider.getApplicationContext();
        LinearLayout shell = new LinearLayout(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        List<de.thonktank.autosecretary.presentation.options.OptionsAction> optionActions =
                new ArrayList<>();
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                action -> { }, optionActions::add, "test", new RewardAnchorRegistry(),
                action -> { });
        FlowRunSummary flow = new FlowRunSummary("run", TaskId.of("laundry"), "Wäsche",
                "colors", "Buntwäsche", "take-down", "Abhängen",
                StepFlowRunState.WAITING_TIME, System.currentTimeMillis() + 60_000L,
                "sheet", 1L, 2, 4, null, Collections.emptyList(), true, 86_400_000L);
        TodayUiModel base = DashboardFixtures.fullDashboard();
        TodayUiModel withFlow = new TodayUiModel(base.xpProgress, base.focus, base.timeline,
                base.completedToday, Collections.singletonList(flow));
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT);

        renderer.render(TodayScreenStateFixtures.shell(NavigationDestination.ALL_TASKS, palette),
                TodayScreenStateFixtures.today(withFlow), AllTasksUiState.empty(),
                options(palette));

        AllTasksComposeHostView host = (AllTasksComposeHostView) shell.getChildAt(1);
        assertEquals(1, host.runningFlowCountForTest());
        host.openFlowRunsForTest();
        assertEquals(1, optionActions.size());

        renderer.render(TodayScreenStateFixtures.shell(NavigationDestination.TODAY, palette),
                TodayScreenStateFixtures.today(withFlow), AllTasksUiState.empty(),
                options(palette));
        assertFalse(containsViewType(content, FlowRunningStripView.class));
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
        TodayActionRecorder recorded = new TodayActionRecorder();
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                recorded, action -> { }, "test", new RewardAnchorRegistry(),
                action -> { });
        List<CalendarEventSnapshot> events = Collections.singletonList(
                new CalendarEventSnapshot("12:00", "Termin", 12 * 60));
        FocusTaskUiModel simple = DashboardFixtures.simpleTask();
        TodayUiModel dashboardState = new TodayUiModel(new XpProgress(10), simple,
                java.util.Arrays.asList(
                        TimelineItemUiModel.task(FocusTaskFixtures.timeline(
                                DashboardFixtures.recurringTask(), "", 2)),
                        TimelineItemUiModel.task(FocusTaskFixtures.timeline(
                                DashboardFixtures.ongoingTask(), "", 3))),
                Collections.singletonList(DashboardFixtures.completedTodayTask()));
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.AUTO);
        renderer.render(TodayScreenStateFixtures.shell(NavigationDestination.TODAY, palette),
                TodayScreenStateFixtures.today(TodayUiModel.compose(dashboardState, events)),
                AllTasksUiState.empty(), options(palette));

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
                recorded.lastToday(
                        de.thonktank.autosecretary.presentation.today.TodayAction.Kind
                                .UNDO_OCCURRENCE).id);
    }

    @Test public void completedHistoryFollowsTheEmptyStateWhenNothingIsOpen() {
        Context context = ApplicationProvider.getApplicationContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                action -> { }, action -> { }, "test", new RewardAnchorRegistry(),
                action -> { });
        TodayUiModel today = new TodayUiModel(new XpProgress(10), null,
                Collections.emptyList(),
                Collections.singletonList(DashboardFixtures.completedTodayTask()));
        DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.AUTO);

        renderer.render(TodayScreenStateFixtures.shell(NavigationDestination.TODAY, palette),
                TodayScreenStateFixtures.today(today.withCalendar(Collections.emptyList())),
                AllTasksUiState.empty(), options(palette));

        View empty = findDirectChild(content, EmptyStateView.class);
        CompletedTodayView completed = content.findViewById(R.id.dashboard_completed_today);
        assertEquals(View.VISIBLE, empty.getVisibility());
        assertEquals(View.VISIBLE, completed.getVisibility());
        assertTrue(content.indexOfChild(completed) > content.indexOfChild(empty));
    }

    private static de.thonktank.autosecretary.presentation.options.OptionsScreenState options(
            DayPalette palette) {
        return new de.thonktank.autosecretary.presentation.options.OptionsScreenState(palette,
                de.thonktank.autosecretary.data.preferences.UiThemeMode.AUTO,
                de.thonktank.autosecretary.data.preferences.FocusStepLimit.AUTO,
                60, CalendarPermissionStatus.GRANTED, CalendarUiState.empty(),
                de.thonktank.autosecretary.update.presentation.UpdateUiState.idle(),
                Collections.emptyList());
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

    private static boolean containsViewType(View view, Class<?> type) {
        if (type.isInstance(view)) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++)
            if (containsViewType(group.getChildAt(index), type)) return true;
        return false;
    }
}
