package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates one active occurrence per target slot from carry-forward and fresh templates. */
final class OccurrenceAssembler {
    private final StepRepository steps;
    private final TodayRepository today;
    private final IdGenerator ids;
    private final StepSnapshotFactory snapshots;

    OccurrenceAssembler(StepRepository steps, TodayRepository today, IdGenerator ids) {
        this.steps = steps;
        this.today = today;
        this.ids = ids;
        snapshots = new StepSnapshotFactory(ids);
    }

    Result assemble(Task task, LocalDate today, List<Occurrence> history,
                     Map<TaskSlot, Integer> globalNextOrders,
                     OccurrenceCarryForward.Result carry,
                     DueDatePlanner.Plan planned, TaskSchedule schedule,
                     Map<String, Integer> scheduleRanks) {
        Set<TaskSlot> targetSlots = new LinkedHashSet<>();
        Map<TaskSlot, Occurrence> active = new java.util.HashMap<>(carry.open);
        List<TaskSlot> configuredSlots = schedule.slots(task.id);
        for (TaskSlot slot : configuredSlots)
            if (carry.carry.containsKey(slot) || planned.stepsBySlot.containsKey(slot))
                targetSlots.add(slot);
        targetSlots.addAll(carry.carry.keySet());
        targetSlots.addAll(planned.stepsBySlot.keySet());
        for (Map.Entry<TaskSlot, List<TaskStepTemplate>> entry : planned.stepsBySlot.entrySet())
            if (entry.getValue() == null) targetSlots.add(entry.getKey());

        boolean changed = false;
        for (TaskSlot slot : targetSlots) {
            if (carry.open.containsKey(slot)) continue;
            Set<String> sourceIds = new HashSet<>();
            List<OccurrenceStep> carried = carry.carry.get(slot);
            if (carried != null) for (OccurrenceStep step : carried) {
                if (step.sourceTemplateId != null) sourceIds.add(step.sourceTemplateId);
            }
            List<TaskStepTemplate> freshSnapshots = new ArrayList<>();
            List<TaskStepTemplate> fresh = planned.stepsBySlot.get(slot);
            if (fresh != null) for (TaskStepTemplate template : fresh) {
                if (sourceIds.contains(template.id)) continue;
                freshSnapshots.add(template);
                sourceIds.add(template.id);
            }
            boolean hasSnapshots = carried != null && !carried.isEmpty()
                    || !freshSnapshots.isEmpty();
            if (!hasSnapshots && carried == null && fresh == null
                    && !planned.stepsBySlot.containsKey(slot)) continue;
            int order = carried != null && !carried.isEmpty()
                    ? OccurrenceCarryForward.carryOrder(history, slot)
                    : scheduleRanks.getOrDefault(task.id.value + '|' + slot.name(),
                    globalNextOrders.getOrDefault(slot, 0) + 1);
            Occurrence occurrence = new Occurrence(ids.nextId(), task.id, today, slot,
                    OccurrenceState.OPEN, order, null);
            this.today.insertOccurrence(occurrence);
            active.put(slot, occurrence);
            globalNextOrders.put(slot, Math.max(globalNextOrders.getOrDefault(slot, 0), order));
            if (hasSnapshots) {
                List<OccurrenceStep> materialized = new ArrayList<>();
                if (carried != null) for (OccurrenceStep step : carried)
                    materialized.add(snapshots.carryForward(step, occurrence.id,
                            materialized.size(), carry.originOccurrenceIds.get(slot)));
                for (TaskStepTemplate template : freshSnapshots)
                    materialized.add(snapshots.fromTemplate(template, occurrence.id,
                            materialized.size()));
                steps.insertOccurrenceSteps(materialized);
            }
            changed = true;
        }
        return new Result(changed, active);
    }

    static final class Result {
        final boolean changed;
        final Map<TaskSlot, Occurrence> activeBySlot;

        Result(boolean changed, Map<TaskSlot, Occurrence> activeBySlot) {
            this.changed = changed;
            this.activeBySlot = Collections.unmodifiableMap(
                    new java.util.HashMap<>(activeBySlot));
        }
    }
}
