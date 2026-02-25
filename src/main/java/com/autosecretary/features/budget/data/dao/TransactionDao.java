package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.time.LocalDate;
import java.util.List;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.projection.AccountBalanceTotal;
import com.autosecretary.features.budget.data.projection.AccountDailyDeltaPoint;
import com.autosecretary.features.budget.data.projection.AccountMonthlyDeltaPoint;
import com.autosecretary.features.budget.data.projection.IncomeExpenseSummary;
import com.autosecretary.features.budget.data.projection.MonthlyTransactionOverviewItem;

@Dao
public interface TransactionDao {

    @Query("""
            SELECT t.id AS transactionId,
                   t.bookingDate AS bookingDate,
                   t.yearMonth AS yearMonth,
                   t.type AS type,
                   t.transactionKind AS transactionKind,
                   t.amountCents AS amountCents,
                   t.note AS note,
                   t.accountId AS accountId,
                   a.name AS accountName,
                   t.categoryId AS categoryId,
                   c.name AS categoryName,
                   c.icon AS categoryIcon,
                   c.colorHex AS categoryColorHex,
                   t.linkedTransactionId AS linkedTransactionId
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
                   t.transactionKind AS transactionKind,
                   t.amountCents AS amountCents,
                   t.note AS note,
                   t.accountId AS accountId,
                   a.name AS accountName,
                   t.categoryId AS categoryId,
                   c.name AS categoryName,
                   c.icon AS categoryIcon,
                   c.colorHex AS categoryColorHex,
                   c.icon AS categoryIcon,
                   c.colorHex AS categoryColorHex,
                   t.linkedTransactionId AS linkedTransactionId
            FROM budget_transaction t
            INNER JOIN budget_account a ON a.id = t.accountId
            LEFT JOIN budget_category c ON c.id = t.categoryId
            WHERE t.yearMonth = :yearMonth
              AND t.accountId = :accountId
            ORDER BY t.bookingDate DESC, t.id DESC
            """)
    List<MonthlyTransactionOverviewItem> getMonthlyOverviewForAccount(String yearMonth, String accountId);

    @Query("""
            SELECT t.id AS transactionId,
                   t.bookingDate AS bookingDate,
                   t.yearMonth AS yearMonth,
                   t.type AS type,
                   t.amountCents AS amountCents,
                   t.note AS note,
                   t.accountId AS accountId,
                   a.name AS accountName,
                   t.categoryId AS categoryId,
                   c.name AS categoryName,
                   c.icon AS categoryIcon,
                   c.colorHex AS categoryColorHex
            FROM budget_transaction t
            INNER JOIN budget_account a ON a.id = t.accountId
            LEFT JOIN budget_category c ON c.id = t.categoryId
            WHERE t.bookingDate BETWEEN :fromDate AND :toDate
            ORDER BY t.bookingDate DESC, t.id DESC
            """)
    List<MonthlyTransactionOverviewItem> getOverviewInDateRange(LocalDate fromDate, LocalDate toDate);

    @Query("""
            SELECT bookingDate AS bucketDate,
                   SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END) AS deltaCents
            FROM budget_transaction
            WHERE accountId = :accountId
              AND bookingDate BETWEEN :fromDate AND :toDate
            GROUP BY bookingDate
            ORDER BY bookingDate ASC
            """)
    List<AccountDailyDeltaPoint> getDailyDeltasForAccount(
            String accountId,
            LocalDate fromDate,
            LocalDate toDate
    );

    @Query("""
            SELECT yearMonth,
                   SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END) AS deltaCents
            FROM budget_transaction
            WHERE accountId = :accountId
              AND yearMonth BETWEEN :fromYearMonth AND :toYearMonth
            GROUP BY yearMonth
            ORDER BY yearMonth ASC
            """)
    List<AccountMonthlyDeltaPoint> getMonthlyDeltasForAccount(
            String accountId,
            String fromYearMonth,
            String toYearMonth
    );

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM budget_transaction
            WHERE accountId = :accountId
              AND bookingDate < :beforeDate
            """)
    long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' AND transactionKind != 'INTERNAL_TRANSFER' THEN amountCents ELSE 0 END), 0) AS sumIncomeCents,
                   COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND transactionKind != 'INTERNAL_TRANSFER' THEN amountCents ELSE 0 END), 0) AS sumExpenseCents
            FROM budget_transaction
            WHERE yearMonth = :yearMonth
            """)
    IncomeExpenseSummary getIncomeExpenseSummary(String yearMonth);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM budget_transaction
            """)
    long getNetBalanceCents();



    @Query("""
            SELECT accountId AS accountId,
                   COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0) AS balanceCents
            FROM budget_transaction
            GROUP BY accountId
            """)
    List<AccountBalanceTotal> getAccountBalanceTotals();

    @Query("SELECT * FROM budget_transaction ORDER BY bookingDate DESC")
    List<BudgetTransactionEntity> findAll();

    @Query("SELECT * FROM budget_transaction WHERE accountId = :accountId ORDER BY bookingDate DESC")
    List<BudgetTransactionEntity> findByAccountId(String accountId);

    @Query("SELECT * FROM budget_transaction WHERE id = :transactionId LIMIT 1")
    BudgetTransactionEntity findById(String transactionId);

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

    @Query("UPDATE budget_transaction SET linkedTransactionId = :linkedTransactionId WHERE id = :transactionId")
    void updateLinkedTransactionId(String transactionId, String linkedTransactionId);

    @Query("SELECT COUNT(*) > 0 FROM budget_transaction WHERE importHash = :importHash")
    boolean existsByImportHash(String importHash);

    @Query("SELECT id FROM budget_category WHERE type = :type AND archived = 0 LIMIT 1")
    String findDefaultCategoryId(String type);

    @Query("UPDATE budget_transaction SET templateId = :templateId WHERE id IN (:transactionIds)")
    void updateTemplateIdForTransactions(List<String> transactionIds, String templateId);

    @Transaction
    default void createTransferPair(BudgetTransactionEntity debit, BudgetTransactionEntity credit) {
        debit.transactionKind = BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER;
        debit.categoryId = null;
        debit.linkedTransactionId = credit.id;

        credit.transactionKind = BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER;
        credit.categoryId = null;
        credit.linkedTransactionId = debit.id;

        insert(debit);
        insert(credit);
        updateLinkedTransactionId(debit.id, credit.id);
        updateLinkedTransactionId(credit.id, debit.id);
    }

    @Transaction
    default boolean updateTransferPair(String transactionId,
                                       String sourceAccountId,
                                       String targetAccountId,
                                       long amountCents,
                                       LocalDate bookingDate,
                                       String yearMonth,
                                       String note) {
        BudgetTransactionEntity transaction = findById(transactionId);
        if (transaction == null || transaction.linkedTransactionId == null) {
            return false;
        }

        BudgetTransactionEntity linked = findById(transaction.linkedTransactionId);
        if (linked == null) {
            return false;
        }

        BudgetTransactionEntity debit = transaction.type == BudgetTransactionEntity.TransactionType.EXPENSE
                ? transaction : linked;
        BudgetTransactionEntity credit = transaction.type == BudgetTransactionEntity.TransactionType.INCOME
                ? transaction : linked;

        debit.accountId = sourceAccountId;
        debit.type = BudgetTransactionEntity.TransactionType.EXPENSE;
        debit.amountCents = amountCents;
        debit.bookingDate = bookingDate;
        debit.yearMonth = yearMonth;
        debit.note = note;
        debit.categoryId = null;
        debit.transactionKind = BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER;
        debit.linkedTransactionId = credit.id;

        credit.accountId = targetAccountId;
        credit.type = BudgetTransactionEntity.TransactionType.INCOME;
        credit.amountCents = amountCents;
        credit.bookingDate = bookingDate;
        credit.yearMonth = yearMonth;
        credit.note = note;
        credit.categoryId = null;
        credit.transactionKind = BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER;
        credit.linkedTransactionId = debit.id;

        update(debit);
        update(credit);
        return true;
    }

    @Transaction
    default void deleteWithLinked(String transactionId) {
        BudgetTransactionEntity transaction = findById(transactionId);
        if (transaction == null) {
            return;
        }

        String linkedId = transaction.linkedTransactionId;
        deleteById(transaction.id);
        if (linkedId != null) {
            deleteById(linkedId);
        }
    }
}
