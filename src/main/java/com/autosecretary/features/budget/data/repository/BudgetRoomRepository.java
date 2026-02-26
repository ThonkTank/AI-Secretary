package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.autosecretary.features.budget.data.dao.BudgetLimitDao;
import com.autosecretary.features.budget.data.dao.BudgetLookupDao;
import com.autosecretary.features.budget.data.dao.TransactionDao;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.dao.BudgetRecurringTemplateDao;
import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;

public class BudgetRoomRepository implements BudgetRepository {
    private final BudgetLookupDao lookupDao;
    private final TransactionDao transactionDao;
    private final BudgetLimitDao limitDao;
    private final BudgetRecurringTemplateDao recurringTemplateDao;

    public BudgetRoomRepository(BudgetLookupDao lookupDao,
                                 TransactionDao transactionDao,
                                 BudgetLimitDao limitDao,
                                 BudgetRecurringTemplateDao recurringTemplateDao) {
        this.lookupDao = lookupDao;
        this.transactionDao = transactionDao;
        this.limitDao = limitDao;
        this.recurringTemplateDao = recurringTemplateDao;
    }

    @Override public BudgetAccount findAccountById(String accountId) {
        return lookupDao.findAccountById(accountId);
    }

    @Override public List<BudgetAccount> findActiveAccounts() {
        return lookupDao.getActiveAccounts();
    }

    @Override public List<BudgetCategory> getActiveCategories() {
        return lookupDao.getActiveCategories();
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
        return limitDao.getLimitForCategoryAndMonth(categoryId, yearMonth);
    }

    @Override public BudgetLimit findPreviousMonthLimit(String categoryId, String targetYearMonth) {
        String prevYearMonth = YearMonth.parse(targetYearMonth).minusMonths(1).toString();
        return limitDao.getPreviousMonthLimit(categoryId, prevYearMonth);
    }

    @Override public long getPreviousMonthExpenseCents(String categoryId, String targetYearMonth) {
        String prevYearMonth = YearMonth.parse(targetYearMonth).minusMonths(1).toString();
        return limitDao.getPreviousMonthExpenseCents(categoryId, prevYearMonth);
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

        long total = 0L;
        for (BudgetRecurringTemplateEntity template : templates) {
            total += template.avgAmountCents;
        }
        return total;
    }

    @Override public void saveTransaction(BudgetTransactionEntity transaction) {
        transactionDao.insert(transaction);
    }

    @Override public String findDefaultActiveAccountId() {
        return lookupDao.findFirstActiveAccountId();
    }

    @Override public void applyExpenseToAccountBalance(String accountId, long expenseCents) {
        if (isAllAccounts(accountId) || expenseCents <= 0) {
            return;
        }
        BudgetAccount account = lookupDao.findAccountById(accountId);
        if (account == null) {
            return;
        }
        long updatedBalance = account.currentBalanceCents - expenseCents;
        lookupDao.updateCurrentBalanceCents(accountId, updatedBalance);
    }

    @Override public void saveTransaction(String accountId, String categoryId, TransactionDirection type,
                                          long amountCents, LocalDate bookingDate, String note) {
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId, categoryId, type, amountCents, bookingDate);
        entity.note = (note == null || note.trim().isEmpty()) ? null : note.trim();
        transactionDao.insert(entity);
    }

    @Override public void updateTransaction(BudgetTransactionEntity transaction) {
        transactionDao.update(transaction);
    }

    @Override public void updateTransaction(String transactionId, String accountId, String categoryId,
                                            TransactionDirection type, long amountCents,
                                            LocalDate bookingDate, String note) {
        BudgetTransactionEntity entity = transactionDao.findById(transactionId);
        if (entity == null) return;
        entity.accountId = accountId;
        entity.categoryId = categoryId;
        entity.type = type;
        entity.amountCents = amountCents;
        entity.setBookingDate(bookingDate);
        entity.note = (note == null || note.trim().isEmpty()) ? null : note.trim();
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
        applyTransferFields(debit, credit.id, note);
        applyTransferFields(credit, debit.id, note);
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

        BudgetTransactionEntity debit = transaction.type == TransactionDirection.EXPENSE
                ? transaction : linked;
        BudgetTransactionEntity credit = transaction.type == TransactionDirection.INCOME
                ? transaction : linked;

        debit.accountId = sourceAccountId;
        debit.type = TransactionDirection.EXPENSE;
        debit.amountCents = amountCents;
        debit.setBookingDate(bookingDate);
        applyTransferFields(debit, credit.id, note);

        credit.accountId = targetAccountId;
        credit.type = TransactionDirection.INCOME;
        credit.amountCents = amountCents;
        credit.setBookingDate(bookingDate);
        applyTransferFields(credit, debit.id, note);

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
        return transactionDao.getMonthlyOverview(yearMonth, null);
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
            String accountId, String fromYearMonth, String toYearMonth) {
        return transactionDao.getMonthlyDeltasForAccount(accountId, fromYearMonth, toYearMonth);
    }

    @Override public long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate) {
        return transactionDao.getNetAmountBeforeDateForAccount(accountId, beforeDate);
    }

    /** Returns true when {@code accountId} is null or blank, meaning "aggregate over all accounts". */
    private static boolean isAllAccounts(String accountId) {
        return accountId == null || accountId.isBlank();
    }

    private static void applyTransferFields(BudgetTransactionEntity entity,
                                            String linkedId,
                                            String note) {
        entity.transactionKind = BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER;
        entity.categoryId = null;
        entity.note = note;
        entity.linkedTransactionId = linkedId;
    }
}
