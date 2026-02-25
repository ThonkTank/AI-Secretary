package com.autosecretary.features.budget.domain;

/**
 * Zentrale Konfiguration für die Erkennung wiederkehrender Budget-Muster.
 */
public final class PatternDetectionConfig {
    /**
     * Mindestähnlichkeit (0..1), ab der zwei normalisierte Payees in dieselbe Gruppe fallen.
     */
    public final double payeeSimilarityThreshold;

    /**
     * Zulässige relative Betragsabweichung (0..1) pro Buchung gegenüber dem Gruppenmittel.
     */
    public final double amountVarianceThreshold;

    /**
     * Untere Grenze (in Tagen) für ein als "wöchentlich" akzeptiertes Durchschnittsintervall.
     */
    public final int weeklyIntervalMinDays;

    /**
     * Obere Grenze (in Tagen) für ein als "wöchentlich" akzeptiertes Durchschnittsintervall.
     */
    public final int weeklyIntervalMaxDays;

    /**
     * Gewichtung der Einzelkomponenten zur Berechnung des Confidence-Scores.
     */
    public final ScoringWeights scoringWeights;

    /**
     * Provider für bekannte Abo-Muster, die den Confidence-Score erhöhen können.
     */
    public final KnownSubscriptionPatterns knownSubscriptionPatterns;

    public PatternDetectionConfig(double payeeSimilarityThreshold,
                                  double amountVarianceThreshold,
                                  int weeklyIntervalMinDays,
                                  int weeklyIntervalMaxDays,
                                  ScoringWeights scoringWeights,
                                  KnownSubscriptionPatterns knownSubscriptionPatterns) {
        this.payeeSimilarityThreshold = payeeSimilarityThreshold;
        this.amountVarianceThreshold = amountVarianceThreshold;
        this.weeklyIntervalMinDays = weeklyIntervalMinDays;
        this.weeklyIntervalMaxDays = weeklyIntervalMaxDays;
        this.scoringWeights = scoringWeights;
        this.knownSubscriptionPatterns = knownSubscriptionPatterns;
    }

    public static PatternDetectionConfig defaults() {
        return new PatternDetectionConfig(
                0.75,
                0.15,
                5,
                9,
                new ScoringWeights(0.3, 0.3, 0.3, 0.1),
                KnownSubscriptionPatterns.defaults()
        );
    }

    public static final class ScoringWeights {
        /**
         * Maximaler Score-Anteil aus der Anzahl gefundener Buchungen (häufigere Serien => höherer Wert).
         */
        public final double occurrenceWeight;

        /**
         * Maximaler Score-Anteil aus Betragsstabilität (niedrige Varianz => höherer Wert).
         */
        public final double amountVarianceWeight;

        /**
         * Fester Score-Anteil, wenn ein valider Datums-Rhythmus gefunden wurde.
         */
        public final double patternTypeWeight;

        /**
         * Zusätzlicher Score-Anteil bei Treffer auf bekannte Abo-Anbieter.
         */
        public final double knownSubscriptionWeight;

        public ScoringWeights(double occurrenceWeight,
                              double amountVarianceWeight,
                              double patternTypeWeight,
                              double knownSubscriptionWeight) {
            this.occurrenceWeight = occurrenceWeight;
            this.amountVarianceWeight = amountVarianceWeight;
            this.patternTypeWeight = patternTypeWeight;
            this.knownSubscriptionWeight = knownSubscriptionWeight;
        }
    }
}
