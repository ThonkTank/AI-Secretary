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

/**
 * DAO for transaction data access, timeline calculations, and transfer pair operations.
 *
 * Responsibilities:
 * - Transaction CRUD: insert, update, delete, and query by ID or account
 * - Balance timeline: daily and monthly delta aggregations for balance charts
 * - Transfer pairs: create/update/delete linked transactions (debit/credit between accounts)
 *
 * All balance calculations use the sign convention:
 * - INCOME transactions add to balance (positive)
 * - EXPENSE transactions subtract from balance (negative)
 *
 * Transfer pairs: Inter-account transfers are stored as two linked transactions (debit and credit)
 * that share the same linkedTransactionId. Use createTransferPair(), updateTransferPair(), and
 * deleteWithLinked() to ensure both are kept in sync.
 *
 * @see BudgetRoomRepository for cross-DAO transaction coordination
 */
@Dao
public interface BudgetTransactionDao {

    /**
     * Retrieves all transactions for a given month (optionally filtered by account).
     *
     * Returns denormalized summary data: transaction fields joined with account and category metadata.
     *
     * @param yearMonth "YYYY-MM" format
     * @param accountId account UUID, or null for all accounts. If null, returns transactions from all accounts;
     *                  if provided, filters to that account only
     * @return list of MonthlyOverviewItem (ordered by booking date DESC, then ID DESC)
     */
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

    /**
     * Retrieves daily balance deltas for a given account over a date range.
     *
     * Groups transactions by booking date and sums the daily net change.
     * Sign convention: INCOME adds (positive), EXPENSE subtracts (negative).
     * Used to populate balance chart data with daily granularity.
     *
     * @param accountId account UUID
     * @param fromDate earliest date (inclusive)
     * @param toDate latest date (inclusive)
     * @return list of DailyDeltaPoint (ordered by date ASC)
     */
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

    /**
     * Retrieves monthly balance deltas for a given account over a date range.
     *
     * Groups transactions by year-month and sums the monthly net change.
     * Sign convention: INCOME adds (positive), EXPENSE subtracts (negative).
     * Used to populate balance chart data with monthly granularity.
     *
     * @param accountId account UUID
     * @param fromYearMonth earliest month ("YYYY-MM" format, inclusive)
     * @param toYearMonth latest month ("YYYY-MM" format, inclusive)
     * @return list of MonthlyDeltaPoint (ordered by month ASC)
     */
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

    /**
     * Calculates the net balance for an account before a given date.
     *
     * Sums all transactions (using sign convention) that have a bookingDate strictly before the given date.
     * Returns 0 if no transactions exist.
     *
     * @param accountId account UUID
     * @param beforeDate cutoff date (exclusive; transactions before this date are included)
     * @return net balance in cents
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM budget_transaction
            WHERE accountId = :accountId
              AND bookingDate < :beforeDate
            """)
    long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate);

    /**
     * Calculates the total net balance across all transactions (all accounts).
     *
     * Returns 0 if no transactions exist.
     *
     * @return net balance in cents (sum of all INCOME minus all EXPENSE)
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM budget_transaction
            """)
    long getNetBalanceCents();

    /**
     * Retrieves all transactions (all accounts), ordered by booking date descending.
     */
    @Query("SELECT * FROM budget_transaction ORDER BY bookingDate DESC")
    List<BudgetTransactionEntity> findAll();

    /**
     * Retrieves all transactions for a given account, ordered by booking date descending.
     *
     * @param accountId account UUID
     * @return list of transactions for the account (may be empty)
     */
    @Query("SELECT * FROM budget_transaction WHERE accountId = :accountId ORDER BY bookingDate DESC")
    List<BudgetTransactionEntity> findByAccountId(String accountId);

    /**
     * Retrieves a transaction by ID.
     *
     * @param transactionId transaction UUID
     * @return the transaction, or null if not found
     */
    @Query("SELECT * FROM budget_transaction WHERE id = :transactionId LIMIT 1")
    BudgetTransactionEntity findById(String transactionId);

    /**
     * Inserts or replaces a single transaction.
     *
     * If a transaction with the same ID already exists, it is updated.
     * For transfers, use createTransferPair() to ensure both sides are inserted atomically.
     *
     * @param transaction the transaction to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetTransactionEntity transaction);

    /**
     * Inserts or replaces multiple transactions in a single batch operation.
     *
     * Each transaction is replaced if one with the same ID already exists.
     * For atomic multi-transaction operations, consider using a @Transaction default method.
     *
     * @param transactions the transactions to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BudgetTransactionEntity> transactions);

    /**
     * Updates an existing transaction.
     *
     * @param transaction the transaction (ID must exist or update silently does nothing)
     */
    @Update
    void update(BudgetTransactionEntity transaction);

    /**
     * Deletes a transaction by ID.
     *
     * For transfers, use deleteWithLinked() to ensure both sides are deleted atomically.
     *
     * @param transactionId transaction UUID
     */
    @Query("DELETE FROM budget_transaction WHERE id = :transactionId")
    void deleteById(String transactionId);

    /**
     * Checks if a transaction with the given import hash already exists.
     *
     * Used during import to detect and skip duplicate transactions (idempotency check).
     *
     * @param importHash the import hash (derived from transaction data to detect duplicates)
     * @return true if a transaction with this hash exists
     */
    @Query("SELECT COUNT(*) > 0 FROM budget_transaction WHERE importHash = :importHash")
    boolean existsByImportHash(String importHash);

    /**
     * Associates one or more transactions with a recurring template.
     *
     * Used when the user confirms a recurring pattern: all matching transactions are linked
     * to the template ID for future reference and pattern refinement.
     *
     * @param transactionIds list of transaction UUIDs
     * @param templateId recurring template UUID
     */
    @Query("UPDATE budget_transaction SET templateId = :templateId WHERE id IN (:transactionIds)")
    void updateTemplateIdForTransactions(List<String> transactionIds, String templateId);

    /**
     * Atomically creates a linked pair of transactions for an inter-account transfer.
     *
     * Inter-account transfers are stored as two transactions:
     * - debit: outgoing transaction from source account (type = EXPENSE)
     * - credit: incoming transaction to destination account (type = INCOME)
     *
     * Both transactions must share the same linkedTransactionId and have the same amountCents.
     * If either insert fails, both are rolled back (all-or-nothing).
     *
     * @param debit the outgoing transaction from the source account
     * @param credit the incoming transaction to the destination account
     */
    @Transaction
    default void createTransferPair(BudgetTransactionEntity debit, BudgetTransactionEntity credit) {
        insert(debit);
        insert(credit);
    }

    /**
     * Atomically updates both sides of a transfer pair.
     *
     * Both transactions are updated in a single database transaction.
     * If either update fails, both are rolled back.
     *
     * @param debit the updated outgoing transaction
     * @param credit the updated incoming transaction
     */
    @Transaction
    default void updateTransferPair(BudgetTransactionEntity debit, BudgetTransactionEntity credit) {
        update(debit);
        update(credit);
    }

    /**
     * Atomically deletes a transaction and its linked partner (if present).
     *
     * If the transaction has a linkedTransactionId, both the transaction and its linked partner
     * are deleted in a single database transaction. If either deletion fails, both are rolled back.
     * Used for removing inter-account transfers as a unit.
     *
     * No-op if the transaction does not exist.
     *
     * @param transactionId the transaction to delete (along with its partner, if linked)
     */
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
