package com.autosecretary.features.budget.domain;

import java.util.List;

/**
 * Scoring-Heuristik für Recurring-Vorschläge.
 */
public final class SuggestionScorer {
    private static final double OCCURRENCE_CAP = 10.0;
    private static final double OCCURRENCE_WEIGHT = 0.3;
    private static final double AMOUNT_VARIANCE_WEIGHT = 0.3;
    private static final double PATTERN_TYPE_WEIGHT = 0.3;
    private static final double KNOWN_SUBSCRIPTION_WEIGHT = 0.1;

    private static final String[] KNOWN_SUBSCRIPTION_PATTERNS = {
            "NETFLIX", "SPOTIFY", "AMAZON PRIME", "DISNEY", "APPLE",
            "GOOGLE", "MICROSOFT", "ADOBE", "DROPBOX", "ZOOM",
            "GYM", "FITNESS", "STUDIO", "TELEKOM", "VODAFONE",
            "O2", "VERSICHERUNG", "INSURANCE", "RUNDFUNK", "GEZ"
    };

    private SuggestionScorer() {
    }

    public static double calculateConfidence(List<RecurringBudgetTransaction> txList,
                                             DatePatternDetector.PatternResult pattern,
                                             int avgAmount,
                                             int minAmount,
                                             int maxAmount) {
        double score = 0;
        score += Math.min(txList.size() / OCCURRENCE_CAP, OCCURRENCE_WEIGHT);

        if (avgAmount != 0) {
            double variance = Math.abs(maxAmount - minAmount) / (double) Math.abs(avgAmount);
            score += Math.max(AMOUNT_VARIANCE_WEIGHT - variance, 0);
        }

        if (pattern.type() != null) {
            score += PATTERN_TYPE_WEIGHT;
        }

        String normalized = txList.get(0).payee != null ? PayeeGrouper.normalizePayee(txList.get(0).payee) : "";
        if (isKnownSubscription(normalized)) {
            score += KNOWN_SUBSCRIPTION_WEIGHT;
        }

        return Math.min(score, 1.0);
    }

    static boolean isKnownSubscription(String normalizedPayee) {
        for (String pattern : KNOWN_SUBSCRIPTION_PATTERNS) {
            if (normalizedPayee.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
