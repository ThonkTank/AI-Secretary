package com.autosecretary.features.budget.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Erkennung von Datums-Patterns (monatlich, wöchentlich, Intervalle).
 */
public final class DatePatternDetector {
    private static final int WEEKLY_INTERVAL_MIN_DAYS = 5;
    private static final int WEEKLY_INTERVAL_MAX_DAYS = 9;

    private DatePatternDetector() {
    }

    public static PatternResult detectDatePattern(List<RecurringBudgetTransaction> transactions) {
        if (transactions.size() < 2) {
            return null;
        }
        List<LocalDate> dates = transactions.stream()
                .map(tx -> tx.transactionDate)
                .sorted()
                .collect(Collectors.toList());

        PatternResult monthlyDay = checkMonthlyDay(dates);
        if (monthlyDay != null) {
            return monthlyDay;
        }

        PatternResult monthlyLast = checkMonthlyLast(dates);
        if (monthlyLast != null) {
            return monthlyLast;
        }

        PatternResult weekly = checkWeekly(dates, WEEKLY_INTERVAL_MIN_DAYS, WEEKLY_INTERVAL_MAX_DAYS);
        if (weekly != null) {
            return weekly;
        }

        return checkInterval(dates);
    }

    static PatternResult checkMonthlyDay(List<LocalDate> dates) {
        List<Integer> daysOfMonth = dates.stream().map(LocalDate::getDayOfMonth).collect(Collectors.toList());
        int dominantDay = mode(daysOfMonth);

        boolean allMatch = daysOfMonth.stream()
                .allMatch(d -> Math.abs(d - dominantDay) <= 2
                        || (dominantDay >= 28 && d <= 3)
                        || (d >= 28 && dominantDay <= 3));

        if (allMatch) {
            return new PatternResult(RecurringBudgetTransaction.RecurringType.MONTHLY_DAY, dominantDay, null);
        }
        return null;
    }

    static PatternResult checkMonthlyLast(List<LocalDate> dates) {
        boolean allLastDays = dates.stream().allMatch(date -> {
            int lastDay = date.lengthOfMonth();
            return date.getDayOfMonth() >= lastDay - 2;
        });
        if (allLastDays) {
            return new PatternResult(RecurringBudgetTransaction.RecurringType.MONTHLY_LAST, 0, null);
        }
        return null;
    }

    static PatternResult checkWeekly(List<LocalDate> dates, int minDays, int maxDays) {
        List<DayOfWeek> weekdays = dates.stream().map(LocalDate::getDayOfWeek).collect(Collectors.toList());
        Map<DayOfWeek, Long> counts = weekdays.stream()
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
        DayOfWeek dominantWeekday = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        long modeCount = counts.getOrDefault(dominantWeekday, 0L);
        if (modeCount >= dates.size() * 0.8) {
            List<Long> intervals = calculateIntervals(dates);
            double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
            if (avgInterval >= minDays && avgInterval <= maxDays) {
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

        double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        boolean consistent = intervals.stream()
                .allMatch(i -> Math.abs(i - avgInterval) <= avgInterval * 0.2 + 2);

        if (consistent && avgInterval >= 3) {
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

    static int mode(List<Integer> values) {
        Map<Integer, Long> counts = values.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse(values.get(0));
    }

    public record PatternResult(RecurringBudgetTransaction.RecurringType type,
                                int value,
                                DayOfWeek dayOfWeek) {
    }
}
