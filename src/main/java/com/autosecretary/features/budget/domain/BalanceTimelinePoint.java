package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

public class BalanceTimelinePoint {
    private final LocalDate date;
    private final long balanceCents;

    public BalanceTimelinePoint(LocalDate date, long balanceCents) {
        this.date = date;
        this.balanceCents = balanceCents;
    }

    public LocalDate getDate() {
        return date;
    }

    public long getBalanceCents() {
        return balanceCents;
    }
}
