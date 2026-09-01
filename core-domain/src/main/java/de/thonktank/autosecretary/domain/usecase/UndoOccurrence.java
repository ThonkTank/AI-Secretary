package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class UndoOccurrence {
    private final OccurrenceCompletionService completion;
    public UndoOccurrence(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock) {
        completion = new OccurrenceCompletionService(catalog, steps, today, transactions, clock);
    }
    public UndoOccurrence(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock,
                   ComboPolicySource policies) {
        completion = new OccurrenceCompletionService(catalog, steps, today, transactions, clock,
                policies);
    }
    public UndoOccurrence(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock,
                   ComboPolicySource policies,
                   FlowRuntimeCoordinator flows) {
        completion = new OccurrenceCompletionService(catalog, steps, today, transactions, clock,
                policies, flows);
    }
    public RewardReceipt execute(String occurrenceId) {
        return completion.undoOccurrence(occurrenceId);
    }
}
