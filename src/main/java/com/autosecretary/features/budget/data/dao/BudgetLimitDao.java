package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

@Dao
public interface BudgetLimitDao {

    @Query("SELECT * FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    BudgetLimit getLimitForCategoryAndMonth(String categoryId, String yearMonth);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0)
            FROM budget_transaction
            WHERE categoryId = :categoryId
              AND yearMonth = :yearMonth
            """)
    long getExpenseCentsForCategoryAndMonth(String categoryId, String yearMonth);

    @Query("""
            SELECT c.id AS categoryId,
                   c.name AS categoryName,
                   c.icon AS categoryIcon,
                   c.colorHex AS categoryColorHex,
                   COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amountCents ELSE 0 END), 0) AS spentCents,
                   l.limitAmountCents AS limitAmountCents
            FROM budget_category c
            LEFT JOIN budget_transaction t
                   ON t.categoryId = c.id
                  AND t.yearMonth = :yearMonth
                  AND t.transactionKind != 'INTERNAL_TRANSFER'
            LEFT JOIN budget_limit l
                   ON l.categoryId = c.id
                  AND l.yearMonth = :yearMonth
            WHERE c.archived = 0
            GROUP BY c.id, c.name, c.icon, c.colorHex, l.limitAmountCents
            ORDER BY spentCents DESC, c.name COLLATE NOCASE ASC
            """)
    List<CategorySpendSummary> getCategorySpendTotals(String yearMonth);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetLimit budgetLimit);

}
