package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class ToggleStep {
    private final StepExecutionService completion;
    public ToggleStep(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                      TransactionRunner transactions) {
        this(catalog, steps, today, transactions, new SystemClock(new SystemZoneIdProvider()));
    }
    public ToggleStep(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                      TransactionRunner transactions, Clock clock) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock);
    }
    public ToggleStep(CatalogRepository catalog, StepRepository steps, TodayRepository today,
               TransactionRunner transactions, Clock clock,
               ComboPolicySource policies) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock, policies);
    }
    public ToggleStep(CatalogRepository catalog, StepRepository steps, TodayRepository today,
               TransactionRunner transactions, Clock clock,
               ComboPolicySource policies,
               FlowRuntimeCoordinator flows) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock, policies,
                flows);
    }
    public RewardReceipt execute(String stepId) { return completion.toggleStep(stepId); }
    public RewardReceipt execute(String stepId, Long chosenDelayMillis) {
        return completion.toggleStep(stepId, chosenDelayMillis);
    }
}
