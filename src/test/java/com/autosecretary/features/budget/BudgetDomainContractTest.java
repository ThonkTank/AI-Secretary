package com.autosecretary.features.budget;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Charakterisierungstests für Legacy-Verhalten aus BudgetManager.provideSummary(...),
 * provideBudgetLimits(...) und Transaction.calcNextOccurrence(...).
 */
public class BudgetDomainContractTest {

    @Test
    public void monthlySummary_usesIncludeInTotalAndMonthFilter() {
        List<AccountSnapshot> accounts = Arrays.asList(
            new AccountSnapshot("Girokonto DKB", true, 149_119),
            new AccountSnapshot("Sparkonto", false, 1_000_000),
            new AccountSnapshot("Bargeld", true, 7_500),
            new AccountSnapshot("Visa Kreditkarte", true, -5_000)
        );

        List<TransactionSnapshot> transactions = LegacyReferenceData.seedTransactions();

        BudgetSummary summary = BudgetDomainService.provideSummary("2026-02", accounts, transactions);

        assertEquals(151_619, summary.totalBalanceCents);
        assertEquals(0, summary.monthlyIncomeCents);
        assertEquals(24_082, summary.monthlyExpensesCents);
        assertEquals(-24_082, summary.monthlyNetCents);
    }

    @Test
    public void budgetLimit_remainingAndOverBudget_matchLegacyFormula() {
        List<TransactionSnapshot> transactions = Arrays.asList(
            new TransactionSnapshot(1L, -4_532, LocalDate.of(2026, 2, 1), 12L, false),
            new TransactionSnapshot(1L, -2_500, LocalDate.of(2026, 2, 7), 12L, false)
        );

        BudgetLimitSnapshot groceries = new BudgetLimitSnapshot(12L, "2026-02", 6_000, 0);
        BudgetEntry entry = BudgetDomainService.provideBudgetEntry(groceries, transactions);

        assertEquals(7_032, entry.spentCents);
        assertEquals(-1_032, entry.remainingCents);
        assertTrue(entry.overBudget);

        BudgetLimitSnapshot restaurant = new BudgetLimitSnapshot(13L, "2026-02", 15_000, 0);
        BudgetEntry okEntry = BudgetDomainService.provideBudgetEntry(restaurant, transactions);

        assertEquals(0, okEntry.spentCents);
        assertEquals(15_000, okEntry.remainingCents);
        assertFalse(okEntry.overBudget);
    }

    @Test
    public void recurring_nextDue_followsLegacyTransactionRules() {
        LocalDate jan31 = LocalDate.of(2026, 1, 31);

        assertEquals(
            LocalDate.of(2026, 2, 28),
            BudgetDomainService.nextDue(jan31, RecurringType.MONTHLY_DAY, 31, null)
        );
        assertEquals(
            LocalDate.of(2026, 2, 28),
            BudgetDomainService.nextDue(jan31, RecurringType.MONTHLY_LAST, 0, null)
        );
        assertEquals(
            LocalDate.of(2026, 2, 7),
            BudgetDomainService.nextDue(LocalDate.of(2026, 1, 31), RecurringType.WEEKLY, 0, DayOfWeek.SATURDAY)
        );
        assertEquals(
            LocalDate.of(2026, 2, 14),
            BudgetDomainService.nextDue(LocalDate.of(2026, 1, 31), RecurringType.INTERVAL, 2, java.time.temporal.ChronoUnit.WEEKS)
        );
    }

    enum RecurringType { MONTHLY_DAY, MONTHLY_LAST, WEEKLY, INTERVAL }

    static final class AccountSnapshot {
        final String name;
        final boolean includeInTotal;
        final int currentBalanceCents;

        AccountSnapshot(String name, boolean includeInTotal, int currentBalanceCents) {
            this.name = name;
            this.includeInTotal = includeInTotal;
            this.currentBalanceCents = currentBalanceCents;
        }
    }

    static final class TransactionSnapshot {
        final Long accountId;
        final int amountCents;
        final LocalDate date;
        final Long categoryId;
        final boolean isIncome;

        TransactionSnapshot(Long accountId, int amountCents, LocalDate date, Long categoryId, boolean isIncome) {
            this.accountId = accountId;
            this.amountCents = amountCents;
            this.date = date;
            this.categoryId = categoryId;
            this.isIncome = isIncome;
        }
    }

    static final class BudgetLimitSnapshot {
        final Long categoryId;
        final String yearMonth;
        final int limitCents;
        final int rolloverCents;

        BudgetLimitSnapshot(Long categoryId, String yearMonth, int limitCents, int rolloverCents) {
            this.categoryId = categoryId;
            this.yearMonth = yearMonth;
            this.limitCents = limitCents;
            this.rolloverCents = rolloverCents;
        }
    }

    static final class BudgetSummary {
        final int totalBalanceCents;
        final int monthlyIncomeCents;
        final int monthlyExpensesCents;
        final int monthlyNetCents;

        BudgetSummary(int totalBalanceCents, int monthlyIncomeCents, int monthlyExpensesCents, int monthlyNetCents) {
            this.totalBalanceCents = totalBalanceCents;
            this.monthlyIncomeCents = monthlyIncomeCents;
            this.monthlyExpensesCents = monthlyExpensesCents;
            this.monthlyNetCents = monthlyNetCents;
        }
    }

    static final class BudgetEntry {
        final int spentCents;
        final int remainingCents;
        final boolean overBudget;

        BudgetEntry(int spentCents, int remainingCents, boolean overBudget) {
            this.spentCents = spentCents;
            this.remainingCents = remainingCents;
            this.overBudget = overBudget;
        }
    }

    static final class BudgetDomainService {
        static BudgetSummary provideSummary(String yearMonth, List<AccountSnapshot> accounts,
                                            List<TransactionSnapshot> transactions) {
            int totalBalance = accounts.stream()
                .filter(a -> a.includeInTotal)
                .mapToInt(a -> a.currentBalanceCents)
                .sum();

            int income = 0;
            int expenses = 0;
            for (TransactionSnapshot tx : transactions) {
                if (!YearMonth.from(tx.date).toString().equals(yearMonth)) continue;
                if (tx.isIncome) {
                    income += tx.amountCents;
                } else {
                    expenses += Math.abs(tx.amountCents);
                }
            }

            return new BudgetSummary(totalBalance, income, expenses, income - expenses);
        }

        static BudgetEntry provideBudgetEntry(BudgetLimitSnapshot limit, List<TransactionSnapshot> transactions) {
            int spent = transactions.stream()
                .filter(tx -> !tx.isIncome)
                .filter(tx -> limit.categoryId.equals(tx.categoryId))
                .filter(tx -> YearMonth.from(tx.date).toString().equals(limit.yearMonth))
                .mapToInt(tx -> Math.abs(tx.amountCents))
                .sum();

            int remaining = limit.limitCents + limit.rolloverCents - spent;
            return new BudgetEntry(spent, remaining, remaining < 0);
        }

        static LocalDate nextDue(LocalDate from, RecurringType type, int repetitionValue,
                                 Object unitOrDay) {
            switch (type) {
                case MONTHLY_DAY:
                    LocalDate next = from.plusMonths(1);
                    return next.withDayOfMonth(Math.min(repetitionValue, next.lengthOfMonth()));
                case MONTHLY_LAST:
                    LocalDate nextMonth = from.plusMonths(1);
                    return nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
                case WEEKLY:
                    return from.plusWeeks(1);
                case INTERVAL:
                    java.time.temporal.ChronoUnit unit = (java.time.temporal.ChronoUnit) unitOrDay;
                    return switch (unit) {
                        case DAYS -> from.plusDays(repetitionValue);
                        case WEEKS -> from.plusWeeks(repetitionValue);
                        case MONTHS -> from.plusMonths(repetitionValue);
                        default -> throw new IllegalArgumentException("unsupported unit");
                    };
                default:
                    throw new IllegalArgumentException("unsupported recurring type");
            }
        }
    }

    static final class LegacyReferenceData {
        private LegacyReferenceData() {}

        static List<TransactionSnapshot> seedTransactions() {
            // Abgeleitet aus history/legacy/data/SeedTestData.java
            return Arrays.asList(
                new TransactionSnapshot(1L, 280_000, LocalDate.of(2026, 1, 28), 1L, true),
                new TransactionSnapshot(1L, -85_000, LocalDate.of(2026, 1, 1), 10L, false),
                new TransactionSnapshot(1L, -1_299, LocalDate.of(2026, 1, 15), 24L, false),
                new TransactionSnapshot(1L, -4_532, LocalDate.of(2026, 2, 1), 12L, false),
                new TransactionSnapshot(1L, -2_850, LocalDate.of(2026, 2, 2), 13L, false),
                new TransactionSnapshot(1L, -9_900, LocalDate.of(2026, 2, 1), 14L, false),
                new TransactionSnapshot(3L, -2_500, LocalDate.of(2026, 2, 1), 12L, false),
                new TransactionSnapshot(1L, -3_000, LocalDate.of(2026, 2, 3), 16L, false),
                new TransactionSnapshot(1L, -15_000, LocalDate.of(2026, 1, 31), 26L, false),
                new TransactionSnapshot(3L, -5_000, LocalDate.of(2026, 1, 20), 29L, false)
            );
        }
    }
}
