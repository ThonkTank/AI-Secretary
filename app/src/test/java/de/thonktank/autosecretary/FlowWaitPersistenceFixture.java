package de.thonktank.autosecretary;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.presentation.today.*;
import java.time.*;
import java.util.*;

/** Real graph commands and SQLite, with a controllable wall clock for dialog assertions. */
public final class FlowWaitPersistenceFixture implements AutoCloseable {
    private final AppDatabase database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase.class).allowMainThreadQueries().build();
    private final LocalDate date = LocalDate.of(2026, 9, 17);
    public long now = 1_000_000;
    private int sequence;
    private final ApplicationUseCaseComposition app = new ApplicationUseCaseComposition(database, new Clock() {
        @Override public LocalDate today() { return date; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }, () -> now, () -> "wait-test-" + ++sequence, ComboPolicySource.defaults());
    public final List<TodayAction> actions = new ArrayList<>();

    public String start(boolean parallel) {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Wäsche")
                .addStep("Buntwäsche", FlowDelayPolicy.fixed(parallel ? 0 : 60_000));
        if (parallel) {
            draft = draft.addStep("Kurz", FlowDelayPolicy.fixed(60_000))
                    .addStep("Lang", FlowDelayPolicy.fixed(120_000));
            List<String> keys = draft.graph.stepIds;
            draft = draft.withGraph(new FlowTileGraph(keys, List.of(
                    new FlowTileGraph.Link(keys.get(0), keys.get(1)),
                    new FlowTileGraph.Link(keys.get(0), keys.get(2)))));
        }
        TaskId task = app.flows.saveGraph.execute(draft.edit());
        app.today.materializeDue.execute();
        String candidate = app.today.loadDashboard.execute(date).flowTaskSheets.stream()
                .flatMap(sheet -> sheet.entries.stream()).filter(entry -> entry.candidateTemplate != null
                        && entry.candidateTemplate.taskId.equals(task)).findFirst().orElseThrow().targetId;
        String run = app.flows.runtime.start(candidate, null).runId;
        if (parallel) for (FlowRunSummary.Step step : run(run).steps)
            if (step.state == FlowGraphRun.State.AVAILABLE) app.flows.runtime.complete(step.id, null);
        return run;
    }

    public FlowRunSummary run(String id) {
        return app.today.loadDashboard.execute(date).flowRuns.stream()
                .filter(run -> run.id.equals(id)).findFirst().orElseThrow();
    }

    public List<FlowChainUiModel> chains() {
        return app.today.loadDashboard.execute(date).flowRuns.stream().map(run ->
                new FlowChainUiModel(run, Map.of(), new RewardTextFormatter(Locale.GERMANY))).toList();
    }

    public Map<String, Long> deadlines() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (FlowChainUiModel chain : chains()) for (FlowWaitUiModel wait : chain.editableWaits)
            result.put(chain.runId + "/" + wait.waitId, wait.readyAtEpochMillis);
        return result;
    }

    public void accept(TodayAction action) {
        actions.add(action);
        if (action.kind != TodayAction.Kind.ADJUST_FLOW_WAIT)
            throw new AssertionError("Editing must not collect or emit another command: " + action.kind);
        if (!app.flows.runtime.adjustWait(action.id, action.relatedId, action.longValue))
            throw new AssertionError("Expected a persisted wait change");
    }

    public void expire() { now += 120_000; app.flows.activateReadyFlows.execute(); }
    public int xp() { return app.today.loadDashboard.execute(date).xp; }
    @Override public void close() { database.close(); }
}
