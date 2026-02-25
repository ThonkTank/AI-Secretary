package com.autosecretary.features.budget.data;

import com.autosecretary.features.budget.domain.BudgetRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BudgetRoomRepository implements BudgetRepository {
    private final BudgetLookupDao lookupDao;
    private final TransactionDao transactionDao;
    private final BudgetLimitDao limitDao;
    private final BudgetRecurringTemplateDao recurringTemplateDao;

    public BudgetRoomRepository(BudgetLookupDao lookupDao,
                                 TransactionDao transactionDao,
                                 BudgetLimitDao limitDao,
                                 BudgetRecurringTemplateDao recurringTemplateDao) {
        this.lookupDao = lookupDao;
        this.transactionDao = transactionDao;
        this.limitDao = limitDao;
        this.recurringTemplateDao = recurringTemplateDao;
    }

    @Override public BudgetAccount findAccountById(String accountId) {
        return lookupDao.findAccountById(accountId);
    }

    @Override public List<BudgetAccount> findActiveAccounts() {
        return lookupDao.getActiveAccounts();
    }

    @Override public List<BudgetCategory> getActiveCategories() {
        return lookupDao.getActiveCategories();
    }

    @Override public List<BudgetTransactionEntity> findAllTransactions() {
        return transactionDao.findAll();
    }

    @Override public List<BudgetTransactionEntity> findTransactionsForAccount(String accountId) {
        return transactionDao.findByAccountId(accountId);
    }

    @Override public BudgetLimit findBudgetLimit(String categoryId, String yearMonth) {
        return limitDao.getLimitForCategoryAndMonth(categoryId, yearMonth);
    }

    @Override public void saveTransaction(BudgetTransactionEntity transaction) {
        transactionDao.insert(transaction);
        recalculateAccountBalances();
    }

    @Override public void deleteTransaction(String transactionId) {
        transactionDao.deleteById(transactionId);
        recalculateAccountBalances();
    }

    @Override public void saveBudgetLimit(BudgetLimit budgetLimit) {
        BudgetLimit existing = limitDao.getLimitForCategoryAndMonth(budgetLimit.categoryId, budgetLimit.yearMonth);
        if (existing != null) {
            budgetLimit.id = existing.id;
        }
        limitDao.insert(budgetLimit);
    }

    @Override public void saveTransactions(List<BudgetTransactionEntity> transactions) {
        transactionDao.insertAll(transactions);
        recalculateAccountBalances();
    }

    @Override public void insertAccount(BudgetAccount account) {
        lookupDao.insertAccount(account);
    }

    @Override public void insertCategory(BudgetCategory category) {
        lookupDao.insertCategory(category);
    }

    @Override public List<MonthlyTransactionOverviewItem> getMonthlyOverview(String yearMonth) {
        return transactionDao.getMonthlyOverview(yearMonth);
    }

    @Override public List<CategorySpendTotal> getCategorySpendTotals(String yearMonth) {
        return limitDao.getCategorySpendTotals(yearMonth);
    }

    @Override
    public long getCurrentBalanceCents(String accountId) {
        if (accountId != null) {
            Long value = lookupDao.findCurrentBalanceCentsByAccountId(accountId);
            return value == null ? 0L : value;
        }
        return lookupDao.sumCurrentBalanceCentsForActiveAccounts();
    }

    @Override
    public long getUpcomingExpenseTemplateCents(String accountId, LocalDate fromDate, LocalDate toDate) {
        List<BudgetRecurringTemplateEntity> templates = findActiveExpenseTemplates(accountId, fromDate, toDate);
        long total = 0;
        for (BudgetRecurringTemplateEntity template : templates) {
            total += Math.abs(template.avgAmountCents);
        }
        return total;
    }

    @Override
    public List<BudgetRecurringTemplateEntity> findActiveExpenseTemplates(String accountId,
                                                                          LocalDate fromDate,
                                                                          LocalDate toDate) {
        if (accountId != null) {
            return recurringTemplateDao.findActiveExpenseTemplatesForAccountInRange(accountId, fromDate, toDate);
        }
        return recurringTemplateDao.findActiveExpenseTemplatesForActiveAccountsInRange(fromDate, toDate);
    }

    private void recalculateAccountBalances() {
        List<AccountBalanceTotal> totals = transactionDao.getAccountBalanceTotals();
        Map<String, Long> balancesByAccount = new HashMap<>();
        for (AccountBalanceTotal total : totals) {
            balancesByAccount.put(total.accountId, total.balanceCents);
        }

        for (BudgetAccount account : lookupDao.getActiveAccounts()) {
            long balance = balancesByAccount.getOrDefault(account.id, 0L);
            lookupDao.updateCurrentBalanceCents(account.id, balance);
        }
    }
}
