package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;
import com.autosecretary.features.budget.domain.timeline.AccountBalanceTimelineService;
import com.autosecretary.features.budget.domain.timeline.BalanceTimelinePoint;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;
import com.autosecretary.features.budget.ui.BudgetViewModel;
import com.autosecretary.features.budget.ui.state.BudgetChartPoint;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BudgetOverviewLoader {

    public static class OverviewData {
        private final List<BudgetAccount> accounts;
        private final String accountId;
        private final List<BudgetTransactionRow> rows;
        private final BudgetSummaryData summary;
        private final List<BudgetChartPoint> chartPoints;

        public OverviewData(List<BudgetAccount> accounts,
                            String accountId,
                            List<BudgetTransactionRow> rows,
                            BudgetSummaryData summary,
                            List<BudgetChartPoint> chartPoints) {
            this.accounts = accounts;
            this.accountId = accountId;
            this.rows = rows;
            this.summary = summary;
            this.chartPoints = chartPoints;
        }

        public List<BudgetAccount> getAccounts() {
            return accounts;
        }

        public String getAccountId() {
            return accountId;
        }

        public List<BudgetTransactionRow> getRows() {
            return rows;
        }

        public BudgetSummaryData getSummary() {
            return summary;
        }

        public List<BudgetChartPoint> getChartPoints() {
            return chartPoints;
        }
    }

    private final BudgetRepository repository;
    private final BudgetSummaryPresentationMapper summaryPresentationMapper;
    private final BudgetChartStateMapper chartStateMapper;

    public BudgetOverviewLoader(BudgetRepository repository,
                                BudgetSummaryPresentationMapper summaryPresentationMapper,
                                BudgetChartStateMapper chartStateMapper) {
        this.repository = repository;
        this.summaryPresentationMapper = summaryPresentationMapper;
        this.chartStateMapper = chartStateMapper;
    }

    public OverviewData load(YearMonth month,
                             String selectedAccountId,
                             BudgetViewModel.TimeRangeFilter filter) {
        List<BudgetAccount> accounts = repository.findActiveAccounts();
        String accountId = resolveSelectedAccountId(selectedAccountId, accounts);
        if (accountId == null) {
            return new OverviewData(accounts, null, new ArrayList<>(), null, new ArrayList<>());
        }

        List<MonthlyOverviewItem> items =
                repository.getMonthlyOverviewForAccount(month.toString(), accountId);

        List<BudgetTransactionRow> rows = buildTransactionRows(items);
        BudgetSummaryData summary = computeSummary(items, accountId);
        List<BudgetChartPoint> points = loadBalanceChartData(accountId, filter);

        return new OverviewData(accounts, accountId, rows, summary, points);
    }

    public static String resolveSelectedAccountId(String selectedAccountId, List<BudgetAccount> fallbackAccounts) {
        if (selectedAccountId != null && !selectedAccountId.isBlank()) {
            return selectedAccountId;
        }
        if (!fallbackAccounts.isEmpty()) {
            return fallbackAccounts.get(0).id;
        }
        return null;
    }

    private List<BudgetTransactionRow> buildTransactionRows(List<MonthlyOverviewItem> items) {
        List<BudgetTransactionRow> rows = new ArrayList<>();
        for (MonthlyOverviewItem item : items) {
            boolean isExpense = "EXPENSE".equals(item.type);
            rows.add(new BudgetTransactionRow(
                    item.transactionId,
                    buildTransactionLabel(item),
                    formatTransactionAmount(item.amountCents, isExpense),
                    isExpense,
                    item.categoryColorHex,
                    item.amountCents,
                    isExpense ? BudgetTransactionEntity.TransactionType.EXPENSE
                            : BudgetTransactionEntity.TransactionType.INCOME,
                    item.categoryId,
                    item.note,
                    item.bookingDate,
                    item.accountId
            ));
        }
        return rows;
    }

    private BudgetSummaryData computeSummary(List<MonthlyOverviewItem> items, String accountId) {
        BudgetAccount account = repository.findAccountById(accountId);
        long freeBudgetCents = account != null ? account.currentBalanceCents : 0L;
        return summaryPresentationMapper.toSummary(items, freeBudgetCents);
    }

    private String buildTransactionLabel(MonthlyOverviewItem item) {
        if ("INTERNAL_TRANSFER".equals(item.transactionKind)) {
            return item.note != null && !item.note.isBlank() ? "Überweisung · " + item.note : "Überweisung";
        }
        if (item.categoryName != null) {
            String icon = item.categoryIcon != null && !item.categoryIcon.trim().isEmpty()
                    ? item.categoryIcon : BudgetCategory.DEFAULT_ICON;
            return icon + " " + item.categoryName;
        }
        if (item.note != null) {
            return item.note;
        }
        return "Buchung";
    }

    private String formatTransactionAmount(long amountCents, boolean isExpense) {
        return String.format(
                Locale.GERMAN,
                "%s%.2f €",
                isExpense ? "-" : "+",
                amountCents / 100.0
        );
    }

    private List<BudgetChartPoint> loadBalanceChartData(String accountId, BudgetViewModel.TimeRangeFilter filter) {
        BudgetViewModel.TimeRangeFilter resolvedFilter = filter == null
                ? BudgetViewModel.TimeRangeFilter.DAYS_30
                : filter;

        long openingBalanceCents = 0;
        List<BalanceTimelinePoint> series;
        LocalDate now = LocalDate.now();

        if (resolvedFilter == BudgetViewModel.TimeRangeFilter.DAYS_30) {
            LocalDate fromDate = now.minusDays(29);
            long startBalance = openingBalanceCents
                    + repository.getNetAmountBeforeDateForAccount(accountId, fromDate);
            List<DailyDeltaPoint> deltas =
                    repository.getDailyDeltasForAccount(accountId, fromDate, now);
            series = AccountBalanceTimelineService.reconstructDaily(fromDate, now, startBalance, deltas);
        } else {
            int months = resolvedFilter == BudgetViewModel.TimeRangeFilter.MONTHS_3 ? 3 : 12;
            YearMonth toMonth = YearMonth.from(now);
            YearMonth fromMonth = toMonth.minusMonths(months - 1L);
            LocalDate startDate = fromMonth.atDay(1);
            long startBalance = openingBalanceCents
                    + repository.getNetAmountBeforeDateForAccount(accountId, startDate);
            List<MonthlyDeltaPoint> deltas = repository.getMonthlyDeltasForAccount(
                    accountId,
                    fromMonth.toString(),
                    toMonth.toString()
            );
            series = AccountBalanceTimelineService.reconstructMonthly(fromMonth, toMonth, startBalance, deltas);
        }

        return chartStateMapper.map(resolvedFilter, series);
    }
}
