package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * Persistenz-Abstraktion für Budget-Import und Recurring-Operationen.
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
            String status,
            String errorMessage
    ) {
    }

    ImportRecord createImport(String accountId, String fileName, String fileHash);

    void markImportCompleted(String importId, int totalTransactions, int importedTransactions, int autoCategorized,
                             LocalDate periodStart, LocalDate periodEnd);

    void markImportFailed(String importId, String errorMessage);

    boolean existsTransactionByImportHash(String importHash);

    String findDefaultCategoryId(boolean income);

    boolean isKnownCategory(String categoryId);

    List<BudgetCategory> loadActiveCategoriesForImport();

    void saveTransactionsBatch(List<BudgetTransactionEntity> transactions);

    List<BudgetTransactionEntity> loadTransactionsForAccount(String accountId);

    String createRecurringTemplate(RecurringSuggestion suggestion, String accountId, LocalDate nextDueDate);

    void linkTransactionsToTemplate(List<String> transactionIds, String templateId);

    void synchronizeRecurringTemplateState(LocalDate referenceDate);

    void notifyBudgetDataUpdated();
}
