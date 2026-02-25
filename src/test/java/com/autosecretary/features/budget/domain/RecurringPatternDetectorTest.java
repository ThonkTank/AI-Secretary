package com.autosecretary.features.budget.domain;

import org.junit.Assert;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public class RecurringPatternDetectorTest {

    @Test
    public void detectPatterns_groupsByPayeeAndSuggestsMonthly() {
        RecurringBudgetTransaction tx1 = tx(1L, -1200, LocalDate.of(2025, 1, 1), "NETFLIX.COM");
        RecurringBudgetTransaction tx2 = tx(2L, -1200, LocalDate.of(2025, 2, 1), "Netflix 123");
        RecurringBudgetTransaction tx3 = tx(3L, -1200, LocalDate.of(2025, 3, 2), "NETFLIX *A1");

        List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(List.of(tx1, tx2, tx3));

        Assert.assertEquals(1, suggestions.size());
        RecurringSuggestion candidate = suggestions.get(0);
        Assert.assertEquals(RecurringBudgetTransaction.RecurringType.MONTHLY_DAY, candidate.suggestedType());
        Assert.assertTrue(candidate.confidenceScore() > 0.6);
    }

    @Test
    public void detectPatterns_suggestsWeeklyWhenWeekdayIsStable() {
        RecurringBudgetTransaction tx1 = tx(1L, -3000, LocalDate.of(2025, 1, 6), "GYM FIT"); // Mon
        RecurringBudgetTransaction tx2 = tx(2L, -3000, LocalDate.of(2025, 1, 13), "GYM FIT");
        RecurringBudgetTransaction tx3 = tx(3L, -3000, LocalDate.of(2025, 1, 20), "GYM FIT");

        List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(List.of(tx1, tx2, tx3));

        Assert.assertEquals(1, suggestions.size());
        Assert.assertEquals(RecurringBudgetTransaction.RecurringType.WEEKLY, suggestions.get(0).suggestedType());
        Assert.assertEquals(DayOfWeek.MONDAY, suggestions.get(0).suggestedDayOfWeek());
    }

    private RecurringBudgetTransaction tx(Long id, int amount, LocalDate date, String payee) {
        RecurringBudgetTransaction tx = new RecurringBudgetTransaction.Builder(10L, amount, date, 5L)
                .payee(payee)
                .build();
        tx.id = id;
        return tx;
    }
}
