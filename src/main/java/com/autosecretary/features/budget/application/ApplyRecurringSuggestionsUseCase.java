package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Übernimmt Recurring-Vorschläge, erstellt Templates und verknüpft bestehende Buchungen.
 */
public class ApplyRecurringSuggestionsUseCase {
    private final BudgetImportRepository repository;
    private final ExecutorService executor;

    public ApplyRecurringSuggestionsUseCase(BudgetImportRepository repository, ExecutorService executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public void executeAsync(Long accountId,
                             List<RecurringSuggestion> suggestions,
                             Runnable onCompleted,
                             java.util.function.Consumer<String> onError) {
        executor.execute(() -> {
            try {
                List<Long> templateIds = new ArrayList<>();
                for (RecurringSuggestion suggestion : suggestions) {
                    LocalDate nextDue = calculateNextDue(suggestion);
                    Long templateId = repository.createRecurringTemplate(suggestion, accountId, nextDue);
                    templateIds.add(templateId);
                    repository.linkTransactionsToTemplate(suggestion.transactionIds(), templateId);
                }

                if (!templateIds.isEmpty()) {
                    repository.notifyBudgetDataUpdated();
                }
                onCompleted.run();
            } catch (Exception e) {
                onError.accept(e.getMessage());
            }
        });
    }

    private LocalDate calculateNextDue(RecurringSuggestion suggestion) {
        LocalDate today = LocalDate.now();
        switch (suggestion.suggestedType()) {
            case MONTHLY_DAY -> {
                int targetDay = suggestion.suggestedValue();
                LocalDate thisMonth = today.withDayOfMonth(Math.min(targetDay, today.lengthOfMonth()));
                if (!thisMonth.isAfter(today)) {
                    LocalDate nextMonth = today.plusMonths(1);
                    return nextMonth.withDayOfMonth(Math.min(targetDay, nextMonth.lengthOfMonth()));
                }
                return thisMonth;
            }
            case MONTHLY_LAST -> {
                LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
                if (!endOfMonth.isAfter(today)) {
                    LocalDate nextMonth = today.plusMonths(1);
                    return nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
                }
                return endOfMonth;
            }
            case WEEKLY -> {
                LocalDate nextOccurrence = today;
                while (nextOccurrence.getDayOfWeek() != suggestion.suggestedDayOfWeek()) {
                    nextOccurrence = nextOccurrence.plusDays(1);
                }
                if (!nextOccurrence.isAfter(today)) {
                    return nextOccurrence.plusWeeks(1);
                }
                return nextOccurrence;
            }
            case INTERVAL -> {
                return today.plusDays(Math.max(1, suggestion.suggestedValue()));
            }
            default -> {
                return today.plusMonths(1);
            }
        }
    }

    public RecurringBudgetTransaction.Builder configureTemplateBuilder(RecurringBudgetTransaction.Builder builder,
                                                              RecurringSuggestion suggestion) {
        return switch (suggestion.suggestedType()) {
            case MONTHLY_DAY -> builder.recurringMonthlyDay(suggestion.suggestedValue());
            case MONTHLY_LAST -> builder.recurringMonthlyLast();
            case WEEKLY -> builder.recurringWeekly(suggestion.suggestedDayOfWeek());
            case INTERVAL -> builder.recurringInterval(suggestion.suggestedValue(), RecurringBudgetTransaction.RepUnit.DAY);
        };
    }
}
