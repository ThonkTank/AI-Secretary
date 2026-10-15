package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;

/** Partial Room update: execution state, identities, rewards and times are never written. */
public final class StepDetailsUpdate {
    @NonNull public String id;
    @NonNull public String text;
    @NonNull public String note;
    @NonNull public String amountKind;
    public Integer plannedSets, plannedReps, plannedDurationSeconds;
    @NonNull public String restTimerMode;
    public Integer restTimerSeconds;

    public StepDetailsUpdate(String id, String text, String note, String amountKind,
                             Integer plannedSets, Integer plannedReps, Integer plannedDurationSeconds,
                             String restTimerMode, Integer restTimerSeconds) {
        this.id = id; this.text = text; this.note = note; this.amountKind = amountKind;
        this.plannedSets = plannedSets; this.plannedReps = plannedReps;
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.restTimerMode = restTimerMode; this.restTimerSeconds = restTimerSeconds;
    }
}
