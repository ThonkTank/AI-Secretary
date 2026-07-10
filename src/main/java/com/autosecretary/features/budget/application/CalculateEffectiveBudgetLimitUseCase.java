package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetLimit;
import com.autosecretary.features.budget.domain.BudgetRepository;

/**
 * Calculates the effective monthly budget limit for a category, accounting for rollover from previous months.
 *
 * <p><strong>Rollover concept:</strong>
 * Budget discipline often requires carrying forward unused (or overspent) budget between months:
 * <ul>
 *   <li><strong>Positive rollover:</strong> If a category's limit is €1000 and only €800 is spent,
 *       the unused €200 can roll forward to the next month (increasing next month's effective limit
 *       to €1200, subject to optional caps).</li>
 *   <li><strong>Negative rollover:</strong> If a category is overspent, the overage can carry backward,
 *       reducing next month's available budget (subject to optional caps).</li>
 * </ul>
 *
 * <p><strong>Calculation formula:</strong>
 * <pre>
 *   effective limit = base limit + fixed carryover + capped delta
 * </pre>
 * where:
 * <ul>
 *   <li><strong>base limit:</strong> the category's fixed limit for the target month</li>
 *   <li><strong>fixed carryover:</strong> a manually configured fixed amount (e.g., always add €50)</li>
 *   <li><strong>delta:</strong> the difference between previous month's limit and previous month's spending</li>
 *   <li><strong>capped delta:</strong> delta constrained by optional min/max caps to avoid extreme rollover</li>
 * </ul>
 *
 * <p><strong>Example:</strong>
 * If Jan limit is €1000, Jan spent €800, Feb limit is €1000, rollover is enabled with no caps:
 * <pre>
 *   delta = 1000 - 800 = 200
 *   effective_feb_limit = 1000 + 200 = €1200
 * </pre>
 * But if a cap of €100 is set, effective limit would be €1000 + €100 = €1100.
 *
 * @see Result for the output fields and their meanings
 */
public class CalculateEffectiveBudgetLimitUseCase {

    /**
     * Result of a budget limit calculation.
     *
     * @param baseLimitCents the fixed monthly limit for the target month
     * @param spentCents amount already spent in the target month
     * @param rawDeltaCents (previous month limit) - (previous month spent); can be positive or negative
     * @param appliedDeltaCents rawDeltaCents after applying rollover caps; this is what actually carries forward
     * @param effectiveLimitCents final effective limit = base + fixed carryover + appliedDelta
     * @param rolloverApplied true if rollover calculation was performed; false if this month has no rollover settings
     */
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

    /**
     * Calculates the effective limit for a category in a target month, accounting for previous month's rollover.
     *
     * @param categoryId the category UUID
     * @param targetYearMonth ISO format, e.g., "2024-01" (same format as BudgetLimitEntity.yearMonth)
     * @return Result with effective limit and diagnostic fields; see {@link Result} for field meanings
     */
    public Result execute(String categoryId, String targetYearMonth) {
        BudgetLimit target = repository.findBudgetLimit(categoryId, targetYearMonth);
        long spentCents = repository.getCategoryExpenseCents(categoryId, targetYearMonth);
        if (target == null) {
            return new Result(0L, spentCents, 0L, 0L, 0L, false);
        }

        long baseLimitCents = target.limitAmountCents();
        if (!target.rolloverEnabled()) {
            // Rollover disabled: effective limit is just the base limit (clamped to 0 minimum)
            return new Result(baseLimitCents, spentCents, 0L, 0L, Math.max(baseLimitCents, 0L), false);
        }

        // Rollover is enabled: fetch previous month's limit and calculate the delta.
        BudgetLimit previous = repository.findPreviousMonthLimit(categoryId, targetYearMonth);
        long rawDeltaCents = 0L;
        if (previous != null) {
            // Delta = (previous month's limit) - (previous month's spending)
            // Positive delta = unused budget (carry forward to current month)
            // Negative delta = overspend (carry backward, reducing current month's limit)
            long previousSpentCents = repository.getPreviousMonthExpenseCents(categoryId, targetYearMonth);
            rawDeltaCents = previous.limitAmountCents() - previousSpentCents;
        }

        // Apply rollover caps to limit how much can carry forward (positive) or backward (negative)
        long appliedDelta = applyDeltaCaps(rawDeltaCents, target.rolloverCapPositiveCents(), target.rolloverCapOverrunCents());

        // Final effective limit: base + fixed carryover + capped delta
        long effective = baseLimitCents + target.rolloverCarryoverCents() + appliedDelta;

        // Ensure effective limit is never negative (edge case when carryover debt is large)
        return new Result(baseLimitCents, spentCents, rawDeltaCents, appliedDelta, Math.max(0L, effective), true);
    }

    private long applyDeltaCaps(long delta, Long capPositiveCents, Long capOverrunCents) {
        long capped = delta;
        if (capPositiveCents != null) {
            capped = Math.min(capped, capPositiveCents);
        }
        if (capOverrunCents != null) {
            capped = Math.max(capped, -capOverrunCents);
        }
        return capped;
    }
}
