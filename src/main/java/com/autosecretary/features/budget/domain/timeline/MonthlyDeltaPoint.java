package com.autosecretary.features.budget.domain.timeline;

import java.time.YearMonth;

/**
 * One month's net transaction amount (the "delta") — the sum of all income minus all expenses
 * for that month, in cents.
 * <p>
 * Input to {@link AccountBalanceTimelineService#reconstructMonthly}. Months with no transactions
 * may be absent from the input list (the service treats missing months as zero delta).
 * Deltas can be negative (net expense) or positive (net income).
 * <p>
 * Produced by {@code BudgetRepository.getMonthlyDeltasForAccount()} via database aggregation.
 * Similar to {@link DailyDeltaPoint}, but aggregated by month instead of day.
 */
public record MonthlyDeltaPoint(YearMonth yearMonth, long deltaCents) {}
