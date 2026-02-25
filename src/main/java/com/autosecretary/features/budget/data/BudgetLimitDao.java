package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BudgetLimitDao {

    @Query("SELECT * FROM budget_limit WHERE yearMonth = :yearMonth ORDER BY categoryId ASC")
    List<BudgetLimit> getLimitsForMonth(String yearMonth);

    @Query("SELECT * FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    BudgetLimit getLimitForCategoryAndMonth(String categoryId, String yearMonth);

    @Query("""
            SELECT c.id AS categoryId,
                   c.name AS categoryName,
                   COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS spent,
                   l.amount AS limitAmount
            FROM budget_category c
            LEFT JOIN budget_transaction t
                   ON t.categoryId = c.id
                  AND t.yearMonth = :yearMonth
            LEFT JOIN budget_limit l
                   ON l.categoryId = c.id
                  AND l.yearMonth = :yearMonth
            WHERE c.archived = 0
            GROUP BY c.id, c.name, l.amount
            ORDER BY spent DESC, c.name COLLATE NOCASE ASC
            """)
    List<CategorySpendTotal> getCategorySpendTotals(String yearMonth);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetLimit budgetLimit);

    @Update
    void update(BudgetLimit budgetLimit);

    @Delete
    void delete(BudgetLimit budgetLimit);

    @Query("DELETE FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth")
    void deleteByCategoryAndMonth(String categoryId, String yearMonth);
}
