package de.thonktank.autosecretary;

public final class MotionTokens {
    public final long stateChangeDurationMs;
    public final long deferDurationMs;
    public final long forestBreathDurationMs;
    public final float forestBreathAlpha;

    public MotionTokens(long stateChangeDurationMs, long deferDurationMs,
                        long forestBreathDurationMs, float forestBreathAlpha) {
        this.stateChangeDurationMs = stateChangeDurationMs;
        this.deferDurationMs = deferDurationMs;
        this.forestBreathDurationMs = forestBreathDurationMs;
        this.forestBreathAlpha = forestBreathAlpha;
    }

    public static MotionTokens standard() {
        return new MotionTokens(240L, 180L, 6_000L, .018f);
    }
}
