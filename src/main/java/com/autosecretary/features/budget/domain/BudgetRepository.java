package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.CategorySpendTotal;
import com.autosecretary.features.budget.data.MonthlyTransactionOverviewItem;

import java.util.List;

public interface BudgetRepository {
    BudgetAccount findAccountById(String accountId);
    List<BudgetAccount> findActiveAccounts();
    List<BudgetCategory> getActiveCategories();
    List<BudgetTransactionEntity> findAllTransactions();
    List<BudgetTransactionEntity> findTransactionsForAccount(String accountId);
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);
    void saveTransaction(BudgetTransactionEntity transaction);
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
    List<CategorySpendTotal> getCategorySpendTotals(String yearMonth);
}
