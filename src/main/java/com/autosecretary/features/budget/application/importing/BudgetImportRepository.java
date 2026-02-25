package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.time.LocalDate;
import java.util.List;

/**
 * Persistenz-Abstraktion für Budget-Import und Recurring-Operationen.
 */
public interface BudgetImportRepository {

    record ImportRecord(
            Long id,
            Long accountId,
            String fileName,
            String fileHash,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalTransactions,
            int importedTransactions,
            int autoCategorized,
            String status,
            String errorMessage
    ) {
    }

    ImportRecord createImport(Long accountId, String fileName, String fileHash);

    void markImportCompleted(Long importId, int totalTransactions, int importedTransactions, int autoCategorized,
                             LocalDate periodStart, LocalDate periodEnd);

    void markImportFailed(Long importId, String errorMessage);

    boolean existsTransactionByImportHash(String importHash);

    Long findDefaultCategoryId(boolean income);

    void saveTransactionsBatch(List<BudgetTransactionEntity> transactions);

    List<BudgetTransactionEntity> loadTransactionsForAccount(Long accountId);

    Long createRecurringTemplate(RecurringSuggestion suggestion, Long accountId, LocalDate nextDueDate);

    void linkTransactionsToTemplate(List<Long> transactionIds, Long templateId);

    void notifyBudgetDataUpdated();
}
