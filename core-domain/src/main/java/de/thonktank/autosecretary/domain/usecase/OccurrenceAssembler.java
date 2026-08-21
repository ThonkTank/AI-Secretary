package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.CarryForwardReason;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.repository.MaterializationRepository;

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
    private final MaterializationRepository repository;
    private final IdGenerator ids;

    OccurrenceAssembler(MaterializationRepository repository, IdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    boolean assemble(Task task, LocalDate today, List<Occurrence> history,
                     Map<TaskSlot, Integer> globalNextOrders,
                     OccurrenceCarryForward.Result carry,
                     DueDatePlanner.Plan planned, TaskSchedule schedule,
                     Map<String, Integer> scheduleRanks) {
        Set<TaskSlot> targetSlots = new LinkedHashSet<>();
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
            List<OccurrenceStep> steps = new ArrayList<>();
            Set<String> sourceIds = new HashSet<>();
            List<OccurrenceStep> carried = carry.carry.get(slot);
            if (carried != null) for (OccurrenceStep step : carried) {
                steps.add(copyStep(step, ids.nextId(), "pending:" + task.id.value,
                        carry.originOccurrenceIds.get(slot)));
                if (step.sourceTemplateId != null) sourceIds.add(step.sourceTemplateId);
            }
            List<TaskStepTemplate> fresh = planned.stepsBySlot.get(slot);
            if (fresh != null) for (TaskStepTemplate template : fresh) {
                if (sourceIds.contains(template.id)) continue;
                steps.add(snapshot(template, ids.nextId(), "pending:" + task.id.value));
                sourceIds.add(template.id);
            }
            if (steps.isEmpty() && carried == null && fresh == null
                    && !planned.stepsBySlot.containsKey(slot)) continue;
            int order = carried != null && !carried.isEmpty()
                    ? OccurrenceCarryForward.carryOrder(history, slot)
                    : scheduleRanks.getOrDefault(task.id.value + '|' + slot.name(),
                    globalNextOrders.getOrDefault(slot, 0) + 1);
            Occurrence occurrence = new Occurrence(ids.nextId(), task.id, today, slot,
                    OccurrenceState.OPEN, order, null);
            repository.insertOccurrence(occurrence);
            globalNextOrders.put(slot, Math.max(globalNextOrders.getOrDefault(slot, 0), order));
            if (!steps.isEmpty()) {
                List<OccurrenceStep> positioned = new ArrayList<>();
                for (int index = 0; index < steps.size(); index++) {
                    OccurrenceStep step = steps.get(index);
                    positioned.add(new OccurrenceStep(step.id, occurrence.id, index, step.text,
                            step.done, step.amount, step.note,
                            step.repetitionProgress == null ? Collections.emptyList()
                                    : step.repetitionProgress.actualRepetitions,
                            step.sourceTemplateId, step.comboOwnerId,
                            step.originOccurrenceId, step.carryForwardReason));
                }
                repository.insertOccurrenceSteps(positioned);
            }
            changed = true;
        }
        return changed;
    }

    private OccurrenceStep copyStep(OccurrenceStep step, String id, String comboOwner,
                                    String originOccurrenceId) {
        return new OccurrenceStep(id, "pending", 0, step.text, false, step.amount, step.note,
                step.repetitionProgress == null ? Collections.emptyList()
                        : step.repetitionProgress.actualRepetitions,
                step.sourceTemplateId,
                step.comboOwnerId == null ? comboOwner : step.comboOwnerId,
                originOccurrenceId, CarryForwardReason.UNFINISHED_STEP);
    }

    private OccurrenceStep snapshot(TaskStepTemplate template, String id, String comboOwner) {
        return new OccurrenceStep(id, "pending", 0, template.text, false, template.amount,
                template.note, Collections.emptyList(), template.id,
                "step:" + template.id);
    }
}
