package com.autosecretary.features.budget.ui.state;

/**
 * Single data point on the account balance timeline chart.
 *
 * Each point represents the account's running balance on a specific date.
 * Multiple points form a line chart showing balance changes over the selected time range.
 *
 * {@code balanceCents} is the absolute account balance at the date specified by {@code label}.
 * It can be positive (surplus) or negative (overdraft).
 *
 * <p>{@code label} is formatted for chart display using German locale:
 * daily view {@code "dd.MM"} (e.g. {@code "15.02"}), monthly view {@code "MMM yy"} (e.g. {@code "Feb 25"}).
 * Format is set by {@code BudgetOverviewLoader}.
 */
public record BudgetChartPoint(String label, long balanceCents) {}
