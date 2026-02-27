package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.TransactionKind;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;
import com.autosecretary.features.budget.domain.timeline.AccountBalanceTimelineService;
import com.autosecretary.features.budget.domain.timeline.BalanceTimelinePoint;
import com.autosecretary.features.budget.domain.timeline.DailyDeltaPoint;
import com.autosecretary.features.budget.domain.timeline.MonthlyDeltaPoint;
import com.autosecretary.features.budget.ui.state.TimeRangeFilter;
import com.autosecretary.features.budget.ui.state.BudgetChartPoint;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;
import com.autosecretary.features.budget.ui.state.BudgetTransactionRow;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Assembles all data needed for the budget overview in one pass:
 * queries the repository, maps transactions to display rows, computes the income/expense
 * summary, and loads balance chart data — all ready to post directly to LiveData.
 */
public class BudgetOverviewLoader {

    // 30-day window spans today back to today-29, giving 30 inclusive days.
    private static final int DAYS_30_WINDOW_OFFSET = 29;

    private static final DateTimeFormatter DAILY_LABEL =
            DateTimeFormatter.ofPattern("dd.MM", Locale.GERMAN);
    private static final DateTimeFormatter MONTHLY_LABEL =
            DateTimeFormatter.ofPattern("MMM yy", Locale.GERMAN);

    public record OverviewData(
            List<BudgetAccount> accounts,
            String accountId,
            List<BudgetTransactionRow> rows,
            BudgetSummaryData summary,
            List<BudgetChartPoint> chartPoints
    ) {}

    private final BudgetRepository repository;
    private final BudgetSummaryPresentationMapper summaryPresentationMapper;

    public BudgetOverviewLoader(BudgetRepository repository,
                                BudgetSummaryPresentationMapper summaryPresentationMapper) {
        this.repository = repository;
        this.summaryPresentationMapper = summaryPresentationMapper;
    }

    public OverviewData load(YearMonth month,
                             String selectedAccountId,
                             TimeRangeFilter filter) {
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
            rows.add(new BudgetTransactionRow(
                    item.transactionId,
                    buildTransactionLabel(item),
                    item.direction,
                    item.categoryColorHex,
                    item.amountCents,
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
        // "Free budget" is the account's current running balance, not income-minus-expenses.
        long freeBudgetCents = account != null ? account.currentBalanceCents : 0L;
        return summaryPresentationMapper.toSummary(items, freeBudgetCents);
    }

    private String buildTransactionLabel(MonthlyOverviewItem item) {
        if (item.transactionKind == TransactionKind.INTERNAL_TRANSFER) {
            return item.note != null && !item.note.isBlank() ? "Überweisung · " + item.note : "Überweisung";
        }
        if (item.categoryName != null) {
            return BudgetSummaryPresentationMapper.categoryLabel(item.categoryIcon, item.categoryName);
        }
        if (item.note != null) {
            return item.note;
        }
        return "Buchung";
    }

    private List<BudgetChartPoint> loadBalanceChartData(String accountId, TimeRangeFilter filter) {
        TimeRangeFilter resolvedFilter = filter == null ? TimeRangeFilter.DAYS_30 : filter;

        List<BalanceTimelinePoint> series;
        LocalDate now = LocalDate.now();

        if (resolvedFilter == TimeRangeFilter.DAYS_30) {
            LocalDate fromDate = now.minusDays(DAYS_30_WINDOW_OFFSET);
            long startBalance = repository.getNetAmountBeforeDateForAccount(accountId, fromDate);
            List<DailyDeltaPoint> deltas =
                    repository.getDailyDeltasForAccount(accountId, fromDate, now);
            series = AccountBalanceTimelineService.reconstructDaily(fromDate, now, startBalance, deltas);
        } else {
            int months = resolvedFilter.months;
            YearMonth toMonth = YearMonth.from(now);
            YearMonth fromMonth = toMonth.minusMonths(months - 1L);
            LocalDate startDate = fromMonth.atDay(1);
            long startBalance = repository.getNetAmountBeforeDateForAccount(accountId, startDate);
            List<MonthlyDeltaPoint> deltas = repository.getMonthlyDeltasForAccount(
                    accountId,
                    fromMonth,
                    toMonth
            );
            series = AccountBalanceTimelineService.reconstructMonthly(fromMonth, toMonth, startBalance, deltas);
        }

        DateTimeFormatter labelFormat = resolvedFilter == TimeRangeFilter.DAYS_30 ? DAILY_LABEL : MONTHLY_LABEL;
        List<BudgetChartPoint> points = new ArrayList<>();
        for (BalanceTimelinePoint p : series) {
            points.add(new BudgetChartPoint(p.date().format(labelFormat), p.balanceCents()));
        }
        return points;
    }
}
