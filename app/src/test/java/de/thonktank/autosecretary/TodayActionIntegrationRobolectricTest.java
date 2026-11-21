package de.thonktank.autosecretary;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.calendar.*;
import de.thonktank.autosecretary.data.observable.*;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.TodayQueue;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.*;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.presentation.shell.AppShellScreenState;
import de.thonktank.autosecretary.presentation.today.*;
import de.thonktank.autosecretary.testing.StepTestFixtures;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.*;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Actual renderer -> ViewModel -> dispatcher -> use cases -> file-backed Room.
 * Touch geometry is covered by TodayInteractionInstrumentationTest. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class TodayActionIntegrationRobolectricTest {
    @Rule public final InstantTaskExecutorRule instantExecutors = new InstantTaskExecutorRule();
    private static final String DATABASE = "today-action-integration";
    private final Clock clock = new Clock() {
        public LocalDate today() { return LocalDate.of(2026, 9, 12); }
        public LocalTime time() { return LocalTime.NOON; }
    };
    private final AppLogger logger = new AppLogger() {
        public void info(String tag, String message) { }
        public void error(String tag, String message, Throwable error) {
            throw new AssertionError(tag + ": " + message, error);
        }
    };
    private Context context;
    private AppDatabase database;
    private ApplicationUseCaseComposition useCases;
    private RoomRepositoryFixture repository;
    private TodayViewModel viewModel;
    private PresentationInvalidationSource invalidations;
    private DashboardRenderer renderer;
    private LinearLayout content;
    private int sequence;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE);
        context.deleteSharedPreferences("forest_ui");
        openDatabase();
    }

    @After public void tearDown() {
        closeDatabase();
        context.deleteDatabase(DATABASE);
        context.deleteSharedPreferences("forest_ui");
    }

    @Test public void openingDetailLeavesQueueIntactAcrossDatabaseReopen() {
        add("A", TaskSlot.MORNING);
        add("B", TaskSlot.MIDDAY);
        add("C", TaskSlot.EVENING);
        mount();
        assertOrder("A", "B", "C");
        Map<String, List<String>> untouched = domainSnapshot();

        title("C");

        assertEquals("C", viewModel.state().getValue().today().focus.title());
        assertOrder("A", "B", "C");
        assertEquals(untouched, domainSnapshot());
        reopen();
        assertNull(viewModel.state().getValue().today().focus);
        assertOrder("A", "B", "C");
        assertEquals(untouched, domainSnapshot());
    }

    @Test public void laterThroughRendererMovesToSectionEndAndUsesTheNewFocus() {
        add("A", TaskSlot.MORNING);
        add("B", TaskSlot.MORNING);
        add("C", TaskSlot.MIDDAY);
        mount();
        Map<String, List<String>> untouched = domainSnapshot();

        title("A");
        later();
        assertOrder("B", "A", "C");
        title("B");
        later();
        assertOrder("A", "B", "C");
        assertEquals(TaskSlot.MORNING, queue().get(0).slot);
        assertEquals(TaskSlot.MORNING, queue().get(1).slot);
        assertEquals(untouched, domainSnapshot());
    }

    @Test public void sectionEndFocusMovesBehindExistingNextSectionAndPersists() {
        add("A", TaskSlot.MORNING);
        add("B", TaskSlot.MIDDAY);
        mount();
        Map<String, List<String>> untouched = domainSnapshot();
        title("A");
        later();
        assertOrder("B", "A");
        assertEquals(TaskSlot.MIDDAY, queue().get(1).slot);
        reopen();
        assertOrder("B", "A");
        assertEquals(TaskSlot.MIDDAY, queue().get(1).slot);
        assertEquals(untouched, domainSnapshot());
    }

    @Test public void singleFocusLaterTraversesEmptySectionsAndPersistsHiddenState() {
        add("Allein", TaskSlot.MORNING);
        mount();
        Map<String, List<String>> untouched = domainSnapshot();
        title("Allein");
        for (TaskSlot expected : Arrays.asList(TaskSlot.MIDDAY, TaskSlot.EVENING, TaskSlot.LATER)) {
            later();
            assertEquals(expected, queue().get(0).slot);
            assertEquals("Allein", viewModel.state().getValue().today().focus.title());
        }
        later();
        assertTrue(queue().isEmpty());
        assertNull(viewModel.state().getValue().today().focus);
        reopen();
        assertTrue(queue().isEmpty());
        assertNull(viewModel.state().getValue().today().focus);
        assertEquals(untouched, domainSnapshot());
    }

    @Test public void mixedFlowAndTaskSteeringPreservesHistoryCandidatesResourcesAndRewards() {
        TaskId completed = add("Verlauf", TaskSlot.MORNING);
        useCases.today.materializeDue.execute();
        String occurrence = repository.today.openOccurrences().stream()
                .filter(item -> item.taskId.equals(completed)).findFirst().orElseThrow().id;
        useCases.today.complete.execute(occurrence);
        add("Aufgabe", TaskSlot.MORNING);
        List<TaskStepDefinition> steps = Arrays.asList(
                StepTestFixtures.definition("start", 0, "Waschen", 0, 0,
                        StepAmount.duration(60), "", StepActivationKind.SCHEDULED),
                StepTestFixtures.definition("end", 1, "Aufhängen", 0, 0,
                        StepAmount.none(), "", StepActivationKind.FOLLOW_UP));
        TaskId flow = useCases.catalog.create.execute(definition("Wäsche", TaskSlot.MORNING, steps));
        useCases.flows.saveGraph.execute(new FlowGraphEdit(flow, "Wäsche",
                new FlowTileGraph(Arrays.asList("start", "end"),
                        Collections.singletonList(new FlowTileGraph.Link("start", "end"))),
                steps, new LinkedHashMap<>(Map.of("start", FlowDelayPolicy.fixed(60_000), "end", FlowDelayPolicy.fixed(0))),
                Collections.singletonList(new FlowGraphEdit.Resource("washer", null, "Maschine", 1, true)),
                Collections.singletonList(new FlowGraphEdit.Binding(new FlowGraphEdit.Lease(
                        "lease", null, "washer", "start", "end", 1), false))));
        mount();
        assertOrder("Aufgabe", "Wäsche");
        Map<String, List<String>> untouched = domainSnapshot();
        assertFalse(untouched.get("flow_candidates").isEmpty());
        assertFalse(untouched.get("capacity_resources").isEmpty());
        assertFalse(untouched.get("reward_bookings").isEmpty());
        assertEquals(1, viewModel.state().getValue().today().completedToday.size());

        title("Wäsche");
        assertOrder("Aufgabe", "Wäsche");
        viewModel.dispatch(TodayAction.showOverview());
        render();
        hide("Wäsche");
        assertOrder("Aufgabe");
        reopen();
        assertOrder("Aufgabe");
        restore("Wäsche");
        assertOrder("Aufgabe", "Wäsche");
        assertEquals(untouched, domainSnapshot());
        assertEquals(1, viewModel.state().getValue().today().completedToday.size());
    }

    @Test public void hideSurvivesReopenAndExpiresOnNextDayWithoutChangingTheTask() {
        add("A", TaskSlot.MORNING);
        add("B", TaskSlot.EVENING);
        mount();
        Map<String, List<String>> unchanged = domainSnapshot();
        hide("A");
        assertOrder("B");
        reopen();
        assertOrder("B");
        java.time.LocalDate tomorrow = clock.today().plusDays(1);
        assertEquals(2, TodayQueue.visible(useCases.today.loadDashboard.execute(tomorrow), tomorrow).size());
        restore("A");
        assertOrder("A", "B");
        assertEquals(unchanged, domainSnapshot());
    }

    @Test public void aLongTaskKeepsOpenStepsAndASeparateCollapsedDoneGroup() {
        List<TaskStepDefinition> steps = new ArrayList<>();
        for (int i = 1; i <= 14; i++)
            steps.add(StepTestFixtures.definition(null, i - 1, "Schritt " + i, 0, 0,
                    StepAmount.none(), "", StepActivationKind.SCHEDULED));
        TaskId id = useCases.catalog.create.execute(definition("Lange Aufgabe", TaskSlot.MORNING, steps));
        useCases.today.materializeDue.execute();
        String occurrence = repository.today.openOccurrences().stream()
                .filter(item -> item.taskId.equals(id)).findFirst().orElseThrow().id;
        List<de.thonktank.autosecretary.domain.model.OccurrenceStep> current =
                repository.steps.occurrenceSteps(occurrence);
        for (int i = 0; i < 5; i++) useCases.today.toggleStep.execute(current.get(i).id);
        mount();
        title("Lange Aufgabe");
        assertEquals(9, viewModel.state().getValue().feature.focus.rows.size());
        assertTrue(viewModel.state().getValue().feature.focus.rows.get(0).expanded());
        content.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertNotNull(find(content, view -> view instanceof TextView
                && "Schritt 14".contentEquals(((TextView) view).getText())));
        assertNull(find(content, view -> view instanceof TextView
                && ((TextView) view).getText().toString().endsWith("Schritt 1")));
        View done = find(content, view -> view instanceof TextView
                && "Erledigt (5)".contentEquals(((TextView) view).getText()));
        assertNotNull(done);
        assertTrue(done.performClick());
        assertNotNull(find(content, view -> view instanceof TextView
                && ((TextView) view).getText().toString().endsWith("Schritt 1")));
    }

    @Test public void quickAddAndSortKeepTemplateAndExecutionOrderIndependent() {
        TaskId task = add("Aufgabe", TaskSlot.MORNING);
        mount();
        title("Aufgabe");
        String occurrence = viewModel.state().getValue().today().focus.itemId();
        for (String title : Arrays.asList("Erster", "Zweiter", "Dritter")) {
            viewModel.dispatch(TodayAction.addTodayStep(occurrence, title));
            render();
        }
        var steps = repository.steps.occurrenceSteps(occurrence);
        assertEquals(Arrays.asList("Erster", "Zweiter", "Dritter"),
                steps.stream().map(step -> step.text).collect(java.util.stream.Collectors.toList()));
        viewModel.dispatch(TodayAction.sortTodaySteps(occurrence,
                Arrays.asList(steps.get(2).id, steps.get(0).id, steps.get(1).id)));
        render();
        reopen();
        assertEquals(Arrays.asList("Dritter", "Erster", "Zweiter"),
                repository.steps.occurrenceSteps(occurrence).stream()
                        .map(step -> step.text).collect(java.util.stream.Collectors.toList()));
        assertEquals(Arrays.asList("Erster", "Zweiter", "Dritter"),
                repository.steps.templates(task).stream()
                        .map(step -> step.text).collect(java.util.stream.Collectors.toList()));
    }

    @Test public void closeConfirmationForwardsTheSelectedTaskAndAcknowledgesOnlyItsRequest() {
        TaskId task = add("Tagesabschluss", TaskSlot.EVENING);
        mount();
        viewModel.dispatch(TodayAction.requestClose(task.value, "Tagesabschluss"));
        TodayRequest request = viewModel.state().getValue().firstRequest();
        assertNotNull(request);
        assertEquals(TodayRequest.Kind.CONFIRM_CLOSE, request.kind);
        assertEquals(task.value, request.taskId);
        assertEquals("Tagesabschluss", request.title);
        viewModel.dispatch(TodayAction.acknowledgeRequest("other-request"));
        assertEquals(request.id, viewModel.state().getValue().firstRequest().id);
        viewModel.dispatch(TodayAction.acknowledgeRequest(request.id));
        assertNull(viewModel.state().getValue().firstRequest());
    }

    private TaskId add(String title, TaskSlot slot) {
        return useCases.catalog.create.execute(definition(title, slot, Collections.emptyList()));
    }
    private TaskDefinition definition(String title, TaskSlot slot, List<TaskStepDefinition> steps) {
        return new TaskDefinition(title, null, slot, Recurrence.ONCE, 1, 0, 0,
                TaskBoundKind.FOREVER, null, null, null, null, "", steps);
    }
    private void openDatabase() {
        database = Room.databaseBuilder(context, AppDatabase.class, DATABASE).allowMainThreadQueries()
                .setQueryExecutor(Runnable::run).setTransactionExecutor(Runnable::run).build();
        repository = new RoomRepositoryFixture(database);
        useCases = new ApplicationUseCaseComposition(database, clock, () -> "integration-" + (++sequence),
                ComboPolicySource.defaults());
    }
    private void closeDatabase() {
        if (viewModel != null) { viewModel.onCleared(); viewModel = null; }
        if (invalidations != null) { invalidations.close(); invalidations = null; }
        if (database != null) database.close();
    }
    private void reopen() { closeDatabase(); openDatabase(); mount(); }
    private void mount() {
        CalendarDataSource calendar = new CalendarDataSource() {
            public CalendarResult loadToday() { return new CalendarResult.Success(Collections.emptyList()); }
            public Subscription observeChanges(Runnable observer) { return () -> { }; }
        };
        UiPreferences preferences = new UiPreferences(context, logger);
        invalidations = new PresentationInvalidationSource(new DatabaseInvalidationSource(database),
                new CalendarInvalidationSource(calendar), new PreferenceInvalidationSource(preferences),
                new ClockInvalidationSource(clock, observer -> () -> { }), Runnable::run);
        AndroidUiTextProvider texts = new AndroidUiTextProvider(context);
        DashboardPresenter presenter = new DashboardPresenter(clock, useCases.today.loadDashboard,
                useCases.today.materializeDue, new DashboardUiMapper(texts));
        viewModel = new TodayViewModel(useCases.today, useCases.catalog, presenter,
                calendar, preferences, clock, logger, texts, invalidations, new SavedStateHandle(),
                new DirectExecutor(), Runnable::run);
        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        renderer = new DashboardRenderer(context, scroll, content, viewModel::dispatch,
                action -> fail("Unexpected options action"), "test", new RewardAnchorRegistry(),
                action -> fail("Unexpected all-tasks action"));
        render();
    }
    private void render() {
        database.getInvalidationTracker().refreshVersionsSync();
        assertFalse(viewModel.state().getValue().loading);
        assertTrue(viewModel.state().getValue().requests.toString(), viewModel.state().getValue().requests.isEmpty());
        renderer.render(new AppShellScreenState(NavigationDestination.TODAY,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT)), viewModel.state().getValue(), null, null);
    }
    private void title(String title) {
        if (viewModel.state().getValue().today().focus != null) {
            viewModel.dispatch(TodayAction.showOverview());
            render();
        }
        String label = context.getString(R.string.today_open_task, title);
        View target = find(content, view -> view.getContentDescription() != null
                && view.getContentDescription().toString().startsWith(label));
        assertNotNull("Missing accessible title: " + title, target);
        assertTrue(target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null));
        render();
    }
    private void hide(String title) {
        clickDescription(context.getString(R.string.today_hide_task, title));
    }
    private void restore(String title) {
        View toggle = find(content, view -> view instanceof TextView
                && context.getString(R.string.today_hidden_group, 1)
                .contentEquals(((TextView) view).getText()));
        assertNotNull(toggle);
        assertTrue(toggle.performClick());
        clickDescription(context.getString(R.string.today_restore_task, title));
    }
    private void clickDescription(String description) {
        View action = find(content, view -> description.contentEquals(
                view.getContentDescription() == null ? "" : view.getContentDescription()));
        assertNotNull(description, action);
        assertTrue(action.performClick());
        render();
    }
    private void later() {
        View target = find(content, view -> view instanceof TextView && context.getString(R.string.action_later)
                .contentEquals(((TextView) view).getText()));
        assertNotNull("Missing Later action", target);
        assertTrue(target.performClick());
        render();
    }
    private View find(View view, Predicate<View> match) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (match.test(view)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = find(((ViewGroup) view).getChildAt(i), match);
            if (result != null) return result;
        }
        return null;
    }
    private List<TodayQueue.Entry> queue() {
        return TodayQueue.visible(useCases.today.loadDashboard.execute(clock.today()), clock.today());
    }
    private void assertOrder(String... titles) {
        List<String> actual = new ArrayList<>();
        TodayUiModel today = viewModel.state().getValue().today();
        today.openTasks.forEach(item -> actual.add(item.row.title));
        assertEquals(Arrays.asList(titles), actual);
    }
    private Map<String, List<String>> domainSnapshot() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : Arrays.asList("tasks", "task_steps", "occurrences", "occurrence_steps", "stats",
                "reward_bookings", "flow_candidates", "capacity_resources", "step_flow_runs", "flow_run_steps",
                "flow_run_resources")) {
            List<String> rows = new ArrayList<>();
            try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query("SELECT * FROM " + table)) {
                while (cursor.moveToNext()) {
                    List<String> columns = new ArrayList<>();
                    for (int i = 0; i < cursor.getColumnCount(); i++) columns.add(cursor.getColumnName(i)
                            + "=" + cursor.getType(i) + ":" + cursor.getString(i));
                    rows.add(columns.toString());
                }
            }
            Collections.sort(rows);
            result.put(table, rows);
        }
        return result;
    }
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; return Collections.emptyList(); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        public void execute(Runnable action) { if (shutdown) throw new IllegalStateException(); action.run(); }
    }
}
