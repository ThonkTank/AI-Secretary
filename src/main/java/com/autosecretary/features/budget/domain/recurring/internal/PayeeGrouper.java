package com.autosecretary.features.budget.domain.recurring.internal;

import com.autosecretary.features.budget.domain.recurring.RecurringBudgetTransaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Groups transactions by payee using fuzzy string matching based on the Levenshtein distance.
 *
 * <p><b>Normalization:</b> Before comparison, payees are converted to uppercase, stripped of
 * digits and special characters ({@code #*}), and non-letter/non-space characters are replaced
 * with spaces. This handles common bank-statement noise like reference numbers and transaction IDs
 * embedded in the payee field (e.g., "NETFLIX INC 12345" → "NETFLIX INC").
 *
 * <p><b>Similarity scoring:</b> Two normalized payees are grouped together if their similarity
 * score is ≥ {@link #PAYEE_SIMILARITY_THRESHOLD} (0.75). Similarity is computed as
 * {@code 1 - (levenshteinDistance / maxLength)}, where:
 * <ul>
 *   <li>1.0 = identical strings</li>
 *   <li>0.75 = e.g., "NETFLIX" vs "NETFLIX INC" (threshold: accept this grouping)</li>
 *   <li>0.0 = completely different strings</li>
 * </ul>
 *
 * <p>See <a href="https://en.wikipedia.org/wiki/Levenshtein_distance">Levenshtein distance</a>
 * for algorithm details.
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
        String upper = payee.toUpperCase(Locale.GERMAN);
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
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[m][n];
    }
}
