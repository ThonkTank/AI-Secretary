package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.AccountDailyDeltaPoint;
import com.autosecretary.features.budget.data.AccountMonthlyDeltaPoint;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountBalanceTimelineService {

    public List<BalanceTimelinePoint> reconstructDaily(LocalDate fromDate,
                                                       LocalDate toDate,
                                                       long startBalanceCents,
                                                       List<AccountDailyDeltaPoint> dailyDeltas) {
        Map<LocalDate, Long> deltaByDate = new HashMap<>();
        for (AccountDailyDeltaPoint point : dailyDeltas) {
            if (point != null && point.bucketDate != null) {
                deltaByDate.put(point.bucketDate, point.deltaCents);
            }
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

    public List<BalanceTimelinePoint> reconstructMonthly(YearMonth fromMonth,
                                                         YearMonth toMonth,
                                                         long startBalanceCents,
                                                         List<AccountMonthlyDeltaPoint> monthlyDeltas) {
        Map<YearMonth, Long> deltaByMonth = new HashMap<>();
        for (AccountMonthlyDeltaPoint point : monthlyDeltas) {
            if (point == null || point.yearMonth == null) continue;
            deltaByMonth.put(YearMonth.parse(point.yearMonth), point.deltaCents);
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
