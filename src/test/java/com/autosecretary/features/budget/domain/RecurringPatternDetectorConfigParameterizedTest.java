package com.autosecretary.features.budget.domain;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class RecurringPatternDetectorConfigParameterizedTest {

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                        "payee-threshold-default-groups-similar-payees",
                        List.of(
                                tx(1L, -1199, LocalDate.of(2025, 1, 5), "SPOTIFY"),
                                tx(2L, -1200, LocalDate.of(2025, 2, 5), "SPOTFY"),
                                tx(3L, -1201, LocalDate.of(2025, 3, 5), "SPOTIFY")
                        ),
                        withThresholds(0.75, 0.15, 5, 9),
                        1,
                        RecurringBudgetTransaction.RecurringType.MONTHLY_DAY,
                        null
                },
                {
                        "payee-threshold-strict-prevents-grouping",
                        List.of(
                                tx(1L, -1199, LocalDate.of(2025, 1, 5), "SPOTIFY"),
                                tx(2L, -1200, LocalDate.of(2025, 2, 5), "SPOTFY"),
                                tx(3L, -1201, LocalDate.of(2025, 3, 5), "SPOTIFY")
                        ),
                        withThresholds(0.90, 0.15, 5, 9),
                        0,
                        null,
                        null
                },
                {
                        "weekly-interval-default-detects-weekly",
                        List.of(
                                tx(4L, -3000, LocalDate.of(2025, 1, 6), "GYM FIT"),
                                tx(5L, -3000, LocalDate.of(2025, 1, 13), "GYM FIT"),
                                tx(6L, -3000, LocalDate.of(2025, 1, 20), "GYM FIT")
                        ),
                        withThresholds(0.75, 0.15, 5, 9),
                        1,
                        RecurringBudgetTransaction.RecurringType.WEEKLY,
                        0
                },
                {
                        "weekly-interval-strict-falls-back-to-interval",
                        List.of(
                                tx(4L, -3000, LocalDate.of(2025, 1, 6), "GYM FIT"),
                                tx(5L, -3000, LocalDate.of(2025, 1, 13), "GYM FIT"),
                                tx(6L, -3000, LocalDate.of(2025, 1, 20), "GYM FIT")
                        ),
                        withThresholds(0.75, 0.15, 8, 9),
                        1,
                        RecurringBudgetTransaction.RecurringType.INTERVAL,
                        7
                }
        });
    }

    private final List<RecurringBudgetTransaction> transactions;
    private final PatternDetectionConfig config;
    private final int expectedSuggestionCount;
    private final RecurringBudgetTransaction.RecurringType expectedType;
    private final Integer expectedValue;

    public RecurringPatternDetectorConfigParameterizedTest(String ignoredName,
                                                           List<RecurringBudgetTransaction> transactions,
                                                           PatternDetectionConfig config,
                                                           int expectedSuggestionCount,
                                                           RecurringBudgetTransaction.RecurringType expectedType,
                                                           Integer expectedValue) {
        this.transactions = transactions;
        this.config = config;
        this.expectedSuggestionCount = expectedSuggestionCount;
        this.expectedType = expectedType;
        this.expectedValue = expectedValue;
    }

    @Test
    public void detectPatterns_appliesConfiguredThresholds() {
        List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(transactions, 3, config);

        Assert.assertEquals(expectedSuggestionCount, suggestions.size());
        if (expectedType != null) {
            Assert.assertEquals(expectedType, suggestions.get(0).suggestedType());
        }
        if (expectedValue != null) {
            Assert.assertEquals(expectedValue.intValue(), suggestions.get(0).suggestedValue());
        }
    }

    @Test
    public void detectPatterns_scoreChangesWithConfiguredWeights() {
        PatternDetectionConfig lowKnownSubscriptionWeight = new PatternDetectionConfig(
                config.payeeSimilarityThreshold,
                config.amountVarianceThreshold,
                config.weeklyIntervalMinDays,
                config.weeklyIntervalMaxDays,
                new PatternDetectionConfig.ScoringWeights(0.3, 0.3, 0.3, 0.0),
                config.knownSubscriptionPatterns
        );

        PatternDetectionConfig highKnownSubscriptionWeight = new PatternDetectionConfig(
                config.payeeSimilarityThreshold,
                config.amountVarianceThreshold,
                config.weeklyIntervalMinDays,
                config.weeklyIntervalMaxDays,
                new PatternDetectionConfig.ScoringWeights(0.3, 0.3, 0.3, 0.2),
                config.knownSubscriptionPatterns
        );

        List<RecurringBudgetTransaction> netflixSeries = List.of(
                tx(11L, -1500, LocalDate.of(2025, 1, 1), "NETFLIX"),
                tx(12L, -1500, LocalDate.of(2025, 2, 1), "NETFLIX"),
                tx(13L, -1500, LocalDate.of(2025, 3, 1), "NETFLIX")
        );

        double scoreLow = RecurringPatternDetector.detectPatterns(netflixSeries, 3, lowKnownSubscriptionWeight)
                .get(0)
                .confidenceScore();
        double scoreHigh = RecurringPatternDetector.detectPatterns(netflixSeries, 3, highKnownSubscriptionWeight)
                .get(0)
                .confidenceScore();

        Assert.assertTrue(scoreHigh > scoreLow);
    }

    private static PatternDetectionConfig withThresholds(double payeeSimilarity,
                                                         double amountVariance,
                                                         int weeklyMin,
                                                         int weeklyMax) {
        return new PatternDetectionConfig(
                payeeSimilarity,
                amountVariance,
                weeklyMin,
                weeklyMax,
                PatternDetectionConfig.defaults().scoringWeights,
                PatternDetectionConfig.defaults().knownSubscriptionPatterns
        );
    }

    private static RecurringBudgetTransaction tx(Long id, int amount, LocalDate date, String payee) {
        RecurringBudgetTransaction tx = new RecurringBudgetTransaction.Builder(10L, amount, date, 5L)
                .payee(payee)
                .build();
        tx.id = id;
        return tx;
    }
}
