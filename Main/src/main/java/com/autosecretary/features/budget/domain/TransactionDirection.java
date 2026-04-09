package com.autosecretary.features.budget.domain;

public enum TransactionDirection {
    INCOME,
    EXPENSE;

    /** Derives direction from the sign of a signed-cents value (0 is treated as INCOME). */
    public static TransactionDirection fromAmountCents(long cents) {
        return cents >= 0 ? INCOME : EXPENSE;
    }

    /** Applies the correct sign to an absolute-cents value: INCOME → positive, EXPENSE → negative. */
    public long toSignedCents(long absCents) {
        return this == INCOME ? Math.abs(absCents) : -Math.abs(absCents);
    }
}
