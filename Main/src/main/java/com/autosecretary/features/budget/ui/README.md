# Budget UI Layer

## Entry points

| Class | Role |
|---|---|
| `BudgetFragment` | Main budget screen fragment. Inflates views, observes `BudgetViewModel` LiveData, delegates dialog operations to `*DialogController` objects. Entry point from `MainActivity`. |
| `BudgetViewModel` | Central state owner. Loads data via the repository + `BudgetOverviewLoader`, owns account/filter selection, exposes 11 LiveData streams. All DB work runs on the injected `ExecutorService`. |
| `BudgetViewModelFactory` | `ViewModelProvider.Factory` that wires all dependencies into `BudgetViewModel`. Configured in `AppCompositionRoot`. |

## Data flow

```
BudgetViewModel (ViewModel)
  └─ BudgetRepository / BudgetOverviewLoader  ← background executor
       ↓ (on main via postToMain)
  LiveData<BudgetSummaryData>      → BudgetFragment.observeSummary()
  LiveData<List<BudgetTransactionRow>>  → BudgetFragment.renderTransactions()
  LiveData<List<BudgetLimitBar>>   → BudgetFragment.renderLimitBars()
  LiveData<List<BudgetChartPoint>> → BudgetBalanceChartView (custom View in internal/)
  LiveData<BudgetUiState>          → show/hide loading, empty, content, error states
```

All user actions (add transaction, change account, change time range) call ViewModel methods,
which trigger a repository reload, which posts updated LiveData back to the Fragment.

## Sub-packages

| Package | README | Role |
|---------|--------|------|
| `internal/` | [`internal/README.md`](internal/README.md) | Dialog controllers, chart view, data loader, formatters — private implementation helpers |
| `state/` | [`state/README.md`](state/README.md) | Immutable view-state objects posted via LiveData (`BudgetSummaryData`, `BudgetTransactionRow`, etc.) |
| `widget/` | [`widget/README.md`](widget/README.md) | Home-screen widget (`BudgetWidgetProvider`) |

## Reading order for newcomers

1. **`state/README.md`** — understand the view-state objects before reading any Fragment/ViewModel code.
2. **`BudgetViewModel.java`** — see what data is loaded and how it is posted to the Fragment.
3. **`BudgetFragment.java`** — see how LiveData observations map to view updates; start at `onViewCreated`.
4. **`internal/README.md`** — then read the dialog controllers and supporting helpers.
5. **`widget/README.md`** — home-screen widget is self-contained; read independently.

## Public resources

- [Android ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Android LiveData](https://developer.android.com/topic/libraries/architecture/livedata)
- [Android Fragment lifecycle](https://developer.android.com/guide/fragments/lifecycle)
