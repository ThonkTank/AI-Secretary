package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

/**
 * DAO for budget limits and category spending summaries.
 *
 * Tracks user-defined spending limits per category per month and calculates
 * current spending against those limits. Used by the UI to display budget bars
 * and alert users to overspending.
 */
@Dao
public interface BudgetLimitDao {

    /**
     * Retrieves the spending limit for a category in a given month.
     *
     * @param categoryId the category UUID
     * @param yearMonth "YYYY-MM" format
     * @return the limit record, or null if no limit is set for this category/month
     */
    @Query("SELECT * FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    BudgetLimit findLimitForCategoryAndMonth(String categoryId, String yearMonth);

    /**
     * Retrieves the total amount spent (EXPENSE only) in a category for a given month.
     *
     * Returns 0 if no transactions exist.
     *
     * @param categoryId the category UUID
     * @param yearMonth "YYYY-MM" format
     * @return total spent in cents
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0)
            FROM budget_transaction
            WHERE categoryId = :categoryId
              AND yearMonth = :yearMonth
            """)
    long getExpenseCentsForCategoryAndMonth(String categoryId, String yearMonth);

    /**
     * Retrieves spending summary for all active categories in a given month.
     *
     * Joins categories with transactions and limits to show:
     * - Current spending (sum of EXPENSE transactions, excluding internal transfers)
     * - User-defined limit (if set)
     * - Category metadata (name, icon, color)
     *
     * Categories with no transactions or limits are included. Results are sorted
     * by spending descending, then by category name case-insensitive.
     *
     * @param yearMonth "YYYY-MM" format
     * @return list of CategorySpendSummary records for all active categories
     */
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

    /**
     * Inserts or replaces a budget limit.
     *
     * If a limit for the same category/month already exists, it is updated.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetLimit budgetLimit);

}
