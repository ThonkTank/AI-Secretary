# Budget feature overview

## Start here

The budget feature is organized by responsibility and then by sub-feature:

- `ui/`: fragment/view-model and budget screen presentation.
- `application/`: use-cases coordinating workflows.
  - `application/importing/`: end-to-end import flow (parse, deduplicate, persist, recurring suggestions).
- `domain/`: pure business logic, repository interfaces, and domain value types (no Android dependencies).
- `data/entity/`: canonical Room entities (`BudgetAccount`, `BudgetCategory`, `BudgetTransactionEntity`, `BudgetLimit`, `BudgetImportEntity`, `BudgetRecurringTemplateEntity`).
- `data/dao/`: Room DAO interfaces for entity access and aggregate queries.
- `data/repository/`: Room-backed repository implementations.
- `data/api/`: HTTP clients and secure credential storage (`ClaudeStatementApiClient` for PDF import via Claude API; `ClaudeApiKeyStore` for AES-256-GCM encrypted API key storage).

## If you're new here, read in this order

1. **This file** — understand the package structure and layer responsibilities.
2. **`domain/README.md`** — learn the domain vocabulary (transactions, accounts, categories, recurring) and core data contracts.
3. **`application/README.md`** — understand the use-case layer and how it orchestrates domain + data.
4. **`ui/README.md`** — understand how the ViewModel and Fragment connect to the application layer.
5. **`data/README.md`** — dive into Room entities, DAOs, and repository implementations once you know what they serve.

For the import pipeline specifically, `application/importing/README.md` is the most complete reference (includes a pipeline diagram, CSV format, error handling, and troubleshooting).

## Placement rule for new code

- Add pure business logic and domain types to `domain/`.
- Add new workflow orchestration to `application/`.
- Add import-related workflows and adapters to `application/importing/`.
- Add new screens, fragments, or ViewModels to `ui/`.
- Add canonical persisted entities to `data/entity/`.
- Add DAO interfaces to `data/dao/`.
- Add Room-backed repository implementations to `data/repository/`.
- Add external API HTTP clients or encrypted credential storage to `data/api/`.

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
