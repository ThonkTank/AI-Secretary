package com.autosecretary.features.budget.domain;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Primary data contract for the budget feature.
 * <p>
 * Covers five related areas:
 * <ol>
 *   <li><b>Accounts</b> — {@code findAccountById}, {@code findActiveAccounts}, {@code insertAccount}</li>
 *   <li><b>Categories</b> — {@code findActiveCategories}, {@code insertCategory}</li>
 *   <li><b>Transactions</b> — find / save / update / delete, including transfer pairs and batch insert</li>
 *   <li><b>Budget limits</b> — per-category monthly limits ({@code findBudgetLimit}, {@code saveBudgetLimit})
 *       and spend summaries ({@code getCategoryExpenseCents}, {@code getMonthlyOverview})</li>
 *   <li><b>Balance &amp; timeline</b> — current balance, net balance, daily/monthly deltas used for the
 *       balance chart ({@code getDailyDeltasForAccount}, {@code getMonthlyDeltasForAccount})</li>
 * </ol>
 * <p>
 * <b>yearMonth parameters:</b> All methods that accept a {@code yearMonth} string expect the format
 * {@code "YYYY-MM"} (e.g., {@code "2024-01"} for January 2024). Use
 * {@code YearMonth.now().toString()} or {@code yearMonth.toString()} to produce a compatible value.
 * <p>
 * The concrete Room implementation lives in {@code budget/data/repository/BudgetRoomRepository}.
 * All calls are expected to run on a background thread (see {@code AppCompositionRoot} for threading setup).
 */
public interface BudgetRepository {
    BudgetAccount findAccountById(String accountId);
    List<BudgetAccount> findActiveAccounts();

    /** Raw pass-through write for a fully-constructed Room entity. */
    void insertAccount(BudgetAccount account);

    List<BudgetCategory> findActiveCategories();

    /** Raw pass-through write for a fully-constructed Room entity. */
    void insertCategory(BudgetCategory category);
    List<BudgetTransaction> findAllTransactions();
    List<BudgetTransaction> findTransactionsForAccount(String accountId);
    BudgetTransaction findTransactionById(String transactionId);
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);
    BudgetLimit findPreviousMonthLimit(String categoryId, String targetYearMonth);
    long getPreviousMonthExpenseCents(String categoryId, String targetYearMonth);
    long getCategoryExpenseCents(String categoryId, String yearMonth);

    /**
     * Returns the current balance for {@code accountId}. Pass {@code null} or blank to
     * aggregate across all active accounts.
     */
    long getCurrentBalanceCents(String accountId);

    /**
     * Sums the expected expense amounts for all active recurring templates on {@code accountId}
     * whose next-due date falls within [{@code fromDate}, {@code toDate}]. Used to show
     * upcoming committed expenses in the budget overview. Pass {@code null} or blank to
     * aggregate across all active accounts.
     */
    long getUpcomingExpenseTemplateCents(String accountId, LocalDate fromDate, LocalDate toDate);

    /** Returns the ID of the first active account, or {@code null} if no accounts exist. */
    String findDefaultActiveAccountId();

    /** Persists an already-constructed transaction entity (e.g. during CSV import). */
    void saveTransaction(BudgetTransaction transaction);

    /**
     * Atomically inserts {@code transaction} and deducts {@code expenseCents} from the
     * stored balance of {@code accountId} in a single database transaction.
     */
    void saveTransactionAndDeductBalance(BudgetTransaction transaction,
                                         String accountId,
                                         long expenseCents);

    /**
     * Convenience variant of {@link #saveTransactionAndDeductBalance} that builds the
     * {@code EXPENSE} transaction entity internally. Use this to avoid leaking
     * {@code BudgetTransactionEntity} construction into callers outside the budget feature.
     */
    void bookExpenseAndDeductBalance(String accountId, String categoryId,
                                     long amountCents, LocalDate bookingDate, String note);

    /**
     * Groups the editable fields of a transaction for use with the convenience overloads of
     * {@link #saveTransaction(TransactionCreateDetails)} and
     * {@link #updateTransaction(String, TransactionCreateDetails)}.
     */
    record TransactionCreateDetails(
            String accountId,
            String categoryId,
            TransactionDirection direction,
            long amountCents,
            LocalDate bookingDate,
            String note
    ) {}

    /** Convenience overload used by the "Add Transaction" dialog — builds the entity internally. */
    void saveTransaction(TransactionCreateDetails details);

    void updateTransaction(BudgetTransaction transaction);
    void updateTransaction(String transactionId, TransactionCreateDetails details);

    /**
     * Batch-inserts pre-built transaction entities without updating account balances.
     * Callers are responsible for ensuring balance consistency after this call (e.g., by
     * invoking a balance rebuild). Intended for bulk seed operations where a single rebuild
     * is more efficient than per-row adjustments.
     * For user-initiated transaction creation, use {@link #saveTransaction(TransactionCreateDetails)}.
     */
    void saveTransactions(List<BudgetTransaction> transactions);
    void deleteTransaction(String transactionId);

    /**
     * Inserts a transfer as two linked transactions: a debit on {@code sourceAccountId} and a
     * credit on {@code targetAccountId}. Both rows store each other's ID in their
     * {@code linkedTransactionId} column so the pair can be updated/deleted atomically.
     *
     * @param details encapsulates source account, target account, amount, date, and note
     */
    void createTransfer(TransferDetails details);

    /**
     * Updates both legs of an existing transfer identified by {@code transactionId} (either
     * leg's ID may be passed). Returns {@code true} if the transfer was found and updated,
     * {@code false} if the transaction or its linked counterpart could not be located.
     *
     * @param transactionId ID of one leg of the transfer pair
     * @param details encapsulates source account, target account, amount, date, and note
     */
    boolean updateTransfer(String transactionId, TransferDetails details);

    void saveBudgetLimit(BudgetLimit budgetLimit);
    List<MonthlyOverviewItem> getMonthlyOverview(String yearMonth);
    List<MonthlyOverviewItem> getMonthlyOverviewForAccount(String yearMonth, String accountId);
    List<CategorySpendSummary> getCategorySpendTotals(String yearMonth);
    List<DailyDeltaPoint> getDailyDeltasForAccount(String accountId, LocalDate fromDate, LocalDate toDate);
    List<MonthlyDeltaPoint> getMonthlyDeltasForAccount(String accountId, YearMonth fromYearMonth, YearMonth toYearMonth);

    /**
     * Returns the cumulative net balance (income minus expenses) for {@code accountId} for all
     * transactions booked strictly before {@code beforeDate}. Used as the {@code startBalanceCents}
     * opening value when reconstructing a balance timeline via
     * {@link com.autosecretary.features.budget.domain.timeline.AccountBalanceTimelineService}.
     */
    long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate);

    /**
     * Returns the total net balance (income minus expenses) computed from all transaction records,
     * across all accounts. Unlike {@link #getCurrentBalanceCents} this always recomputes from
     * the transaction table, so it remains accurate even when stored account balances are stale.
     */
    long getNetBalanceCents();
}
