package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
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
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
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
    private ApplicationTaskRepository repository;
    private SequenceIds ids;
    private MutableMoment moments;
    private TaskUseCases tasks;
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
        repository = new RoomTaskRepository(database);
        ids = new SequenceIds();
        moments = new MutableMoment(1_000_000L);
        clock = new Clock() {
            @Override public LocalDate today() { return TODAY; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        tasks = new TaskUseCases(repository, clock, moments, ids);
        tasks.create.execute(laundryTask());
        task = repository.allTasks().get(0);
        tasks.saveCapacityResource.execute("washer", "Waschmaschine", 1);
        tasks.saveCapacityResource.execute("dry", "Trockenplatz", 2);
        tasks.saveStepFlowDefinition.execute(task.id, transitions(), leases(task));
        tasks.materializeDue.execute();
    }

    @After public void tearDown() { database.close(); }

    @Test public void washerAndTwoDryingPlacesGateFourRunsWithoutBlockingWaitingWork() {
        assertTrue(tasks.activateReadyFlows.execute());
        StepFlowRun first = offeredRun();
        assertEquals("colors", first.seedStepId);
        assertEquals(1, countRuns(StepFlowRunState.OFFERED));
        assertEquals(3, countRuns(StepFlowRunState.WAITING_RESOURCE));
        assertEquals(2, countResources(FlowResourceState.RESERVED));

        tasks.toggleStep.execute(openStep(first).id);
        first = repository.findFlowRun(first.id);
        assertEquals(StepFlowRunState.WAITING_TIME, first.state);
        assertEquals(1, first.currentPosition);
        assertEquals(2, countResources(first.id, FlowResourceState.ACTIVE));
        assertTrue(openSteps(first.currentSheetOccurrenceId).isEmpty());
        assertEquals(0, countRuns(StepFlowRunState.OFFERED));

        moments.advance(TWO_HOURS);
        assertTrue(tasks.activateReadyFlows.execute());
        first = repository.findFlowRun(first.id);
        assertEquals(StepFlowRunState.OFFERED, first.state);
        assertEquals(2, repository.occurrenceSteps(first.currentSheetOccurrenceId).size());
        assertEquals("Aufhängen", openStep(first).text);

        tasks.toggleStep.execute(openStep(first).id);
        first = repository.findFlowRun(first.id);
        StepFlowRun second = offeredRun();
        assertFalse(first.id.equals(second.id));
        assertEquals(StepFlowRunState.WAITING_TIME, first.state);
        assertEquals(2, first.currentPosition);
        assertEquals(2, consumingUnits("dry"));
        assertEquals(1, consumingUnits("washer"));
        assertEquals(2, countRuns(StepFlowRunState.WAITING_RESOURCE));

        tasks.toggleStep.execute(openStep(second).id);
        moments.advance(TWO_HOURS);
        tasks.activateReadyFlows.execute();
        second = repository.findFlowRun(second.id);
        assertEquals("Aufhängen", openStep(second).text);
        tasks.toggleStep.execute(openStep(second).id);
        assertEquals(0, countRuns(StepFlowRunState.OFFERED));

        moments.advance(ONE_DAY - TWO_HOURS);
        tasks.activateReadyFlows.execute();
        first = repository.findFlowRun(first.id);
        assertEquals("Abhängen", openStep(first).text);
        tasks.toggleStep.execute(openStep(first).id);

        assertEquals(2, countRuns(StepFlowRunState.OFFERED));
        assertEquals(2, consumingUnits("dry"));
        assertEquals(1, consumingUnits("washer"));
    }

    @Test public void timedAndCapacityWaitsStayOutOfTheNormalTaskList() {
        tasks.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();

        tasks.toggleStep.execute(openStep(first).id);

        Dashboard dashboard = tasks.loadDashboard.execute(TODAY);
        assertTrue(dashboard.tasks.isEmpty());
        assertEquals(4, dashboard.flowRuns.size());
        assertEquals(StepFlowRunState.WAITING_TIME,
                repository.findFlowRun(first.id).state);
    }

    @Test public void partialHarvestMovesUntouchedSuccessorImmediatelyAndUndoRestoresSheet() {
        tasks.activateReadyFlows.execute();
        StepFlowRun run = offeredRun();
        Occurrence original = repository.findOccurrence(run.currentSheetOccurrenceId);
        tasks.toggleStep.execute(openStep(run).id);
        moments.advance(TWO_HOURS);
        tasks.activateReadyFlows.execute();
        run = repository.findFlowRun(run.id);
        assertEquals("Aufhängen", openStep(run).text);

        assertFalse(tasks.harvest.execute(original.id).bookings.isEmpty());
        Occurrence harvested = repository.findOccurrence(original.id);
        run = repository.findFlowRun(run.id);
        String replacementId = run.currentSheetOccurrenceId;
        assertEquals(OccurrenceState.HARVESTED_WITH_MISSED_STEPS, harvested.state);
        assertFalse(original.id.equals(replacementId));
        assertEquals("Aufhängen", openStep(run).text);
        assertTrue(repository.flowRunResources(run.id).stream()
                .allMatch(value -> value.state == FlowResourceState.ACTIVE));

        assertFalse(tasks.undoOccurrence.execute(original.id).bookings.isEmpty());
        run = repository.findFlowRun(run.id);
        assertEquals(original.id, run.currentSheetOccurrenceId);
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(original.id).state);
        assertNull(repository.findOccurrence(replacementId));
        assertEquals("Aufhängen", openStep(run).text);
    }

    @Test public void undoCompletedStepRemovesUntouchedSuccessorAndRewindsClaims() {
        tasks.activateReadyFlows.execute();
        StepFlowRun run = offeredRun();
        String firstStepId = openStep(run).id;
        tasks.toggleStep.execute(firstStepId);
        moments.advance(TWO_HOURS);
        tasks.activateReadyFlows.execute();
        run = repository.findFlowRun(run.id);
        assertEquals(2, repository.occurrenceSteps(run.currentSheetOccurrenceId).size());

        assertFalse(tasks.toggleStep.execute(firstStepId).bookings.isEmpty());
        run = repository.findFlowRun(run.id);
        assertEquals(0, run.currentPosition);
        assertEquals(StepFlowRunState.OFFERED, run.state);
        assertEquals(1, repository.occurrenceSteps(run.currentSheetOccurrenceId).size());
        assertFalse(repository.findOccurrenceStep(firstStepId).done);
        assertEquals(2, countResources(run.id, FlowResourceState.RESERVED));
    }

    @Test public void dashboardLoadsEveryActiveRunWithTwoBulkSnapshotQueries() {
        queries.clear();

        Dashboard dashboard = tasks.loadDashboard.execute(TODAY);

        assertEquals(4, dashboard.flowRuns.size());
        assertEquals(1, queries.stream().filter(sql -> sql.contains("FROM flow_run_steps")
                && sql.contains(" IN ")).count());
        assertEquals(1, queries.stream().filter(sql -> sql.contains("FROM flow_run_resources")
                && sql.contains(" IN ")).count());
        assertTrue("Dashboard query count was " + queries.size(), queries.size() <= 12);
    }

    @Test public void enteredDelayIsSnapshottedAndRememberedWithoutChangingOtherRuns() {
        long chosen = 3L * 60L * 60L * 1_000L;
        tasks.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();

        tasks.toggleStep.execute(openStep(first).id, chosen);

        first = repository.findFlowRun(first.id);
        assertEquals(Long.valueOf(1_000_000L + chosen), first.readyAtEpochMillis);
        assertEquals(Long.valueOf(chosen), repository.flowRunSteps(first.id).get(0)
                .chosenDelayMillis);
        StepTransition remembered = repository.stepTransitions(task.id).stream()
                .filter(value -> value.sourceStepId.equals("colors"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(chosen, remembered.delay.proposedDelayMillis());
        StepFlowRun other = repository.activeFlowRuns().stream()
                .filter(value -> value.seedStepId.equals("whites"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(TWO_HOURS, repository.flowRunSteps(other.id).get(0)
                .delayAfter.proposedDelayMillis());
    }

    @Test public void deferRequeuesOfferAndCancelFreesClaimsForTheNextRun() {
        tasks.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();
        String firstSheet = first.currentSheetOccurrenceId;

        tasks.defer.execute(firstSheet);

        first = repository.findFlowRun(first.id);
        StepFlowRun second = offeredRun();
        assertEquals(StepFlowRunState.WAITING_RESOURCE, first.state);
        assertNull(repository.findOccurrence(firstSheet));
        assertFalse(first.id.equals(second.id));
        assertTrue(first.queueOrder > second.queueOrder);

        String secondSheet = second.currentSheetOccurrenceId;
        assertTrue(tasks.cancelFlowRun.execute(second.id));

        assertEquals(StepFlowRunState.CANCELLED, repository.findFlowRun(second.id).state);
        assertNull(repository.findOccurrence(secondSheet));
        StepFlowRun third = offeredRun();
        assertFalse(second.id.equals(third.id));
        assertEquals(2, countResources(third.id, FlowResourceState.RESERVED));
    }

    @Test public void queueOrderAndReadyTimeCanBeAdjustedExplicitly() {
        tasks.activateReadyFlows.execute();
        StepFlowRun first = offeredRun();
        StepFlowRun preferred = repository.activeFlowRuns().stream()
                .filter(value -> value.state == StepFlowRunState.WAITING_RESOURCE)
                .reduce((left, right) -> right).orElseThrow(AssertionError::new);
        assertTrue(tasks.reorderFlowRun.execute(preferred.id, 0L));
        tasks.defer.execute(first.currentSheetOccurrenceId);
        assertEquals(preferred.id, offeredRun().id);

        StepFlowRun run = offeredRun();
        tasks.toggleStep.execute(openStep(run).id);
        run = repository.findFlowRun(run.id);
        assertEquals(StepFlowRunState.WAITING_TIME, run.state);
        assertTrue(tasks.adjustFlowRunReadyAt.execute(run.id, moments.nowEpochMillis()));
        run = repository.findFlowRun(run.id);
        assertEquals(StepFlowRunState.OFFERED, run.state);
        assertEquals("Aufhängen", openStep(run).text);
    }

    @Test public void foregroundAndWidgetReadsExposeTheReadyActionImmediately() {
        AtomicInteger wakeSchedules = new AtomicInteger();
        DashboardPresenter presenter = new DashboardPresenter(clock, tasks.loadDashboard,
                tasks.materializeDue, new DashboardUiMapper(new AndroidUiTextProvider(
                ApplicationProvider.getApplicationContext())), tasks.applyComboDecay,
                tasks.activateReadyFlows, wakeSchedules::incrementAndGet);

        assertTrue(presenter.prepare());
        TodayUiModel today = presenter.load();
        assertEquals(1, wakeSchedules.get());
        assertNotNull(today.focus);
        assertEquals("Wäsche waschen", today.focus.title());
        assertEquals("Buntwäsche", today.focus.steps.get(0).title);

        WidgetDashboardUiModel widget = new WidgetDashboardMapper(
                new AndroidUiTextProvider(ApplicationProvider.getApplicationContext()))
                .map(tasks.loadDashboard.execute(TODAY), TODAY);
        assertNotNull(widget.focus);
        assertEquals("Wäsche waschen", widget.focus.title);
        assertEquals("Buntwäsche", widget.focus.steps.get(0).title);
    }

    private StepFlowRun offeredRun() {
        for (StepFlowRun run : repository.activeFlowRuns())
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
        for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId))
            if (!step.done) result.add(step);
        return result;
    }

    private int countRuns(StepFlowRunState state) {
        int result = 0;
        for (StepFlowRun run : repository.activeFlowRuns()) if (run.state == state) result++;
        return result;
    }

    private int countResources(FlowResourceState state) {
        int result = 0;
        for (StepFlowRun run : repository.activeFlowRuns())
            result += countResources(run.id, state);
        return result;
    }

    private int countResources(String runId, FlowResourceState state) {
        int result = 0;
        for (FlowRunResourceSnapshot resource : repository.flowRunResources(runId))
            if (resource.state == state) result++;
        return result;
    }

    private int consumingUnits(String resourceId) {
        int result = 0;
        for (FlowRunResourceSnapshot value : repository.consumingFlowResources())
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
        return new TaskStepDefinition(id, position, text, 0, 0, StepAmount.none(), "", kind);
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
