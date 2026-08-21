package de.thonktank.autosecretary.domain.today;

/** One positions-only persistence change for an occurrence step. */
public final class TodayStepPositionUpdate {
    public final String stepId;
    public final int position;

    public TodayStepPositionUpdate(String stepId, int position) {
        if (stepId == null || stepId.isEmpty() || position < 0)
            throw new IllegalArgumentException("Step id and non-negative position are required");
        this.stepId = stepId;
        this.position = position;
    }
}
