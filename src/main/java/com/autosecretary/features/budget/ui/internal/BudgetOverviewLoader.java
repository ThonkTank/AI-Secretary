package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.projection.AccountDailyDeltaPoint;
import com.autosecretary.features.budget.data.projection.AccountMonthlyDeltaPoint;
import com.autosecretary.features.budget.data.projection.MonthlyTransactionOverviewItem;
import com.autosecretary.features.budget.domain.AccountBalanceTimelineService;
import com.autosecretary.features.budget.domain.BalanceTimelinePoint;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CalculateFreeBudgetUseCase;
import com.autosecretary.features.budget.ui.BudgetViewModel;
import com.autosecretary.features.budget.ui.state.BudgetChartPoint;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BudgetOverviewLoader {

    public static class OverviewData {
        private final List<BudgetAccount> accounts;
        private final String accountId;
        private final List<BudgetViewModel.BudgetTransactionRow> rows;
        private final BudgetSummaryData summary;
        private final List<BudgetChartPoint> chartPoints;

        public OverviewData(List<BudgetAccount> accounts,
                            String accountId,
                            List<BudgetViewModel.BudgetTransactionRow> rows,
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

        public List<BudgetViewModel.BudgetTransactionRow> getRows() {
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
    private final CalculateFreeBudgetUseCase calculateFreeBudgetUseCase;
    private final BudgetSummaryPresentationMapper summaryPresentationMapper;
    private final AccountBalanceTimelineService balanceTimelineService;
    private final BudgetChartStateMapper chartStateMapper;

    public BudgetOverviewLoader(BudgetRepository repository,
                                CalculateFreeBudgetUseCase calculateFreeBudgetUseCase,
                                BudgetSummaryPresentationMapper summaryPresentationMapper,
                                AccountBalanceTimelineService balanceTimelineService,
                                BudgetChartStateMapper chartStateMapper) {
        this.repository = repository;
        this.calculateFreeBudgetUseCase = calculateFreeBudgetUseCase;
        this.summaryPresentationMapper = summaryPresentationMapper;
        this.balanceTimelineService = balanceTimelineService;
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

        List<MonthlyTransactionOverviewItem> items =
                repository.getMonthlyOverviewForAccount(month.toString(), accountId);

        List<BudgetViewModel.BudgetTransactionRow> rows = buildTransactionRows(items);
        BudgetSummaryData summary = computeSummary(items, accountId);
        List<BudgetChartPoint> points = loadBalanceChartData(accountId, filter);

        return new OverviewData(accounts, accountId, rows, summary, points);
    }

    private String resolveSelectedAccountId(String selectedAccountId, List<BudgetAccount> fallbackAccounts) {
        if (selectedAccountId != null && !selectedAccountId.isBlank()) {
            return selectedAccountId;
        }
        if (!fallbackAccounts.isEmpty()) {
            return fallbackAccounts.get(0).id;
        }
        return null;
    }

    private List<BudgetViewModel.BudgetTransactionRow> buildTransactionRows(List<MonthlyTransactionOverviewItem> items) {
        List<BudgetViewModel.BudgetTransactionRow> rows = new ArrayList<>();
        for (MonthlyTransactionOverviewItem item : items) {
            boolean isExpense = "EXPENSE".equals(item.type);
            rows.add(new BudgetViewModel.BudgetTransactionRow(
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

    private BudgetSummaryData computeSummary(List<MonthlyTransactionOverviewItem> items, String accountId) {
        long freeBudgetCents = calculateFreeBudgetUseCase.execute(accountId, LocalDate.now(), 7);
        return summaryPresentationMapper.toSummary(items, freeBudgetCents);
    }

    private String buildTransactionLabel(MonthlyTransactionOverviewItem item) {
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
            List<AccountDailyDeltaPoint> deltas =
                    repository.getDailyDeltasForAccount(accountId, fromDate, now);
            series = balanceTimelineService.reconstructDaily(fromDate, now, startBalance, deltas);
        } else {
            int months = resolvedFilter == BudgetViewModel.TimeRangeFilter.MONTHS_3 ? 3 : 12;
            YearMonth toMonth = YearMonth.from(now);
            YearMonth fromMonth = toMonth.minusMonths(months - 1L);
            LocalDate startDate = fromMonth.atDay(1);
            long startBalance = openingBalanceCents
                    + repository.getNetAmountBeforeDateForAccount(accountId, startDate);
            List<AccountMonthlyDeltaPoint> deltas = repository.getMonthlyDeltasForAccount(
                    accountId,
                    fromMonth.toString(),
                    toMonth.toString()
            );
            series = balanceTimelineService.reconstructMonthly(fromMonth, toMonth, startBalance, deltas);
        }

        return chartStateMapper.map(resolvedFilter, series);
    }
}
