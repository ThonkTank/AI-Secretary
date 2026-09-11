package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.model.TrainingContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.model.FlowCandidate;
import de.thonktank.autosecretary.domain.model.FlowTaskSheet;
import de.thonktank.autosecretary.domain.model.FlowTaskSheetPlacement;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class LoadDashboard {
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final FlowRepository flowRepository;
    private final LoadTrainingContext loadTrainingContext;
    private final LoadGraphFlowSheets graphSheets;
    private final TransactionRunner transactions;

    public LoadDashboard(CatalogRepository catalog, StepRepository steps,
                         TodayRepository today, FlowRepository flowRepository) {
        this(catalog, steps, today, flowRepository, null, null);
    }

    public LoadDashboard(CatalogRepository catalog, StepRepository steps,
                         TodayRepository today, FlowRepository flowRepository,
                         TrainingRepository trainingRepository,
                         TransactionRunner transactions) {
        this(catalog, steps, today, flowRepository, trainingRepository, transactions, null);
    }

    public LoadDashboard(CatalogRepository catalog, StepRepository steps,
                         TodayRepository today, FlowRepository flowRepository,
                         TrainingRepository trainingRepository, TransactionRunner transactions,
                         LoadGraphFlowSheets graphSheets) {
        this.catalog = catalog;
        this.steps = steps;
        this.today = today;
        this.flowRepository = flowRepository;
        this.graphSheets = graphSheets;
        this.transactions = transactions;
        this.loadTrainingContext = trainingRepository == null ? null
                : new LoadTrainingContext(steps, trainingRepository, transactions);
    }

    public Dashboard execute(LocalDate today) {
        return transactions == null ? read(today) : transactions.inTransaction(() -> read(today));
    }

    private Dashboard read(LocalDate today) {
        Map<TaskId, Task> tasks = new HashMap<>();
        for (Task task : catalog.allTasks()) tasks.put(task.id, task);
        LoadGraphFlowSheets.Result graph = graphSheets == null ? null : graphSheets.execute(today);
        List<FlowRunSummary> allFlowRuns = graph == null
                ? LoadFlowRuns.summaries(tasks, flowRepository, flowRepository.activeFlowRuns()) : graph.runs;
        Map<String, FlowRunSummary> flowById = new HashMap<>();
        for (FlowRunSummary run : allFlowRuns) flowById.put(run.id, run);
        TaskSchedule schedule = new TaskSchedule(catalog.scheduleEntries());
        List<Occurrence> open = this.today.openOccurrences();
        List<Occurrence> completed = this.today.completedOccurrences(today);
        List<String> occurrenceIds = new ArrayList<>();
        for (Occurrence occurrence : open) occurrenceIds.add(occurrence.id);
        for (Occurrence occurrence : completed) occurrenceIds.add(occurrence.id);
        Map<String, List<OccurrenceStep>> steps = groupSteps(
                this.steps.occurrenceStepsFor(occurrenceIds));
        Map<String, List<RewardBooking>> rewards = groupRewards(
                this.today.rewardBookings(occurrenceIds));

        List<DashboardTask> result = new ArrayList<>();
        Set<TaskId> included = new HashSet<>();
        open.sort(Comparator.comparing((Occurrence value) -> value.scheduledOn)
                .thenComparingInt(value -> value.sortOrder).thenComparing(value -> value.id));
        Map<String, Integer> openCounts = new HashMap<>();
        for (Occurrence occurrence : open) {
            if (occurrence.kind == OccurrenceKind.FLOW_STEP) continue;
            String key = occurrence.taskId.value + '|' + occurrence.slot.name();
            openCounts.put(key, openCounts.getOrDefault(key, 0) + 1);
        }
        Set<String> accumulatedSlots = new HashSet<>();
        Map<String, FlowSheetBuilder> flowSheets = new LinkedHashMap<>();
        Map<String, FlowTaskSheetPlacement> placements = new HashMap<>();
        for (FlowTaskSheetPlacement placement : flowRepository.flowTaskSheetPlacements())
            placements.put(sheetKey(placement.taskId, placement.slot), placement);
        for (Occurrence occurrence : graph == null ? open : java.util.Collections.<Occurrence>emptyList()) {
            if (occurrence.kind != OccurrenceKind.FLOW_STEP) continue;
            Task task = tasks.get(occurrence.taskId);
            FlowRunSummary run = flowById.get(occurrence.flowRunId);
            if (task == null || task.archived || task.conditionDone || run == null) continue;
            if (run.state != StepFlowRunState.OFFERED) continue;
            OccurrenceStep current = null;
            for (OccurrenceStep step : steps.getOrDefault(occurrence.id,
                    java.util.Collections.emptyList()))
                if (!step.done && run.currentStepId.equals(step.sourceTemplateId)) {
                    current = step;
                    break;
                }
            if (current == null) continue;
            String key = sheetKey(occurrence.taskId, occurrence.slot);
            FlowTaskSheetPlacement placement = placements.get(key);
            if (placement == null) {
                placement = new FlowTaskSheetPlacement(
                        FlowTaskSheetPlacement.stableId(occurrence.taskId, occurrence.slot),
                        occurrence.taskId, occurrence.slot, today, occurrence.sortOrder);
                placements.put(key, placement);
            }
            FlowTaskSheetPlacement resolvedPlacement = placement;
            flowSheets.computeIfAbsent(key,
                    ignored -> new FlowSheetBuilder(resolvedPlacement, task)).entries.add(
                    FlowTaskSheet.Entry.runStep(run, current));
        }
        List<FlowCandidate> candidates = graph == null ? flowRepository.flowCandidates() : java.util.Collections.emptyList();
        CandidateProjection candidateProjection = candidateProjection(candidates);
        for (FlowCandidate candidate : candidates) {
            Task task = tasks.get(candidate.taskId);
            if (task == null || task.archived || task.conditionDone
                    || !candidateStartable(candidate, candidateProjection)) continue;
            TaskStepTemplate template = candidateProjection.templatesById.get(
                    candidate.seedStepId);
            if (template == null) continue;
            String key = sheetKey(candidate.taskId, candidate.slot);
            FlowTaskSheetPlacement placement = placements.get(key);
            if (placement == null) continue;
            flowSheets.computeIfAbsent(key,
                    ignored -> new FlowSheetBuilder(placement, task)).entries.add(
                    FlowTaskSheet.Entry.candidate(candidate, template));
        }
        List<FlowTaskSheet> visibleFlowSheets = new ArrayList<>(graph == null ? java.util.Collections.emptyList() : graph.sheets);
        for (FlowTaskSheet sheet : visibleFlowSheets) included.add(sheet.task.id);
        for (FlowSheetBuilder builder : flowSheets.values()) {
            builder.entries.sort(Comparator
                    .comparingInt((FlowTaskSheet.Entry value) ->
                            value.kind == FlowTaskSheet.Entry.Kind.RUN_STEP ? 0 : 1)
                    .thenComparingLong(value -> value.queueOrder));
            if (!builder.entries.isEmpty()) {
                visibleFlowSheets.add(new FlowTaskSheet(builder.placement, builder.task,
                        builder.entries));
                included.add(builder.task.id);
            }
        }
        for (Occurrence occurrence : open) {
            Task task = tasks.get(occurrence.taskId);
            if (task == null || task.archived || task.conditionDone) continue;
            if (occurrence.kind == OccurrenceKind.FLOW_STEP) continue;
            if (graph != null && task.kind == de.thonktank.autosecretary.domain.model.TaskKind.FLOW) continue;
            String key = occurrence.taskId.value + '|' + occurrence.slot.name();
            if (occurrence.kind != OccurrenceKind.FLOW_STEP
                    && task.missedOccurrenceMode == MissedOccurrenceMode.ACCUMULATE
                    && !accumulatedSlots.add(key)) continue;
            int backlog = occurrence.kind != OccurrenceKind.FLOW_STEP
                    && task.missedOccurrenceMode == MissedOccurrenceMode.ACCUMULATE
                    ? Math.max(0, openCounts.getOrDefault(key, 1) - 1) : 0;
            result.add(item(task, occurrence, steps, rewards, false, backlog));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.ongoing && !task.conditionText.isEmpty() && !task.archived
                    && !task.conditionDone && !included.contains(task.id)) {
                result.add(new DashboardTask(task, null, new ArrayList<>(), false,
                        java.util.Collections.emptyMap(), 0, schedule.primary(task.id).slot));
                included.add(task.id);
            }
        for (Occurrence occurrence : completed) {
            if (occurrence.kind == OccurrenceKind.FLOW_STEP) continue;
            Task task = tasks.get(occurrence.taskId);
            if (task == null) continue;
            result.add(item(task, occurrence, steps, rewards, true, 0));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.archived && today.equals(task.lastCompletedOn) && !included.contains(task.id))
                result.add(new DashboardTask(task, null, new ArrayList<>(), true,
                        java.util.Collections.emptyMap(), 0, schedule.primary(task.id).slot));
        result.sort(Comparator.comparingInt((DashboardTask item) -> item.done ? 1 : 0)
                .thenComparing(item -> item.occurrence == null
                        ? LocalDate.MAX : item.occurrence.scheduledOn)
                .thenComparingInt(item -> item.occurrence == null
                        ? item.displaySlot.rank : item.occurrence.slot.rank)
                .thenComparingInt(item -> item.occurrence == null
                        ? Integer.MAX_VALUE : item.occurrence.sortOrder)
                .thenComparingLong(item -> item.task.catalogOrder));
        Map<String, ComboProgress> combos = new HashMap<>();
        for (ComboProgress combo : this.today.combos()) combos.put(combo.ownerId, combo);
        List<FlowRunSummary> flowRuns = new ArrayList<>(allFlowRuns);
        Map<String, TrainingContext> trainingContexts = new HashMap<>();
        if (loadTrainingContext != null) for (DashboardTask item : result)
            for (OccurrenceStep step : item.steps)
                if (step.sourceTemplateId != null
                        && !trainingContexts.containsKey(step.sourceTemplateId)) {
                    TrainingContext context = loadTrainingContext.execute(step.sourceTemplateId);
                    if (context != null) trainingContexts.put(step.sourceTemplateId, context);
                }
        return new Dashboard(this.today.xp(), result, combos, flowRuns, visibleFlowSheets,
                trainingContexts);
    }

    private static DashboardTask item(Task task, Occurrence occurrence,
                                      Map<String, List<OccurrenceStep>> steps,
                                      Map<String, List<RewardBooking>> rewards, boolean done,
                                      int backlogCount) {
        List<OccurrenceStep> values = steps.get(occurrence.id);
        Map<String, Integer> stepXp = new HashMap<>();
        Map<String, Integer> stepPlanXp = new HashMap<>();
        int awardedXp = 0;
        List<RewardBooking> bookings = rewards.get(occurrence.id);
        if (bookings != null) for (RewardBooking booking : bookings) {
            if (booking.target == RewardBooking.Target.HEAD) awardedXp += booking.xpDelta;
            else if (booking.occurrenceStepId != null) stepXp.put(booking.occurrenceStepId,
                    stepXp.getOrDefault(booking.occurrenceStepId, 0) + booking.xpDelta);
            if (booking.occurrenceStepId != null && booking.plannedXp != null)
                stepPlanXp.put(booking.occurrenceStepId, booking.plannedXp);
        }
        return new DashboardTask(task, occurrence, values == null ? new ArrayList<>() : values,
                done, stepXp, awardedXp, occurrence.slot, backlogCount, stepPlanXp);
    }

    private static Map<String, List<OccurrenceStep>> groupSteps(List<OccurrenceStep> values) {
        Map<String, List<OccurrenceStep>> result = new HashMap<>();
        for (OccurrenceStep step : values)
            result.computeIfAbsent(step.occurrenceId, ignored -> new ArrayList<>()).add(step);
        return result;
    }

    private static Map<String, List<RewardBooking>> groupRewards(List<RewardBooking> values) {
        Map<String, List<RewardBooking>> result = new HashMap<>();
        for (RewardBooking booking : values)
            result.computeIfAbsent(booking.occurrenceId, ignored -> new ArrayList<>()).add(booking);
        return result;
    }

    private CandidateProjection candidateProjection(List<FlowCandidate> candidates) {
        CandidateProjection result = new CandidateProjection();
        if (candidates.isEmpty()) return result;
        Set<TaskId> uniqueTaskIds = new java.util.LinkedHashSet<>();
        for (FlowCandidate candidate : candidates) uniqueTaskIds.add(candidate.taskId);
        List<TaskId> taskIds = new ArrayList<>(uniqueTaskIds);
        Map<TaskId, List<TaskStepTemplate>> templatesByTask = new HashMap<>();
        for (TaskStepTemplate template : steps.templatesFor(taskIds)) {
            result.templatesById.put(template.id, template);
            templatesByTask.computeIfAbsent(template.taskId, ignored -> new ArrayList<>())
                    .add(template);
        }
        Map<TaskId, List<StepTransition>> transitionsByTask = new HashMap<>();
        for (StepTransition transition : flowRepository.stepTransitionsFor(taskIds)) {
            TaskStepTemplate source = result.templatesById.get(transition.sourceStepId);
            if (source != null) transitionsByTask.computeIfAbsent(source.taskId,
                    ignored -> new ArrayList<>()).add(transition);
        }
        Map<TaskId, List<StepResourceLease>> leasesByTask = new HashMap<>();
        for (StepResourceLease lease : flowRepository.stepResourceLeasesFor(taskIds))
            leasesByTask.computeIfAbsent(lease.taskId, ignored -> new ArrayList<>()).add(lease);
        List<CapacityResource> resources = flowRepository.capacityResources();
        for (CapacityResource resource : resources) result.capacities.put(resource.id, resource);
        for (FlowRunResourceSnapshot resource : flowRepository.consumingFlowResources())
            result.used.put(resource.resourceId,
                    result.used.getOrDefault(resource.resourceId, 0) + resource.units);
        for (TaskId taskId : taskIds) try {
            result.definitions.put(taskId, new StepFlowDefinition(taskId,
                    templatesByTask.getOrDefault(taskId, java.util.Collections.emptyList()),
                    transitionsByTask.getOrDefault(taskId, java.util.Collections.emptyList()),
                    leasesByTask.getOrDefault(taskId, java.util.Collections.emptyList()),
                    resources));
        } catch (IllegalArgumentException invalid) {
            // An invalid current definition leaves all of its persisted candidates unavailable.
        }
        return result;
    }

    private static boolean candidateStartable(FlowCandidate candidate,
                                               CandidateProjection projection) {
        try {
            StepFlowDefinition definition = projection.definitions.get(candidate.taskId);
            if (definition == null) return false;
            List<TaskStepTemplate> path = definition.resolvedPath(candidate.seedStepId);
            Map<String, Integer> required = new HashMap<>();
            for (StepResourceLease lease : definition.leasesForPath(path))
                if (lease.acquireStepId.equals(candidate.seedStepId))
                    required.put(lease.resourceId,
                            required.getOrDefault(lease.resourceId, 0) + lease.units);
            for (Map.Entry<String, Integer> entry : required.entrySet()) {
                CapacityResource capacity = projection.capacities.get(entry.getKey());
                if (capacity == null || projection.used.getOrDefault(entry.getKey(), 0)
                        + entry.getValue()
                        > capacity.capacity) return false;
            }
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String sheetKey(TaskId taskId, TaskSlot slot) {
        return taskId.value + '|' + slot.name();
    }

    private static final class FlowSheetBuilder {
        final FlowTaskSheetPlacement placement;
        final Task task;
        final List<FlowTaskSheet.Entry> entries = new ArrayList<>();

        FlowSheetBuilder(FlowTaskSheetPlacement placement, Task task) {
            this.placement = placement;
            this.task = task;
        }
    }

    private static final class CandidateProjection {
        final Map<String, TaskStepTemplate> templatesById = new HashMap<>();
        final Map<TaskId, StepFlowDefinition> definitions = new HashMap<>();
        final Map<String, CapacityResource> capacities = new HashMap<>();
        final Map<String, Integer> used = new HashMap<>();
    }
}
