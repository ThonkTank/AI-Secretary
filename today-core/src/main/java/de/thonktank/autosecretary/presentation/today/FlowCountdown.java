package de.thonktank.autosecretary.presentation.today;

/** Pure wall-clock projection; a unit transition never changes the radial denominator. */
public final class FlowCountdown {
    public final long value;
    public final Unit unit;
    public final float fraction;
    public enum Unit { DAYS, HOURS, MINUTES, SECONDS }
    public FlowCountdown(long start, long end, long now) {
        long remaining = Math.max(0L, end - now);
        fraction = end <= start ? 0f : Math.max(0f, Math.min(1f,
                remaining / (float) (end - start)));
        long seconds = remaining / 1000L;
        if (seconds >= 86400) { value = seconds / 86400; unit = Unit.DAYS; }
        else if (seconds >= 3600) { value = seconds / 3600; unit = Unit.HOURS; }
        else if (seconds >= 60) { value = seconds / 60; unit = Unit.MINUTES; }
        else { value = remaining > 0 ? Math.max(1L, seconds) : 0; unit = Unit.SECONDS; }
    }
}
