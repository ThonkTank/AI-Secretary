# Review Backlog — budget/domain/internal

## Open Issues

- [warning] `DatePatternDetector.java:172` / `RecurringTemplateScheduler.java:20–53` — `PatternResult.value` is a type-unsafe union field whose meaning changes per `type` (day-of-month for `MONTHLY_DAY`, interval days for `INTERVAL`, always 0 for `MONTHLY_LAST`/`WEEKLY`). Documented via Javadoc. Consider a sealed interface hierarchy (`MonthlyDayPattern(int dayOfMonth)`, `WeeklyPattern(DayOfWeek)`, `IntervalPattern(int days)`, `MonthlyLastPattern()`) so each variant is self-describing and extensible without silent conventions. Same overloading exists in `BudgetRecurringTemplateEntity.recurringValue`.
