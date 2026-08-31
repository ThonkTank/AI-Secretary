package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Immutable load and effort target copied into executable step snapshots. */
public final class TrainingPrescription {
    public final ResistanceLoad load;
    public final int targetRir;

    public TrainingPrescription(ResistanceLoad load, int targetRir) {
        if (load == null || load.mode == ResistanceLoad.Mode.UNSPECIFIED)
            throw new IllegalArgumentException("A training prescription needs a resistance mode");
        if (targetRir < 0 || targetRir > 5)
            throw new IllegalArgumentException("Target RIR must be between zero and five");
        this.load = load;
        this.targetRir = targetRir;
    }

    @Override public boolean equals(Object other) {
        return other instanceof TrainingPrescription
                && load.equals(((TrainingPrescription) other).load)
                && targetRir == ((TrainingPrescription) other).targetRir;
    }

    @Override public int hashCode() { return Objects.hash(load, targetRir); }
}
