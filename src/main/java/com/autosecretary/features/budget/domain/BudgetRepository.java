package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;

import java.time.LocalDate;
import java.util.List;

public interface BudgetRepository {
    BudgetAccount findAccountById(String accountId);
    List<BudgetAccount> findActiveAccounts();
    void insertAccount(BudgetAccount account);
    List<BudgetCategory> getActiveCategories();
    void insertCategory(BudgetCategory category);
    List<BudgetTransactionEntity> findAllTransactions();
    List<BudgetTransactionEntity> findTransactionsForAccount(String accountId);
    BudgetTransactionEntity findTransactionById(String transactionId);
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);
    BudgetLimit findPreviousMonthLimit(String categoryId, String targetYearMonth);
    long getPreviousMonthExpenseCents(String categoryId, String targetYearMonth);
    long getCategoryExpenseCents(String categoryId, String yearMonth);
    /**
     * Returns the user-set budget limit for the given category and month. Falls back to the
     * previous month's limit if no limit is configured for {@code targetYearMonth}. Returns
     * {@code null} if no limit has ever been set for this category.
     */
    Long getEffectiveLimitCents(String categoryId, String targetYearMonth);

    long getCurrentBalanceCents(String accountId);

    /**
     * Sums the expected expense amounts for all active recurring templates on {@code accountId}
     * whose next-due date falls within [{@code fromDate}, {@code toDate}]. Used to show
     * upcoming committed expenses in the budget overview.
     */
    long getUpcomingExpenseTemplateCents(String accountId, LocalDate fromDate, LocalDate toDate);

    /** Persists an already-constructed transaction entity (e.g. during CSV import). */
    void saveTransaction(BudgetTransactionEntity transaction);

    /** Convenience overload used by the "Add Transaction" dialog — builds the entity internally. */
    void saveTransaction(String accountId, String categoryId, boolean isExpense,
                         long amountCents, LocalDate bookingDate, String note);

    void updateTransaction(BudgetTransactionEntity transaction);
    void updateTransaction(String transactionId, String accountId, String categoryId,
                           boolean isExpense, long amountCents, LocalDate bookingDate, String note);
    void saveTransactions(List<BudgetTransactionEntity> transactions);
    void deleteTransaction(String transactionId);

    /**
     * Inserts a transfer as two linked transactions: a debit on {@code sourceAccountId} and a
     * credit on {@code targetAccountId}. Both rows store each other's ID in their
     * {@code linkedTransactionId} column so the pair can be updated/deleted atomically.
     */
    void createTransfer(String sourceAccountId, String targetAccountId, long amountCents,
                        java.time.LocalDate bookingDate, String note);

    /**
     * Updates both legs of an existing transfer identified by {@code transactionId} (either
     * leg's ID may be passed). Returns {@code true} if the transfer was found and updated,
     * {@code false} if the transaction or its linked counterpart could not be located.
     */
    boolean updateTransfer(String transactionId, String sourceAccountId, String targetAccountId,
                           long amountCents, java.time.LocalDate bookingDate, String note);

    void saveBudgetLimit(BudgetLimit budgetLimit);
    List<MonthlyOverviewItem> getMonthlyOverview(String yearMonth);
    List<MonthlyOverviewItem> getMonthlyOverviewForAccount(String yearMonth, String accountId);
    List<CategorySpendSummary> getCategorySpendTotals(String yearMonth);
    List<DailyDeltaPoint> getDailyDeltasForAccount(String accountId, LocalDate fromDate, LocalDate toDate);
    List<MonthlyDeltaPoint> getMonthlyDeltasForAccount(String accountId, String fromYearMonth, String toYearMonth);

    /**
     * Returns the cumulative net balance (income minus expenses) for {@code accountId} for all
     * transactions booked strictly before {@code beforeDate}. Used as the {@code startBalanceCents}
     * opening value when reconstructing a balance timeline via
     * {@link com.autosecretary.features.budget.domain.timeline.AccountBalanceTimelineService}.
     */
    long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate);
}
