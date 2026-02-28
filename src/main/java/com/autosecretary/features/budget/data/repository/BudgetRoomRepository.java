package com.autosecretary.features.budget.data.repository;

import android.util.Log;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;
import com.autosecretary.features.budget.domain.TransferDetails;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import androidx.room.RoomDatabase;
import com.autosecretary.features.budget.data.dao.BudgetLimitDao;
import com.autosecretary.features.budget.data.dao.BudgetLookupDao;
import com.autosecretary.features.budget.data.dao.BudgetTransactionDao;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.dao.BudgetRecurringTemplateDao;
import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;

/**
 * Room-backed implementation of the BudgetRepository interface.
 *
 * Coordinates multiple DAOs (lookup, transaction, limit, recurring) to implement
 * business-level budget operations. While DAOs handle single-table queries,
 * repositories implement operations that span multiple tables or maintain
 * cross-table invariants (e.g., creating transfers that link two transactions).
 *
 * Injected via dependency injection; accessed by domain/application layer through
 * the BudgetRepository interface, never directly.
 */
public class BudgetRoomRepository implements BudgetRepository {
    /** DAO for account and category reads/writes. */
    private final BudgetLookupDao lookupDao;
    /** DAO for transaction CRUD and timeline queries. */
    private final BudgetTransactionDao transactionDao;
    /** DAO for budget limit and category spend queries. */
    private final BudgetLimitDao limitDao;
    /** DAO for recurring template queries and status updates. */
    private final BudgetRecurringTemplateDao recurringTemplateDao;
    /** Database for transactional operations across multiple DAOs. */
    private final RoomDatabase database;

    public BudgetRoomRepository(BudgetLookupDao lookupDao,
                                 BudgetTransactionDao transactionDao,
                                 BudgetLimitDao limitDao,
                                 BudgetRecurringTemplateDao recurringTemplateDao,
                                 RoomDatabase database) {
        this.lookupDao = lookupDao;
        this.transactionDao = transactionDao;
        this.limitDao = limitDao;
        this.recurringTemplateDao = recurringTemplateDao;
        this.database = database;
    }

    /**
     * Convention: When {@code accountId} is null or blank, operations aggregate
     * over all active accounts. This is used for "total balance" and "upcoming expenses"
     * queries. See {@link #isAllAccounts(String)}.
     */

    @Override public BudgetAccount findAccountById(String accountId) {
        return lookupDao.findAccountById(accountId);
    }

    @Override public List<BudgetAccount> findActiveAccounts() {
        return lookupDao.findActiveAccounts();
    }

    @Override public List<BudgetCategory> findActiveCategories() {
        return lookupDao.findActiveCategories();
    }

    @Override public List<BudgetTransactionEntity> findAllTransactions() {
        return transactionDao.findAll();
    }

    @Override public List<BudgetTransactionEntity> findTransactionsForAccount(String accountId) {
        return transactionDao.findByAccountId(accountId);
    }

    @Override public BudgetTransactionEntity findTransactionById(String transactionId) {
        return transactionDao.findById(transactionId);
    }

    @Override public BudgetLimit findBudgetLimit(String categoryId, String yearMonth) {
        return limitDao.findLimitForCategoryAndMonth(categoryId, yearMonth);
    }

    @Override public BudgetLimit findPreviousMonthLimit(String categoryId, String targetYearMonth) {
        return limitDao.findLimitForCategoryAndMonth(categoryId, previousYearMonth(targetYearMonth));
    }

    @Override public long getPreviousMonthExpenseCents(String categoryId, String targetYearMonth) {
        return limitDao.getExpenseCentsForCategoryAndMonth(categoryId, previousYearMonth(targetYearMonth));
    }

    @Override public long getCategoryExpenseCents(String categoryId, String yearMonth) {
        return limitDao.getExpenseCentsForCategoryAndMonth(categoryId, yearMonth);
    }

    @Override public long getCurrentBalanceCents(String accountId) {
        if (isAllAccounts(accountId)) {
            return lookupDao.sumCurrentBalanceCentsForActiveAccounts();
        }
        Long value = lookupDao.findCurrentBalanceCentsByAccountId(accountId);
        return value != null ? value : 0L;
    }

    @Override public long getUpcomingExpenseTemplateCents(String accountId, LocalDate fromDate, LocalDate toDate) {
        List<BudgetRecurringTemplateEntity> templates = isAllAccounts(accountId)
                ? recurringTemplateDao.findActiveExpenseTemplatesForActiveAccountsInRange(fromDate, toDate)
                : recurringTemplateDao.findActiveExpenseTemplatesForAccountInRange(accountId, fromDate, toDate);

        return templates.stream().mapToLong(t -> t.avgAmountCents).sum();
    }

    @Override public void saveTransaction(BudgetTransactionEntity transaction) {
        transactionDao.insert(transaction);
    }

    /**
     * Inserts a transaction and optionally deducts its amount from the account balance,
     * in a single atomic database operation.
     *
     * Balance is deducted only when {@code accountId} is non-null/non-blank AND
     * {@code expenseCents > 0}. If either condition is false (e.g., "all accounts" context
     * or a zero-amount booking), the transaction is still persisted but no balance change is made.
     *
     * @param transaction the transaction to insert
     * @param accountId account UUID to deduct from; null or blank means no balance adjustment
     * @param expenseCents absolute amount to deduct (positive value); 0 means no adjustment
     */
    @Override public void saveTransactionAndDeductBalance(BudgetTransactionEntity transaction,
                                                          String accountId,
                                                          long expenseCents) {
        database.runInTransaction(() -> {
            transactionDao.insert(transaction);
            if (!isAllAccounts(accountId) && expenseCents > 0) {
                lookupDao.adjustCurrentBalanceCents(accountId, -expenseCents);
            }
        });
    }

    @Override public String findDefaultActiveAccountId() {
        return lookupDao.findFirstActiveAccountId();
    }

    @Override public void saveTransaction(String accountId, String categoryId, TransactionDirection direction,
                                          long amountCents, LocalDate bookingDate, String note) {
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId, categoryId, direction, amountCents, bookingDate);
        entity.note = normalizeNote(note);
        transactionDao.insert(entity);
    }

    /**
     * Creates an EXPENSE transaction for the given account and deducts its amount from
     * the account balance in a single atomic database operation.
     *
     * Convenience wrapper over {@link #saveTransactionAndDeductBalance} that always uses
     * {@link TransactionDirection#EXPENSE} and passes {@code amountCents} as the deduction.
     *
     * @param accountId account UUID (must not be null or blank; null yields no balance change)
     * @param categoryId category UUID (may be null)
     * @param amountCents expense amount in cents (positive)
     * @param bookingDate booking date for the transaction
     * @param note optional user note (null or blank is normalized to null)
     */
    @Override public void bookExpenseAndDeductBalance(String accountId, String categoryId,
                                                      long amountCents, LocalDate bookingDate,
                                                      String note) {
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId, categoryId, TransactionDirection.EXPENSE, amountCents, bookingDate);
        entity.note = normalizeNote(note);
        saveTransactionAndDeductBalance(entity, accountId, amountCents);
    }

    @Override public void updateTransaction(BudgetTransactionEntity transaction) {
        transactionDao.update(transaction);
    }

    /**
     * Updates an existing transaction by replacing all its editable fields atomically.
     *
     * Silent no-op if the transaction is not found — acceptable because this overload may
     * be called speculatively or during UI cleanup when the transaction may no longer exist.
     *
     * @param transactionId UUID of the transaction to update
     * @param accountId new account UUID
     * @param categoryId new category UUID (may be null)
     * @param direction new direction (INCOME or EXPENSE)
     * @param amountCents new amount in cents
     * @param bookingDate new booking date (also updates yearMonth)
     * @param note new user note (null or blank is normalized to null)
     */
    @Override public void updateTransaction(String transactionId, String accountId, String categoryId,
                                            TransactionDirection direction, long amountCents,
                                            LocalDate bookingDate, String note) {
        BudgetTransactionEntity entity = transactionDao.findById(transactionId);
        // Silent no-op if transaction not found. Acceptable because this method may be called
        // speculatively or during UI cleanup when the transaction may no longer exist.
        // Log at DEBUG level for troubleshooting without failing the operation.
        if (entity == null) {
            Log.d("BudgetRoomRepository", "updateTransaction: transaction not found for id=" + transactionId);
            return;
        }
        entity.accountId = accountId;
        entity.categoryId = categoryId;
        entity.direction = direction;
        entity.amountCents = amountCents;
        entity.setBookingDate(bookingDate);
        entity.note = normalizeNote(note);
        transactionDao.update(entity);
    }

    @Override public void deleteTransaction(String transactionId) {
        transactionDao.deleteWithLinked(transactionId);
    }

    /**
     * Creates a transfer between two accounts by recording two linked transactions.
     *
     * Design: Transfers are represented as a debit on the source account (EXPENSE)
     * and a credit on the target account (INCOME), linked via {@code linkedTransactionId}.
     * This design maintains the invariant that every transaction is attributed to an
     * account, preventing double-counting in balance queries.
     *
     * Both transactions have {@code transactionKind = INTERNAL_TRANSFER} and null
     * {@code categoryId} (transfers don't belong to categories).
     *
     * @param details encapsulates source account, target account, amount, date, and note
     */
    @Override
    public void createTransfer(TransferDetails details) {
        BudgetTransactionEntity debit = new BudgetTransactionEntity(
                details.sourceAccountId(), null, TransactionDirection.EXPENSE, details.amountCents(), details.bookingDate());
        BudgetTransactionEntity credit = new BudgetTransactionEntity(
                details.targetAccountId(), null, TransactionDirection.INCOME, details.amountCents(), details.bookingDate());
        populateTransferPair(debit, credit, details);
        transactionDao.createTransferPair(debit, credit);
    }

    /**
     * Updates an existing transfer between two accounts.
     *
     * Returns false if the transaction is not found or is not a properly linked transfer pair.
     * Otherwise, updates both legs of the transfer and returns true.
     *
     * @param transactionId ID of one leg of the transfer pair
     * @param details encapsulates source account, target account, amount, date, and note
     */
    @Override
    public boolean updateTransfer(String transactionId, TransferDetails details) {
        Optional<TransferValidation> validation = validateTransferExists(transactionId);
        if (validation.isEmpty()) {
            Log.d("BudgetRoomRepository", "updateTransfer: transfer validation failed for id=" + transactionId);
            return false;
        }

        TransferValidation v = validation.get();
        populateTransferPair(v.debit, v.credit, details);
        transactionDao.updateTransferPair(v.debit, v.credit);
        return true;
    }

    /**
     * Validates that a transaction exists and is a properly linked transfer pair.
     *
     * Returns an Optional containing the debit and credit legs if valid, or empty if:
     * - The transaction doesn't exist
     * - The transaction has no linked pair (not a transfer)
     * - The linked transaction is missing (data corruption)
     */
    private Optional<TransferValidation> validateTransferExists(String transactionId) {
        BudgetTransactionEntity transaction = transactionDao.findById(transactionId);
        if (transaction == null || transaction.linkedTransactionId == null) {
            return Optional.empty();
        }

        BudgetTransactionEntity linked = transactionDao.findById(transaction.linkedTransactionId);
        if (linked == null) {
            return Optional.empty();
        }

        BudgetTransactionEntity debit, credit;
        if (transaction.direction == TransactionDirection.EXPENSE) {
            debit = transaction;
            credit = linked;
        } else {
            debit = linked;
            credit = transaction;
        }

        return Optional.of(new TransferValidation(debit, credit));
    }

    /**
     * Represents the validated pair of debit and credit legs in a transfer.
     * Debit is the EXPENSE side (source account), credit is the INCOME side (target account).
     */
    private record TransferValidation(BudgetTransactionEntity debit, BudgetTransactionEntity credit) {}

    @Override public void saveBudgetLimit(BudgetLimit budgetLimit) {
        limitDao.insert(budgetLimit);
    }

    @Override public void saveTransactions(List<BudgetTransactionEntity> transactions) {
        transactionDao.insertAll(transactions);
    }

    @Override public void insertAccount(BudgetAccount account) {
        lookupDao.insertAccount(account);
    }

    @Override public void insertCategory(BudgetCategory category) {
        lookupDao.insertCategory(category);
    }

    @Override public List<MonthlyOverviewItem> getMonthlyOverview(String yearMonth) {
        return transactionDao.getMonthlyOverview(yearMonth, null); // null = all accounts
    }

    @Override public List<MonthlyOverviewItem> getMonthlyOverviewForAccount(String yearMonth, String accountId) {
        return transactionDao.getMonthlyOverview(yearMonth, accountId);
    }

    @Override public List<CategorySpendSummary> getCategorySpendTotals(String yearMonth) {
        return limitDao.getCategorySpendTotals(yearMonth);
    }

    @Override public List<DailyDeltaPoint> getDailyDeltasForAccount(
            String accountId, LocalDate fromDate, LocalDate toDate) {
        return transactionDao.getDailyDeltasForAccount(accountId, fromDate, toDate);
    }

    @Override public List<MonthlyDeltaPoint> getMonthlyDeltasForAccount(
            String accountId, YearMonth fromYearMonth, YearMonth toYearMonth) {
        return transactionDao.getMonthlyDeltasForAccount(accountId, fromYearMonth, toYearMonth);
    }

    @Override public long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate) {
        return transactionDao.getNetAmountBeforeDateForAccount(accountId, beforeDate);
    }

    @Override public long getNetBalanceCents() {
        return transactionDao.getNetBalanceCents();
    }

    /** Returns true when {@code accountId} is null or blank, meaning "aggregate over all accounts". */
    private static boolean isAllAccounts(String accountId) {
        return accountId == null || accountId.isBlank();
    }

    /** Returns the ISO-8601 year-month string for the month preceding {@code targetYearMonth}. */
    private static String previousYearMonth(String targetYearMonth) {
        return YearMonth.parse(targetYearMonth).minusMonths(1).toString();
    }

    /** Trims whitespace from {@code note} and returns {@code null} if the result is blank. */
    private static String normalizeNote(String note) {
        if (note == null) return null;
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Populates both legs of a transfer pair atomically.
     *
     * Sets all transfer-related fields on both the debit (EXPENSE from source) and
     * credit (INCOME to target) legs, linking them via their IDs.
     *
     * @param debit the transaction leg being debited (source account, EXPENSE direction)
     * @param credit the transaction leg being credited (target account, INCOME direction)
     * @param details encapsulates source account, target account, amount, date, and note
     */
    private static void populateTransferPair(BudgetTransactionEntity debit,
                                              BudgetTransactionEntity credit,
                                              TransferDetails details) {
        String normalizedNote = normalizeNote(details.note());

        debit.accountId = details.sourceAccountId();
        debit.direction = TransactionDirection.EXPENSE;
        debit.amountCents = details.amountCents();
        debit.setBookingDate(details.bookingDate());
        debit.transactionKind = TransactionKind.INTERNAL_TRANSFER;
        debit.categoryId = null;
        debit.note = normalizedNote;
        debit.linkedTransactionId = credit.id;

        credit.accountId = details.targetAccountId();
        credit.direction = TransactionDirection.INCOME;
        credit.amountCents = details.amountCents();
        credit.setBookingDate(details.bookingDate());
        credit.transactionKind = TransactionKind.INTERNAL_TRANSFER;
        credit.categoryId = null;
        credit.note = normalizedNote;
        credit.linkedTransactionId = debit.id;
    }
}
