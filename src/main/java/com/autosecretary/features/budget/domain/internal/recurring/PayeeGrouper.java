package com.autosecretary.features.budget.domain.internal.recurring;

import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Groups transactions by payee using fuzzy (Levenshtein-based) string matching.
 *
 * <p>Normalization rules applied before comparison: convert to uppercase, strip all digits and
 * {@code #*} characters, replace remaining non-letter/non-space characters with spaces, and
 * collapse whitespace. This handles common bank-statement noise such as reference numbers and
 * transaction IDs embedded in the payee field.
 *
 * <p>Two normalized payees are considered the same group when their similarity score is ≥
 * {@link #PAYEE_SIMILARITY_THRESHOLD} (0.75). Similarity is {@code 1 - (levenshteinDistance /
 * maxLength)}, so identical strings score 1.0 and completely different strings score 0.0.
 */
public final class PayeeGrouper {
    private static final double PAYEE_SIMILARITY_THRESHOLD = 0.75;

    private static final Pattern DIGITS_AND_SPECIAL = Pattern.compile("[0-9#*]+");
    private static final Pattern NON_LETTER_CHARS = Pattern.compile("[^A-ZÄÖÜ\\s]");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private PayeeGrouper() {
    }

    public static Map<String, List<RecurringBudgetTransaction>> groupBySimilarPayee(List<RecurringBudgetTransaction> transactions) {
        Map<String, List<RecurringBudgetTransaction>> groupedByPayee = new HashMap<>();
        for (RecurringBudgetTransaction tx : transactions) {
            String normalized = normalizePayee(tx.payee);
            if (normalized.isEmpty()) {
                continue;
            }
            String existingGroup = findMatchingGroup(normalized, groupedByPayee.keySet(), PAYEE_SIMILARITY_THRESHOLD);
            if (existingGroup != null) {
                groupedByPayee.get(existingGroup).add(tx);
            } else {
                groupedByPayee.computeIfAbsent(normalized, k -> new ArrayList<>()).add(tx);
            }
        }
        return groupedByPayee;
    }

    public static String normalizePayee(String payee) {
        if (payee == null) {
            return "";
        }
        String upper = payee.toUpperCase();
        String noDigits = DIGITS_AND_SPECIAL.matcher(upper).replaceAll("");
        String lettersOnly = NON_LETTER_CHARS.matcher(noDigits).replaceAll(" ");
        return MULTI_WHITESPACE.matcher(lettersOnly).replaceAll(" ").trim();
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
        return 1.0 - ((double) distance / maxLength);
    }

    static String findMatchingGroup(String normalized, Set<String> keys, double similarityThreshold) {
        String bestKey = null;
        double bestScore = -1;
        for (String key : keys) {
            double score = payeeSimilarity(normalized, key);
            if (score >= similarityThreshold && score > bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }
        return bestKey;
    }

    static int levenshteinDistance(String a, String b) {
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
}
