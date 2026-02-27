package com.autosecretary.features.budget.domain.recurring.internal;

import java.util.Arrays;

/**
 * Calculates a 0–1 confidence score for a detected recurring transaction suggestion.
 *
 * <p>Score components (weights sum to 1.0):
 * <ul>
 *   <li><b>Occurrence count (0.3)</b> — {@code min(count / 10, 0.3)}. More historical
 *       occurrences increase confidence; capped at 10 samples (full weight).</li>
 *   <li><b>Amount variance (0.3)</b> — {@code max(0.3 - variance_ratio, 0)}. Low spread
 *       between min/max amount relative to the average scores higher; penalised linearly
 *       toward zero as variance grows.</li>
 *   <li><b>Pattern type bonus (0.3)</b> — flat bonus always included, because
 *       {@code calculateConfidence} is only called when a structured date pattern was
 *       already confirmed by {@code DatePatternDetector}. Both count and amount variance are
 *       meaningful only in that context, so this is equally weighted.</li>
 *   <li><b>Known subscription (0.1)</b> — small tie-breaker for well-known recurring payees
 *       (streaming services, gyms, telecoms, etc.) where confidence is inherently higher.</li>
 * </ul>
 *
 * <p>Scores above approximately <b>0.7</b> are considered high-confidence and suitable for
 * auto-applying without user confirmation. Scores between 0.4–0.7 are surfaced as suggestions
 * requiring user review.
 */
public final class SuggestionScorer {
    // How many occurrences are needed to reach the full occurrence weight (30%).
    private static final double OCCURRENCE_CAP = 10.0;
    // Occurrence count and amount variance are equally weighted — both are strong, independent signals.
    private static final double OCCURRENCE_WEIGHT = 0.3;
    private static final double AMOUNT_VARIANCE_WEIGHT = 0.3;
    // A detected structured pattern is as important as the quantitative checks above.
    private static final double PATTERN_TYPE_BONUS = 0.3;
    // Known-subscription match is a weaker tie-breaker, not a primary signal.
    private static final double KNOWN_SUBSCRIPTION_WEIGHT = 0.1;

    /**
     * Scores at or above this threshold are considered high-confidence and suitable for
     * auto-applying a recurring template without user confirmation.
     */
    public static final double AUTO_APPLY_THRESHOLD = 0.7;

    private static final String[] KNOWN_SUBSCRIPTION_PATTERNS = {
            "NETFLIX", "SPOTIFY", "AMAZON PRIME", "DISNEY", "APPLE",
            "GOOGLE", "MICROSOFT", "ADOBE", "DROPBOX", "ZOOM",
            "GYM", "FITNESS", "STUDIO", "TELEKOM", "VODAFONE",
            "O2", "VERSICHERUNG", "INSURANCE", "RUNDFUNK", "GEZ"
    };

    private SuggestionScorer() {
    }

    public static double calculateConfidence(int occurrenceCount,
                                             long avgAmount,
                                             long minAmount,
                                             long maxAmount,
                                             String normalizedPayee) {
        double score = 0.0;
        score += Math.min((occurrenceCount / OCCURRENCE_CAP) * OCCURRENCE_WEIGHT, OCCURRENCE_WEIGHT);
        score += calculateAmountVarianceScore(avgAmount, minAmount, maxAmount);
        score += PATTERN_TYPE_BONUS;

        if (isKnownSubscription(normalizedPayee)) {
            score += KNOWN_SUBSCRIPTION_WEIGHT;
        }

        return Math.min(score, 1.0);
    }

    static double calculateAmountVarianceScore(long avgAmount, long minAmount, long maxAmount) {
        if (avgAmount == 0) {
            return 0.0;
        }
        double variance = Math.abs(maxAmount - minAmount) / (double) Math.abs(avgAmount);
        return Math.max(AMOUNT_VARIANCE_WEIGHT - variance, 0);
    }

    static boolean isKnownSubscription(String normalizedPayee) {
        return Arrays.stream(KNOWN_SUBSCRIPTION_PATTERNS)
                .anyMatch(normalizedPayee::contains);
    }
}
