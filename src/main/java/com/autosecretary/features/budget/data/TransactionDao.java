package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface TransactionDao {

    @Query("""
            SELECT t.id AS transactionId,
                   t.bookingDate AS bookingDate,
                   t.yearMonth AS yearMonth,
                   t.type AS type,
                   t.amount AS amount,
                   t.note AS note,
                   t.accountId AS accountId,
                   a.name AS accountName,
                   t.categoryId AS categoryId,
                   c.name AS categoryName
            FROM budget_transaction t
            INNER JOIN budget_account a ON a.id = t.accountId
            LEFT JOIN budget_category c ON c.id = t.categoryId
            WHERE t.yearMonth = :yearMonth
            ORDER BY t.bookingDate DESC, t.id DESC
            """)
    List<MonthlyTransactionOverviewItem> getMonthlyOverview(String yearMonth);

    @Query("""
            SELECT t.id AS transactionId,
                   t.bookingDate AS bookingDate,
                   t.yearMonth AS yearMonth,
                   t.type AS type,
                   t.amount AS amount,
                   t.note AS note,
                   t.accountId AS accountId,
                   a.name AS accountName,
                   t.categoryId AS categoryId,
                   c.name AS categoryName
            FROM budget_transaction t
            INNER JOIN budget_account a ON a.id = t.accountId
            LEFT JOIN budget_category c ON c.id = t.categoryId
            WHERE t.bookingDate BETWEEN :fromDate AND :toDate
            ORDER BY t.bookingDate DESC, t.id DESC
            """)
    List<MonthlyTransactionOverviewItem> getOverviewInDateRange(LocalDate fromDate, LocalDate toDate);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS sumIncome,
                   COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS sumExpense
            FROM budget_transaction
            WHERE yearMonth = :yearMonth
            """)
    IncomeExpenseSummary getIncomeExpenseSummary(String yearMonth);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetTransactionEntity transaction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BudgetTransactionEntity> transactions);

    @Update
    void update(BudgetTransactionEntity transaction);

    @Delete
    void delete(BudgetTransactionEntity transaction);

    @Query("DELETE FROM budget_transaction WHERE id = :transactionId")
    void deleteById(String transactionId);
}
