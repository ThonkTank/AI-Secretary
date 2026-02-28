package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Accepts user-approved recurring suggestions, creates persistent templates, and links existing transactions.
 *
 * <p>When the user accepts a recurring pattern detected during import:
 * <ol>
 *   <li>Compute the next due date based on the recurring type (MONTHLY_DAY, WEEKLY, etc.)</li>
 *   <li>Create a recurring template in the database</li>
 *   <li>Link existing transactions to this template (for historical tracking)</li>
 *   <li>Synchronize template scheduling state (active/inactive status)</li>
 *   <li>Notify the UI of the update</li>
 * </ol>
 *
 * <p>Runs asynchronously on a background executor; results via callback.
 *
 * @see domain/recurring/README.md for recurring type definitions and pattern detection
 * @see ApplyCallback for success/error reporting
 */
public class ApplyRecurringSuggestionsUseCase {
    private final BudgetImportRepository repository;
    private final ExecutorService executor;

    public ApplyRecurringSuggestionsUseCase(BudgetImportRepository repository, ExecutorService executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public interface ApplyCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    public void executeAsync(String accountId,
                             List<RecurringSuggestion> suggestions,
                             ApplyCallback callback) {
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
                callback.onSuccess();
            } catch (Exception e) {
                String msg = e.getMessage();
                callback.onError(msg != null ? msg : e.getClass().getSimpleName());
            }
        });
    }

    /**
     * Computes the next due date for a recurring suggestion.
     *
     * <p>Recurring type meanings:
     * <ul>
     *   <li><strong>MONTHLY_DAY</strong> — occurs on a fixed day of the month (e.g., 15th);
     *       if the target day doesn't exist in a month (e.g., Feb 30), wraps to the last day of that month.</li>
     *   <li><strong>MONTHLY_LAST</strong> — occurs on the last day of the month.</li>
     *   <li><strong>WEEKLY</strong> — occurs on a fixed day of week (e.g., Monday);
     *       computes the next occurrence from today.</li>
     *   <li><strong>INTERVAL</strong> — occurs every N days; computes today + N days.</li>
     * </ul>
     *
     * <p>For all types, the result is the next future occurrence. If today is the due date,
     * the next occurrence is in the future (for INTERVAL and WEEKLY) or next month (for MONTHLY_*).
     *
     * @param suggestion contains suggestedType, suggestedValue (day of month or interval days),
     *                  and suggestedDayOfWeek (only used for WEEKLY)
     * @return the next due date
     *
     * @see domain/recurring/README.md for pattern detection algorithm
     */
    private LocalDate calculateNextDue(RecurringSuggestion suggestion) {
        LocalDate today = LocalDate.now();
        return switch (suggestion.suggestedType()) {
            case MONTHLY_DAY -> {
                // Set the target day in the current month, clamping to the month's actual length.
                // E.g., for target day 31 in February, use Feb 28/29.
                int targetDay = suggestion.suggestedValue();
                LocalDate thisMonth = today.withDayOfMonth(Math.min(targetDay, today.lengthOfMonth()));
                // If this month's due date has already passed, jump to next month's due date.
                if (!thisMonth.isAfter(today)) {
                    LocalDate nextMonth = today.plusMonths(1);
                    yield nextMonth.withDayOfMonth(Math.min(targetDay, nextMonth.lengthOfMonth()));
                }
                yield thisMonth;
            }
            case MONTHLY_LAST -> {
                // Get the last day of the current month. If it's in the future, use it;
                // otherwise jump to the last day of next month.
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
