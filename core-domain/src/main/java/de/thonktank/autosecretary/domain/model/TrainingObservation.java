package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Optional training detail attached atomically to one set result. */
public final class TrainingObservation {
    public enum Safety { NONE, PAIN_OR_TECHNIQUE }
    public enum Origin { USER, SYNTHETIC, LEGACY }

    public final ResistanceLoad load;
    /** 0-5, where 5 means five or more repetitions in reserve; null for legacy data. */
    public final Integer rir;
    public final Safety safety;
    public final Origin origin;

    public TrainingObservation(ResistanceLoad load, Integer rir, Safety safety, Origin origin) {
        if (load == null || safety == null || origin == null
                || rir != null && (rir < 0 || rir > 5))
            throw new IllegalArgumentException("Invalid training observation");
        this.load = load;
        this.rir = rir;
        this.safety = safety;
        this.origin = origin;
    }

    public static TrainingObservation user(ResistanceLoad load, int rir) {
        return new TrainingObservation(load, rir, Safety.NONE, Origin.USER);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TrainingObservation)) return false;
        TrainingObservation value = (TrainingObservation) other;
        return load.equals(value.load) && Objects.equals(rir, value.rir)
                && safety == value.safety && origin == value.origin;
    }

    @Override public int hashCode() { return Objects.hash(load, rir, safety, origin); }
}
