package de.thonktank.autosecretary;

public final class MotionTokens {
    public final long stateChangeDurationMs;
    public final long deferDurationMs;
    public final long forestBreathDurationMs;
    public final long leafFlightDurationMs;
    public final long glintDurationMs;
    public final long afterglowDurationMs;
    public final long dewDurationMs;
    public final float forestBreathDistanceDp;

    public MotionTokens(long stateChangeDurationMs, long deferDurationMs,
                        long forestBreathDurationMs, long leafFlightDurationMs,
                        long glintDurationMs, long afterglowDurationMs,
                        long dewDurationMs,
                        float forestBreathDistanceDp) {
        this.stateChangeDurationMs = stateChangeDurationMs;
        this.deferDurationMs = deferDurationMs;
        this.forestBreathDurationMs = forestBreathDurationMs;
        this.leafFlightDurationMs = leafFlightDurationMs;
        this.glintDurationMs = glintDurationMs;
        this.afterglowDurationMs = afterglowDurationMs;
        this.dewDurationMs = dewDurationMs;
        this.forestBreathDistanceDp = forestBreathDistanceDp;
    }

    public static MotionTokens standard() {
        return new MotionTokens(240L, 240L, 11_000L, 420L, 520L, 1_000L, 180L, 18f);
    }
}
