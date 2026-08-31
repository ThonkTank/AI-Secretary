package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.MomentSource;
import de.thonktank.autosecretary.SystemMomentSource;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.repository.MaterializationRepository;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.StepFlowRunRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transactional orchestration for the current calendar plan.
 *
 * Calendar planning, stale-work rollover and occurrence creation are deliberately delegated
 * to separate components. This class owns only the transaction boundary and task-level order.
 */
public final class MaterializeDueOccurrences {
    private final MaterializationRepository repository;
    private final ComboObligationRepository obligations;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final IdGenerator ids;
    private final MomentSource moments;
    private final StepFlowDefinitionRepository flowDefinitions;
    private final StepFlowRunRepository flowRuns;
    private final DueDatePlanner planner = new DueDatePlanner();

    public MaterializeDueOccurrences(MaterializationRepository repository,
                              ComboObligationRepository obligations,
                              StepFlowDefinitionRepository flowDefinitions,
                              StepFlowRunRepository flowRuns,
                              TransactionRunner transactions, Clock clock, IdGenerator ids) {
        this(repository, obligations, flowDefinitions, flowRuns, transactions, clock,
                new SystemMomentSource(), ids);
    }

    public MaterializeDueOccurrences(MaterializationRepository repository,
                              ComboObligationRepository obligations,
                              StepFlowDefinitionRepository flowDefinitions,
                              StepFlowRunRepository flowRuns,
                              TransactionRunner transactions, Clock clock,
                              MomentSource moments, IdGenerator ids) {
        this.repository = repository;
        this.obligations = obligations;
        this.transactions = transactions;
        this.clock = clock;
        this.moments = moments;
        this.ids = ids;
        this.flowDefinitions = flowDefinitions;
        this.flowRuns = flowRuns;
    }

    public boolean execute() {
        return transactions.inTransaction(() -> {
            LocalDate today = clock.today();
            List<Task> active = repository.activeTasks();
            List<TaskId> taskIds = new ArrayList<>();
            for (Task task : active) taskIds.add(task.id);
            Map<TaskId, List<TaskStepTemplate>> templates = groupTemplates(
                    repository.templatesFor(taskIds));
            TaskSchedule schedule = new TaskSchedule(repository.scheduleEntriesFor(taskIds));
            Map<String, Integer> scheduleRanks = scheduleRanks(schedule.entries());
            List<Occurrence> allOccurrences = repository.allOccurrences();
            Map<TaskId, List<Occurrence>> occurrences = groupOccurrences(allOccurrences);
            List<String> occurrenceIds = new ArrayList<>();
            for (Occurrence occurrence : allOccurrences) occurrenceIds.add(occurrence.id);
            Map<String, List<OccurrenceStep>> steps = groupSteps(
                    repository.occurrenceStepsFor(occurrenceIds));
            Map<TaskSlot, Integer> globalNextOrders = nextOrders(allOccurrences);
            boolean changed = false;
            for (Task task : active)
                changed |= prepareTask(task, today,
                        occurrences.getOrDefault(task.id, Collections.emptyList()),
                        templates.getOrDefault(task.id, Collections.emptyList()),
                        globalNextOrders, steps, schedule, scheduleRanks);
            return changed;
        });
    }

    private boolean prepareTask(Task task, LocalDate today, List<Occurrence> history,
                                List<TaskStepTemplate> templates,
                                Map<TaskSlot, Integer> globalNextOrders,
                                Map<String, List<OccurrenceStep>> stepsByOccurrence,
                                TaskSchedule schedule,
                                Map<String, Integer> scheduleRanks) {
        DueDatePlanner.Plan planned = planner.throughToday(
                task, schedule, today, history, templates);
        FlowMaterialization flowMaterialization = materializeFlowRuns(task, templates, planned,
                scheduleRanks);
        planned = flowMaterialization.ordinaryPlan;
        boolean changed = flowMaterialization.changed;
        boolean hasOpen;
        if (task.missedOccurrenceMode == MissedOccurrenceMode.ACCUMULATE) {
            AccumulatingOccurrenceAssembler.Result assembled =
                    new AccumulatingOccurrenceAssembler(repository, ids).assemble(task, planned,
                            globalNextOrders, scheduleRanks);
            changed |= assembled.changed;
            changed |= materializeAccumulatedObligations(task, planned, assembled.byDue);
            hasOpen = hasOpen(history) || assembled.changed;
        } else {
            OccurrenceCarryForward.Result carry = new OccurrenceCarryForward()
                    .collect(repository, today, history, stepsByOccurrence);
            changed |= carry.changed;
            OccurrenceAssembler.Result assembled = new OccurrenceAssembler(repository, ids).assemble(
                    task, today, history, globalNextOrders, carry, planned, schedule, scheduleRanks);
            changed |= assembled.changed;
            changed |= materializeObligations(task, planned, assembled.activeBySlot);
            hasOpen = !carry.open.isEmpty() || !assembled.activeBySlot.isEmpty();
        }

        if (planned.nextDueChanged || planned.materializedCount > 0) {
            repository.updateTask(task.afterPlanning(planned.nextDue, planned.materializedCount));
            changed = true;
        } else if (task.recurrence == Recurrence.ONCE && !hasOpen) {
            repository.updateTask(task.withOccurrenceState(true, null, task.lastScheduledOn,
                    task.lastCompletedOn, task.hasCompletedOccurrence));
            changed = true;
        }
        return changed;
    }

    private FlowMaterialization materializeFlowRuns(Task task,
                                                    List<TaskStepTemplate> templates,
                                                    DueDatePlanner.Plan planned,
                                                    Map<String, Integer> scheduleRanks) {
        boolean hasFollowUp = false;
        for (TaskStepTemplate template : templates)
            if (template.activationKind == StepActivationKind.FOLLOW_UP) {
                hasFollowUp = true;
                break;
            }
        if (!hasFollowUp) return new FlowMaterialization(planned, false);
        List<StepTransition> transitions = flowDefinitions.stepTransitions(task.id);
        List<StepResourceLease> leases = flowDefinitions.stepResourceLeases(task.id);
        if (transitions.isEmpty() && leases.isEmpty())
            return new FlowMaterialization(planned, false);

        StepFlowDefinition definition = new StepFlowDefinition(task.id, templates, transitions,
                leases, flowDefinitions.capacityResources());
        Map<TaskSlot, List<TaskStepTemplate>> ordinaryBySlot = new LinkedHashMap<>();
        List<DueDatePlanner.PlannedDue> ordinaryDues = new ArrayList<>();
        boolean changed = false;
        for (DueDatePlanner.PlannedDue due : planned.dues) {
            List<TaskStepTemplate> ordinary = new ArrayList<>();
            boolean containedFlow = false;
            for (TaskStepTemplate template : due.templates) {
                if (!definition.participates(template.id)) {
                    ordinary.add(template);
                    continue;
                }
                containedFlow = true;
                String sourceKey = "flow:" + task.id.value + ':' + template.id + ':'
                        + due.scheduledOn + ':' + due.slot.storageCode;
                if (flowRuns.findFlowRunBySourceKey(sourceKey) != null) continue;
                long rank = scheduleRanks.getOrDefault(
                        task.id.value + '|' + due.slot.name(), 0);
                long queueOrder = rank * 1_000_000_000L
                        + due.scheduledOn.toEpochDay() * 1_000L + template.position;
                FlowRunSnapshot snapshot = new CreateFlowRunSnapshot(ids).execute(definition,
                        template.id, sourceKey, due.scheduledOn, due.slot, queueOrder,
                        moments.nowEpochMillis());
                changed |= flowRuns.insertFlowRun(snapshot);
            }
            if (!containedFlow || !ordinary.isEmpty()) {
                ordinaryDues.add(new DueDatePlanner.PlannedDue(
                        due.scheduledOn, due.slot, ordinary));
                addPlannedTemplates(ordinaryBySlot, due.slot, ordinary);
            }
        }
        DueDatePlanner.Plan ordinaryPlan = new DueDatePlanner.Plan(ordinaryBySlot, ordinaryDues,
                planned.nextDue, planned.materializedCount, planned.nextDueChanged);
        return new FlowMaterialization(ordinaryPlan, changed);
    }

    private static void addPlannedTemplates(Map<TaskSlot, List<TaskStepTemplate>> bySlot,
                                            TaskSlot slot,
                                            List<TaskStepTemplate> templates) {
        if (templates.isEmpty()) {
            if (!bySlot.containsKey(slot)) bySlot.put(slot, null);
            return;
        }
        List<TaskStepTemplate> selected = bySlot.get(slot);
        if (selected == null) {
            selected = new ArrayList<>();
            bySlot.put(slot, selected);
        }
        for (TaskStepTemplate template : templates) {
            boolean present = false;
            for (TaskStepTemplate existing : selected)
                if (existing.id.equals(template.id)) {
                    present = true;
                    break;
                }
            if (!present) selected.add(template);
        }
    }

    private static final class FlowMaterialization {
        final DueDatePlanner.Plan ordinaryPlan;
        final boolean changed;

        FlowMaterialization(DueDatePlanner.Plan ordinaryPlan, boolean changed) {
            this.ordinaryPlan = ordinaryPlan;
            this.changed = changed;
        }
    }

    private boolean materializeAccumulatedObligations(Task task, DueDatePlanner.Plan planned,
                                                       Map<String, Occurrence> byDue) {
        if (planned.dues.isEmpty()) return false;
        List<ComboObligation> writes = new ArrayList<>();
        for (DueDatePlanner.PlannedDue due : planned.dues) {
            Occurrence occurrence = byDue.get(
                    AccumulatingOccurrenceAssembler.key(due.scheduledOn, due.slot));
            if (occurrence == null) continue;
            writes.add(ComboObligation.open(ComboProgress.taskOwner(task.id), task.id,
                    ComboProgress.Kind.TASK, due.slot, due.scheduledOn, occurrence.id));
            for (TaskStepTemplate template : due.templates)
                writes.add(ComboObligation.open(ComboProgress.stepOwner(template.id), task.id,
                        ComboProgress.Kind.STEP, due.slot, due.scheduledOn, occurrence.id));
        }
        obligations.insertComboObligations(writes);
        return !writes.isEmpty();
    }

    private static boolean hasOpen(List<Occurrence> history) {
        for (Occurrence value : history) if (value.state == OccurrenceState.OPEN) return true;
        return false;
    }

    private boolean materializeObligations(Task task, DueDatePlanner.Plan planned,
                                           Map<TaskSlot, Occurrence> openBySlot) {
        if (planned.dues.isEmpty()) return false;
        List<ComboObligation> writes = new ArrayList<>();
        for (DueDatePlanner.PlannedDue due : planned.dues) {
            Occurrence occurrence = openBySlot.get(due.slot);
            if (occurrence == null) continue;
            writes.add(ComboObligation.open(ComboProgress.taskOwner(task.id), task.id,
                    ComboProgress.Kind.TASK, due.slot, due.scheduledOn, occurrence.id));
            for (TaskStepTemplate template : due.templates)
                writes.add(ComboObligation.open(ComboProgress.stepOwner(template.id), task.id,
                        ComboProgress.Kind.STEP, due.slot, due.scheduledOn, occurrence.id));
        }
        obligations.insertComboObligations(writes);
        return !writes.isEmpty();
    }

    private static Map<TaskSlot, Integer> nextOrders(List<Occurrence> values) {
        Map<TaskSlot, Integer> result = new HashMap<>();
        for (Occurrence value : values) if (value.state == de.thonktank.autosecretary.domain.model.OccurrenceState.OPEN)
            result.put(value.slot, Math.max(result.getOrDefault(value.slot, 0), value.sortOrder));
        return result;
    }

    private static Map<TaskId, List<Occurrence>> groupOccurrences(List<Occurrence> values) {
        Map<TaskId, List<Occurrence>> result = new HashMap<>();
        for (Occurrence value : values)
            result.computeIfAbsent(value.taskId, ignored -> new ArrayList<>()).add(value);
        return result;
    }

    private static Map<String, List<OccurrenceStep>> groupSteps(List<OccurrenceStep> values) {
        Map<String, List<OccurrenceStep>> result = new HashMap<>();
        for (OccurrenceStep value : values)
            result.computeIfAbsent(value.occurrenceId, ignored -> new ArrayList<>()).add(value);
        return result;
    }

    private static Map<TaskId, List<TaskStepTemplate>> groupTemplates(
            List<TaskStepTemplate> values) {
        Map<TaskId, List<TaskStepTemplate>> result = new HashMap<>();
        for (TaskStepTemplate value : values)
            result.computeIfAbsent(value.taskId, ignored -> new ArrayList<>()).add(value);
        return result;
    }

    private static Map<String, Integer> scheduleRanks(List<TaskScheduleEntry> values) {
        List<TaskScheduleEntry> ordered = new ArrayList<>(values);
        ordered.sort(java.util.Comparator
                .comparingInt((TaskScheduleEntry value) -> value.slot.rank)
                .thenComparingLong(value -> value.displayOrder).thenComparing(value -> value.id));
        Map<String, Integer> result = new HashMap<>();
        Map<TaskSlot, Integer> next = new HashMap<>();
        for (TaskScheduleEntry value : ordered) {
            int rank = next.getOrDefault(value.slot, 0) + 1;
            next.put(value.slot, rank);
            result.put(value.taskId.value + '|' + value.slot.name(), rank);
        }
        return result;
    }
}
