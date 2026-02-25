package com.autosecretary.features.budget.domain;

import org.junit.Assert;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public class RecurringPatternDetectorTest {

    @Test
    public void detectPatterns_groupsByPayeeAndSuggestsMonthly() {
        BudgetTransaction tx1 = tx(1L, -1200, LocalDate.of(2025, 1, 1), "NETFLIX.COM");
        BudgetTransaction tx2 = tx(2L, -1200, LocalDate.of(2025, 2, 1), "Netflix 123");
        BudgetTransaction tx3 = tx(3L, -1200, LocalDate.of(2025, 3, 2), "NETFLIX *A1");

        List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(List.of(tx1, tx2, tx3));

        Assert.assertEquals(1, suggestions.size());
        RecurringSuggestion candidate = suggestions.get(0);
        Assert.assertEquals(BudgetTransaction.RecurringType.MONTHLY_DAY, candidate.suggestedType());
        Assert.assertTrue(candidate.confidenceScore() > 0.6);
    }

    @Test
    public void detectPatterns_suggestsWeeklyWhenWeekdayIsStable() {
        BudgetTransaction tx1 = tx(1L, -3000, LocalDate.of(2025, 1, 6), "GYM FIT"); // Mon
        BudgetTransaction tx2 = tx(2L, -3000, LocalDate.of(2025, 1, 13), "GYM FIT");
        BudgetTransaction tx3 = tx(3L, -3000, LocalDate.of(2025, 1, 20), "GYM FIT");

        List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(List.of(tx1, tx2, tx3));

        Assert.assertEquals(1, suggestions.size());
        Assert.assertEquals(BudgetTransaction.RecurringType.WEEKLY, suggestions.get(0).suggestedType());
        Assert.assertEquals(DayOfWeek.MONDAY, suggestions.get(0).suggestedDayOfWeek());
    }

    private BudgetTransaction tx(Long id, int amount, LocalDate date, String payee) {
        BudgetTransaction tx = new BudgetTransaction.Builder(10L, amount, date, 5L)
                .payee(payee)
                .build();
        tx.id = id;
        return tx;
    }
}
