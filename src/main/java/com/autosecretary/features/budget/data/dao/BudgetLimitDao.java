package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

@Dao
public interface BudgetLimitDao {

    @Query("SELECT * FROM budget_limit WHERE yearMonth = :yearMonth ORDER BY categoryId ASC")
    List<BudgetLimit> getLimitsForMonth(String yearMonth);

    @Query("SELECT * FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    BudgetLimit getLimitForCategoryAndMonth(String categoryId, String yearMonth);

    @Query("""
            SELECT * FROM budget_limit
            WHERE categoryId = :categoryId
              AND yearMonth = strftime('%Y-%m', date(:targetYearMonth || '-01', '-1 month'))
            LIMIT 1
            """)
    BudgetLimit getPreviousMonthLimit(String categoryId, String targetYearMonth);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0)
            FROM budget_transaction
            WHERE categoryId = :categoryId
              AND yearMonth = strftime('%Y-%m', date(:targetYearMonth || '-01', '-1 month'))
            """)
    long getPreviousMonthExpenseCents(String categoryId, String targetYearMonth);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0)
            FROM budget_transaction
            WHERE categoryId = :categoryId
              AND yearMonth = :yearMonth
            """)
    long getExpenseCentsForCategoryAndMonth(String categoryId, String yearMonth);

    @Query("""
            SELECT CASE
                     WHEN target.rolloverEnabled = 0 THEN target.limitAmountCents
                     ELSE MAX(
                            0,
                            target.limitAmountCents
                            + target.rolloverCarryoverCents
                            + COALESCE(prev.limitAmountCents, 0)
                            - COALESCE(prevSpent.spentCents, 0)
                          )
                   END AS effectiveLimitCents
            FROM budget_limit target
            LEFT JOIN budget_limit prev
                   ON prev.categoryId = target.categoryId
                  AND prev.yearMonth = strftime('%Y-%m', date(:targetYearMonth || '-01', '-1 month'))
            LEFT JOIN (
                SELECT categoryId,
                       SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END) AS spentCents
                FROM budget_transaction
                WHERE yearMonth = strftime('%Y-%m', date(:targetYearMonth || '-01', '-1 month'))
                GROUP BY categoryId
            ) prevSpent ON prevSpent.categoryId = target.categoryId
            WHERE target.categoryId = :categoryId
              AND target.yearMonth = :targetYearMonth
            LIMIT 1
            """)
    Long getEffectiveLimitCentsForMonth(String categoryId, String targetYearMonth);

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

    @Update
    void update(BudgetLimit budgetLimit);

    @Delete
    void delete(BudgetLimit budgetLimit);

    @Query("DELETE FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth")
    void deleteByCategoryAndMonth(String categoryId, String yearMonth);
}
