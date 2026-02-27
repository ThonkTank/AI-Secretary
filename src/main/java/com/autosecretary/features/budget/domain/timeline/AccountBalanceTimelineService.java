package com.autosecretary.features.budget.domain.timeline;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
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
                .collect(Collectors.toMap(DailyDeltaPoint::date, DailyDeltaPoint::deltaCents));
        return reconstructTimeline(
                fromDate, toDate, startBalanceCents, deltaByDate,
                Function.identity(),
                d -> d.plusDays(1),
                (d1, d2) -> !d1.isAfter(d2));
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
        return reconstructTimeline(
                fromMonth, toMonth, startBalanceCents, deltaByMonth,
                YearMonth::atEndOfMonth,
                m -> m.plusMonths(1),
                (m1, m2) -> !m1.isAfter(m2));
    }

    /**
     * Generic timeline reconstruction helper.
     * Accumulates deltas over a sequence of temporal periods.
     *
     * @param fromPeriod       first period of the window (inclusive)
     * @param toPeriod         last period of the window (inclusive)
     * @param startBalanceCents initial balance before the first period
     * @param deltaMap         map of deltas keyed by period
     * @param toLocalDate      function to convert a period to a LocalDate for storage
     * @param nextPeriod       function to get the next period in the sequence
     * @param loopCondition    predicate to determine if iteration should continue
     * @return timeline points from fromPeriod to toPeriod
     */
    private static <T> List<BalanceTimelinePoint> reconstructTimeline(
            T fromPeriod,
            T toPeriod,
            long startBalanceCents,
            Map<T, Long> deltaMap,
            Function<T, LocalDate> toLocalDate,
            Function<T, T> nextPeriod,
            BiPredicate<T, T> loopCondition) {
        List<BalanceTimelinePoint> points = new ArrayList<>();
        long runningBalance = startBalanceCents;
        T cursor = fromPeriod;
        while (loopCondition.test(cursor, toPeriod)) {
            runningBalance += deltaMap.getOrDefault(cursor, 0L);
            points.add(new BalanceTimelinePoint(toLocalDate.apply(cursor), runningBalance));
            cursor = nextPeriod.apply(cursor);
        }
        return points;
    }
}
