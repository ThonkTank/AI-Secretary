package de.thonktank.autosecretary.domain.model;

/** User-owned integer combo policy. Persisted events retain the value used at booking time. */
public final class ComboPolicy {
    public static final int DEFAULT_GAIN_POINTS = 2;
    public static final int DEFAULT_DECAY_POINTS = 1;

    public final int gainPoints;
    public final int decayPoints;
    public final ComboDecayTrigger trigger;

    public ComboPolicy(int gainPoints, int decayPoints, ComboDecayTrigger trigger) {
        if (gainPoints < 0 || decayPoints < 0 || trigger == null)
            throw new IllegalArgumentException("Combo values must be non-negative and need a trigger");
        this.gainPoints = gainPoints;
        this.decayPoints = decayPoints;
        this.trigger = trigger;
    }

    public static ComboPolicy defaults() {
        return new ComboPolicy(DEFAULT_GAIN_POINTS, DEFAULT_DECAY_POINTS,
                ComboDecayTrigger.DAILY_OVERDUE);
    }
}
