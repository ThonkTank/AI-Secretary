package com.autosecretary.features.budget.domain;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class PayeeGrouperTest {

    @Test
    public void normalizePayee_removesNoiseButKeepsLetters() {
        String normalized = PayeeGrouper.normalizePayee("Netflix *123 #Abo!");

        Assert.assertEquals("NETFLIX ABO", normalized);
    }

    @Test
    public void groupBySimilarPayee_groupsFuzzyMatches() {
        RecurringBudgetTransaction tx1 = tx(1L, "NETFLIX.COM");
        RecurringBudgetTransaction tx2 = tx(2L, "Netflix 123");
        RecurringBudgetTransaction tx3 = tx(3L, "NETFLIX*ABO");
        RecurringBudgetTransaction tx4 = tx(4L, "SPOTIFY LTD");

        Map<String, List<RecurringBudgetTransaction>> grouped = PayeeGrouper.groupBySimilarPayee(List.of(tx1, tx2, tx3, tx4));

        Assert.assertEquals(2, grouped.size());
        Assert.assertTrue(grouped.keySet().stream().anyMatch(k -> k.contains("NETFLIX")));
        Assert.assertTrue(grouped.keySet().stream().anyMatch(k -> k.contains("SPOTIFY")));
    }

    @Test
    public void payeeSimilarity_returnsLowerScoreForDifferentPayees() {
        double similar = PayeeGrouper.payeeSimilarity("NETFLIX", "NETFLIXE");
        double different = PayeeGrouper.payeeSimilarity("NETFLIX", "SPOTIFY");

        Assert.assertTrue(similar > PayeeGrouper.PAYEE_SIMILARITY_THRESHOLD);
        Assert.assertTrue(different < PayeeGrouper.PAYEE_SIMILARITY_THRESHOLD);
    }

    private RecurringBudgetTransaction tx(Long id, String payee) {
        RecurringBudgetTransaction tx = new RecurringBudgetTransaction.Builder(10L, -1200, LocalDate.of(2025, 1, 1), 5L)
                .payee(payee)
                .build();
        tx.id = id;
        return tx;
    }
}
