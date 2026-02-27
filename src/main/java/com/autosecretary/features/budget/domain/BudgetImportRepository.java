package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.domain.importing.ImportStatus;
import com.autosecretary.features.budget.domain.importing.ImportCategory;
import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;

import java.time.LocalDate;
import java.util.List;
import com.autosecretary.features.budget.domain.TransactionDirection;

/**
 * Persistence abstraction for two related concerns:
 *
 * <ol>
 *   <li><b>Import lifecycle</b> — tracks CSV file imports (deduplication via SHA-256 file hash
 *       and per-transaction {@code importHash}) and exposes batch-persist / load operations for
 *       {@link com.autosecretary.features.budget.domain.importing.ImportTransactionRecord}s.</li>
 *   <li><b>Recurring template management</b> — creates templates from detected
 *       {@link RecurringSuggestion}s, links historical transactions to them, and synchronizes
 *       predicted future occurrences.</li>
 * </ol>
 *
 * <p>The primary consumer is
 * {@code com.autosecretary.features.budget.application.importing.BudgetImportUseCase}.
 */
public interface BudgetImportRepository {

    record ImportRecord(
            String id,
            String accountId,
            String fileName,
            String fileHash,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalTransactions,
            int importedTransactions,
            int autoCategorized,
            ImportStatus status,
            String errorMessage
    ) {
        public static ImportRecord pending(String id, String accountId,
                                    String fileName, String fileHash) {
            return new ImportRecord(id, accountId, fileName, fileHash,
                    null, null, 0, 0, 0, ImportStatus.PENDING, null);
        }
    }

    ImportRecord createImport(String accountId, String fileName, String fileHash);

    void markImportCompleted(String importId, int totalTransactions, int importedTransactions, int autoCategorized,
                             LocalDate periodStart, LocalDate periodEnd);

    void markImportFailed(String importId, String errorMessage);

    boolean existsTransactionByImportHash(String importHash);

    String findDefaultCategoryId(TransactionDirection direction);

    boolean isKnownCategory(String categoryId);

    List<ImportCategory> loadActiveCategoriesForImport();

    void saveTransactionsBatch(List<ImportTransactionRecord> transactions);

    List<ImportTransactionRecord> loadTransactionsForAccount(String accountId);

    String createRecurringTemplate(RecurringSuggestion suggestion, String accountId, LocalDate nextDueDate);

    void linkTransactionsToTemplate(List<String> transactionIds, String templateId);

    /**
     * Walks all active recurring templates, advances each template's {@code nextDue} date via
     * {@code RecurringTemplateScheduler.computeNextDue()}, deactivates templates whose computed
     * next-due is {@code null}, and inserts predicted {@code BudgetTransactionEntity} rows for
     * templates due within a lookahead window from {@code referenceDate}. Should be called once
     * per app session (e.g. on app resume) to keep predicted transactions up to date.
     */
    void synchronizeRecurringTemplateState(LocalDate referenceDate);

    /**
     * Signals the Room implementation to post a LiveData update so the UI refreshes after a
     * bulk import or template sync. This method exists on the repository interface because the
     * Room implementation owns the LiveData; it is <em>not</em> a pure persistence operation
     * and must not be called from domain logic that does not own the UI lifecycle.
     */
    void notifyBudgetDataUpdated();
}
