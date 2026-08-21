package de.thonktank.autosecretary.domain.today;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Repository-free permutation of open slots in one occurrence snapshot. */
public final class TodayStepOrder {
    private TodayStepOrder() { }

    public static TodayStepMoveResult move(TodayOccurrenceSnapshot snapshot,
                                           String movingStepId, String beforeStepId) {
        Occurrence occurrence = snapshot.occurrence;
        OccurrenceStep beforeStep = snapshot.resolvedTarget;
        List<OccurrenceStep> current = new ArrayList<>(snapshot.steps);
        OccurrenceStep moving = find(current, movingStepId);
        if (moving == null)
            return result(TodayStepMoveResult.Status.INVALID_TARGET, current);
        if (moving.done)
            return result(TodayStepMoveResult.Status.STEP_ALREADY_DONE, current);
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN
                || !occurrence.id.equals(moving.occurrenceId))
            return result(TodayStepMoveResult.Status.OCCURRENCE_CLOSED, current);
        if (beforeStepId != null && beforeStep == null)
            return result(TodayStepMoveResult.Status.INVALID_TARGET, current);
        if (beforeStep != null && !moving.occurrenceId.equals(beforeStep.occurrenceId))
            return result(TodayStepMoveResult.Status.TARGET_IN_OTHER_OCCURRENCE, current);
        if (beforeStep != null && (beforeStep.done || find(current, beforeStep.id) == null))
            return result(TodayStepMoveResult.Status.INVALID_TARGET, current);
        if (beforeStep != null && beforeStep.id.equals(moving.id))
            return result(TodayStepMoveResult.Status.NO_CHANGE, current);

        List<OccurrenceStep> open = open(current);
        int source = indexOf(open, moving.id);
        if (source < 0) return result(TodayStepMoveResult.Status.INVALID_TARGET, current);
        open.remove(source);
        int target = beforeStepId == null ? open.size() : indexOf(open, beforeStep.id);
        if (target < 0) return result(TodayStepMoveResult.Status.INVALID_TARGET, current);
        open.add(target, moving);
        if (sameIds(open(current), open))
            return result(TodayStepMoveResult.Status.NO_CHANGE, current);

        Map<String, OccurrenceStep> originals = new HashMap<>();
        for (OccurrenceStep step : current) originals.put(step.id, step);
        List<OccurrenceStep> ordered = new ArrayList<>();
        List<TodayStepPositionUpdate> updates = new ArrayList<>();
        int openIndex = 0;
        for (int position = 0; position < current.size(); position++) {
            OccurrenceStep value = current.get(position).done
                    ? current.get(position) : open.get(openIndex++);
            OccurrenceStep positioned = value.position == position
                    ? value : value.relocate(value.occurrenceId, position);
            ordered.add(positioned);
            OccurrenceStep original = originals.get(positioned.id);
            if (original.position != position)
                updates.add(new TodayStepPositionUpdate(positioned.id, position));
        }
        return new TodayStepMoveResult(TodayStepMoveResult.Status.MOVED, ordered,
                ids(open(ordered)), updates);
    }

    private static TodayStepMoveResult result(TodayStepMoveResult.Status status,
                                               List<OccurrenceStep> current) {
        return new TodayStepMoveResult(status, current, ids(open(current)),
                new ArrayList<>());
    }

    private static OccurrenceStep find(List<OccurrenceStep> values, String id) {
        if (id == null) return null;
        for (OccurrenceStep value : values) if (value.id.equals(id)) return value;
        return null;
    }

    private static List<OccurrenceStep> open(List<OccurrenceStep> values) {
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep value : values) if (!value.done) result.add(value);
        return result;
    }

    private static int indexOf(List<OccurrenceStep> values, String id) {
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(id)) return index;
        return -1;
    }

    private static List<String> ids(List<OccurrenceStep> values) {
        List<String> result = new ArrayList<>();
        for (OccurrenceStep value : values) result.add(value.id);
        return result;
    }

    private static boolean sameIds(List<OccurrenceStep> left, List<OccurrenceStep> right) {
        return ids(left).equals(ids(right));
    }
}
