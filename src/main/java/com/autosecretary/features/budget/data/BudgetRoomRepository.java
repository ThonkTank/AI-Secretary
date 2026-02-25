package com.autosecretary.features.budget.data;

import com.autosecretary.features.budget.domain.BudgetRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class BudgetRoomRepository implements BudgetRepository {
    private final BudgetLookupDao lookupDao;
    private final TransactionDao transactionDao;
    private final BudgetLimitDao limitDao;

    public BudgetRoomRepository(BudgetLookupDao lookupDao,
                                 TransactionDao transactionDao,
                                 BudgetLimitDao limitDao) {
        this.lookupDao = lookupDao;
        this.transactionDao = transactionDao;
        this.limitDao = limitDao;
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

    @Override public void saveTransaction(BudgetTransactionEntity transaction) {
        transactionDao.insert(transaction);
    }

    @Override public void updateTransaction(BudgetTransactionEntity transaction) {
        transactionDao.update(transaction);
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
        return transactionDao.updateTransferPair(
                transactionId,
                sourceAccountId,
                targetAccountId,
                amountCents,
                bookingDate,
                YearMonth.from(bookingDate).toString(),
                note
        );
    }

    @Override public void saveBudgetLimit(BudgetLimit budgetLimit) {
        BudgetLimit existing = limitDao.getLimitForCategoryAndMonth(budgetLimit.categoryId, budgetLimit.yearMonth);
        if (existing != null) {
            budgetLimit.id = existing.id;
        }
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

    @Override public List<MonthlyTransactionOverviewItem> getMonthlyOverview(String yearMonth) {
        return transactionDao.getMonthlyOverview(yearMonth);
    }

    @Override public List<MonthlyTransactionOverviewItem> getMonthlyOverviewForAccount(String yearMonth, String accountId) {
        return transactionDao.getMonthlyOverviewForAccount(yearMonth, accountId);
    }

    @Override public List<CategorySpendTotal> getCategorySpendTotals(String yearMonth) {
        return limitDao.getCategorySpendTotals(yearMonth);
    }

    @Override public List<AccountDailyDeltaPoint> getDailyDeltasForAccount(
            String accountId, LocalDate fromDate, LocalDate toDate) {
        return transactionDao.getDailyDeltasForAccount(accountId, fromDate, toDate);
    }

    @Override public List<AccountMonthlyDeltaPoint> getMonthlyDeltasForAccount(
            String accountId, String fromYearMonth, String toYearMonth) {
        return transactionDao.getMonthlyDeltasForAccount(accountId, fromYearMonth, toYearMonth);
    }

    @Override public long getNetAmountBeforeDateForAccount(String accountId, LocalDate beforeDate) {
        return transactionDao.getNetAmountBeforeDateForAccount(accountId, beforeDate);
    }
}
