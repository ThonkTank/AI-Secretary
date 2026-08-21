package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

/** Reorders only the open step slots of one active occurrence. */
final class TodayStepOrder {
    private TodayStepOrder() { }

    static boolean move(TaskRepository repository, String stepId, String beforeStepId) {
        OccurrenceStep moving = repository.findOccurrenceStep(stepId);
        if (moving == null || moving.done) return false;
        Occurrence occurrence = repository.findOccurrence(moving.occurrenceId);
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return false;

        OccurrenceStep before = beforeStepId == null ? null
                : repository.findOccurrenceStep(beforeStepId);
        if (before != null && (before.done
                || !moving.occurrenceId.equals(before.occurrenceId))) return false;
        if (before != null && before.id.equals(moving.id)) return false;

        List<OccurrenceStep> all = repository.occurrenceSteps(moving.occurrenceId);
        List<OccurrenceStep> open = new ArrayList<>();
        for (OccurrenceStep step : all) if (!step.done) open.add(step);
        int source = indexOf(open, moving.id);
        if (source < 0) return false;
        open.remove(source);
        int target = before == null ? open.size() : indexOf(open, before.id);
        if (target < 0) return false;
        open.add(target, moving);

        int openIndex = 0;
        boolean changed = false;
        for (int index = 0; index < all.size(); index++) {
            OccurrenceStep original = all.get(index);
            OccurrenceStep ordered = original.done ? original : open.get(openIndex++);
            if (!ordered.id.equals(original.id) || ordered.position != index) changed = true;
            OccurrenceStep positioned = ordered.relocate(ordered.occurrenceId, index);
            if (positioned.position != ordered.position) changed = true;
            repository.updateOccurrenceStep(positioned);
        }
        return changed;
    }

    static boolean moveToFirstOpen(TaskRepository repository, String stepId) {
        OccurrenceStep moving = repository.findOccurrenceStep(stepId);
        if (moving == null || moving.done) return false;
        String before = null;
        for (OccurrenceStep step : repository.occurrenceSteps(moving.occurrenceId)) {
            if (!step.done && !step.id.equals(stepId)) {
                before = step.id;
                break;
            }
        }
        return before != null && move(repository, stepId, before);
    }

    private static int indexOf(List<OccurrenceStep> values, String id) {
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(id)) return index;
        return -1;
    }
}
