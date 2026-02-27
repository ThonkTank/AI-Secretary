# Review Backlog — budget/domain/internal

## Open Issues

- [consider] `DatePatternDetector.java:172` / `RecurringTemplateScheduler.java:20–53` — `PatternResult.value` is a dual-purpose field (day-of-month for `MONTHLY_DAY`, interval days for `INTERVAL`, always 0 for `MONTHLY_LAST`/`WEEKLY`) and `dayOfWeek` is null except for `WEEKLY`. A sealed interface hierarchy would make each variant self-describing and eliminate the silent nullability convention. However, that would add 4 new files and a visitor/instanceof dispatch pattern for what is ultimately a 4-variant union — more moving parts for a contained internal type. Current form is well-documented via Javadoc. The parallel issue in `BudgetRecurringTemplateEntity.recurringValue` has been promoted to the `budget/` parent backlog.
