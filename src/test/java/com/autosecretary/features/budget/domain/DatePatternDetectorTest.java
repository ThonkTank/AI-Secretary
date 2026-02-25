package com.autosecretary.features.budget.domain;

import org.junit.Assert;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public class DatePatternDetectorTest {

    @Test
    public void detectDatePattern_detectsMonthlyDayAcrossMonthBoundaries() {
        List<BudgetTransaction> txList = List.of(
                tx(LocalDate.of(2025, 1, 31)),
                tx(LocalDate.of(2025, 2, 1)),
                tx(LocalDate.of(2025, 3, 2))
        );

        DatePatternDetector.PatternResult result = DatePatternDetector.detectDatePattern(txList);

        Assert.assertNotNull(result);
        Assert.assertEquals(BudgetTransaction.RecurringType.MONTHLY_DAY, result.type());
    }

    @Test
    public void detectDatePattern_detectsMonthlyLastOnShortAndLongMonths() {
        List<BudgetTransaction> txList = List.of(
                tx(LocalDate.of(2025, 1, 31)),
                tx(LocalDate.of(2025, 2, 28)),
                tx(LocalDate.of(2025, 3, 30)),
                tx(LocalDate.of(2025, 4, 29))
        );

        DatePatternDetector.PatternResult result = DatePatternDetector.checkMonthlyLast(
                txList.stream().map(t -> t.transactionDate).toList()
        );

        Assert.assertNotNull(result);
        Assert.assertEquals(BudgetTransaction.RecurringType.MONTHLY_LAST, result.type());
    }

    @Test
    public void detectDatePattern_detectsWeeklyWithOneOutlier() {
        List<BudgetTransaction> txList = List.of(
                tx(LocalDate.of(2025, 1, 6)),
                tx(LocalDate.of(2025, 1, 13)),
                tx(LocalDate.of(2025, 1, 20)),
                tx(LocalDate.of(2025, 1, 28)),
                tx(LocalDate.of(2025, 2, 3))
        );

        DatePatternDetector.PatternResult result = DatePatternDetector.detectDatePattern(txList);

        Assert.assertNotNull(result);
        Assert.assertEquals(BudgetTransaction.RecurringType.WEEKLY, result.type());
        Assert.assertEquals(DayOfWeek.MONDAY, result.dayOfWeek());
    }

    @Test
    public void detectDatePattern_detectsConsistentInterval() {
        List<BudgetTransaction> txList = List.of(
                tx(LocalDate.of(2025, 1, 1)),
                tx(LocalDate.of(2025, 1, 15)),
                tx(LocalDate.of(2025, 1, 29)),
                tx(LocalDate.of(2025, 2, 12))
        );

        DatePatternDetector.PatternResult result = DatePatternDetector.detectDatePattern(txList);

        Assert.assertNotNull(result);
        Assert.assertEquals(BudgetTransaction.RecurringType.INTERVAL, result.type());
        Assert.assertEquals(14, result.value());
    }

    private BudgetTransaction tx(LocalDate date) {
        return new BudgetTransaction.Builder(10L, -1200, date, 5L)
                .payee("DATE TEST")
                .build();
    }
}
