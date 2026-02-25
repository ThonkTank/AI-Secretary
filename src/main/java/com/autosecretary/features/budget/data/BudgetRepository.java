package com.autosecretary.features.budget.data;

import java.util.List;

public interface BudgetRepository {
    Account findAccountById(Long accountId);
    List<Account> findActiveAccounts();
    List<Transaction> findAllTransactions();
    List<Transaction> findTransactionsForAccount(Long accountId);
    BudgetLimit findBudgetLimit(Long categoryId, String yearMonth);
    void saveTransaction(Transaction transaction);
    void deleteTransaction(Long transactionId);
    void saveBudgetLimit(BudgetLimit budgetLimit);
    void saveAccount(Account account);
}
