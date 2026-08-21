package de.thonktank.autosecretary.domain.today;

import de.thonktank.autosecretary.domain.model.OccurrenceStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Typed outcome and canonical order of a pure Today-step move. */
public final class TodayStepMoveResult {
    public enum Status {
        MOVED,
        NO_CHANGE,
        STEP_ALREADY_DONE,
        OCCURRENCE_CLOSED,
        INVALID_TARGET,
        TARGET_IN_OTHER_OCCURRENCE
    }

    public final Status status;
    public final List<OccurrenceStep> orderedSteps;
    public final List<String> openStepIds;
    public final List<TodayStepPositionUpdate> positionUpdates;

    TodayStepMoveResult(Status status, List<OccurrenceStep> orderedSteps,
                        List<String> openStepIds,
                        List<TodayStepPositionUpdate> positionUpdates) {
        this.status = status;
        this.orderedSteps = Collections.unmodifiableList(new ArrayList<>(orderedSteps));
        this.openStepIds = Collections.unmodifiableList(new ArrayList<>(openStepIds));
        this.positionUpdates = Collections.unmodifiableList(new ArrayList<>(positionUpdates));
    }

    public boolean moved() { return status == Status.MOVED; }
}
