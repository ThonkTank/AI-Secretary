package com.autosecretary.features.budget.ui.internal;

import android.content.res.Resources;

import com.autosecretary.R;
import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
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
import com.autosecretary.shared.ui.ColorUtil;

import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles all data needed for the budget overview in one pass:
 * queries the repository, maps transactions to display rows, computes the income/expense
 * summary, and loads balance chart data — all ready to post directly to LiveData.
 */
public class BudgetOverviewLoader {

    // 30-day window spans today back to today-29, giving 30 inclusive days.
    private static final int DAYS_30_WINDOW_OFFSET = 29;

    public record OverviewData(
            List<BudgetAccountEntity> accounts,
            String accountId,
            List<BudgetTransactionRow> rows,
            BudgetSummaryData summary,
            List<BudgetChartPoint> chartPoints
    ) {}

    private final BudgetRepository repository;
    private final String labelTransfer;
    private final String labelTransferNotePrefix;
    private final String labelDefaultBooking;

    public BudgetOverviewLoader(BudgetRepository repository,
                                Resources resources) {
        this.repository = repository;
        this.labelTransfer = resources.getString(R.string.budget_transfer_row_label);
        this.labelTransferNotePrefix = resources.getString(R.string.budget_transfer_note_prefix);
        this.labelDefaultBooking = resources.getString(R.string.budget_default_booking_label);
    }

    public OverviewData load(YearMonth month,
                             String selectedAccountId,
                             TimeRangeFilter filter) {
        List<BudgetAccountEntity> accounts = repository.findActiveAccounts();
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

    /**
     * Returns the account ID to use for the current view: the persisted {@code selectedAccountId}
     * if non-blank, otherwise the first account in {@code fallbackAccounts}, or {@code null} when
     * the account list is empty (no accounts configured yet).
     */
    private static String resolveSelectedAccountId(String selectedAccountId, List<BudgetAccountEntity> fallbackAccounts) {
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
                    resolveColor(item.categoryColorHex),
                    item.amountCents,
                    item.categoryId,
                    item.note,
                    item.bookingDate,
                    item.accountId));
        }
        return rows;
    }

    /** Pre-resolves a hex color string to an int on the background thread, avoiding work in onBindViewHolder. */
    private static int resolveColor(String colorHex) {
        return ColorUtil.parseColorSafe(colorHex, BudgetTransactionRow.NO_CATEGORY_COLOR);
    }

    private BudgetSummaryData computeSummary(List<MonthlyOverviewItem> items, String accountId) {
        BudgetAccountEntity account = repository.findAccountById(accountId);
        // "Free budget" is the account's current running balance, not income-minus-expenses.
        long freeBudgetCents = account != null ? account.currentBalanceCents : 0L;
        return BudgetSummaryPresentationMapper.toSummary(items, freeBudgetCents);
    }

    // Label priority: internal-transfer label > category name (with icon) > note > generic fallback.
    private String buildTransactionLabel(MonthlyOverviewItem item) {
        if (item.transactionKind == TransactionKind.INTERNAL_TRANSFER) {
            return item.note != null && !item.note.isBlank() ? labelTransferNotePrefix + item.note : labelTransfer;
        }
        if (item.categoryName != null) {
            return BudgetSummaryPresentationMapper.categoryLabel(item.categoryIcon, item.categoryName);
        }
        if (item.note != null) {
            return item.note;
        }
        return labelDefaultBooking;
    }

    private List<BudgetChartPoint> loadBalanceChartData(String accountId, TimeRangeFilter filter) {
        TimeRangeFilter resolvedFilter = filter == null ? TimeRangeFilter.DAYS_30 : filter;
        LocalDate now = LocalDate.now();

        List<BalanceTimelinePoint> series = resolvedFilter == TimeRangeFilter.DAYS_30
                ? loadDailyTimeline(accountId, now)
                : loadMonthlyTimeline(accountId, now, resolvedFilter.months);

        DateTimeFormatter labelFormat = resolvedFilter == TimeRangeFilter.DAYS_30 ? DateFormatters.CHART_DAILY : DateFormatters.CHART_MONTHLY;
        List<BudgetChartPoint> points = new ArrayList<>();
        for (BalanceTimelinePoint p : series) {
            points.add(new BudgetChartPoint(p.date().format(labelFormat), p.balanceCents()));
        }
        return points;
    }

    private List<BalanceTimelinePoint> loadDailyTimeline(String accountId, LocalDate now) {
        LocalDate fromDate = now.minusDays(DAYS_30_WINDOW_OFFSET);
        long startBalance = repository.getNetAmountBeforeDateForAccount(accountId, fromDate);
        List<DailyDeltaPoint> deltas = repository.getDailyDeltasForAccount(accountId, fromDate, now);
        return AccountBalanceTimelineService.reconstructDaily(fromDate, now, startBalance, deltas);
    }

    private List<BalanceTimelinePoint> loadMonthlyTimeline(String accountId, LocalDate now, int months) {
        YearMonth toMonth = YearMonth.from(now);
        // -1 because the range is inclusive on both ends: months=3 → this month + 2 prior = 3 total.
        YearMonth fromMonth = toMonth.minusMonths(months - 1);
        LocalDate startDate = fromMonth.atDay(1);
        long startBalance = repository.getNetAmountBeforeDateForAccount(accountId, startDate);
        List<MonthlyDeltaPoint> deltas = repository.getMonthlyDeltasForAccount(accountId, fromMonth, toMonth);
        return AccountBalanceTimelineService.reconstructMonthly(fromMonth, toMonth, startBalance, deltas);
    }
}
