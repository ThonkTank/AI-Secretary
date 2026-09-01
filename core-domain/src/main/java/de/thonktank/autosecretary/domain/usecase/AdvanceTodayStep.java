package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.AdvanceTodayStepResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Advances a visible non-active step with its planned value and focuses it when still open. */
public final class AdvanceTodayStep {
    private final StepExecutionService completion;

    public AdvanceTodayStep(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock);
    }

    public AdvanceTodayStep(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock,
                     ComboPolicySource policies) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock, policies);
    }

    public AdvanceTodayStep(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock,
                     ComboPolicySource policies,
                     FlowRuntimeCoordinator flows) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock, policies,
                flows);
    }

    public AdvanceTodayStepResult execute(String stepId) {
        return completion.advanceStepWithPlannedResult(stepId);
    }
}
