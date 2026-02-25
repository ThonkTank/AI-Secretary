package com.autosecretary.features.budget.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Portierung des Legacy-Pattern-Detectors für wiederkehrende Budget-Buchungen.
 */
public final class RecurringPatternDetector {
    private static final int MIN_OCCURRENCES_DEFAULT = 3;
    private static final double PAYEE_SIMILARITY_THRESHOLD = 0.75;
    private static final double AMOUNT_VARIANCE_THRESHOLD = 0.15;

    private RecurringPatternDetector() {
    }

    public static List<RecurringSuggestion> detectPatterns(List<RecurringBudgetTransaction> transactions) {
        return detectPatterns(transactions, MIN_OCCURRENCES_DEFAULT);
    }

    public static List<RecurringSuggestion> detectPatterns(List<RecurringBudgetTransaction> transactions,
                                                           int minOccurrences) {
        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>();
        }

        List<RecurringBudgetTransaction> eligible = transactions.stream()
                .filter(tx -> !tx.isRecurring)
                .filter(tx -> !tx.isPredicted)
                .filter(tx -> tx.parentRecurringId == null)
                .filter(tx -> tx.payee != null && !tx.payee.trim().isEmpty())
                .filter(tx -> tx.transactionDate != null)
                .collect(Collectors.toList());

        if (eligible.size() < minOccurrences) {
            return new ArrayList<>();
        }

        Map<String, List<RecurringBudgetTransaction>> groupedByPayee = new HashMap<>();
        for (RecurringBudgetTransaction tx : eligible) {
            String normalized = normalizePayee(tx.payee);
            if (normalized.isEmpty()) {
                continue;
            }
            String existingGroup = findMatchingGroup(normalized, groupedByPayee.keySet());
            if (existingGroup != null) {
                groupedByPayee.get(existingGroup).add(tx);
            } else {
                groupedByPayee.computeIfAbsent(normalized, k -> new ArrayList<>()).add(tx);
            }
        }

        List<RecurringSuggestion> candidates = new ArrayList<>();
        for (Map.Entry<String, List<RecurringBudgetTransaction>> group : groupedByPayee.entrySet()) {
            List<RecurringBudgetTransaction> txList = group.getValue();
            if (txList.size() < minOccurrences) {
                continue;
            }
            txList.sort(Comparator.comparing(tx -> tx.transactionDate));
            if (!hasConsistentAmounts(txList)) {
                continue;
            }

            RecurringSuggestion candidate = analyzePattern(group.getKey(), txList);
            if (candidate != null && candidate.suggestedType() != null) {
                candidates.add(candidate);
            }
        }

        candidates.sort((a, b) -> Double.compare(b.confidenceScore(), a.confidenceScore()));
        return candidates;
    }

    public static String normalizePayee(String payee) {
        if (payee == null) {
            return "";
        }
        return payee
                .toUpperCase()
                .replaceAll("[0-9#*]+", "")
                .replaceAll("[^A-ZÄÖÜ\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static double payeeSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }

        int distance = levenshteinDistance(a, b);
        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) {
            return 1.0;
        }
        return 1.0 - ((double) distance / maxLength);
    }

    private static RecurringSuggestion analyzePattern(String normalizedPayee,
                                                      List<RecurringBudgetTransaction> transactions) {
        int sumAmounts = 0;
        int minAmount = Integer.MAX_VALUE;
        int maxAmount = Integer.MIN_VALUE;
        Map<Long, Integer> categoryCounts = new HashMap<>();
        List<Long> txIds = new ArrayList<>();
        String displayPayee = transactions.get(0).payee;

        for (RecurringBudgetTransaction tx : transactions) {
            int absAmount = Math.abs(tx.amountCents);
            sumAmounts += absAmount;
            minAmount = Math.min(minAmount, absAmount);
            maxAmount = Math.max(maxAmount, absAmount);
            if (tx.id != null) {
                txIds.add(tx.id);
            }
            if (tx.categoryId != null) {
                categoryCounts.merge(tx.categoryId, 1, Integer::sum);
            }
        }

        int avgAmount = sumAmounts / transactions.size();
        Long categoryId = categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (transactions.get(0).amountCents < 0) {
            avgAmount = -avgAmount;
            int tmp = minAmount;
            minAmount = -maxAmount;
            maxAmount = -tmp;
        }

        PatternResult pattern = detectDatePattern(transactions);
        if (pattern == null) {
            return null;
        }

        double confidence = calculateConfidence(transactions, pattern, avgAmount, minAmount, maxAmount);

        return new RecurringSuggestion(
                normalizedPayee,
                displayPayee,
                categoryId,
                avgAmount,
                minAmount,
                maxAmount,
                pattern.type,
                pattern.value,
                pattern.dayOfWeek,
                txIds,
                confidence
        );
    }

    private static PatternResult detectDatePattern(List<RecurringBudgetTransaction> transactions) {
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

        PatternResult weekly = checkWeekly(dates);
        if (weekly != null) {
            return weekly;
        }

        return checkInterval(dates);
    }

    private static PatternResult checkMonthlyDay(List<LocalDate> dates) {
        List<Integer> daysOfMonth = dates.stream().map(LocalDate::getDayOfMonth).collect(Collectors.toList());
        int mode = mode(daysOfMonth);

        boolean allMatch = daysOfMonth.stream()
                .allMatch(d -> Math.abs(d - mode) <= 2
                        || (mode >= 28 && d <= 3)
                        || (d >= 28 && mode <= 3));

        if (allMatch) {
            return new PatternResult(RecurringBudgetTransaction.RecurringType.MONTHLY_DAY, mode, null);
        }
        return null;
    }

    private static PatternResult checkMonthlyLast(List<LocalDate> dates) {
        boolean allLastDays = dates.stream().allMatch(date -> {
            int lastDay = date.lengthOfMonth();
            return date.getDayOfMonth() >= lastDay - 2;
        });
        if (allLastDays) {
            return new PatternResult(RecurringBudgetTransaction.RecurringType.MONTHLY_LAST, 0, null);
        }
        return null;
    }

    private static PatternResult checkWeekly(List<LocalDate> dates) {
        List<DayOfWeek> weekdays = dates.stream().map(LocalDate::getDayOfWeek).collect(Collectors.toList());
        Map<DayOfWeek, Long> counts = weekdays.stream()
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
        DayOfWeek mode = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        long modeCount = counts.getOrDefault(mode, 0L);
        if (modeCount >= dates.size() * 0.8) {
            List<Long> intervals = calculateIntervals(dates);
            double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
            if (avgInterval >= 5 && avgInterval <= 9) {
                return new PatternResult(RecurringBudgetTransaction.RecurringType.WEEKLY, 0, mode);
            }
        }
        return null;
    }

    private static PatternResult checkInterval(List<LocalDate> dates) {
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

    private static boolean hasConsistentAmounts(List<RecurringBudgetTransaction> txList) {
        if (txList.size() < 2) {
            return true;
        }
        List<Integer> amounts = txList.stream().map(tx -> Math.abs(tx.amountCents)).collect(Collectors.toList());
        int avg = amounts.stream().mapToInt(Integer::intValue).sum() / amounts.size();
        if (avg == 0) {
            return false;
        }
        return amounts.stream().allMatch(amount -> Math.abs(amount - avg) <= avg * AMOUNT_VARIANCE_THRESHOLD);
    }

    private static List<Long> calculateIntervals(List<LocalDate> dates) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        return intervals;
    }

    private static int mode(List<Integer> values) {
        Map<Integer, Long> counts = values.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse(values.get(0));
    }

    private static String findMatchingGroup(String normalized, Set<String> keys) {
        for (String key : keys) {
            if (payeeSimilarity(normalized, key) >= PAYEE_SIMILARITY_THRESHOLD) {
                return key;
            }
        }
        return null;
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + cost
                    );
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    private static double calculateConfidence(List<RecurringBudgetTransaction> txList,
                                              PatternResult pattern,
                                              int avgAmount,
                                              int minAmount,
                                              int maxAmount) {
        double score = 0;
        score += Math.min(txList.size() / 10.0, 0.3);

        if (avgAmount != 0) {
            double variance = Math.abs(maxAmount - minAmount) / (double) Math.abs(avgAmount);
            score += Math.max(0.3 - variance, 0);
        }

        if (pattern.type != null) {
            score += 0.3;
        }

        String normalized = txList.get(0).payee != null ? normalizePayee(txList.get(0).payee) : "";
        if (isKnownSubscription(normalized)) {
            score += 0.1;
        }

        return Math.min(score, 1.0);
    }

    private static boolean isKnownSubscription(String normalizedPayee) {
        String[] knownPatterns = {
                "NETFLIX", "SPOTIFY", "AMAZON PRIME", "DISNEY", "APPLE",
                "GOOGLE", "MICROSOFT", "ADOBE", "DROPBOX", "ZOOM",
                "GYM", "FITNESS", "STUDIO", "TELEKOM", "VODAFONE",
                "O2", "VERSICHERUNG", "INSURANCE", "RUNDFUNK", "GEZ"
        };

        for (String pattern : knownPatterns) {
            if (normalizedPayee.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private record PatternResult(RecurringBudgetTransaction.RecurringType type,
                                 int value,
                                 DayOfWeek dayOfWeek) {
    }
}
