package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.projection.AccountDailyDeltaPoint;
import com.autosecretary.features.budget.data.projection.AccountMonthlyDeltaPoint;
import com.autosecretary.features.budget.data.projection.CategorySpendTotal;
import com.autosecretary.features.budget.data.projection.MonthlyTransactionOverviewItem;

import java.time.LocalDate;
import java.util.List;

public interface BudgetRepository {
    BudgetAccount findAccountById(String accountId);
    List<BudgetAccount> findActiveAccounts();
    List<BudgetCategory> getActiveCategories();
    List<BudgetTransactionEntity> findAllTransactions();
    List<BudgetTransactionEntity> findTransactionsForAccount(String accountId);
    BudgetTransactionEntity findTransactionById(String transactionId);
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);
    BudgetLimit findPreviousMonthLimit(String categoryId, String targetYearMonth);
    long getPreviousMonthExpenseCents(String categoryId, String targetYearMonth);
    long getCategoryExpenseCents(String categoryId, String yearMonth);
    Long getEffectiveLimitCents(String categoryId, String targetYearMonth);
    long getCurrentBalanceCents(String accountId);
    long getUpcomingExpenseTemplateCents(String accountId, LocalDate fromDate, LocalDate toDate);
    void saveTransaction(BudgetTransactionEntity transaction);
    void updateTransaction(BudgetTransactionEntity transaction);
    void saveTransactions(List<BudgetTransactionEntity> transactions);
    void deleteTransaction(String transactionId);
    void createTransfer(String sourceAccountId, String targetAccountId, long amountCents,
                        java.time.LocalDate bookingDate, String note);
    boolean updateTransfer(String transactionId, String sourceAccountId, String targetAccountId,
                           long amountCents, java.time.LocalDate bookingDate, String note);
    void saveBudgetLimit(BudgetLimit budgetLimit);
    void insertAccount(BudgetAccount account);
    void insertCategory(BudgetCategory category);
    List<MonthlyTransactionOverviewItem> getMonthlyOverview(String yearMonth);
    List<MonthlyTransactionOverviewItem> getMonthlyOverviewForAccount(String yearMonth, String accountId);
    List<CategorySpendTotal> getCategorySpendTotals(String yearMonth);
    List<AccountDailyDeltaPoint> getDailyDeltasForAccount(String accountId, LocalDate fromDate, LocalDate toDate);
    List<AccountMonthlyDeltaPoint> getMonthlyDeltasForAccount(String accountId, String fromYearMonth, String toYearMonth);
    long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate);
}
