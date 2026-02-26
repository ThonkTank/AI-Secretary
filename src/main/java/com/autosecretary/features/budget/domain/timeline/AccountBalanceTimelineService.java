package com.autosecretary.features.budget.domain.timeline;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AccountBalanceTimelineService {

    private AccountBalanceTimelineService() {
    }

    public static List<BalanceTimelinePoint> reconstructDaily(LocalDate fromDate,
                                                              LocalDate toDate,
                                                              long startBalanceCents,
                                                              List<DailyDeltaPoint> dailyDeltas) {
        Map<LocalDate, Long> deltaByDate = new HashMap<>();
        for (DailyDeltaPoint point : dailyDeltas) {
            if (point == null || point.bucketDate() == null) continue;
            deltaByDate.put(point.bucketDate(), point.deltaCents());
        }

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

    public static List<BalanceTimelinePoint> reconstructMonthly(YearMonth fromMonth,
                                                                YearMonth toMonth,
                                                                long startBalanceCents,
                                                                List<MonthlyDeltaPoint> monthlyDeltas) {
        Map<YearMonth, Long> deltaByMonth = new HashMap<>();
        for (MonthlyDeltaPoint point : monthlyDeltas) {
            if (point == null || point.yearMonth() == null) continue;
            deltaByMonth.put(YearMonth.parse(point.yearMonth()), point.deltaCents());
        }

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
