package com.autosecretary.features.budget.domain.recurring;

import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.recurring.internal.DatePatternDetector;
import com.autosecretary.features.budget.domain.recurring.internal.PayeeGrouper;
import com.autosecretary.features.budget.domain.recurring.internal.SuggestionScorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects recurring transaction patterns from historical budget transaction data.
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>Filter eligible transactions (must have payee, date, not already recurring/predicted)</li>
 *   <li>Group by similar payee using fuzzy matching ({@code PayeeGrouper})</li>
 *   <li>For each group, check that transaction amounts are consistent ({@code AMOUNT_VARIANCE_THRESHOLD})</li>
 *   <li>Detect the date pattern ({@code DatePatternDetector}) — MONTHLY_DAY, MONTHLY_LAST, WEEKLY, or INTERVAL</li>
 *   <li>Score confidence ({@code SuggestionScorer}) based on occurrence count, amount consistency, pattern type, and known subscriptions</li>
 *   <li>Return sorted list of {@link RecurringSuggestion} (highest confidence first)</li>
 * </ol>
 *
 * <p>Returns {@code null} candidates (those without a detected date pattern) or groups smaller than
 * {@link #MIN_OCCURRENCES_DEFAULT}.
 *
 * <p>See README.md in this package for entry points, data types, and troubleshooting.
 */
public final class RecurringPatternDetector {
    private static final int MIN_OCCURRENCES_DEFAULT = 3;
    /**
     * Maximum allowed per-element deviation from average amount, as a fraction of the average.
     * Set to 15% to allow transactions with minor rounding differences or occasional promos
     * while filtering out genuinely inconsistent amounts.
     *
     * <p><b>Coupling note:</b> {@code SuggestionScorer.calculateAmountVarianceScore} uses
     * {@code AMOUNT_VARIANCE_WEIGHT - variance_ratio} where {@code variance_ratio = (max-min)/avg}.
     * Because this filter guarantees each element is within ±AMOUNT_VARIANCE_THRESHOLD of avg,
     * the worst-case {@code (max-min)/avg} is {@code 2 × AMOUNT_VARIANCE_THRESHOLD = 0.30}.
     * That bound lines up with {@code AMOUNT_VARIANCE_WEIGHT (0.30)}, ensuring the scorer uses
     * the full [0, 0.30] range. If you change this constant, verify {@code SuggestionScorer}
     * still produces meaningful scores across the new allowed variance range.
     */
    private static final double AMOUNT_VARIANCE_THRESHOLD = 0.15;

    private record AmountStats(long avg, long min, long max) {
        /** True when every transaction's amount is within {@link #AMOUNT_VARIANCE_THRESHOLD} of avg. */
        boolean isConsistent(List<RecurringBudgetTransaction> txList) {
            if (avg == 0) return false;
            return txList.stream()
                    .allMatch(tx -> Math.abs(Math.abs(tx.amountCents) - avg) <= avg * AMOUNT_VARIANCE_THRESHOLD);
        }

        /**
         * Computes average, minimum, and maximum amounts from a list of transactions.
         *
         * @param txList a non-empty list of transactions
         * @return amount statistics
         * @throws ArithmeticException if txList is empty (division by zero)
         */
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

    private static boolean isEligibleForDetection(RecurringBudgetTransaction tx) {
        return !tx.isRecurring && !tx.isPredicted && tx.parentRecurringId == null
                && tx.payee != null && !tx.payee.isBlank() && tx.bookingDate != null;
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
                .filter(RecurringPatternDetector::isEligibleForDetection)
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
            txList.sort(Comparator.comparing(tx -> tx.bookingDate));
            AmountStats amountStats = AmountStats.from(txList);
            if (!amountStats.isConsistent(txList)) {
                continue;
            }

            // group.getKey() is the normalized payee from PayeeGrouper.groupBySimilarPayee();
            // passed unchanged to SuggestionScorer for known-subscription pattern matching.
            RecurringSuggestion candidate = analyzePattern(group.getKey(), txList, amountStats);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator.comparingDouble(RecurringSuggestion::confidenceScore).reversed());
        return candidates;
    }

    /**
     * Analyzes a list of transactions with consistent amounts to produce a recurring suggestion.
     *
     * @param normalizedPayee the normalized/grouped payee name from {@code PayeeGrouper}
     * @param transactions a non-empty list of transactions with consistent amounts and detected date pattern
     * @param amountStats pre-computed amount statistics for these transactions
     * @return a {@link RecurringSuggestion} if a date pattern is detected, null otherwise
     */
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

        TransactionDirection direction = TransactionDirection.fromAmountCents(transactions.get(0).amountCents);

        DatePatternDetector.PatternResult pattern = DatePatternDetector.detectDatePattern(transactions);
        if (pattern == null) {
            return null;
        }

        double confidence = SuggestionScorer.calculateConfidence(
                transactions.size(), amountStats.avg(), amountStats.min(), amountStats.max(), normalizedPayee);

        return new RecurringSuggestion(
                normalizedPayee,
                displayPayee,
                categoryId,
                amountStats.avg(),
                amountStats.min(),
                amountStats.max(),
                direction,
                pattern.type(),
                pattern.value(),
                pattern.dayOfWeek(),
                txIds,
                confidence
        );
    }
}
