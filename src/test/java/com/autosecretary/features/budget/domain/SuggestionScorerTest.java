package com.autosecretary.features.budget.domain;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

public class SuggestionScorerTest {

    @Test
    public void calculateConfidence_capsAtOne() {
        List<BudgetTransaction> txList = List.of(
                tx("NETFLIX"), tx("NETFLIX"), tx("NETFLIX"), tx("NETFLIX"), tx("NETFLIX"),
                tx("NETFLIX"), tx("NETFLIX"), tx("NETFLIX"), tx("NETFLIX"), tx("NETFLIX"), tx("NETFLIX")
        );
        DatePatternDetector.PatternResult pattern =
                new DatePatternDetector.PatternResult(BudgetTransaction.RecurringType.MONTHLY_DAY, 1, null);

        double score = SuggestionScorer.calculateConfidence(txList, pattern, -1000, -1000, -1000);

        Assert.assertEquals(1.0, score, 0.0001);
    }

    @Test
    public void calculateConfidence_rewardsKnownSubscription() {
        List<BudgetTransaction> known = List.of(tx("Spotify AB"), tx("Spotify AB"), tx("Spotify AB"));
        List<BudgetTransaction> unknown = List.of(tx("Local Shop"), tx("Local Shop"), tx("Local Shop"));
        DatePatternDetector.PatternResult pattern =
                new DatePatternDetector.PatternResult(BudgetTransaction.RecurringType.MONTHLY_DAY, 1, null);

        double knownScore = SuggestionScorer.calculateConfidence(known, pattern, -1000, -1050, -950);
        double unknownScore = SuggestionScorer.calculateConfidence(unknown, pattern, -1000, -1050, -950);

        Assert.assertTrue(knownScore > unknownScore);
    }

    @Test
    public void calculateConfidence_penalizesLargeVariance() {
        List<BudgetTransaction> txList = List.of(tx("Service"), tx("Service"), tx("Service"));
        DatePatternDetector.PatternResult pattern =
                new DatePatternDetector.PatternResult(BudgetTransaction.RecurringType.INTERVAL, 30, null);

        double lowVariance = SuggestionScorer.calculateConfidence(txList, pattern, -1000, -1020, -980);
        double highVariance = SuggestionScorer.calculateConfidence(txList, pattern, -1000, -2000, -200);

        Assert.assertTrue(lowVariance > highVariance);
    }

    private BudgetTransaction tx(String payee) {
        return new BudgetTransaction.Builder(10L, -1000, LocalDate.of(2025, 1, 1), 5L)
                .payee(payee)
                .build();
    }
}
