package de.thonktank.autosecretary.timer;

import java.util.Objects;

/** Persisted user-started countdown attached to one occurrence step. */
public final class TimerSession {
    public enum Kind { DURATION, REST }
    public enum State { RUNNING, PAUSED, FINISHED }

    public final String id;
    public final String stepId;
    public final String title;
    public final Kind kind;
    public final State state;
    public final int totalSeconds;
    public final long remainingMillis;
    public final long targetElapsedRealtime;
    public final long targetEpochMillis;
    public final int notificationId;
    public final boolean completionObserved;

    public TimerSession(String id, String stepId, String title, Kind kind, State state,
                        int totalSeconds, long remainingMillis, long targetElapsedRealtime,
                        long targetEpochMillis, int notificationId,
                        boolean completionObserved) {
        if (id == null || id.isEmpty() || stepId == null || stepId.isEmpty()
                || title == null || title.trim().isEmpty() || kind == null || state == null
                || totalSeconds < 1 || remainingMillis < 0 || notificationId < 1)
            throw new IllegalArgumentException("Complete timer session is required");
        this.id = id;
        this.stepId = stepId;
        this.title = title.trim();
        this.kind = kind;
        this.state = state;
        this.totalSeconds = totalSeconds;
        this.remainingMillis = remainingMillis;
        this.targetElapsedRealtime = targetElapsedRealtime;
        this.targetEpochMillis = targetEpochMillis;
        this.notificationId = notificationId;
        this.completionObserved = completionObserved;
    }

    public long remainingAt(long elapsedRealtime) {
        return state == State.RUNNING
                ? Math.max(0, targetElapsedRealtime - elapsedRealtime) : remainingMillis;
    }

    public TimerSession paused(long elapsedRealtime) {
        if (state != State.RUNNING) return this;
        return copy(State.PAUSED, remainingAt(elapsedRealtime), 0, 0, completionObserved);
    }

    public TimerSession resumed(long elapsedRealtime, long epochMillis) {
        if (state != State.PAUSED || remainingMillis <= 0) return this;
        return copy(State.RUNNING, remainingMillis, elapsedRealtime + remainingMillis,
                epochMillis + remainingMillis, false);
    }

    public TimerSession finished() {
        return copy(State.FINISHED, 0, targetElapsedRealtime, targetEpochMillis, false);
    }

    public TimerSession observed() {
        return copy(state, remainingMillis, targetElapsedRealtime, targetEpochMillis, true);
    }

    private TimerSession copy(State changedState, long changedRemaining, long elapsed, long epoch,
                              boolean observed) {
        return new TimerSession(id, stepId, title, kind, changedState, totalSeconds,
                changedRemaining, elapsed, epoch, notificationId, observed);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TimerSession)) return false;
        TimerSession value = (TimerSession) other;
        return id.equals(value.id) && stepId.equals(value.stepId) && title.equals(value.title)
                && kind == value.kind && state == value.state
                && totalSeconds == value.totalSeconds && remainingMillis == value.remainingMillis
                && targetElapsedRealtime == value.targetElapsedRealtime
                && targetEpochMillis == value.targetEpochMillis
                && notificationId == value.notificationId
                && completionObserved == value.completionObserved;
    }

    @Override public int hashCode() {
        return Objects.hash(id, stepId, title, kind, state, totalSeconds, remainingMillis,
                targetElapsedRealtime, targetEpochMillis, notificationId, completionObserved);
    }
}
