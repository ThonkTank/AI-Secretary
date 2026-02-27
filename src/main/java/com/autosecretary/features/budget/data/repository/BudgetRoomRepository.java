package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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

public class BudgetRoomRepository implements BudgetRepository {
    private final BudgetLookupDao lookupDao;
    private final BudgetTransactionDao transactionDao;
    private final BudgetLimitDao limitDao;
    private final BudgetRecurringTemplateDao recurringTemplateDao;
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

    @Override public void updateTransaction(String transactionId, String accountId, String categoryId,
                                            TransactionDirection direction, long amountCents,
                                            LocalDate bookingDate, String note) {
        BudgetTransactionEntity entity = transactionDao.findById(transactionId);
        if (entity == null) return;
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

    @Override
    public void createTransfer(String sourceAccountId,
                               String targetAccountId,
                               long amountCents,
                               LocalDate bookingDate,
                               String note) {
        BudgetTransactionEntity debit = new BudgetTransactionEntity(
                sourceAccountId, null, TransactionDirection.EXPENSE, amountCents, bookingDate);
        BudgetTransactionEntity credit = new BudgetTransactionEntity(
                targetAccountId, null, TransactionDirection.INCOME, amountCents, bookingDate);
        populateTransferLeg(debit, sourceAccountId, TransactionDirection.EXPENSE,
                amountCents, bookingDate, credit.id, note);
        populateTransferLeg(credit, targetAccountId, TransactionDirection.INCOME,
                amountCents, bookingDate, debit.id, note);
        transactionDao.createTransferPair(debit, credit);
    }

    @Override
    public boolean updateTransfer(String transactionId,
                                  String sourceAccountId,
                                  String targetAccountId,
                                  long amountCents,
                                  LocalDate bookingDate,
                                  String note) {
        BudgetTransactionEntity transaction = transactionDao.findById(transactionId);
        if (transaction == null || transaction.linkedTransactionId == null) {
            return false;
        }

        BudgetTransactionEntity linked = transactionDao.findById(transaction.linkedTransactionId);
        if (linked == null) {
            return false;
        }

        BudgetTransactionEntity debit, credit;
        if (transaction.direction == TransactionDirection.EXPENSE) {
            debit = transaction;
            credit = linked;
        } else {
            debit = linked;
            credit = transaction;
        }

        populateTransferLeg(debit, sourceAccountId, TransactionDirection.EXPENSE,
                amountCents, bookingDate, credit.id, note);
        populateTransferLeg(credit, targetAccountId, TransactionDirection.INCOME,
                amountCents, bookingDate, debit.id, note);

        transactionDao.updateTransferPair(debit, credit);
        return true;
    }

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

    private static void populateTransferLeg(BudgetTransactionEntity leg,
                                              String accountId,
                                              TransactionDirection direction,
                                              long amountCents,
                                              LocalDate bookingDate,
                                              String linkedId,
                                              String note) {
        leg.accountId = accountId;
        leg.direction = direction;
        leg.amountCents = amountCents;
        leg.setBookingDate(bookingDate);
        leg.transactionKind = TransactionKind.INTERNAL_TRANSFER;
        leg.categoryId = null;
        leg.note = normalizeNote(note);
        leg.linkedTransactionId = linkedId;
    }
}
