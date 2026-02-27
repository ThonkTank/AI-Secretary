package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.domain.internal.recurring.DatePatternDetector;
import com.autosecretary.features.budget.domain.internal.recurring.PayeeGrouper;
import com.autosecretary.features.budget.domain.internal.recurring.SuggestionScorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portierung des Legacy-Pattern-Detectors für wiederkehrende Budget-Buchungen.
 */
public final class RecurringPatternDetector {
    private static final int MIN_OCCURRENCES_DEFAULT = 3;
    /** Maximum allowed per-element deviation from average, as a fraction of the average. */
    private static final double AMOUNT_VARIANCE_THRESHOLD = 0.15;

    private record AmountStats(long avg, long min, long max) {
        /** True when every transaction's amount is within {@link #AMOUNT_VARIANCE_THRESHOLD} of avg. */
        boolean isConsistent(List<RecurringBudgetTransaction> txList) {
            if (avg == 0) return false;
            return txList.stream()
                    .allMatch(tx -> Math.abs(Math.abs(tx.amountCents) - avg) <= avg * AMOUNT_VARIANCE_THRESHOLD);
        }

        static AmountStats from(List<RecurringBudgetTransaction> txList) {
            long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (RecurringBudgetTransaction tx : txList) {
                long abs = Math.abs(tx.amountCents);
                sum += abs;
                if (abs < min) min = abs;
                if (abs > max) max = abs;
            }
            return new AmountStats(sum / txList.size(), min, max);
        }
    }

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
                .filter(tx -> !tx.isRecurring && !tx.isPredicted && tx.parentRecurringId == null
                        && tx.payee != null && !tx.payee.isBlank() && tx.transactionDate != null)
                .toList();

        if (eligible.size() < minOccurrences) {
            return new ArrayList<>();
        }

        Map<String, List<RecurringBudgetTransaction>> groupedByPayee = PayeeGrouper.groupBySimilarPayee(eligible);

        List<RecurringSuggestion> candidates = new ArrayList<>();
        for (Map.Entry<String, List<RecurringBudgetTransaction>> group : groupedByPayee.entrySet()) {
            List<RecurringBudgetTransaction> txList = group.getValue();
            if (txList.size() < minOccurrences) {
                continue;
            }
            txList.sort(Comparator.comparing(tx -> tx.transactionDate));
            AmountStats amountStats = AmountStats.from(txList);
            if (!amountStats.isConsistent(txList)) {
                continue;
            }

            RecurringSuggestion candidate = analyzePattern(group.getKey(), txList, amountStats);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator.comparingDouble(RecurringSuggestion::confidenceScore).reversed());
        return candidates;
    }

    private static RecurringSuggestion analyzePattern(String normalizedPayee,
                                                      List<RecurringBudgetTransaction> transactions,
                                                      AmountStats amountStats) {
        Map<String, Integer> categoryCounts = new HashMap<>();
        List<String> txIds = new ArrayList<>();
        String displayPayee = transactions.get(0).payee;

        for (RecurringBudgetTransaction tx : transactions) {
            if (tx.id != null) {
                txIds.add(tx.id);
            }
            if (tx.categoryId != null) {
                categoryCounts.merge(tx.categoryId, 1, Integer::sum);
            }
        }

        String categoryId = categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        TransactionDirection transactionType = TransactionDirection.fromAmountCents(transactions.get(0).amountCents);

        DatePatternDetector.PatternResult pattern = DatePatternDetector.detectDatePattern(transactions);
        if (pattern == null) {
            return null;
        }

        double confidence = SuggestionScorer.calculateConfidence(
                transactions.size(), pattern, amountStats.avg(), amountStats.min(), amountStats.max(), normalizedPayee);

        return new RecurringSuggestion(
                normalizedPayee,
                displayPayee,
                categoryId,
                amountStats.avg(),
                amountStats.min(),
                amountStats.max(),
                transactionType,
                pattern.type(),
                pattern.value(),
                pattern.dayOfWeek(),
                txIds,
                confidence
        );
    }
}
