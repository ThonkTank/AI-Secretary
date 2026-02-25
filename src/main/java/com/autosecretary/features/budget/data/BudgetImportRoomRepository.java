package com.autosecretary.features.budget.data;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.time.LocalDate;
import java.util.List;

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
        synchronizeRecurringTemplateState(LocalDate.now());
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
    public void saveTransactionsBatch(List<BudgetTransactionEntity> transactions) {
        transactionDao.insertAll(transactions);
        updateAccountBalances();
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
        entity.nextDue = nextDueDate != null ? nextDueDate : LocalDate.now();
        entity.active = true;

        templateDao.insert(entity);
        synchronizeRecurringTemplateState(LocalDate.now());
        return entity.id;
    }

    @Override
    public void linkTransactionsToTemplate(List<String> transactionIds, String templateId) {
        // No-Op: BudgetTransactionEntity hat noch kein templateId-Feld.
        // Wird in einem späteren Schritt ergänzt.
    }

    @Override
    public void synchronizeRecurringTemplateState(LocalDate referenceDate) {
        for (BudgetRecurringTemplateEntity template : templateDao.findAllActiveTemplates()) {
            LocalDate dueDate = template.nextDue != null ? template.nextDue : referenceDate;
            boolean active = true;

            if ("WEEKLY".equals(template.recurringType) && template.recurringDayOfWeek != null) {
                while (dueDate.isBefore(referenceDate)
                        || dueDate.getDayOfWeek() != template.recurringDayOfWeek) {
                    dueDate = dueDate.plusDays(1);
                }
            } else if ("INTERVAL".equals(template.recurringType)) {
                int intervalDays = Math.max(1, template.recurringValue);
                while (dueDate.isBefore(referenceDate)) {
                    dueDate = dueDate.plusDays(intervalDays);
                }
            } else if ("MONTHLY_DAY".equals(template.recurringType)) {
                if (template.recurringValue < 1 || template.recurringValue > 31) {
                    active = false;
                } else {
                    while (dueDate.isBefore(referenceDate)) {
                        LocalDate nextMonth = dueDate.plusMonths(1);
                        dueDate = nextMonth.withDayOfMonth(Math.min(template.recurringValue, nextMonth.lengthOfMonth()));
                    }
                }
            } else if ("MONTHLY_LAST".equals(template.recurringType)) {
                while (dueDate.isBefore(referenceDate)) {
                    LocalDate nextMonth = dueDate.plusMonths(1);
                    dueDate = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
                }
            } else if (template.nextDue == null) {
                active = false;
            }

            templateDao.updateNextDueAndStatus(template.id, dueDate, active);
        }
    }

    @Override
    public void notifyBudgetDataUpdated() {
        // No-Op: UI-Refresh passiert über die Callback-Kette im ViewModel.
    }

    private void updateAccountBalances() {
        List<AccountBalanceTotal> totals = transactionDao.getAccountBalanceTotals();
        for (BudgetAccount account : lookupDao.getActiveAccounts()) {
            long balance = 0;
            for (AccountBalanceTotal total : totals) {
                if (account.id.equals(total.accountId)) {
                    balance = total.balanceCents;
                    break;
                }
            }
            lookupDao.updateCurrentBalanceCents(account.id, balance);
        }
    }
}
