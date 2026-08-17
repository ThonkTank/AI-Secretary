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
    public final float forestBreathVerticalDp;
    public final float leafFlightXDp;
    public final float leafFlightYDp;
    public final float leafFlightRotationDegrees;
    public final float focusEnterDistanceDp;

    public MotionTokens(long stateChangeDurationMs, long deferDurationMs,
                        long forestBreathDurationMs, long leafFlightDurationMs,
                        long glintDurationMs, long afterglowDurationMs,
                        long dewDurationMs,
                        float forestBreathDistanceDp, float forestBreathVerticalDp,
                        float leafFlightXDp, float leafFlightYDp,
                        float leafFlightRotationDegrees, float focusEnterDistanceDp) {
        this.stateChangeDurationMs = stateChangeDurationMs;
        this.deferDurationMs = deferDurationMs;
        this.forestBreathDurationMs = forestBreathDurationMs;
        this.leafFlightDurationMs = leafFlightDurationMs;
        this.glintDurationMs = glintDurationMs;
        this.afterglowDurationMs = afterglowDurationMs;
        this.dewDurationMs = dewDurationMs;
        this.forestBreathDistanceDp = forestBreathDistanceDp;
        this.forestBreathVerticalDp = forestBreathVerticalDp;
        this.leafFlightXDp = leafFlightXDp;
        this.leafFlightYDp = leafFlightYDp;
        this.leafFlightRotationDegrees = leafFlightRotationDegrees;
        this.focusEnterDistanceDp = focusEnterDistanceDp;
    }

    public static MotionTokens standard() {
        return new MotionTokens(240L, 240L, 11_000L, 420L, 520L, 1_000L,
                180L, 18f, 7f, 210f, 120f, 200f, 18f);
    }
}
