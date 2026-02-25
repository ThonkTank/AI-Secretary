package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;

import java.util.List;

public interface BudgetRepository {
    BudgetAccount findAccountById(String accountId);
    List<BudgetAccount> findActiveAccounts();
    List<BudgetTransactionEntity> findAllTransactions();
    List<BudgetTransactionEntity> findTransactionsForAccount(String accountId);
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);
    void saveTransaction(BudgetTransactionEntity transaction);
    void deleteTransaction(String transactionId);
    void saveBudgetLimit(BudgetLimit budgetLimit);
}
