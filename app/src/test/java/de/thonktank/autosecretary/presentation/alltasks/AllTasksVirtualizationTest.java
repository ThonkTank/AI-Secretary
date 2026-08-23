package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.*;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import static android.view.View.MeasureSpec.EXACTLY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AllTasksVirtualizationTest {
    @Test public void contentRebindReusesTheExistingHolderChildHierarchy() {
        Context context = ApplicationProvider.getApplicationContext();
        AllTasksView view = new AllTasksView(context, new Recorder());
        AllTasksUiState state = AllTasksUiState.from(catalog(2),
                AllTasksPresentationState.defaults());
        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        shadowOf(Looper.getMainLooper()).idle();
        layout(view, 412, 900);

        int position = view.positionForTest(AllTasksRow.Kind.TASK_HEADER, "task-0|MORNING");
        View card = view.hierarchyAnchorForTest(position);
        assertNotNull(card);
        View header = ((ViewGroup) card).getChildAt(0);
        View title = ((ViewGroup) ((ViewGroup) header).getChildAt(0)).getChildAt(0);

        view.bind(state.withQuery("Aufgabe"),
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        shadowOf(Looper.getMainLooper()).idle();
        layout(view, 412, 900);

        View reboundCard = view.hierarchyAnchorForTest(position);
        assertSame(card, reboundCard);
        assertSame(header, ((ViewGroup) reboundCard).getChildAt(0));
        assertSame(title, ((ViewGroup) ((ViewGroup) ((ViewGroup) reboundCard)
                .getChildAt(0)).getChildAt(0)).getChildAt(0));
    }

    @Test public void longCatalogAttachesOnlyVisibleRecyclerRowsAndKeepsStableIds() {
        Context context = ApplicationProvider.getApplicationContext();
        AllTasksView view = new AllTasksView(context, new Recorder());
        AllTasksUiState state = AllTasksUiState.from(catalog(120),
                AllTasksPresentationState.defaults());
        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        layout(view, 412, 900);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(120, view.rowCountForTest());
        assertTrue(view.recyclerForTest().getChildCount() < view.rowCountForTest());
        long firstId = view.rowIdForTest(0);
        RecyclerView.ViewHolder first = view.recyclerForTest().findViewHolderForAdapterPosition(0);
        assertEquals(firstId, first.getItemId());

        view.bind(state.withQuery("Aufgabe"),
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        shadowOf(Looper.getMainLooper()).idle();
        layout(view, 412, 900);

        assertEquals(firstId, view.rowIdForTest(0));
        RecyclerView.ViewHolder rebound =
                view.recyclerForTest().findViewHolderForAdapterPosition(0);
        assertEquals(firstId, rebound.getItemId());
    }

    @Test public void flatRowsMakeCrossTaskDragAndAccessibilityReorderUnambiguous() {
        Context context = ApplicationProvider.getApplicationContext();
        Recorder recorder = new Recorder();
        AllTasksView view = new AllTasksView(context, recorder);
        AllTasksUiState state = AllTasksUiState.from(catalog(2),
                AllTasksPresentationState.defaults())
                .toggleExpanded(AllTasksUiState.cardKey("task-0", TaskSlot.MORNING))
                .toggleExpanded(AllTasksUiState.cardKey("task-1", TaskSlot.MORNING));
        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        shadowOf(Looper.getMainLooper()).idle();

        int source = view.positionForTest(AllTasksRow.Kind.STEP, "step-0-a");
        int target = view.positionForTest(AllTasksRow.Kind.STEP_TARGET,
                "task-1|MORNING:end");
        assertTrue(view.dragForTest(source, target));
        assertEquals("step-0-a|task-1|null", recorder.stepMove);

        int secondStep = view.positionForTest(AllTasksRow.Kind.STEP, "step-0-b");
        assertTrue(view.accessibilityActionForTest(secondStep, R.id.action_step_up));
        assertEquals("step-0-b|task-0|step-0-a", recorder.stepMove);
        assertTrue(view.accessibilityActionForTest(source, R.id.action_step_next_task));
        assertEquals("step-0-a|task-1|null", recorder.stepMove);
        assertTrue(view.accessibilityActionForTest(source, R.id.action_step_select_swap));
        assertTrue(view.accessibilityActionForTest(secondStep,
                R.id.action_step_swap_selected));
        assertEquals("step-0-a|step-0-b", recorder.stepSwap);
    }

    @Test public void archivedRowsExposeNeitherDragTargetsNorOrganizationActions() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskCatalog activeCatalog = catalog(1);
        TaskCatalog.Item item = activeCatalog.items.get(0);
        Task archived = item.task.withOccurrenceState(true, item.task.nextDueOn,
                item.task.lastScheduledOn, item.task.lastCompletedOn,
                item.task.hasCompletedOccurrence);
        TaskCatalog archivedCatalog = new TaskCatalog(Collections.singletonList(
                new TaskCatalog.Item(archived, item.steps, item.schedule)));
        AllTasksUiState state = AllTasksUiState.from(archivedCatalog,
                        AllTasksPresentationState.defaults())
                .withStatus(AllTasksUiState.Status.ARCHIVED)
                .toggleExpanded(AllTasksUiState.cardKey(archived.id.value,
                        item.schedule.get(0).slot));
        AllTasksView view = new AllTasksView(context, new Recorder());
        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        shadowOf(Looper.getMainLooper()).idle();

        List<AllTasksRow> rows = AllTasksRow.project(state);
        assertFalse(rows.stream().anyMatch(value -> value.kind == AllTasksRow.Kind.STEP_TARGET));
        int step = view.positionForTest(AllTasksRow.Kind.STEP, "step-0-a");
        assertFalse(view.accessibilityActionForTest(step, R.id.action_step_up));
        assertFalse(view.dragForTest(step, 0));
    }

    @Test public void stepInsertionTargetsOccupySpaceOnlyDuringDrag() {
        Context context = ApplicationProvider.getApplicationContext();
        AllTasksView view = new AllTasksView(context, new Recorder());
        AllTasksUiState state = AllTasksUiState.from(catalog(1),
                AllTasksPresentationState.defaults())
                .toggleExpanded(AllTasksUiState.cardKey("task-0", TaskSlot.MORNING));
        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        layout(view, 412, 1_000);
        shadowOf(Looper.getMainLooper()).idle();
        int target = view.positionForTest(AllTasksRow.Kind.STEP_TARGET,
                "task-0|MORNING:step-0-a");
        RecyclerView.ViewHolder hidden = view.recyclerForTest()
                .findViewHolderForAdapterPosition(target);
        assertTrue(hidden == null || hidden.itemView.getMeasuredHeight() == 0);

        view.setDragActiveForTest(true);
        shadowOf(Looper.getMainLooper()).idle();
        layout(view, 412, 1_000);
        RecyclerView.ViewHolder shown = view.recyclerForTest()
                .findViewHolderForAdapterPosition(target);

        assertTrue(shown != null && shown.itemView.getMeasuredHeight() > 0);
    }

    @Test public void emptyProjectionExplainsSearchFilterAndStatusSeparately() {
        AllTasksUiState base = AllTasksUiState.from(catalog(1),
                AllTasksPresentationState.defaults());
        assertEquals(AllTasksRow.EmptyReason.SEARCH,
                AllTasksRow.project(base.withQuery("unauffindbar")).get(0).emptyReason);
        assertEquals(AllTasksRow.EmptyReason.FILTERS,
                AllTasksRow.project(base.withSlots(Collections.singleton(TaskSlot.LATER)))
                        .get(0).emptyReason);
        assertEquals(AllTasksRow.EmptyReason.STATUS,
                AllTasksRow.project(base.withStatus(AllTasksUiState.Status.ARCHIVED))
                        .get(0).emptyReason);
    }

    @Test public void searchPublishesEveryIncrementImmediately() {
        Context context = ApplicationProvider.getApplicationContext();
        Recorder recorder = new Recorder();
        AllTasksView view = new AllTasksView(context, recorder);
        view.bind(AllTasksUiState.from(catalog(2), AllTasksPresentationState.defaults()),
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));

        view.searchForTest().setText("A");
        view.searchForTest().setText("Au");
        assertEquals("Au", recorder.query);
        assertEquals(2, recorder.queryCount);
    }

    @Test public void managementListFitsRequiredWidthsAndLargeFontWithoutRowOverflow() {
        int[] widths = {320, 412, 600};
        float[] scales = {1f, 1.3f, 2f};
        Context base = ApplicationProvider.getApplicationContext();
        for (int width : widths) for (float scale : scales) {
            Configuration configuration = new Configuration(base.getResources().getConfiguration());
            configuration.fontScale = scale;
            configuration.screenWidthDp = width;
            Context context = base.createConfigurationContext(configuration);
            AllTasksView view = new AllTasksView(context, new Recorder());
            AllTasksUiState state = AllTasksUiState.from(catalog(4),
                    AllTasksPresentationState.defaults())
                    .toggleExpanded(AllTasksUiState.cardKey("task-0", TaskSlot.MORNING));
            view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
            shadowOf(Looper.getMainLooper()).idle();
            layout(view, width, 1_000);
            RecyclerView recycler = view.recyclerForTest();
            assertTrue(width + "dp/" + scale, recycler.getMeasuredWidth() > 0);
            for (int index = 0; index < recycler.getChildCount(); index++) {
                View child = recycler.getChildAt(index);
                assertTrue(width + "dp/" + scale,
                        child.getLeft() >= 0 && child.getRight() <= recycler.getWidth());
                RecyclerView.ViewHolder holder = recycler.getChildViewHolder(child);
                AllTasksRow row = view.adapterRowForTest(
                        holder.getBindingAdapterPosition());
                assertTrue(width + "dp/" + scale,
                        child.getMeasuredHeight() > 0
                                || row.kind == AllTasksRow.Kind.STEP_TARGET);
            }
        }
    }

    @Test public void keyboardFocusRunsFromSearchAndFiltersIntoVisibleListRows() {
        Context context = ApplicationProvider.getApplicationContext();
        AllTasksView view = new AllTasksView(context, new Recorder());
        AllTasksUiState state = AllTasksUiState.from(catalog(2),
                AllTasksPresentationState.defaults())
                .toggleExpanded(AllTasksUiState.cardKey("task-0", TaskSlot.MORNING));
        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        shadowOf(Looper.getMainLooper()).idle();
        layout(view, 412, 1_000);
        ArrayList<View> focusables = new ArrayList<>();
        view.addFocusables(focusables, View.FOCUS_FORWARD);
        int searchIndex = focusables.indexOf(view.searchForTest());
        int firstListFocus = firstDescendantIndex(focusables, view.recyclerForTest());

        assertTrue(searchIndex >= 0);
        assertTrue(firstListFocus > searchIndex);
    }

    @Test public void interactiveRowsMeetMinimumHeightAndIconTargetsAreSquare() {
        Context context = ApplicationProvider.getApplicationContext();
        AllTasksView view = new AllTasksView(context, new Recorder());
        AllTasksUiState state = AllTasksUiState.from(catalog(2),
                AllTasksPresentationState.defaults())
                .toggleExpanded(AllTasksUiState.cardKey("task-0", TaskSlot.MORNING));
        view.bind(state, DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT));
        shadowOf(Looper.getMainLooper()).idle();
        layout(view, 412, 1_000);
        shadowOf(Looper.getMainLooper()).idle();

        List<View> targets = new ArrayList<>();
        collectClickable(view, targets);
        int minimum = Math.round(44 * context.getResources().getDisplayMetrics().density);
        assertTrue("Expected search, filters, result actions and card actions", targets.size() >= 8);
        for (View target : targets) {
            String label = target.getClass().getSimpleName() + " "
                    + String.valueOf(target.getContentDescription());
            assertTrue(label + " width=" + target.getWidth(), target.getWidth() > 0);
            assertTrue(label + " height=" + target.getHeight(), target.getHeight() >= minimum);
            if (target instanceof ImageButton) {
                boolean stepHandle = context.getString(R.string.all_drag_step)
                        .contentEquals(target.getContentDescription());
                int iconMinimum = Math.round((stepHandle ? 44 : 48)
                        * context.getResources().getDisplayMetrics().density);
                assertTrue(label + " icon width=" + target.getWidth(),
                        target.getWidth() >= iconMinimum);
                assertTrue(label + " icon height=" + target.getHeight(),
                        target.getHeight() >= iconMinimum);
            }
        }
    }

    @Test public void rendererMountsManagementRecyclerBesideNotInsideDashboardScroll() {
        Context context = ApplicationProvider.getApplicationContext();
        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(new View(context), new LinearLayout.LayoutParams(-1, 80));
        DashboardRenderer renderer = new DashboardRenderer(context, scroll, content,
                event -> { }, action -> { }, "test", new RewardAnchorRegistry(), new Recorder());
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT);

        renderer.render(new DashboardUiState(NavigationDestination.ALL_TASKS,
                        TodayUiModel.empty(), CalendarUiState.empty(), palette,
                        CalendarPermissionStatus.GRANTED, false, Collections.emptySet(),
                        EditorUiState.closed()),
                AllTasksUiState.from(catalog(120), AllTasksPresentationState.defaults()));

        assertEquals(View.GONE, scroll.getVisibility());
        AllTasksView management = null;
        for (int index = 0; index < shell.getChildCount(); index++)
            if (shell.getChildAt(index) instanceof AllTasksView)
                management = (AllTasksView) shell.getChildAt(index);
        assertTrue(management != null);
        assertFalse(isDescendantOf(management.recyclerForTest(), scroll));
    }

    private static TaskCatalog catalog(int count) {
        List<TaskCatalog.Item> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Task task = Task.restore(TaskId.of("task-" + index), "Aufgabe " + index,
                    Recurrence.DAILY, 1, 0, false, "", false, false,
                    LocalDate.of(2026, 8, 21), null, null,
                    LocalDate.of(2026, 8, 21), 1_024L + index, false,
                    null, TaskBoundKind.FOREVER, null, null, null, null, "");
            List<TaskStepTemplate> steps = Arrays.asList(
                    new TaskStepTemplate("step-" + index + "-a", task.id, 0, "Erster"),
                    new TaskStepTemplate("step-" + index + "-b", task.id, 1, "Zweiter"));
            items.add(new TaskCatalog.Item(task, steps, Collections.singletonList(
                    new TaskScheduleEntry("schedule-" + index, task.id,
                            TaskSlot.MORNING, 1_024L + index))));
        }
        return new TaskCatalog(items);
    }

    private static void layout(View view, int widthDp, int heightDp) {
        float density = view.getResources().getDisplayMetrics().density;
        int width = Math.round(widthDp * density);
        int height = Math.round(heightDp * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static int firstDescendantIndex(List<View> values, ViewGroup parent) {
        for (int index = 0; index < values.size(); index++) {
            View current = values.get(index);
            if (current == parent) return index;
            View ancestor = current;
            while (ancestor.getParent() instanceof View) {
                ancestor = (View) ancestor.getParent();
                if (ancestor == parent) return index;
            }
        }
        return -1;
    }

    private static boolean isDescendantOf(View child, ViewGroup parent) {
        View current = child;
        while (current.getParent() instanceof View) {
            current = (View) current.getParent();
            if (current == parent) return true;
        }
        return false;
    }

    private static void collectClickable(View view, List<View> result) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view.isClickable() && view.isEnabled()) result.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++)
            collectClickable(group.getChildAt(index), result);
    }

    private static final class Recorder implements AllTasksView.Listener {
        String stepMove;
        String stepSwap;
        String query;
        int queryCount;
        @Override public void onQuery(String value) { query = value; queryCount++; }
        @Override public void onMoveStep(String stepId, String taskId, String beforeStepId) {
            stepMove = stepId + '|' + taskId + '|' + beforeStepId;
        }
        @Override public void onSwapSteps(String stepId, String targetStepId) {
            stepSwap = stepId + '|' + targetStepId;
        }
    }
}
