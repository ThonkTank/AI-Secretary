package com.autosecretary.features.budget.data.legacy;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;

import java.util.List;

/**
 * Transitional legacy DAO for pre-v8 tables ({@code accounts}, {@code transactions}).
 *
 * <p>Canonical budget persistence uses {@code BudgetLookupDao}, {@code TransactionDao}, and
 * {@code BudgetLimitDao} over the {@code budget_*} schema. Do not use this DAO for new code.</p>
 */
@Deprecated
@Dao
public interface BudgetDao {
    @Query("SELECT * FROM budget_account WHERE id = :accountId LIMIT 1")
    BudgetAccount findAccountById(String accountId);

    @Query("SELECT * FROM budget_account WHERE archived = 0")
    List<BudgetAccount> findActiveAccounts();

    @Query("SELECT * FROM budget_transaction")
    List<BudgetTransactionEntity> findAllTransactions();

    @Query("SELECT * FROM budget_transaction WHERE accountId = :accountId")
    List<BudgetTransactionEntity> findTransactionsForAccount(String accountId);

    @Query("SELECT * FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);

    @Insert
    void insertTransaction(BudgetTransactionEntity transaction);

    @Update
    void updateTransaction(BudgetTransactionEntity transaction);

    @Delete
    void deleteTransaction(BudgetTransactionEntity transaction);

    @Insert
    void insertBudgetLimit(BudgetLimit budgetLimit);

    @Update
    void updateBudgetLimit(BudgetLimit budgetLimit);

    @Update
    void updateAccount(BudgetAccount account);
}
