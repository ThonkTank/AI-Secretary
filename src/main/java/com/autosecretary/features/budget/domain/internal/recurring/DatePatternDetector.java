package com.autosecretary.features.budget.domain.internal.recurring;

import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detects recurring date patterns from a list of transaction dates.
 *
 * <p>Checks are applied in priority order — the first match wins:
 * <ol>
 *   <li>{@code MONTHLY_DAY} — transactions cluster on the same day of month (±2 day tolerance,
 *       with wrap-around handling for month-end dates).</li>
 *   <li>{@code MONTHLY_LAST} — all transactions fall within the last 3 days of their month.</li>
 *   <li>{@code WEEKLY} — transactions fall on the same day of week in ≥ 80% of cases, with an
 *       average inter-occurrence interval of 5–9 days.</li>
 *   <li>{@code INTERVAL} — a generic fixed interval (≥ 3 days) where all gaps are within ±20%
 *       of the average (plus a ±2 day absolute tolerance).</li>
 * </ol>
 *
 * <p>Returns {@code null} if fewer than 2 transactions are provided or no pattern is detected.
 * Results are returned as a {@link PatternResult}; see its Javadoc for how to interpret
 * the {@code value} field per pattern type.
 */
public final class DatePatternDetector {
    private static final int WEEKLY_INTERVAL_MIN_DAYS = 5;
    private static final int WEEKLY_INTERVAL_MAX_DAYS = 9;
    /** Minimum fraction of transactions that must fall on the dominant day of week to qualify as WEEKLY. */
    private static final double WEEKLY_DAY_MATCH_RATIO = 0.8;

    /** ±N day tolerance when matching a transaction's day-of-month to the dominant day. */
    private static final int MONTHLY_DAY_TOLERANCE = 2;
    /** Day-of-month threshold above which a date is considered "near month end" for wrap-around detection. */
    private static final int MONTHLY_END_WRAP_THRESHOLD = 28;
    /** Day-of-month threshold below which a date is considered "early in month" for wrap-around detection. */
    private static final int MONTHLY_EARLY_WRAP_THRESHOLD = 3;
    /** Transactions within the last N days of their month qualify as MONTHLY_LAST. */
    private static final int MONTHLY_LAST_TAIL_DAYS = 3;
    /** Minimum average interval (days) to be classified as a fixed INTERVAL pattern. */
    private static final int INTERVAL_MIN_DAYS = 3;
    /** Relative tolerance: an interval gap may deviate by up to this fraction of the average. */
    private static final double INTERVAL_RELATIVE_TOLERANCE = 0.2;
    /** Absolute tolerance: an interval gap may deviate by up to this many days regardless of average. */
    private static final long INTERVAL_ABSOLUTE_TOLERANCE_DAYS = 2L;

    private DatePatternDetector() {
    }

    public static PatternResult detectDatePattern(List<RecurringBudgetTransaction> transactions) {
        if (transactions.size() < 2) {
            return null;
        }
        List<LocalDate> dates = transactions.stream()
                .map(tx -> tx.transactionDate)
                .sorted()
                .toList();

        PatternResult monthlyDay = checkMonthlyDay(dates);
        if (monthlyDay != null) {
            return monthlyDay;
        }

        PatternResult monthlyLast = checkMonthlyLast(dates);
        if (monthlyLast != null) {
            return monthlyLast;
        }

        PatternResult weekly = checkWeekly(dates);
        if (weekly != null) {
            return weekly;
        }

        return checkInterval(dates);
    }

    static PatternResult checkMonthlyDay(List<LocalDate> dates) {
        List<Integer> daysOfMonth = dates.stream().map(LocalDate::getDayOfMonth).toList();
        int dominantDay = mode(daysOfMonth);

        boolean allMatch = daysOfMonth.stream()
                .allMatch(d -> isMonthlyDayMatch(d, dominantDay));

        if (allMatch) {
            return new PatternResult(RecurringBudgetTransaction.RecurringType.MONTHLY_DAY, dominantDay, null);
        }
        return null;
    }

    static boolean isMonthlyDayMatch(int day, int dominantDay) {
        return Math.abs(day - dominantDay) <= MONTHLY_DAY_TOLERANCE
                || (dominantDay >= MONTHLY_END_WRAP_THRESHOLD && day <= MONTHLY_EARLY_WRAP_THRESHOLD)
                || (day >= MONTHLY_END_WRAP_THRESHOLD && dominantDay <= MONTHLY_EARLY_WRAP_THRESHOLD);
    }

    static PatternResult checkMonthlyLast(List<LocalDate> dates) {
        boolean allLastDays = dates.stream()
                .allMatch(date -> date.getDayOfMonth() > date.lengthOfMonth() - MONTHLY_LAST_TAIL_DAYS);
        if (allLastDays) {
            return new PatternResult(RecurringBudgetTransaction.RecurringType.MONTHLY_LAST, 0, null);
        }
        return null;
    }

    static PatternResult checkWeekly(List<LocalDate> dates) {
        List<DayOfWeek> weekdays = dates.stream().map(LocalDate::getDayOfWeek).toList();
        Map<DayOfWeek, Long> counts = weekdays.stream()
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
        DayOfWeek dominantWeekday = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("weekday counts must be non-empty (dates.size() >= 2 is guaranteed by caller)"));

        long modeCount = counts.getOrDefault(dominantWeekday, 0L);
        if (modeCount >= dates.size() * WEEKLY_DAY_MATCH_RATIO) {
            List<Long> intervals = calculateIntervals(dates);
            double avgInterval = calculateAverageInterval(intervals);
            if (avgInterval >= WEEKLY_INTERVAL_MIN_DAYS && avgInterval <= WEEKLY_INTERVAL_MAX_DAYS) {
                return new PatternResult(RecurringBudgetTransaction.RecurringType.WEEKLY, 0, dominantWeekday);
            }
        }
        return null;
    }

    static PatternResult checkInterval(List<LocalDate> dates) {
        List<Long> intervals = calculateIntervals(dates);
        if (intervals.isEmpty()) {
            return null;
        }

        double avgInterval = calculateAverageInterval(intervals);
        boolean consistent = intervals.stream()
                .allMatch(i -> isConsistentInterval(i, avgInterval));

        if (consistent && avgInterval >= INTERVAL_MIN_DAYS) {
            return new PatternResult(RecurringBudgetTransaction.RecurringType.INTERVAL,
                    (int) Math.round(avgInterval), null);
        }
        return null;
    }

    static List<Long> calculateIntervals(List<LocalDate> dates) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        return intervals;
    }

    static double calculateAverageInterval(List<Long> intervals) {
        return intervals.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    static boolean isConsistentInterval(long interval, double avgInterval) {
        double deviation = Math.abs(interval - avgInterval);
        double tolerance = avgInterval * INTERVAL_RELATIVE_TOLERANCE + INTERVAL_ABSOLUTE_TOLERANCE_DAYS;
        return deviation <= tolerance;
    }

    static int mode(List<Integer> values) {
        Map<Integer, Long> counts = values.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("mode() called on empty list"));
    }

    /**
     * Result of a pattern detection run.
     *
     * <p>The meaning of {@code value} depends on {@code type}:
     * <ul>
     *   <li>{@code MONTHLY_DAY}  — day of month (1–31) on which the transaction recurs</li>
     *   <li>{@code MONTHLY_LAST} — always 0; the scheduler uses the last day of each month</li>
     *   <li>{@code WEEKLY}       — always 0; use {@code dayOfWeek} instead</li>
     *   <li>{@code INTERVAL}     — number of days between occurrences</li>
     * </ul>
     *
     * <p>{@code dayOfWeek} is non-null only for {@code WEEKLY} patterns; null otherwise.
     */
    public record PatternResult(RecurringBudgetTransaction.RecurringType type,
                                int value,
                                DayOfWeek dayOfWeek) {
    }
}
