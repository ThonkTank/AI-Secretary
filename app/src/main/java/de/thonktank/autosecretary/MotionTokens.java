package de.thonktank.autosecretary;

public final class MotionTokens {
    public final long stateChangeDurationMs;
    public final long deferDurationMs;
    public final long forestBreathDurationMs;
    public final long leafFlightDurationMs;
    public final long glintDurationMs;
    public final long afterglowDurationMs;
    public final long dewDurationMs;
    public final long rewardFlightDurationMs;
    public final long vesselFillDurationMs;
    public final long vesselPulseDurationMs;
    public final long headerAfterglowDurationMs;
    public final float forestBreathDistanceDp;
    public final float forestBreathVerticalDp;
    public final float leafFlightXDp;
    public final float leafFlightYDp;
    public final float leafFlightRotationDegrees;
    public final float focusEnterDistanceDp;

    public MotionTokens(long stateChangeDurationMs, long deferDurationMs,
                        long forestBreathDurationMs, long leafFlightDurationMs,
                        long glintDurationMs, long afterglowDurationMs,
                        long dewDurationMs, long rewardFlightDurationMs,
                        long vesselFillDurationMs, long vesselPulseDurationMs,
                        long headerAfterglowDurationMs,
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
        this.rewardFlightDurationMs = rewardFlightDurationMs;
        this.vesselFillDurationMs = vesselFillDurationMs;
        this.vesselPulseDurationMs = vesselPulseDurationMs;
        this.headerAfterglowDurationMs = headerAfterglowDurationMs;
        this.forestBreathDistanceDp = forestBreathDistanceDp;
        this.forestBreathVerticalDp = forestBreathVerticalDp;
        this.leafFlightXDp = leafFlightXDp;
        this.leafFlightYDp = leafFlightYDp;
        this.leafFlightRotationDegrees = leafFlightRotationDegrees;
        this.focusEnterDistanceDp = focusEnterDistanceDp;
    }

    public static MotionTokens standard() {
        return new MotionTokens(240L, 240L, 11_000L, 420L, 520L, 1_000L,
                180L, 470L, 240L, 2_400L, 600L,
                18f, 7f, 210f, 120f, 200f, 18f);
    }
}
