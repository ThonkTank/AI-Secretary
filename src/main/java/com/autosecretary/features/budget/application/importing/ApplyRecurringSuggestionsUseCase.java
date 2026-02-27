package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
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

    public void executeAsync(String accountId,
                             List<RecurringSuggestion> suggestions,
                             Runnable onCompleted,
                             java.util.function.Consumer<String> onError) {
        executor.execute(() -> {
            try {
                List<String> templateIds = new ArrayList<>();
                for (RecurringSuggestion suggestion : suggestions) {
                    LocalDate nextDue = calculateNextDue(suggestion);
                    String templateId = repository.createRecurringTemplate(suggestion, accountId, nextDue);
                    templateIds.add(templateId);
                    repository.linkTransactionsToTemplate(suggestion.transactionIds(), templateId);
                }

                if (!templateIds.isEmpty()) {
                    repository.synchronizeRecurringTemplateState(LocalDate.now());
                    repository.notifyBudgetDataUpdated();
                }
                onCompleted.run();
            } catch (Exception e) {
                String msg = e.getMessage();
                onError.accept(msg != null ? msg : e.getClass().getSimpleName());
            }
        });
    }

    private LocalDate calculateNextDue(RecurringSuggestion suggestion) {
        LocalDate today = LocalDate.now();
        return switch (suggestion.suggestedType()) {
            case MONTHLY_DAY -> {
                int targetDay = suggestion.suggestedValue();
                LocalDate thisMonth = today.withDayOfMonth(Math.min(targetDay, today.lengthOfMonth()));
                if (!thisMonth.isAfter(today)) {
                    LocalDate nextMonth = today.plusMonths(1);
                    yield nextMonth.withDayOfMonth(Math.min(targetDay, nextMonth.lengthOfMonth()));
                }
                yield thisMonth;
            }
            case MONTHLY_LAST -> {
                LocalDate endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());
                yield endOfMonth.isAfter(today)
                        ? endOfMonth
                        : today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            }
            case WEEKLY -> today.with(TemporalAdjusters.next(suggestion.suggestedDayOfWeek()));
            case INTERVAL -> today.plusDays(Math.max(1, suggestion.suggestedValue()));
        };
    }

}
