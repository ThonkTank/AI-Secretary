package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.domain.internal.recurring.DatePatternDetector;
import com.autosecretary.features.budget.domain.internal.recurring.PayeeGrouper;
import com.autosecretary.features.budget.domain.internal.recurring.SuggestionScorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Portierung des Legacy-Pattern-Detectors für wiederkehrende Budget-Buchungen.
 */
public final class RecurringPatternDetector {
    private static final int MIN_OCCURRENCES_DEFAULT = 3;
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

        Map<String, List<RecurringBudgetTransaction>> groupedByPayee = PayeeGrouper.groupBySimilarPayee(eligible);

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

    private static RecurringSuggestion analyzePattern(String normalizedPayee,
                                                      List<RecurringBudgetTransaction> transactions) {
        long sumAmounts = 0;
        long minAmount = Long.MAX_VALUE;
        long maxAmount = Long.MIN_VALUE;
        Map<String, Integer> categoryCounts = new HashMap<>();
        List<String> txIds = new ArrayList<>();
        String displayPayee = transactions.get(0).payee;

        for (RecurringBudgetTransaction tx : transactions) {
            long absAmount = Math.abs((long) tx.amountCents);
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

        long avgAmount = sumAmounts / transactions.size();
        String categoryId = categoryCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        TransactionDirection transactionType = transactions.get(0).amountCents < 0
                ? TransactionDirection.EXPENSE
                : TransactionDirection.INCOME;

        DatePatternDetector.PatternResult pattern = DatePatternDetector.detectDatePattern(transactions);
        if (pattern == null) {
            return null;
        }

        double confidence = SuggestionScorer.calculateConfidence(transactions, pattern, avgAmount, minAmount, maxAmount);

        return new RecurringSuggestion(
                normalizedPayee,
                displayPayee,
                categoryId,
                avgAmount,
                minAmount,
                maxAmount,
                transactionType,
                pattern.type(),
                pattern.value(),
                pattern.dayOfWeek(),
                txIds,
                confidence
        );
    }

    private static boolean hasConsistentAmounts(List<RecurringBudgetTransaction> txList) {
        if (txList.size() < 2) {
            return true;
        }
        List<Long> amounts = txList.stream().map(tx -> Math.abs((long) tx.amountCents)).collect(Collectors.toList());
        long avg = amounts.stream().mapToLong(Long::longValue).sum() / amounts.size();
        if (avg == 0) {
            return false;
        }
        return amounts.stream().allMatch(amount -> Math.abs(amount - avg) <= avg * AMOUNT_VARIANCE_THRESHOLD);
    }
}
