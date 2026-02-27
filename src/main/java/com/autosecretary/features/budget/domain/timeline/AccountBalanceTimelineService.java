package com.autosecretary.features.budget.domain.timeline;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AccountBalanceTimelineService {

    private AccountBalanceTimelineService() {
    }

    /**
     * Reconstructs a daily balance timeline for a date window.
     *
     * @param fromDate         first day of the window (inclusive)
     * @param toDate           last day of the window (inclusive)
     * @param startBalanceCents account balance at the close of {@code fromDate - 1} (i.e. the
     *                          opening balance before the first day of the window). Each day's
     *                          delta is accumulated on top of this value, so the first returned
     *                          point already reflects {@code fromDate}'s transactions.
     * @param dailyDeltas      net change per day (income minus expenses); days with no
     *                          transactions may be absent from the list
     * @return one {@link BalanceTimelinePoint} per day from {@code fromDate} to {@code toDate}
     */
    public static List<BalanceTimelinePoint> reconstructDaily(LocalDate fromDate,
                                                              LocalDate toDate,
                                                              long startBalanceCents,
                                                              List<DailyDeltaPoint> dailyDeltas) {
        Map<LocalDate, Long> deltaByDate = dailyDeltas.stream()
                .collect(Collectors.toMap(DailyDeltaPoint::bucketDate, DailyDeltaPoint::deltaCents));

        List<BalanceTimelinePoint> points = new ArrayList<>();
        long runningBalance = startBalanceCents;
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            runningBalance += deltaByDate.getOrDefault(cursor, 0L);
            points.add(new BalanceTimelinePoint(cursor, runningBalance));
            cursor = cursor.plusDays(1);
        }
        return points;
    }

    /**
     * Reconstructs a monthly balance timeline for a month window.
     *
     * @param fromMonth        first month of the window (inclusive)
     * @param toMonth          last month of the window (inclusive)
     * @param startBalanceCents account balance at the close of the month before {@code fromMonth}
     *                          (i.e. the opening balance before the first month of the window).
     *                          Each month's delta is accumulated on top, so the first returned
     *                          point already reflects {@code fromMonth}'s transactions.
     * @param monthlyDeltas    net change per month (income minus expenses); months with no
     *                          transactions may be absent
     * @return one {@link BalanceTimelinePoint} per month (dated to the last day of that month)
     */
    public static List<BalanceTimelinePoint> reconstructMonthly(YearMonth fromMonth,
                                                                YearMonth toMonth,
                                                                long startBalanceCents,
                                                                List<MonthlyDeltaPoint> monthlyDeltas) {
        Map<YearMonth, Long> deltaByMonth = monthlyDeltas.stream()
                .collect(Collectors.toMap(MonthlyDeltaPoint::yearMonth, MonthlyDeltaPoint::deltaCents));

        List<BalanceTimelinePoint> points = new ArrayList<>();
        long runningBalance = startBalanceCents;
        YearMonth cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            runningBalance += deltaByMonth.getOrDefault(cursor, 0L);
            points.add(new BalanceTimelinePoint(cursor.atEndOfMonth(), runningBalance));
            cursor = cursor.plusMonths(1);
        }
        return points;
    }
}
