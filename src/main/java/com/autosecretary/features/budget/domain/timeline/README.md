# Timeline — Balance Reconstruction

## What is this?

The timeline module reconstructs **cumulative account balances** from transaction deltas (net change per day or month). It's the engine behind the budget screen's **balance chart**.

## Key Concept: Deltas vs. Balances

- **Delta**: Net change for a period (income − expenses). Can be missing if no transactions occurred that day/month.
- **Balance**: Cumulative account balance on a date. Always includes points for every day/month in the window.

The timeline service fills gaps, reconstructs the continuous curve from sparse deltas, and produces the points needed for visualization.

## The Flow

```
Raw transactions (BudgetTransactionEntity)
        ↓
BudgetRepository.getDailyDeltasForAccount()
        ↓ (aggregated per day/month)
DailyDeltaPoint / MonthlyDeltaPoint
        ↓
AccountBalanceTimelineService.reconstructDaily/Monthly()
        ↓
BalanceTimelinePoint (one per day/month, fully reconstructed)
        ↓
BudgetBalanceChartView (renders the balance curve)
```

## Type Reference

### DailyDeltaPoint / MonthlyDeltaPoint
Input: Per-period net transactions (income − expenses), in cents.
- May be absent for periods with no transactions (treated as zero delta).
- Produced by database aggregation in `BudgetRepository`.

### BalanceTimelinePoint
Output: Cumulative account balance on a given date, in cents.
- One point per day/month from `fromDate/Month` to `toDate/Month`.
- Always fully populated (no gaps).
- Ready for chart rendering.

## Cents Convention

All balance/delta values are in **cents** (integers), not decimal currency:
- 9999 cents = 99.99 EUR
- This avoids floating-point precision issues that plague financial code.

## Where to Start

1. Read the javadocs in `AccountBalanceTimelineService` — the public static methods (`reconstructDaily`, `reconstructMonthly`) show both typical usage patterns and detailed semantics.
2. Look at the usage in `LoadBudgetOverviewUseCase` to see how deltas are fetched and passed to reconstruction.
3. Each method contains an inline accumulation loop; the `@param startBalanceCents` javadoc explains the opening-balance convention.

## When You Need This Module

- Adding new balance chart functionality
- Changing the period granularity (e.g., weekly instead of daily)
- Debugging balance curve issues (check delta aggregation, opening balance calculation, and date range)
