package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.*;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import java.time.LocalDate;
import java.util.*;

/** A read-only, transactionally consistent projection of the single graph execution model. */
public final class LoadGraphFlowSheets {
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final FlowRepository flows;
    private final FlowGraphDefinitionRepository definitions;
    private final FlowGraphRunRepository runs;
    private final TransactionRunner transactions;

    public LoadGraphFlowSheets(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                              FlowRepository flows, FlowGraphDefinitionRepository definitions,
                              FlowGraphRunRepository runs, TransactionRunner transactions) {
        this.catalog = catalog; this.steps = steps; this.today = today; this.flows = flows;
        this.definitions = definitions; this.runs = runs; this.transactions = transactions;
    }

    public Result execute(LocalDate date) {
        return transactions.inTransaction(() -> {
            Map<TaskId, Task> tasks = new HashMap<>();
            for (Task task : catalog.allTasks()) tasks.put(task.id, task);
            return read(date, tasks);
        });
    }

    /** Reuses the inventory already loaded by the surrounding dashboard transaction. */
    public Result execute(LocalDate date, Map<TaskId, Task> tasks) {
        return transactions.inTransaction(() -> read(date, tasks));
    }

    private Result read(LocalDate date, Map<TaskId, Task> tasks) {
        if (tasks.values().stream().noneMatch(task -> task.kind == TaskKind.FLOW))
            return new Result(Collections.emptyList(), Collections.emptyList());
        Map<String, CapacityResource> resources = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        for (CapacityResource resource : flows.capacityResources()) {
            resources.put(resource.id, resource); totals.put(resource.id, resource.capacity);
        }
        Map<String, FlowTaskSheetPlacement> placements = new HashMap<>();
        for (FlowTaskSheetPlacement placement : flows.flowTaskSheetPlacements())
            placements.put(key(placement.taskId, placement.slot), placement);
        List<FlowGraphRunRecord> active = runs.active();
        Map<String, FlowGraphRunRecord> activeById = new HashMap<>();
        for (FlowGraphRunRecord record : active) activeById.put(record.run.id, record);
        Map<String, OccurrenceStep> offers = new HashMap<>();
        List<String> occurrenceIds = new ArrayList<>();
        Map<String, String> occurrenceRuns = new HashMap<>();
        for (Occurrence occurrence : active.isEmpty() ? Collections.<Occurrence>emptyList() : today.openOccurrences())
            if (occurrence.kind == OccurrenceKind.FLOW_STEP && activeById.containsKey(occurrence.flowRunId)) {
                occurrenceIds.add(occurrence.id); occurrenceRuns.put(occurrence.id, occurrence.flowRunId);
            }
        for (OccurrenceStep step : steps.occurrenceStepsFor(occurrenceIds)) {
            if (step.done || step.flowRunStepId == null) continue;
            FlowGraphRunRecord record = activeById.get(occurrenceRuns.get(step.occurrenceId));
            if (record == null || !record.run.steps.containsKey(step.flowRunStepId)) continue;
            if (offers.put(step.flowRunStepId, step) != null)
                throw new IllegalStateException("Runtime step has duplicate open execution offers");
        }
        Map<String, Builder> sheets = new LinkedHashMap<>();
        List<FlowRunSummary> summaries = new ArrayList<>();
        for (FlowGraphRunRecord record : active) {
            FlowGraphRun run = record.run;
            Task task = tasks.get(run.taskId);
            if (task == null || task.archived || task.conditionDone) continue;
            // Candidates, including ones blocked on capacity, are never background runs.
            if (run.steps.values().stream().noneMatch(step -> step.actionAtEpochMillis != null)) continue;
            List<FlowRunSummary.Resource> claims = new ArrayList<>();
            for (FlowGraphRun.Lease lease : run.leases) {
                CapacityResource resource = resources.get(lease.resourceId);
                claims.add(new FlowRunSummary.Resource(lease, resource == null ? "Entfernte Kapazität" : resource.name));
            }
            FlowRunSummary summary = new FlowRunSummary(record, task.title, claims);
            summaries.add(summary);
            Builder sheet = builder(sheets, placements, task, record.slot, date);
            sheet.running.add(summary);
            for (FlowGraphRun.Step step : run.availableSteps()) {
                OccurrenceStep offer = offers.get(step.id);
                if (offer != null) sheet.entries.add(FlowTaskSheet.Entry.action(summary, step, offer, finalTau(run, step)));
            }
            if (run.collectionAvailable()) sheet.entries.add(FlowTaskSheet.Entry.collection(summary));
        }
        Map<TaskId, FlowGraphDefinition> definitionsByTask = new HashMap<>();
        Map<TaskId, Map<String, TaskStepTemplate>> templates = new HashMap<>();
        Map<String, Long> used = new HashMap<>();
        for (FlowGraphRunRecord record : active) for (FlowGraphRun.Lease lease : record.run.leases)
            if (lease.state.consumesCapacity()) used.merge(lease.resourceId, (long) lease.units, Math::addExact);
        FlowGraphExecution.Capacity capacity = new FlowGraphExecution.Capacity(totals, used);
        for (FlowCandidate candidate : flows.flowCandidates()) {
            Task task = tasks.get(candidate.taskId);
            if (task == null || task.archived || task.conditionDone || candidate.scheduledOn.isAfter(date)) continue;
            if (!definitionsByTask.containsKey(task.id)) definitionsByTask.put(task.id, definitions.find(task.id));
            FlowGraphDefinition definition = definitionsByTask.get(task.id);
            if (definition == null || !definition.graph.roots().contains(candidate.seedStepId)) continue;
            // The exact admission reducer is also used for availability; this creates no stored claim.
            int[] next = {0};
            FlowGraphExecution execution = new FlowGraphExecution(() -> "preview:" + candidate.id + ":" + next[0]++);
            FlowGraphRun preview = execution.snapshot(definition, candidate.seedStepId);
            preview = execution.settle(preview, 0, capacity);
            if (preview.steps.get(preview.startStepId).state != FlowGraphRun.State.AVAILABLE) continue;
            Map<String, TaskStepTemplate> taskSteps = templates.computeIfAbsent(task.id, ignored -> {
                Map<String, TaskStepTemplate> result = new HashMap<>();
                for (TaskStepTemplate template : steps.templates(task.id)) result.put(template.id, template);
                return result;
            });
            TaskStepTemplate template = taskSteps.get(candidate.seedStepId);
            if (template != null) builder(sheets, placements, task, candidate.slot, date).entries.add(
                    FlowTaskSheet.Entry.candidate(candidate, template, definition.nodes.get(candidate.seedStepId).waitAfter,
                            finalTau(preview, preview.steps.get(preview.startStepId))));
        }
        List<FlowTaskSheet> visible = new ArrayList<>();
        for (Builder sheet : sheets.values()) if (!sheet.entries.isEmpty()) {
            sheet.entries.sort(Comparator.comparingInt((FlowTaskSheet.Entry entry) ->
                    entry.kind == FlowTaskSheet.Entry.Kind.CANDIDATE ? 1 : 0).thenComparingLong(entry -> entry.queueOrder));
            visible.add(new FlowTaskSheet(sheet.placement, sheet.task, sheet.entries, sheet.running));
        }
        return new Result(summaries, visible);
    }

    private Long finalTau(FlowGraphRun run, FlowGraphRun.Step action) {
        if (action.source.waitAfter.mode != FlowDelayPolicy.Mode.FIXED
                || action.source.waitAfter.proposedDelayMillis() != 0
                || run.steps.values().stream().anyMatch(step -> step != action && step.state != FlowGraphRun.State.DONE)) return null;
        ComboProgress combo = today.combo(ComboProgress.stepOwner(action.source.id));
        return Math.max(0L, Math.addExact(run.uncollectedTau(), RewardPolicy.step(combo).resultXp) - action.earnedTau);
    }

    private static Builder builder(Map<String, Builder> sheets, Map<String, FlowTaskSheetPlacement> placements,
                                    Task task, TaskSlot slot, LocalDate date) {
        String key = key(task.id, slot);
        return sheets.computeIfAbsent(key, ignored -> new Builder(task, placements.getOrDefault(key,
                new FlowTaskSheetPlacement(FlowTaskSheetPlacement.stableId(task.id, slot), task.id, slot, date, 0))));
    }

    private static String key(TaskId taskId, TaskSlot slot) { return taskId.value + "|" + slot.name(); }
    private static final class Builder {
        final Task task; final FlowTaskSheetPlacement placement;
        final List<FlowTaskSheet.Entry> entries = new ArrayList<>();
        final List<FlowRunSummary> running = new ArrayList<>();
        Builder(Task task, FlowTaskSheetPlacement placement) { this.task = task; this.placement = placement; }
    }
    public static final class Result {
        public final List<FlowRunSummary> runs;
        public final List<FlowTaskSheet> sheets;
        Result(List<FlowRunSummary> runs, List<FlowTaskSheet> sheets) {
            this.runs = Collections.unmodifiableList(new ArrayList<>(runs));
            this.sheets = Collections.unmodifiableList(new ArrayList<>(sheets));
        }
    }
}
