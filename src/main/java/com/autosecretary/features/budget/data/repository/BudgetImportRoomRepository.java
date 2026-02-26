package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.data.dao.BudgetLookupDao;
import com.autosecretary.features.budget.data.dao.TransactionDao;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.dao.BudgetImportDao;
import com.autosecretary.features.budget.data.entity.BudgetImportEntity;
import com.autosecretary.features.budget.data.dao.BudgetRecurringTemplateDao;
import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.data.entity.ImportStatus;
import com.autosecretary.features.budget.data.dao.AccountBalanceTotal;
import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.importing.ImportCategory;
import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.RecurringSuggestion;
import com.autosecretary.features.budget.domain.internal.RecurringTemplateScheduler;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Room-Implementierung der BudgetImportRepository-Schnittstelle.
 */
public class BudgetImportRoomRepository implements BudgetImportRepository {
    private final BudgetImportDao importDao;
    private final BudgetRecurringTemplateDao templateDao;
    private final TransactionDao transactionDao;
    private final BudgetLookupDao lookupDao;
    private final Runnable onBudgetDataUpdated;

    public BudgetImportRoomRepository(BudgetImportDao importDao,
                                       BudgetRecurringTemplateDao templateDao,
                                       TransactionDao transactionDao,
                                       BudgetLookupDao lookupDao) {
        this(importDao, templateDao, transactionDao, lookupDao, () -> { });
    }

    public BudgetImportRoomRepository(BudgetImportDao importDao,
                                       BudgetRecurringTemplateDao templateDao,
                                       TransactionDao transactionDao,
                                       BudgetLookupDao lookupDao,
                                       Runnable onBudgetDataUpdated) {
        this.importDao = importDao;
        this.templateDao = templateDao;
        this.transactionDao = transactionDao;
        this.lookupDao = lookupDao;
        this.onBudgetDataUpdated = onBudgetDataUpdated;
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
                /* periodStart */      null,
                /* periodEnd */        null,
                /* total */            0,
                /* imported */         0,
                /* autoCategorized */  0,
                entity.status,
                /* errorMessage */     null
        );
    }

    @Override
    public void markImportCompleted(String importId, int totalTransactions, int importedTransactions,
                                     int autoCategorized, LocalDate periodStart, LocalDate periodEnd) {
        importDao.markCompleted(importId, ImportStatus.COMPLETED, totalTransactions, importedTransactions,
                autoCategorized, periodStart, periodEnd);
        synchronizeRecurringTemplateState(LocalDate.now());
    }

    @Override
    public void markImportFailed(String importId, String errorMessage) {
        importDao.markFailed(importId, ImportStatus.FAILED, errorMessage);
    }

    @Override
    public boolean existsTransactionByImportHash(String importHash) {
        return transactionDao.existsByImportHash(importHash);
    }

    @Override
    public String findDefaultCategoryId(boolean income) {
        BudgetTransactionEntity.TransactionType type = income
                ? BudgetTransactionEntity.TransactionType.INCOME
                : BudgetTransactionEntity.TransactionType.EXPENSE;
        return lookupDao.findDefaultCategoryId(type);
    }

    @Override
    public boolean isKnownCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return false;
        }
        return lookupDao.findCategoryById(categoryId) != null;
    }

    @Override
    public List<ImportCategory> loadActiveCategoriesForImport() {
        return lookupDao.getActiveCategories().stream()
                .map(category -> new ImportCategory(category.id, category.name, BudgetTransactionEntity.TransactionType.valueOf(category.type)))
                .toList();
    }

    @Override
    public void saveTransactionsBatch(List<ImportTransactionRecord> transactions) {
        transactionDao.insertAll(transactions.stream().map(this::toEntity).toList());
        updateAccountBalances();
    }

    @Override
    public List<ImportTransactionRecord> loadTransactionsForAccount(String accountId) {
        return transactionDao.findByAccountId(accountId).stream().map(this::toRecord).toList();
    }

    @Override
    public String createRecurringTemplate(RecurringSuggestion suggestion, String accountId,
                                           LocalDate nextDueDate) {
        BudgetRecurringTemplateEntity entity = new BudgetRecurringTemplateEntity(
                accountId,
                suggestion.normalizedPayee(),
                suggestion.suggestedType()
        );
        entity.displayPayee = suggestion.displayPayee();
        entity.categoryId = suggestion.categoryId();
        entity.avgAmountCents = suggestion.avgAmountCents();
        entity.minAmountCents = suggestion.minAmountCents();
        entity.maxAmountCents = suggestion.maxAmountCents();
        entity.recurringValue = suggestion.suggestedValue();
        entity.recurringDayOfWeek = suggestion.suggestedDayOfWeek();
        entity.nextDue = nextDueDate != null ? nextDueDate : LocalDate.now();
        entity.active = true;

        templateDao.insert(entity);
        synchronizeRecurringTemplateState(LocalDate.now());
        return entity.id;
    }

    @Override
    public void linkTransactionsToTemplate(List<String> transactionIds, String templateId) {
        if (transactionIds == null || transactionIds.isEmpty() || templateId == null || templateId.isBlank()) {
            return;
        }
        transactionDao.updateTemplateIdForTransactions(transactionIds, templateId);
    }

    @Override
    public void synchronizeRecurringTemplateState(LocalDate referenceDate) {
        for (BudgetRecurringTemplateEntity template : templateDao.findAllActiveTemplates()) {
            LocalDate nextDue = RecurringTemplateScheduler.computeNextDue(template, referenceDate);
            boolean active = nextDue != null;
            // When deactivating, preserve the existing nextDue rather than overwriting with null
            LocalDate dueDateToStore = active ? nextDue : template.nextDue;
            templateDao.updateNextDueAndStatus(template.id, dueDateToStore, active);
        }
    }

    @Override
    public void notifyBudgetDataUpdated() {
        onBudgetDataUpdated.run();
    }

    private void updateAccountBalances() {
        List<AccountBalanceTotal> totals = transactionDao.getAccountBalanceTotals();
        Map<String, Long> balanceByAccount = new HashMap<>();
        for (AccountBalanceTotal total : totals) {
            balanceByAccount.put(total.accountId, total.balanceCents);
        }
        for (BudgetAccount account : lookupDao.getActiveAccounts()) {
            long balance = balanceByAccount.getOrDefault(account.id, 0L);
            lookupDao.updateCurrentBalanceCents(account.id, balance);
        }
    }

    private BudgetTransactionEntity toEntity(ImportTransactionRecord record) {
        BudgetTransactionEntity.TransactionType txType =
                "EXPENSE".equals(record.type()) ? BudgetTransactionEntity.TransactionType.EXPENSE
                        : BudgetTransactionEntity.TransactionType.INCOME;
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                record.accountId(),
                record.categoryId(),
                txType,
                record.amountCents(),
                record.bookingDate(),
                record.yearMonth()
        );
        entity.id = record.id();
        entity.note = record.note();
        entity.importHash = record.importHash();
        entity.payee = record.payee();
        entity.importId = record.importId();
        entity.templateId = record.templateId();
        return entity;
    }

    private ImportTransactionRecord toRecord(BudgetTransactionEntity entity) {
        String type = entity.type.name();
        return new ImportTransactionRecord(
                entity.id,
                entity.accountId,
                entity.categoryId,
                type,
                entity.amountCents,
                entity.bookingDate,
                entity.yearMonth,
                entity.note,
                entity.importHash,
                entity.payee,
                entity.importId,
                entity.templateId
        );
    }
}
