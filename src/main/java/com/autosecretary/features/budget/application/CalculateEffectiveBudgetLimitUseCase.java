package com.autosecretary.features.budget.application;

import androidx.annotation.NonNull;

import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.domain.BudgetRepository;

/**
 * Berechnet das effektive Monatslimit inkl. Rollover aus dem Vormonat.
 */
public class CalculateEffectiveBudgetLimitUseCase {

    public static class Result {
        private final long baseLimitCents;
        private final long spentCents;
        private final long rawDeltaCents;
        private final long appliedDeltaCents;
        private final long effectiveLimitCents;
        private final boolean rolloverApplied;

        public Result(long baseLimitCents,
                      long spentCents,
                      long rawDeltaCents,
                      long appliedDeltaCents,
                      long effectiveLimitCents,
                      boolean rolloverApplied) {
            this.baseLimitCents = baseLimitCents;
            this.spentCents = spentCents;
            this.rawDeltaCents = rawDeltaCents;
            this.appliedDeltaCents = appliedDeltaCents;
            this.effectiveLimitCents = effectiveLimitCents;
            this.rolloverApplied = rolloverApplied;
        }

        public long getBaseLimitCents() {
            return baseLimitCents;
        }

        public long getSpentCents() {
            return spentCents;
        }

        public long getRawDeltaCents() {
            return rawDeltaCents;
        }

        public long getAppliedDeltaCents() {
            return appliedDeltaCents;
        }

        public long getEffectiveLimitCents() {
            return effectiveLimitCents;
        }

        public boolean isRolloverApplied() {
            return rolloverApplied;
        }
    }

    private final BudgetRepository repository;

    public CalculateEffectiveBudgetLimitUseCase(@NonNull BudgetRepository repository) {
        this.repository = repository;
    }

    public Result execute(@NonNull String categoryId, @NonNull String targetYearMonth) {
        BudgetLimit target = repository.findBudgetLimit(categoryId, targetYearMonth);
        long spentCents = repository.getCategoryExpenseCents(categoryId, targetYearMonth);
        if (target == null) {
            return new Result(0L, spentCents, 0L, 0L, 0L, false);
        }

        long baseLimitCents = eurosToCents(target.amount);
        if (!target.rolloverEnabled) {
            return new Result(baseLimitCents, spentCents, 0L, 0L, Math.max(baseLimitCents, 0L), false);
        }

        BudgetLimit previous = repository.findPreviousMonthLimit(categoryId, targetYearMonth);
        // Sonderfall: kein Vormonatslimit => kein Delta.
        long rawDeltaCents = 0L;
        if (previous != null) {
            long previousLimitCents = eurosToCents(previous.amount);
            long previousSpentCents = repository.getPreviousMonthExpenseCents(categoryId, targetYearMonth);
            rawDeltaCents = previousLimitCents - previousSpentCents;
        }

        long appliedDelta = applyDeltaCaps(rawDeltaCents, target.rolloverCapPositiveCents, target.rolloverCapNegativeCents);
        long effective = baseLimitCents + target.rolloverCarryoverCents + appliedDelta;

        // Sonderfall: negativer Carryover größer als Basislimit => auf 0 deckeln.
        long effectiveNonNegative = Math.max(0L, effective);

        return new Result(baseLimitCents, spentCents, rawDeltaCents, appliedDelta, effectiveNonNegative, true);
    }

    private long applyDeltaCaps(long delta, Long capPositiveCents, Long capNegativeCents) {
        long capped = delta;
        if (capPositiveCents != null) {
            capped = Math.min(capped, capPositiveCents);
        }
        if (capNegativeCents != null) {
            capped = Math.max(capped, -Math.abs(capNegativeCents));
        }
        return capped;
    }

    private long eurosToCents(double amountEuros) {
        return Math.round(amountEuros * 100.0);
    }
}
