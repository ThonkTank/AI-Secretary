package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;

public class BudgetTransactionMapperTest {

    private final BudgetTransactionMapper mapper = new BudgetTransactionMapper();

    @Test
    public void roundTrip_expensePreservesSignedAmountAndIds() {
        RecurringBudgetTransaction domain = new RecurringBudgetTransaction.Builder(42L, -1234,
                LocalDate.of(2026, 2, 3), 8L)
                .description("Lunch")
                .build();
        domain.id = 99L;

        BudgetTransactionEntity entity = mapper.toEntity(domain);
        RecurringBudgetTransaction remapped = mapper.toDomain(entity);

        Assert.assertEquals("99", entity.id);
        Assert.assertEquals(BudgetTransactionEntity.TransactionType.EXPENSE, entity.type);
        Assert.assertEquals(12.34, entity.amount, 0.0001);
        Assert.assertEquals(Long.valueOf(99L), remapped.id);
        Assert.assertEquals(-1234, remapped.amountCents);
        Assert.assertEquals(Long.valueOf(42L), remapped.accountId);
        Assert.assertEquals(Long.valueOf(8L), remapped.categoryId);
    }

    @Test
    public void toDomain_incomeProducesPositiveCents() {
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                "12",
                null,
                BudgetTransactionEntity.TransactionType.INCOME,
                25.50,
                LocalDate.of(2026, 1, 10),
                "2026-01"
        );
        entity.id = "501";

        RecurringBudgetTransaction domain = mapper.toDomain(entity);

        Assert.assertEquals(Long.valueOf(501L), domain.id);
        Assert.assertEquals(2550, domain.amountCents);
        Assert.assertEquals(Long.valueOf(12L), domain.accountId);
        Assert.assertNull(domain.categoryId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void toDomain_rejectsInvalidAccountId() {
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                "account-x",
                "7",
                BudgetTransactionEntity.TransactionType.EXPENSE,
                1.0,
                LocalDate.of(2026, 1, 1),
                "2026-01"
        );

        mapper.toDomain(entity);
    }
}
