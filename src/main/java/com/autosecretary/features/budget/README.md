# Budget feature overview

## Start here

The budget feature is organized by responsibility and then by sub-feature:

- `ui/`: fragment/view-model and budget screen presentation.
- `application/`: use-cases coordinating workflows.
  - `application/importing/`: end-to-end import flow (parse, deduplicate, persist, recurring suggestions).
- `domain/`: business logic and recurring detection algorithms.
- `data/entity/`: canonical Room entities (`BudgetAccount`, `BudgetCategory`, `BudgetTransactionEntity`, `BudgetLimit`).
- `data/dao/`: Room DAO interfaces for canonical budget queries/mutations.
- `data/repository/`: Room-backed repository implementations.
- `data/projection/`: Room query models/read projections used by dashboards and charts.
- `data/importing/`: import metadata entities/DAOs/templates.
- `data/legacy/`: transitional pre-v8 classes kept for migration reference only.

## Placement rule for new code

- Add new workflow orchestration to `application/`.
- Add import-related workflows and adapters to `application/importing/`.
- Add canonical persisted entities to `data/entity/`.
- Add DAO interfaces to `data/dao/` and read/query projections to `data/projection/`.
- Add Room-backed repository implementations to `data/repository/`.
- Add import-specific entities/DAOs/templates to `data/importing/`.
- Only place files in `data/legacy/` when they are intentionally deprecated/compatibility-only.

## Manual validation for balance time series/chart

1. Open the budget screen and choose one account in the account selector.
2. Add the following transactions (same account), with these exact dates and amounts:
   - 01.01: `+1000,00` (income)
   - 05.01: `-200,00` (expense)
   - 10.01: `-150,00` (expense)
   - 15.01: `+300,00` (income)
3. Expected curve for **30T** (daily):
   - Starts around `1000 €` after first booking.
   - Steps down to `800 €`, then `650 €`, then up to `950 €`.
   - Between booking dates, line stays flat.
4. Switch filters to **3M** and **12M**:
   - Monthly points should preserve the same net movement in January and carry the cumulative balance to following months without new bookings.
5. Delete one transaction (for example `-150,00` on 10.01):
   - Chart should update immediately and skip the removed drop.
6. Import a CSV with additional bookings for the same account:
   - After import completion, chart points should refresh automatically.
7. If recurring suggestions are applied, verify that accepted items also update the chart immediately.
