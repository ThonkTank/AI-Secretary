package com.autosecretary.features.task.domain.internal.scoring;

public final class UrgencyState {
    private final double remainingDays;
    private final double requiredDays;
    private final boolean deadlineExpired;

    public UrgencyState(double remainingDays, double requiredDays, boolean deadlineExpired) {
        this.remainingDays = remainingDays;
        this.requiredDays = requiredDays;
        this.deadlineExpired = deadlineExpired;
    }

    public double remainingDays() {
        return remainingDays;
    }

    public double requiredDays() {
        return requiredDays;
    }

    public boolean isDeadlineExpired() {
        return deadlineExpired;
    }
}
