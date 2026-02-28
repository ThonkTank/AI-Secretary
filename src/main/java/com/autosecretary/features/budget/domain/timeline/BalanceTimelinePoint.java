package com.autosecretary.features.budget.domain.timeline;

import java.time.LocalDate;

/**
 * One point on a balance timeline — the cumulative account balance (in cents) on a given date.
 * <p>
 * Produced by {@link AccountBalanceTimelineService} as output of balance reconstruction.
 * Balance values are stored as <strong>cents</strong> (integers) to avoid floating-point
 * precision issues common in financial applications. For example, 9999 cents = 99.99 EUR.
 * <p>
 * Used to draw balance charts on the budget screen; see {@code BudgetBalanceChartView}.
 */
public record BalanceTimelinePoint(LocalDate date, long balanceCents) {}
