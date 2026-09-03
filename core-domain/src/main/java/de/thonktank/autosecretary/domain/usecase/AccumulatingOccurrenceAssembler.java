package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Materializes every genuine due date as its own open occurrence. */
final class AccumulatingOccurrenceAssembler {
    private final StepRepository steps;
    private final TodayRepository today;
    private final IdGenerator ids;
    private final StepSnapshotFactory snapshots;

    AccumulatingOccurrenceAssembler(StepRepository steps, TodayRepository today,
                                    IdGenerator ids) {
        this.steps = steps;
        this.today = today;
        this.ids = ids;
        snapshots = new StepSnapshotFactory(ids);
    }

    Result assemble(Task task, DueDatePlanner.Plan planned,
                    Map<TaskSlot, Integer> globalNextOrders,
                    Map<String, Integer> scheduleRanks) {
        Map<String, Occurrence> byDue = new HashMap<>();
        boolean changed = false;
        for (DueDatePlanner.PlannedDue due : planned.dues) {
            int order = scheduleRanks.getOrDefault(task.id.value + '|' + due.slot.name(),
                    globalNextOrders.getOrDefault(due.slot, 0) + 1);
            Occurrence occurrence = new Occurrence(ids.nextId(), task.id, due.scheduledOn,
                    due.slot, OccurrenceState.OPEN, order, null);
            today.insertOccurrence(occurrence);
            byDue.put(key(due.scheduledOn, due.slot), occurrence);
            globalNextOrders.put(due.slot,
                    Math.max(globalNextOrders.getOrDefault(due.slot, 0), order));
            List<OccurrenceStep> steps = new ArrayList<>();
            for (int index = 0; index < due.templates.size(); index++) {
                TaskStepTemplate template = due.templates.get(index);
                steps.add(snapshots.fromTemplate(template, occurrence.id, index));
            }
            if (!steps.isEmpty()) this.steps.insertOccurrenceSteps(steps);
            changed = true;
        }
        return new Result(changed, byDue);
    }

    static String key(java.time.LocalDate date, TaskSlot slot) {
        return date + "|" + slot.name();
    }

    static final class Result {
        final boolean changed;
        final Map<String, Occurrence> byDue;

        Result(boolean changed, Map<String, Occurrence> byDue) {
            this.changed = changed;
            this.byDue = Collections.unmodifiableMap(new HashMap<>(byDue));
        }
    }
}
