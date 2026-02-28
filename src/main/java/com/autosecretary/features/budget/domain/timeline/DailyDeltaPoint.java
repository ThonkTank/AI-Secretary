package com.autosecretary.features.budget.domain.timeline;

import java.time.LocalDate;

/**
 * One day's net transaction amount (the "delta") — the sum of all income minus all expenses
 * for that day, in cents.
 * <p>
 * Input to {@link AccountBalanceTimelineService#reconstructDaily}. Days with no transactions
 * may be absent from the input list (the service treats missing days as zero delta).
 * Deltas can be negative (net expense) or positive (net income).
 * <p>
 * Produced by {@code BudgetRepository.getDailyDeltasForAccount()} via database aggregation.
 */
public record DailyDeltaPoint(LocalDate date, long deltaCents) {}
