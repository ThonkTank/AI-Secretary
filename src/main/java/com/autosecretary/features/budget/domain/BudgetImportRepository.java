package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.domain.importing.ImportCategory;
import com.autosecretary.features.budget.domain.importing.ImportStatus;
import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
        /**
         * Creates an import record in the initial {@code PENDING} state.
         * {@code periodStart}, {@code periodEnd}, {@code totalTransactions},
         * {@code importedTransactions}, and {@code autoCategorized} are intentionally
         * left null/zero — they are populated later by {@code markImportCompleted()}.
         * {@code errorMessage} is populated by {@code markImportFailed()} if the import fails.
         */
        public static ImportRecord pending(String id, String accountId,
                                    String fileName, String fileHash) {
            return new ImportRecord(id, accountId, fileName, fileHash,
                    null, null, 0, 0, 0, ImportStatus.PENDING, null);
        }
    }

    record CompletionData(
            int totalTransactions,
            int importedTransactions,
            int autoCategorized,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {}

    ImportRecord createImport(String accountId, String fileName, String fileHash);

    void markImportCompleted(String importId, CompletionData data);

    void markImportFailed(String importId, String errorMessage);

    /**
     * Returns all known import hashes for the given account in one batch query.
     * Used to pre-load the deduplication set before processing an import batch,
     * avoiding per-transaction DB round-trips.
     */
    Set<String> findImportHashesForAccount(String accountId);

    /**
     * Returns the IDs of all active (non-archived) categories in one batch query.
     * Used to pre-load the known-category set before processing an import batch.
     */
    Set<String> findActiveCategoryIds();

    String findDefaultCategoryId(TransactionDirection direction);

    List<ImportCategory> findActiveCategoriesForImport();

    void saveTransactionsBatch(List<ImportTransactionRecord> transactions);

    List<ImportTransactionRecord> findTransactionsForAccount(String accountId);

    String createRecurringTemplate(RecurringSuggestion suggestion, String accountId, LocalDate nextDue);

    void linkTransactionsToTemplate(List<String> transactionIds, String templateId);

    /**
     * Walks all active recurring templates, advances each template's {@code nextDue} date via
     * {@code RecurringTemplateScheduler.computeNextDue()}, deactivates templates whose computed
     * next-due is {@code null}, and inserts predicted {@code BudgetTransactionEntity} rows for
     * templates due within a lookahead window from {@code referenceDate}. Should be called once
     * per app session (e.g. on app resume) to keep predicted transactions up to date.
     */
    void synchronizeRecurringTemplateState(LocalDate referenceDate);

}
