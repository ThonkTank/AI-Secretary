# Features (`features/`)

AutoSecretary is organized into three self-contained features. Each follows the same four-layer structure: `ui/` → `application/` → `domain/` → `data/`.

## Feature overview

| Feature | Package | What it does | Maturity |
|---------|---------|-------------|----------|
| **Task** | `task/` | Recurring task scheduling — creates daily time slots, tracks streaks, supports checklist and manage modes | Stable, fully featured |
| **Budget** | `budget/` | Income/expense tracking — CSV/PDF import, recurring pattern detection, balance charts, home-screen widget | Stable, fully featured |
| **Meal** | `meal/` | Meal planning — recipes, pantry tracking, shopping lists, household nutrition targets | Backend stable; UI is minimal (single fragment, no dedicated meal layouts beyond dialogs) |

## Recommended reading order

1. **Start with Task** — it is the original and most mature feature. Read `task/README.md` first.
2. **Then Budget** — it follows the same patterns but adds import pipelines and external API integration. Read `budget/README.md`.
3. **Then Meal** — it uses a different storage approach (in-memory maps instead of Room) due to its earlier development stage. Read `meal/README.md`.

## Cross-feature integration points

- **Task → Budget:** Tasks can have optional budget fields (`budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`). When a task completes with a budget requirement, `BookTaskCompletionExpenseUseCase` auto-books an expense. See `task/application/internal/budget/`.
- **Task → Meal:** Tasks can reference planned meals via `TaskPlannedMeal`. `TaskMealIntegrationService` (in `meal/application/`) handles pantry depletion and consumption logging when meal tasks complete.
- **Budget ↔ Meal:** No direct integration.

## Shared infrastructure

- `shared/` — cross-feature enums (`Priority`, `Period`) and `WidgetConfiguration`.
- `database/AppDatabase` — Room database shared by task and budget (meal uses in-memory storage).
- `app/AppCompositionRoot` — manual DI root that wires all features.

## Each feature's internal structure

```
feature/
├── ui/              UI layer (Fragments, ViewModels, adapters, dialogs)
├── application/     Use cases, presenters, orchestration
├── domain/          Pure business logic, interfaces, value types
└── data/            Persistence (Room DAOs, entities, repositories, API clients)
```

Implementation details live in `internal/` sub-packages at each layer.

## Further reading

- [Project root README](../../../../../README.md) — quick start, glossary, build commands
- [CLAUDE.md](../../../../../CLAUDE.md) — architecture reference, conventions, rules
