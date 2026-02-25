package com.autosecretary.features.budget.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hilfsklasse für Payee-Normalisierung und unscharfe Gruppierung.
 */
public final class PayeeGrouper {
    static final double PAYEE_SIMILARITY_THRESHOLD = 0.75;

    private PayeeGrouper() {
    }

    public static Map<String, List<RecurringBudgetTransaction>> groupBySimilarPayee(List<RecurringBudgetTransaction> transactions) {
        return groupBySimilarPayee(transactions, PAYEE_SIMILARITY_THRESHOLD);
    }

    private static Map<String, List<RecurringBudgetTransaction>> groupBySimilarPayee(List<RecurringBudgetTransaction> transactions,
                                                                             double similarityThreshold) {
        Map<String, List<RecurringBudgetTransaction>> groupedByPayee = new HashMap<>();
        for (RecurringBudgetTransaction tx : transactions) {
            String normalized = normalizePayee(tx.payee);
            if (normalized.isEmpty()) {
                continue;
            }
            String existingGroup = findMatchingGroup(normalized, groupedByPayee.keySet(), similarityThreshold);
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

    static String findMatchingGroup(String normalized, Set<String> keys, double similarityThreshold) {
        for (String key : keys) {
            if (payeeSimilarity(normalized, key) >= similarityThreshold) {
                return key;
            }
        }
        return null;
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
