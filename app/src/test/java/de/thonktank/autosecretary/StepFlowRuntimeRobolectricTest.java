package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.widget.WidgetDashboardMapper;
import de.thonktank.autosecretary.widget.WidgetDashboardUiModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StepFlowRuntimeRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);
    private static final long TWO_HOURS = 2L * 60L * 60L * 1_000L;
    private static final long ONE_DAY = 24L * 60L * 60L * 1_000L;

    private AppDatabase database;
    private RoomRepositoryFixture repository;
    private SequenceIds ids;
    private MutableMoment moments;
    private ApplicationUseCaseComposition tasks;
    private Task task;
    private Clock clock;
    private final List<String> queries = new CopyOnWriteArrayList<>();

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .setQueryCallback((sql, arguments) -> {
                    if (sql.trim().toUpperCase(java.util.Locale.ROOT).startsWith("SELECT")
                            && !sql.contains("room_table_modification_log"))
                        queries.add(sql);
                }, Runnable::run)
                .build();
        repository = new RoomRepositoryFixture(database);
        ids = new SequenceIds();
        moments = new MutableMoment(1_000_000L);
        clock = new Clock() {
            @Override public LocalDate today() { return TODAY; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        tasks = new ApplicationUseCaseComposition(database, clock, moments, ids,
                de.thonktank.autosecretary.domain.repository.ComboPolicySource.defaults());
        tasks.catalog.create.execute(laundryTask());
        task = repository.catalog.allTasks().get(0);
        tasks.flows.saveCapacityResource.execute("washer", "Waschmaschine", 1);
        tasks.flows.saveCapacityResource.execute("dry", "Trockenplatz", 2);
        tasks.flows.saveStepFlowDefinition.execute(task.id, transitions(), leases(task));
        tasks.today.materializeDue.execute();
    }

    @After public void tearDown() { database.close(); }

    @Test public void washerAndTwoDryingPlacesGateFourRunsWithoutBlockingWaitingWork() {
        assertTrue(tasks.flows.activateReadyFlows.execute());
        StepFlowRun first = offeredRun();
        assertEquals("colors", first.seedStepId);
        assertEquals(1, countRuns(StepFlowRunState.OFFERED));
        assertEquals(3, countRuns(StepFlowRunState.WAITING_RESOURCE));
        assertEquals(2, countResources(FlowResourceState.RESERVED));

        tasks.today.toggleStep.execute(openStep(first).id);
        first = repository.flows.findFlowRun(first.id);
        assertEquals(StepFlowRunState.WAITING_TIME, first.state);
        assertEquals(1, first.currentPosition);
        assertEquals(2, countResources(first.id, FlowResourceState.ACTIVE));
        assertTrue(openSteps(first.currentSheetOccurrenceId).isEmpty());
        assertEquals(0, countRuns(StepFlowRunState.OFFERED));

        moments.advance(TWO_HOURS);
        assertTrue(tasks.flows.activateReadyFlows.execute());
        first = repository.flows.findFlowRun(first.id);
        assertEquals(StepFlowRunState.OFFERED, first.state);
        assertEquals(2, repository.steps.occurrenceSteps(first.currentSheetOccurrenceId).size());
        assertEquals("Aufhängen", openStep(first).text);

        tasks.today.toggleStep.execute(openStep(first).id);
        first = repository.flows.findFlowRun(first.id);
        StepFlowRun second = offeredRun();
        assertFalse(first.id.equals(second.id));
        assertEquals(StepFlowRunState.WAITING_TIME, first.state);
        assertEquals(2, first.currentPosition);
        assertEquals(2, consumingUnits("dry"));
        assertEquals(1, consumingUnits("washer"));
        assertEquals(2, countRuns(StepFlowRunState.WAITING_RESOURCE));

        tasks.today.toggleStep.execute(openStep(second).id);
        moments.advance(TWO_HOURS);
        tasks.flows.activateReadyFlows.execute();
        second = repository.flows.findFlowRun(second.id);
        assertEquals("Aufhängen", openStep(second).text);
        tasks.today.toggleStep.execute(openStep(second).id);
        assertEquals(0, countRuns(StepFlowRunState.OFFERED));

        moments.advance(ONE_DAY - TWO_HOURS);
        tasks.flows.activateReadyFlows.execute();
        first = repository.flows.findFlowRun(first.id);
        assertEquals("Abhängen", openStep(first).text);
        tasks.today.toggleStep.execute(openStep(first).id);

        assertEquals(2, countRuns(StepFlowRunState.OFFERED));
        assertEquals(2, consumingUnits("dry"));
        assertEquals(1, consumingUnits("washer"));
    }

    @Test public void timedAndCapacityWaitsStayOutOfTheNormalTaskList() {
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();

        tasks.today.toggleStep.execute(openStep(first).id);

        Dashboard dashboard = tasks.today.loadDashboard.execute(TODAY);
        assertTrue(dashboard.tasks.isEmpty());
        assertEquals(4, dashboard.flowRuns.size());
        assertEquals(StepFlowRunState.WAITING_TIME,
                repository.flows.findFlowRun(first.id).state);
    }

    @Test public void partialHarvestMovesUntouchedSuccessorImmediatelyAndUndoRestoresSheet() {
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun run = offeredRun();
        Occurrence original = repository.today.findOccurrence(run.currentSheetOccurrenceId);
        tasks.today.toggleStep.execute(openStep(run).id);
        moments.advance(TWO_HOURS);
        tasks.flows.activateReadyFlows.execute();
        run = repository.flows.findFlowRun(run.id);
        assertEquals("Aufhängen", openStep(run).text);

        assertFalse(tasks.today.harvest.execute(original.id).bookings.isEmpty());
        Occurrence harvested = repository.today.findOccurrence(original.id);
        run = repository.flows.findFlowRun(run.id);
        String replacementId = run.currentSheetOccurrenceId;
        assertEquals(OccurrenceState.HARVESTED_WITH_MISSED_STEPS, harvested.state);
        assertFalse(original.id.equals(replacementId));
        assertEquals("Aufhängen", openStep(run).text);
        assertTrue(repository.flows.flowRunResources(run.id).stream()
                .allMatch(value -> value.state == FlowResourceState.ACTIVE));

        assertFalse(tasks.today.undoOccurrence.execute(original.id).bookings.isEmpty());
        run = repository.flows.findFlowRun(run.id);
        assertEquals(original.id, run.currentSheetOccurrenceId);
        assertEquals(OccurrenceState.OPEN, repository.today.findOccurrence(original.id).state);
        assertNull(repository.today.findOccurrence(replacementId));
        assertEquals("Aufhängen", openStep(run).text);
    }

    @Test public void undoCompletedStepRemovesUntouchedSuccessorAndRewindsClaims() {
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun run = offeredRun();
        String firstStepId = openStep(run).id;
        tasks.today.toggleStep.execute(firstStepId);
        moments.advance(TWO_HOURS);
        tasks.flows.activateReadyFlows.execute();
        run = repository.flows.findFlowRun(run.id);
        assertEquals(2, repository.steps.occurrenceSteps(run.currentSheetOccurrenceId).size());

        assertFalse(tasks.today.toggleStep.execute(firstStepId).bookings.isEmpty());
        run = repository.flows.findFlowRun(run.id);
        assertEquals(0, run.currentPosition);
        assertEquals(StepFlowRunState.OFFERED, run.state);
        assertEquals(1, repository.steps.occurrenceSteps(run.currentSheetOccurrenceId).size());
        assertFalse(repository.steps.findOccurrenceStep(firstStepId).done);
        assertEquals(2, countResources(run.id, FlowResourceState.RESERVED));
    }

    @Test public void dashboardLoadsEveryActiveRunWithTwoBulkSnapshotQueries() {
        queries.clear();

        Dashboard dashboard = tasks.today.loadDashboard.execute(TODAY);

        assertEquals(4, dashboard.flowRuns.size());
        assertEquals(1, queries.stream().filter(sql -> sql.contains("FROM flow_run_steps")
                && sql.contains(" IN ")).count());
        assertEquals(1, queries.stream().filter(sql -> sql.contains("FROM flow_run_resources")
                && sql.contains(" IN ")).count());
        assertTrue("Dashboard query count was " + queries.size(), queries.size() <= 12);
    }

    @Test public void enteredDelayIsSnapshottedAndRememberedWithoutChangingOtherRuns() {
        long chosen = 3L * 60L * 60L * 1_000L;
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();

        tasks.today.toggleStep.execute(openStep(first).id, chosen);

        first = repository.flows.findFlowRun(first.id);
        assertEquals(Long.valueOf(1_000_000L + chosen), first.readyAtEpochMillis);
        assertEquals(Long.valueOf(chosen), repository.flows.flowRunSteps(first.id).get(0)
                .chosenDelayMillis);
        StepTransition remembered = repository.flows.stepTransitions(task.id).stream()
                .filter(value -> value.sourceStepId.equals("colors"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(chosen, remembered.delay.proposedDelayMillis());
        StepFlowRun other = repository.flows.activeFlowRuns().stream()
                .filter(value -> value.seedStepId.equals("whites"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(TWO_HOURS, repository.flows.flowRunSteps(other.id).get(0)
                .delayAfter.proposedDelayMillis());
    }

    @Test public void deferRequeuesOfferAndCancelFreesClaimsForTheNextRun() {
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();
        String firstSheet = first.currentSheetOccurrenceId;

        tasks.today.defer.execute(firstSheet);

        first = repository.flows.findFlowRun(first.id);
        StepFlowRun second = offeredRun();
        assertEquals(StepFlowRunState.WAITING_RESOURCE, first.state);
        assertNull(repository.today.findOccurrence(firstSheet));
        assertFalse(first.id.equals(second.id));
        assertTrue(first.queueOrder > second.queueOrder);

        String secondSheet = second.currentSheetOccurrenceId;
        assertTrue(tasks.flows.cancelFlowRun.execute(second.id));

        assertEquals(StepFlowRunState.CANCELLED, repository.flows.findFlowRun(second.id).state);
        assertNull(repository.today.findOccurrence(secondSheet));
        StepFlowRun third = offeredRun();
        assertFalse(second.id.equals(third.id));
        assertEquals(2, countResources(third.id, FlowResourceState.RESERVED));
    }

    @Test public void queueOrderAndReadyTimeCanBeAdjustedExplicitly() {
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();
        StepFlowRun preferred = repository.flows.activeFlowRuns().stream()
                .filter(value -> value.state == StepFlowRunState.WAITING_RESOURCE)
                .reduce((left, right) -> right).orElseThrow(AssertionError::new);
        assertTrue(tasks.flows.reorderFlowRun.execute(preferred.id, first.id));
        tasks.today.defer.execute(first.currentSheetOccurrenceId);
        assertEquals(preferred.id, offeredRun().id);

        StepFlowRun run = offeredRun();
        tasks.today.toggleStep.execute(openStep(run).id);
        run = repository.flows.findFlowRun(run.id);
        assertEquals(StepFlowRunState.WAITING_TIME, run.state);
        assertTrue(tasks.flows.adjustFlowRunReadyAt.execute(run.id, moments.nowEpochMillis()));
        run = repository.flows.findFlowRun(run.id);
        assertEquals(StepFlowRunState.OFFERED, run.state);
        assertEquals("Aufhängen", openStep(run).text);
    }

    @Test public void foregroundAndWidgetReadsExposeTheReadyActionImmediately() {
        AtomicInteger wakeSchedules = new AtomicInteger();
        DashboardPresenter presenter = new DashboardPresenter(clock, tasks.today.loadDashboard,
                tasks.today.materializeDue, new DashboardUiMapper(new AndroidUiTextProvider(
                ApplicationProvider.getApplicationContext())), tasks.today.applyComboDecay,
                tasks.flows.activateReadyFlows, wakeSchedules::incrementAndGet);

        assertTrue(presenter.prepare());
        TodayUiModel today = presenter.load();
        assertEquals(1, wakeSchedules.get());
        assertEquals(4, today.flowRuns.size());
        assertEquals(4, today.withCalendar(java.util.Collections.emptyList()).flowRuns.size());
        assertNotNull(today.focus);
        assertEquals("Wäsche waschen", today.focus.title());
        assertEquals("Buntwäsche", today.focus.steps.get(0).title);
        assertEquals(StepExecutionUiAction.Kind.TOGGLE_WITH_DELAY,
                today.focus.steps.get(0).executionAction.kind);
        assertEquals(TWO_HOURS, today.focus.steps.get(0).executionAction
                .proposedDelayMillis);

        WidgetDashboardUiModel widget = new WidgetDashboardMapper(
                new AndroidUiTextProvider(ApplicationProvider.getApplicationContext()))
                .map(tasks.today.loadDashboard.execute(TODAY), TODAY);
        assertNotNull(widget.focus);
        assertEquals("Wäsche waschen", widget.focus.title);
        assertEquals("Buntwäsche", widget.focus.steps.get(0).title);
    }

    @Test public void waitingHarvestSheetNeverDisplacesExecutableNormalWork() {
        tasks.catalog.create.execute(TaskDefinition.basic("Abwasch", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, java.util.Collections.singletonList("Spülen")));
        tasks.today.materializeDue.execute();
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun flow = offeredRun();
        tasks.today.toggleStep.execute(openStep(flow).id);

        TodayUiModel today = new DashboardUiMapper(new AndroidUiTextProvider(
                ApplicationProvider.getApplicationContext())).map(
                tasks.today.loadDashboard.execute(TODAY), TODAY);

        assertNotNull(today.focus);
        assertEquals("Abwasch", today.focus.title());
        assertFalse(today.timeline.stream().anyMatch(item -> item.task != null
                && item.task.title.equals("Wäsche waschen")));
        assertTrue(today.flowRuns.stream().anyMatch(run -> run.seedTitle.equals("Buntwäsche")));
        WidgetDashboardUiModel widget = new WidgetDashboardMapper(
                new AndroidUiTextProvider(ApplicationProvider.getApplicationContext()))
                .map(tasks.today.loadDashboard.execute(TODAY), TODAY);
        assertEquals("Abwasch", widget.focus.title);
    }

    @Test public void completeRemainingOnlyCompletesIdsPresentBeforeFlowProgression() {
        tasks.flows.activateReadyFlows.execute();
        StepFlowRun run = offeredRun();
        String sheetId = run.currentSheetOccurrenceId;

        tasks.today.completeRemainingSteps.execute(sheetId);

        run = repository.flows.findFlowRun(run.id);
        assertEquals(StepFlowRunState.WAITING_TIME, run.state);
        assertEquals(1, repository.steps.occurrenceSteps(sheetId).size());
        assertTrue(repository.steps.occurrenceSteps(sheetId).get(0).done);
    }

    private StepFlowRun offeredRun() {
        for (StepFlowRun run : repository.flows.activeFlowRuns())
            if (run.state == StepFlowRunState.OFFERED) return run;
        throw new AssertionError("No offered run");
    }

    private OccurrenceStep openStep(StepFlowRun run) {
        List<OccurrenceStep> open = openSteps(run.currentSheetOccurrenceId);
        assertEquals(1, open.size());
        return open.get(0);
    }

    private List<OccurrenceStep> openSteps(String occurrenceId) {
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep step : repository.steps.occurrenceSteps(occurrenceId))
            if (!step.done) result.add(step);
        return result;
    }

    private int countRuns(StepFlowRunState state) {
        int result = 0;
        for (StepFlowRun run : repository.flows.activeFlowRuns()) if (run.state == state) result++;
        return result;
    }

    private int countResources(FlowResourceState state) {
        int result = 0;
        for (StepFlowRun run : repository.flows.activeFlowRuns())
            result += countResources(run.id, state);
        return result;
    }

    private int countResources(String runId, FlowResourceState state) {
        int result = 0;
        for (FlowRunResourceSnapshot resource : repository.flows.flowRunResources(runId))
            if (resource.state == state) result++;
        return result;
    }

    private int consumingUnits(String resourceId) {
        int result = 0;
        for (FlowRunResourceSnapshot value : repository.flows.consumingFlowResources())
            if (resourceId.equals(value.resourceId)) result += value.units;
        return result;
    }

    private static TaskDefinition laundryTask() {
        List<TaskStepDefinition> steps = new ArrayList<>();
        steps.add(step("colors", "Buntwäsche", StepActivationKind.SCHEDULED, 0));
        steps.add(step("whites", "Weißwäsche", StepActivationKind.SCHEDULED, 1));
        steps.add(step("sheets", "Bettwäsche", StepActivationKind.SCHEDULED, 2));
        steps.add(step("towels", "Handtücher", StepActivationKind.SCHEDULED, 3));
        steps.add(step("hang", "Aufhängen", StepActivationKind.FOLLOW_UP, 4));
        steps.add(step("take-down", "Abhängen", StepActivationKind.FOLLOW_UP, 5));
        steps.add(step("put-away", "Wegräumen", StepActivationKind.FOLLOW_UP, 6));
        return new TaskDefinition("Wäsche waschen", null, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", steps);
    }

    private static TaskStepDefinition step(String id, String text, StepActivationKind kind,
                                           int position) {
        return de.thonktank.autosecretary.testing.StepTestFixtures.definition(id, position, text, 0, 0, StepAmount.none(), "", kind);
    }

    private static List<StepTransition> transitions() {
        List<StepTransition> result = new ArrayList<>();
        for (String seed : Arrays.asList("colors", "whites", "sheets", "towels"))
            result.add(new StepTransition(seed, "hang",
                    FlowDelayPolicy.rememberLast(TWO_HOURS)));
        result.add(new StepTransition("hang", "take-down",
                FlowDelayPolicy.rememberLast(ONE_DAY)));
        result.add(new StepTransition("take-down", "put-away", FlowDelayPolicy.fixed(0L)));
        return result;
    }

    private static List<StepResourceLease> leases(Task task) {
        List<StepResourceLease> result = new ArrayList<>();
        for (String seed : Arrays.asList("colors", "whites", "sheets", "towels")) {
            result.add(new StepResourceLease("washer-" + seed, task.id, seed, "hang",
                    "washer", 1));
            result.add(new StepResourceLease("dry-" + seed, task.id, seed, "take-down",
                    "dry", 1));
        }
        return result;
    }

    private static final class SequenceIds implements IdGenerator {
        private int next;
        @Override public String nextId() { return "runtime-" + ++next; }
    }

    private static final class MutableMoment implements MomentSource {
        private long now;
        MutableMoment(long now) { this.now = now; }
        @Override public long nowEpochMillis() { return now; }
        void advance(long millis) { now += millis; }
    }
}
