package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.CarryForwardReason;
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

    OccurrenceAssembler(StepRepository steps, TodayRepository today, IdGenerator ids) {
        this.steps = steps;
        this.today = today;
        this.ids = ids;
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
            List<OccurrenceStep> snapshots = new ArrayList<>();
            Set<String> sourceIds = new HashSet<>();
            List<OccurrenceStep> carried = carry.carry.get(slot);
            if (carried != null) for (OccurrenceStep step : carried) {
                snapshots.add(copyStep(step, ids.nextId(), "pending:" + task.id.value,
                        carry.originOccurrenceIds.get(slot)));
                if (step.sourceTemplateId != null) sourceIds.add(step.sourceTemplateId);
            }
            List<TaskStepTemplate> fresh = planned.stepsBySlot.get(slot);
            if (fresh != null) for (TaskStepTemplate template : fresh) {
                if (sourceIds.contains(template.id)) continue;
                snapshots.add(snapshot(template, ids.nextId(), "pending:" + task.id.value));
                sourceIds.add(template.id);
            }
            if (snapshots.isEmpty() && carried == null && fresh == null
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
            if (!snapshots.isEmpty()) {
                List<OccurrenceStep> positioned = new ArrayList<>();
                for (int index = 0; index < snapshots.size(); index++) {
                    OccurrenceStep step = snapshots.get(index);
                    positioned.add(new OccurrenceStep(step.id, occurrence.id, index, step.text,
                            step.done, step.prescription, step.note,
                            step.repetitionProgress == null ? Collections.emptyList()
                                    : step.repetitionProgress.results,
                            step.sourceTemplateId, step.comboOwnerId,
                            step.originOccurrenceId, step.carryForwardReason));
                }
                steps.insertOccurrenceSteps(positioned);
            }
            changed = true;
        }
        return new Result(changed, active);
    }

    private OccurrenceStep copyStep(OccurrenceStep step, String id, String comboOwner,
                                    String originOccurrenceId) {
        return new OccurrenceStep(id, "pending", 0, step.text, false, step.prescription, step.note,
                step.repetitionProgress == null ? Collections.emptyList()
                        : step.repetitionProgress.results,
                step.sourceTemplateId,
                step.comboOwnerId == null ? comboOwner : step.comboOwnerId,
                originOccurrenceId, CarryForwardReason.UNFINISHED_STEP);
    }

    private OccurrenceStep snapshot(TaskStepTemplate template, String id, String comboOwner) {
        return new OccurrenceStep(id, "pending", 0, template.text, false,
                template.prescription, template.note, Collections.emptyList(), template.id,
                "step:" + template.id, null, CarryForwardReason.NONE);
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
