package com.autosecretary.features.budget.data.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;

@Dao
public interface TransactionDao {

    @Query("""
            SELECT t.id AS transactionId,
                   t.bookingDate AS bookingDate,
                   t.yearMonth AS yearMonth,
                   t.type AS direction,
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
              AND (:accountId IS NULL OR t.accountId = :accountId)
            ORDER BY t.bookingDate DESC, t.id DESC
            """)
    List<MonthlyOverviewItem> getMonthlyOverview(String yearMonth, @Nullable String accountId);

    @Query("""
            SELECT bookingDate AS date,
                   SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END) AS deltaCents
            FROM budget_transaction
            WHERE accountId = :accountId
              AND bookingDate BETWEEN :fromDate AND :toDate
            GROUP BY bookingDate
            ORDER BY bookingDate ASC
            """)
    List<DailyDeltaPoint> getDailyDeltasForAccount(
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
    List<MonthlyDeltaPoint> getMonthlyDeltasForAccount(
            String accountId,
            YearMonth fromYearMonth,
            YearMonth toYearMonth
    );

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM budget_transaction
            WHERE accountId = :accountId
              AND bookingDate < :beforeDate
            """)
    long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM budget_transaction
            """)
    long getNetBalanceCents();

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

    @Query("DELETE FROM budget_transaction WHERE id = :transactionId")
    void deleteById(String transactionId);

    @Query("SELECT COUNT(*) > 0 FROM budget_transaction WHERE importHash = :importHash")
    boolean existsByImportHash(String importHash);

    @Query("UPDATE budget_transaction SET templateId = :templateId WHERE id IN (:transactionIds)")
    void updateTemplateIdForTransactions(List<String> transactionIds, String templateId);

    @Transaction
    default void createTransferPair(BudgetTransactionEntity debit, BudgetTransactionEntity credit) {
        insert(debit);
        insert(credit);
    }

    @Transaction
    default void updateTransferPair(BudgetTransactionEntity debit, BudgetTransactionEntity credit) {
        update(debit);
        update(credit);
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
