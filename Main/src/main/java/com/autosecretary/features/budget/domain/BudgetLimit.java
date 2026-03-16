package com.autosecretary.features.budget.domain;

public record BudgetLimit(
        String id,
        String categoryId,
        String yearMonth,
        long limitAmountCents,
        boolean rolloverEnabled,
        long rolloverCarryoverCents,
        Long rolloverCapPositiveCents,
        Long rolloverCapOverrunCents
) {
    public static BudgetLimit create(String categoryId,
                                     String yearMonth,
                                     long limitAmountCents,
                                     boolean rolloverEnabled,
                                     long rolloverCarryoverCents) {
        return new BudgetLimit(
                null,
                categoryId,
                yearMonth,
                limitAmountCents,
                rolloverEnabled,
                rolloverCarryoverCents,
                null,
                null
        );
    }
}
