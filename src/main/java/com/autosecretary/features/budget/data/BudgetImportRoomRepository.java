package com.autosecretary.features.budget.data;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Room-Implementierung der BudgetImportRepository-Schnittstelle.
 */
public class BudgetImportRoomRepository implements BudgetImportRepository {
    private final BudgetImportDao importDao;
    private final BudgetRecurringTemplateDao templateDao;
    private final TransactionDao transactionDao;
    private final BudgetLookupDao lookupDao;

    public BudgetImportRoomRepository(BudgetImportDao importDao,
                                       BudgetRecurringTemplateDao templateDao,
                                       TransactionDao transactionDao,
                                       BudgetLookupDao lookupDao) {
        this.importDao = importDao;
        this.templateDao = templateDao;
        this.transactionDao = transactionDao;
        this.lookupDao = lookupDao;
    }

    @Override
    public ImportRecord createImport(String accountId, String fileName, String fileHash) {
        BudgetImportEntity entity = new BudgetImportEntity(accountId, fileName, fileHash);
        importDao.insert(entity);
        return new ImportRecord(
                entity.id,
                entity.accountId,
                entity.fileName,
                entity.fileHash,
                null,
                null,
                0,
                0,
                0,
                entity.status,
                null
        );
    }

    @Override
    public void markImportCompleted(String importId, int totalTransactions, int importedTransactions,
                                     int autoCategorized, LocalDate periodStart, LocalDate periodEnd) {
        importDao.markCompleted(importId, totalTransactions, importedTransactions,
                autoCategorized, periodStart, periodEnd);
    }

    @Override
    public void markImportFailed(String importId, String errorMessage) {
        importDao.markFailed(importId, errorMessage);
    }

    @Override
    public boolean existsTransactionByImportHash(String importHash) {
        return transactionDao.existsByImportHash(importHash);
    }

    @Override
    public String findDefaultCategoryId(boolean income) {
        String type = income ? "INCOME" : "EXPENSE";
        return transactionDao.findDefaultCategoryId(type);
    }

    @Override
    public boolean isKnownCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return false;
        }
        return lookupDao.findCategoryById(categoryId) != null;
    }

    @Override
    public List<BudgetCategory> loadActiveCategoriesForImport() {
        return lookupDao.getActiveCategories();
    }

    @Override
    public void saveTransactionsBatch(List<BudgetTransactionEntity> transactions) {
        transactionDao.insertAll(transactions);
    }

    @Override
    public List<BudgetTransactionEntity> loadTransactionsForAccount(String accountId) {
        return transactionDao.findByAccountId(accountId);
    }

    @Override
    public String createRecurringTemplate(RecurringSuggestion suggestion, String accountId,
                                           LocalDate nextDueDate) {
        BudgetRecurringTemplateEntity entity = new BudgetRecurringTemplateEntity(
                accountId,
                suggestion.normalizedPayee(),
                suggestion.suggestedType().name()
        );
        entity.displayPayee = suggestion.displayPayee();
        entity.categoryId = suggestion.categoryId();
        entity.avgAmountCents = suggestion.avgAmountCents();
        entity.minAmountCents = suggestion.minAmountCents();
        entity.maxAmountCents = suggestion.maxAmountCents();
        entity.recurringValue = suggestion.suggestedValue();
        entity.recurringDayOfWeek = suggestion.suggestedDayOfWeek();
        entity.nextDue = nextDueDate;

        templateDao.insert(entity);
        return entity.id;
    }

    @Override
    public void linkTransactionsToTemplate(List<String> transactionIds, String templateId) {
        // No-Op: BudgetTransactionEntity hat noch kein templateId-Feld.
        // Wird in einem späteren Schritt ergänzt.
    }

    @Override
    public void notifyBudgetDataUpdated() {
        // No-Op: UI-Refresh passiert über die Callback-Kette im ViewModel.
    }
}
