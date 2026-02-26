package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
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
        return limitDao.getPreviousMonthLimit(categoryId, targetYearMonth);
    }

    @Override public long getPreviousMonthExpenseCents(String categoryId, String targetYearMonth) {
        return limitDao.getPreviousMonthExpenseCents(categoryId, targetYearMonth);
    }

    @Override public long getCategoryExpenseCents(String categoryId, String yearMonth) {
        return limitDao.getExpenseCentsForCategoryAndMonth(categoryId, yearMonth);
    }

    @Override public Long getEffectiveLimitCents(String categoryId, String targetYearMonth) {
        return limitDao.getEffectiveLimitCentsForMonth(categoryId, targetYearMonth);
    }

    @Override public long getCurrentBalanceCents(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return lookupDao.sumCurrentBalanceCentsForActiveAccounts();
        }
        Long value = lookupDao.findCurrentBalanceCentsByAccountId(accountId);
        return value != null ? value : 0L;
    }

    @Override public long getUpcomingExpenseTemplateCents(String accountId, LocalDate fromDate, LocalDate toDate) {
        List<BudgetRecurringTemplateEntity> templates = (accountId == null || accountId.isBlank())
                ? recurringTemplateDao.findActiveExpenseTemplatesForActiveAccountsInRange(fromDate, toDate)
                : recurringTemplateDao.findActiveExpenseTemplatesForAccountInRange(accountId, fromDate, toDate);

        long total = 0L;
        for (BudgetRecurringTemplateEntity template : templates) {
            total += -template.avgAmountCents;  // avgAmountCents < 0 guaranteed by DAO query
        }
        return total;
    }

    @Override public void saveTransaction(BudgetTransactionEntity transaction) {
        transactionDao.insert(transaction);
    }

    public String findDefaultActiveAccountId() {
        List<BudgetAccount> accounts = lookupDao.getActiveAccounts();
        if (accounts == null || accounts.isEmpty()) {
            return null;
        }
        return accounts.get(0).id;
    }

    public void applyExpenseToAccountBalance(String accountId, long expenseCents) {
        if (accountId == null || accountId.isBlank() || expenseCents <= 0) {
            return;
        }
        BudgetAccount account = lookupDao.findAccountById(accountId);
        if (account == null) {
            return;
        }
        long updatedBalance = account.currentBalanceCents - expenseCents;
        lookupDao.updateCurrentBalanceCents(accountId, updatedBalance);
    }

    @Override public void saveTransaction(String accountId, String categoryId, boolean isExpense,
                                          long amountCents, LocalDate bookingDate, String note) {
        BudgetTransactionEntity.TransactionType type = isExpense
                ? BudgetTransactionEntity.TransactionType.EXPENSE
                : BudgetTransactionEntity.TransactionType.INCOME;
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId, categoryId, type, amountCents, bookingDate,
                YearMonth.from(bookingDate).toString());
        entity.note = note;
        transactionDao.insert(entity);
    }

    @Override public void updateTransaction(BudgetTransactionEntity transaction) {
        transactionDao.update(transaction);
    }

    @Override public void updateTransaction(String transactionId, String accountId, String categoryId,
                                            boolean isExpense, long amountCents,
                                            LocalDate bookingDate, String note) {
        BudgetTransactionEntity.TransactionType type = isExpense
                ? BudgetTransactionEntity.TransactionType.EXPENSE
                : BudgetTransactionEntity.TransactionType.INCOME;
        BudgetTransactionEntity entity = transactionDao.findById(transactionId);
        if (entity == null) {
            entity = new BudgetTransactionEntity(accountId, categoryId, type, amountCents,
                    bookingDate, YearMonth.from(bookingDate).toString());
            entity.id = transactionId;
        }
        entity.accountId = accountId;
        entity.categoryId = categoryId;
        entity.type = type;
        entity.amountCents = amountCents;
        entity.bookingDate = bookingDate;
        entity.yearMonth = YearMonth.from(bookingDate).toString();
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
        String yearMonth = YearMonth.from(bookingDate).toString();

        BudgetTransactionEntity debit = new BudgetTransactionEntity(
                sourceAccountId,
                null,
                BudgetTransactionEntity.TransactionType.EXPENSE,
                amountCents,
                bookingDate,
                yearMonth
        );
        debit.note = note;

        BudgetTransactionEntity credit = new BudgetTransactionEntity(
                targetAccountId,
                null,
                BudgetTransactionEntity.TransactionType.INCOME,
                amountCents,
                bookingDate,
                yearMonth
        );
        credit.note = note;

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

        BudgetTransactionEntity debit = transaction.type == BudgetTransactionEntity.TransactionType.EXPENSE
                ? transaction : linked;
        BudgetTransactionEntity credit = transaction.type == BudgetTransactionEntity.TransactionType.INCOME
                ? transaction : linked;

        String yearMonth = YearMonth.from(bookingDate).toString();

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
        return transactionDao.getMonthlyOverview(yearMonth);
    }

    @Override public List<MonthlyOverviewItem> getMonthlyOverviewForAccount(String yearMonth, String accountId) {
        return transactionDao.getMonthlyOverviewForAccount(yearMonth, accountId);
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
}
