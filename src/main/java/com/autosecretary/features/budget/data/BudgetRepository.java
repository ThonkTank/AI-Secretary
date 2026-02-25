package com.autosecretary.features.budget.data;

import java.util.List;

public interface BudgetRepository {
    BudgetAccount findAccountById(String accountId);
    List<BudgetAccount> findActiveAccounts();
    List<BudgetTransaction> findAllTransactions();
    List<BudgetTransaction> findTransactionsForAccount(String accountId);
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);
    void saveTransaction(BudgetTransaction transaction);
    void deleteTransaction(String transactionId);
    void saveBudgetLimit(BudgetLimit budgetLimit);
}
