package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.domain.BudgetRepository;

/**
 * Berechnet das effektive Monatslimit inkl. Rollover aus dem Vormonat.
 */
public class CalculateEffectiveBudgetLimitUseCase {

    public record Result(
            long baseLimitCents,
            long spentCents,
            long rawDeltaCents,
            long appliedDeltaCents,
            long effectiveLimitCents,
            boolean rolloverApplied
    ) {
    }

    private final BudgetRepository repository;

    public CalculateEffectiveBudgetLimitUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    public Result execute(String categoryId, String targetYearMonth) {
        BudgetLimit target = repository.findBudgetLimit(categoryId, targetYearMonth);
        long spentCents = repository.getCategoryExpenseCents(categoryId, targetYearMonth);
        if (target == null) {
            return new Result(0L, spentCents, 0L, 0L, 0L, false);
        }

        long baseLimitCents = target.limitAmountCents;
        if (!target.rolloverEnabled) {
            return new Result(baseLimitCents, spentCents, 0L, 0L, Math.max(baseLimitCents, 0L), false);
        }

        BudgetLimit previous = repository.findPreviousMonthLimit(categoryId, targetYearMonth);
        // Sonderfall: kein Vormonatslimit => kein Delta.
        long rawDeltaCents = 0L;
        if (previous != null) {
            long previousSpentCents = repository.getPreviousMonthExpenseCents(categoryId, targetYearMonth);
            rawDeltaCents = previous.limitAmountCents - previousSpentCents;
        }

        long appliedDelta = applyDeltaCaps(rawDeltaCents, target.rolloverCapPositiveCents, target.rolloverCapOverrunCents);
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
}
