package com.autosecretary.features.budget.domain;

import java.util.List;

/**
 * Katalog bekannter Abo-Schlüsselwörter zur Confidence-Erhöhung bei wiederkehrenden Abbuchungen.
 */
public final class KnownSubscriptionPatterns {
    private final List<String> patterns;

    public KnownSubscriptionPatterns(List<String> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    public static KnownSubscriptionPatterns defaults() {
        return new KnownSubscriptionPatterns(List.of(
                "NETFLIX", "SPOTIFY", "AMAZON PRIME", "DISNEY", "APPLE",
                "GOOGLE", "MICROSOFT", "ADOBE", "DROPBOX", "ZOOM",
                "GYM", "FITNESS", "STUDIO", "TELEKOM", "VODAFONE",
                "O2", "VERSICHERUNG", "INSURANCE", "RUNDFUNK", "GEZ"
        ));
    }

    public boolean matches(String normalizedPayee) {
        if (normalizedPayee == null || normalizedPayee.isBlank()) {
            return false;
        }
        for (String pattern : patterns) {
            if (normalizedPayee.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
