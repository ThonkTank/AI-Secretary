package de.thonktank.autosecretary;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.StartFlowCandidateResult;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.widget.WidgetDashboardMapper;
import de.thonktank.autosecretary.widget.WidgetDashboardUiModel;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StepFlowRuntimeRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);
    private static final long TWO_HOURS = 7_200_000L;
    private static final long ONE_DAY = 86_400_000L;
    private AppDatabase database;
    private RoomRepositoryFixture repository;
    private SequenceIds ids;
    private MutableMoment moments;
    private ApplicationUseCaseComposition useCases;
    private Task task;
    private Clock clock;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomRepositoryFixture(database);
        ids = new SequenceIds();
        moments = new MutableMoment(1_000_000L);
        clock = new Clock() {
            @Override public LocalDate today() { return TODAY; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        useCases = new ApplicationUseCaseComposition(database, clock, moments, ids,
                de.thonktank.autosecretary.domain.repository.ComboPolicySource.defaults());
        useCases.catalog.create.execute(laundryTask());
        task = repository.catalog.allTasks().get(0);
        useCases.flows.saveCapacityResource.execute("washer", "Waschmaschine", 1);
        useCases.flows.saveCapacityResource.execute("dry", "Trockenplatz", 3);
        useCases.flows.saveStepFlowDefinition.execute(task.id, transitions(), leases(task));
        assertTrue(useCases.today.materializeDue.execute());
    }

    @After public void tearDown() { database.close(); }

    @Test public void dueLaundryCreatesOnlyCandidatesAndOneNonEmptySheet() {
        assertEquals(4, repository.flows.flowCandidates(task.id).size());
        assertTrue(repository.flows.activeFlowRuns(task.id).isEmpty());
        assertTrue(repository.flows.consumingFlowResources().isEmpty());
        assertTrue(repository.today.openOccurrences().isEmpty());
        Dashboard dashboard = useCases.today.loadDashboard.execute(TODAY);
        assertEquals(1, dashboard.flowTaskSheets.size());
        assertEquals(4, dashboard.flowTaskSheets.get(0).entries.size());
        assertTrue(dashboard.flowRuns.isEmpty());
        assertFalse(useCases.today.materializeDue.execute());
    }

    @Test public void foregroundAndWidgetExposeAppOwnedDurationAction() {
        Dashboard dashboard = useCases.today.loadDashboard.execute(TODAY);
        TodayUiModel today = mapper().map(dashboard, TODAY);
        assertEquals("Wäsche waschen", today.focus.title());
        assertEquals("Buntwäsche", today.focus.steps.get(0).title);
        assertEquals(StepExecutionUiAction.Kind.START_FLOW_CANDIDATE_WITH_DELAY,
                today.focus.steps.get(0).activeAction.kind);
        assertEquals(TWO_HOURS, today.focus.steps.get(0).activeAction.proposedDelayMillis);
        assertFalse(today.focus.allowBulkComplete);
        WidgetDashboardUiModel widget = new WidgetDashboardMapper(texts()).map(dashboard, TODAY);
        assertTrue(widget.focus.requiresApp);
        assertTrue(widget.focus.steps.get(0).requiresApp);
    }

    @Test public void atomicStartCreatesRunAndSnapshotsChosenDuration() {
        FlowCandidate colors = candidate("colors");
        StartFlowCandidateResult result = useCases.flows.startFlowCandidate.execute(
                colors.id, TWO_HOURS);
        assertEquals(StartFlowCandidateResult.Status.STARTED, result.status);
        assertNull(repository.flows.findFlowCandidate(colors.id));
        StepFlowRun run = repository.flows.findFlowRun(result.runId);
        assertEquals(StepFlowRunState.WAITING_TIME, run.state);
        assertEquals(1, run.currentPosition);
        assertEquals(Long.valueOf(moments.nowEpochMillis() + TWO_HOURS), run.readyAtEpochMillis);
        assertEquals(Long.valueOf(TWO_HOURS), repository.flows.flowRunSteps(run.id).get(0)
                .chosenDelayMillis);
        assertEquals(1, consumingUnits("washer"));
        assertEquals(1, consumingUnits("dry"));
    }

    @Test public void oneWasherHidesAndAtomicallyRejectsASecondConcurrentStart() {
        start("colors", TWO_HOURS);
        assertTrue(useCases.today.loadDashboard.execute(TODAY).flowTaskSheets.isEmpty());

        FlowCandidate whites = candidate("whites");
        StartFlowCandidateResult rejected = useCases.flows.startFlowCandidate.execute(
                whites.id, TWO_HOURS);

        assertEquals(StartFlowCandidateResult.Status.CAPACITY_CHANGED, rejected.status);
        assertNotNull(repository.flows.findFlowCandidate(whites.id));
        assertEquals(1, repository.flows.activeFlowRuns().size());
        assertEquals(1, consumingUnits("washer"));
    }

    @Test public void oneWasherAndThreeDryingPlacesRejectFourthStart() {
        for (String seed : Arrays.asList("colors", "whites", "sheets")) startAndHang(seed);
        assertEquals(3, consumingUnits("dry"));
        assertEquals(0, consumingUnits("washer"));
        Dashboard dashboard = useCases.today.loadDashboard.execute(TODAY);
        assertTrue(dashboard.flowTaskSheets.isEmpty());
        FlowCandidate towels = candidate("towels");
        StartFlowCandidateResult rejected = useCases.flows.startFlowCandidate.execute(
                towels.id, TWO_HOURS);
        assertEquals(StartFlowCandidateResult.Status.CAPACITY_CHANGED, rejected.status);
        assertNotNull(repository.flows.findFlowCandidate(towels.id));
        assertEquals(3, repository.flows.activeFlowRuns().size());
    }

    @Test public void followUpsShareSheetAndKeepOriginTitle() {
        StepFlowRun run = start("colors", TWO_HOURS);
        moments.advance(TWO_HOURS);
        useCases.flows.activateReadyFlows.execute();
        TodayUiModel hanging = mapper().map(useCases.today.loadDashboard.execute(TODAY), TODAY);
        assertEquals("Buntwäsche: Aufhängen", hanging.focus.steps.get(0).title);
        assertEquals(1, hanging.focus.steps.size());
        useCases.today.toggleStep.execute(openStep(run).id, ONE_DAY);
        assertEquals(3, useCases.today.loadDashboard.execute(TODAY)
                .flowTaskSheets.get(0).entries.size());
        moments.advance(ONE_DAY);
        useCases.flows.activateReadyFlows.execute();
        run = repository.flows.findFlowRun(run.id);
        assertEquals("Buntwäsche: Abhängen", mapper().map(
                useCases.today.loadDashboard.execute(TODAY), TODAY).focus.steps.get(0).title);
        useCases.today.toggleStep.execute(openStep(run).id);
        run = repository.flows.findFlowRun(run.id);
        assertEquals("Buntwäsche: Wegräumen", mapper().map(
                useCases.today.loadDashboard.execute(TODAY), TODAY).focus.steps.get(0).title);
        useCases.today.toggleStep.execute(openStep(run).id);
        assertEquals(StepFlowRunState.COMPLETED, repository.flows.findFlowRun(run.id).state);
        assertEquals(3, useCases.today.loadDashboard.execute(TODAY)
                .flowTaskSheets.get(0).entries.size());
    }

    @Test public void notReadyKeepsDryingPlaceAndHidesRunStep() {
        StepFlowRun run = startAndHang("colors");
        moments.advance(ONE_DAY);
        useCases.flows.activateReadyFlows.execute();
        run = repository.flows.findFlowRun(run.id);
        String stepId = openStep(run).id;
        assertTrue(useCases.flows.postponeFlowRun.execute(run.id, ONE_DAY));
        assertEquals(1, consumingUnits("dry"));
        String runId = run.id;
        assertTrue(useCases.today.loadDashboard.execute(TODAY).flowTaskSheets.get(0).entries
                .stream().noneMatch(value -> value.targetId.equals(runId)));
        moments.advance(ONE_DAY);
        useCases.flows.activateReadyFlows.execute();
        assertEquals(stepId, openStep(repository.flows.findFlowRun(run.id)).id);
    }

    @Test public void deferMovesOnlySheetBehindNormalWork() {
        useCases.catalog.create.execute(TaskDefinition.basic("Abwasch", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Collections.singletonList("Spülen")));
        useCases.today.materializeDue.execute();
        String sheetId = useCases.today.loadDashboard.execute(TODAY)
                .flowTaskSheets.get(0).placement.id;
        assertTrue(useCases.today.deferFlowTaskSheet.execute(sheetId));
        TodayUiModel mapped = mapper().map(useCases.today.loadDashboard.execute(TODAY), TODAY);
        assertEquals("Abwasch", mapped.focus.title());
        assertEquals(4, repository.flows.flowCandidates(task.id).size());
        assertTrue(repository.flows.activeFlowRuns().isEmpty());
        assertTrue(repository.flows.consumingFlowResources().isEmpty());
    }

    @Test public void recompositionPreservesCandidatesAndStartedRuns() {
        StepFlowRun run = start("colors", TWO_HOURS);
        ApplicationUseCaseComposition reloaded = new ApplicationUseCaseComposition(database,
                clock, moments, ids,
                de.thonktank.autosecretary.domain.repository.ComboPolicySource.defaults());
        assertNotNull(repository.flows.findFlowRun(run.id));
        assertEquals(3, repository.flows.flowCandidates(task.id).size());
        Dashboard dashboard = reloaded.today.loadDashboard.execute(TODAY);
        assertEquals(1, dashboard.flowRuns.size());
        assertTrue(dashboard.flowTaskSheets.isEmpty());
    }

    private StepFlowRun startAndHang(String seed) {
        StepFlowRun run = start(seed, TWO_HOURS);
        moments.advance(TWO_HOURS);
        useCases.flows.activateReadyFlows.execute();
        run = repository.flows.findFlowRun(run.id);
        useCases.today.toggleStep.execute(openStep(run).id, ONE_DAY);
        return repository.flows.findFlowRun(run.id);
    }

    private StepFlowRun start(String seed, long delay) {
        StartFlowCandidateResult result = useCases.flows.startFlowCandidate.execute(
                candidate(seed).id, delay);
        assertEquals(StartFlowCandidateResult.Status.STARTED, result.status);
        return repository.flows.findFlowRun(result.runId);
    }

    private FlowCandidate candidate(String seed) {
        return repository.flows.flowCandidates(task.id).stream()
                .filter(value -> value.seedStepId.equals(seed)).findFirst()
                .orElseThrow(AssertionError::new);
    }

    private OccurrenceStep openStep(StepFlowRun run) {
        List<OccurrenceStep> open = new ArrayList<>();
        for (OccurrenceStep step : repository.steps.occurrenceSteps(
                run.currentExecutionOccurrenceId)) if (!step.done) open.add(step);
        assertEquals(1, open.size());
        return open.get(0);
    }

    private int consumingUnits(String resourceId) {
        int result = 0;
        for (FlowRunResourceSnapshot value : repository.flows.consumingFlowResources())
            if (resourceId.equals(value.resourceId)) result += value.units;
        return result;
    }

    private DashboardUiMapper mapper() { return new DashboardUiMapper(texts()); }
    private AndroidUiTextProvider texts() {
        return new AndroidUiTextProvider(ApplicationProvider.getApplicationContext());
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
        StepAmount amount = kind == StepActivationKind.SCHEDULED
                ? StepAmount.duration(2 * 60 * 60) : StepAmount.none();
        return de.thonktank.autosecretary.testing.StepTestFixtures.definition(id, position, text,
                0, 0, amount, "Notiz:" + id, kind);
    }

    private static List<StepTransition> transitions() {
        List<StepTransition> result = new ArrayList<>();
        for (String seed : Arrays.asList("colors", "whites", "sheets", "towels"))
            result.add(new StepTransition(seed, "hang", FlowDelayPolicy.rememberLast(TWO_HOURS)));
        result.add(new StepTransition("hang", "take-down", FlowDelayPolicy.rememberLast(ONE_DAY)));
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
