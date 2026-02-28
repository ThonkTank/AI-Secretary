# Budget Widget

## Purpose

Home-screen widget that displays key account balances at a glance. Users can:
- View the total net balance (sum of all account balances)
- View the free budget (remaining available budget)
- Tap to open the budget app screen
- Tap the "+" button to quickly add a transaction

The widget updates automatically at a configured interval (default: 30 minutes) and whenever a transaction is added, deleted, or edited within the app.

## Architecture

The widget uses Android's **RemoteViews** pattern to render a simple summary view. RemoteViews are necessary because widgets run in a separate process from the app and cannot directly observe app state changes; instead, they request data on-demand.

### Single-class pattern:

1. **BudgetWidgetProvider** (`AppWidgetProvider`)
   - Entry point for the Android widget framework
   - Handles lifecycle (onUpdate, onReceive)
   - Responds to user button clicks: open app, add transaction, manual refresh
   - Persists no state (stateless design — always fetches current data)
   - Routes button clicks via PendingIntent to MainActivity

## Data Flow

```
App start or widget update triggered
  ↓
BudgetWidgetProvider.onUpdate()
  → Create RemoteViews layout (budget_widget.xml)
  → Build PendingIntents for buttons (open, add)
  → Fetch current balance data via LoadBudgetWidgetSummaryUseCase
  → Render text views with formatted balances
  → Pass to AppWidgetManager to display
  ↓
User clicks "Open" or "+" button
  → PendingIntent fires with extras (EXTRA_OPEN_TAB, EXTRA_BUDGET_ACTION)
  → MainActivity handles intent and routes to budget screen
  ↓
User adds/edits/deletes transaction in app
  → Data persists to database
  → App calls notifyWidgetUpdate()
  → Android broadcasts widget update (triggers onUpdate again)
```

## Key Design Constraints

1. **Stateless and simple:**
   - No persistent state in the widget itself
   - Always fetches current balance on every update
   - Two buttons and two summary values only
   - Budget-specific actions (new transaction, open app) available immediately

2. **Synchronous database read:**
   - Widget updates run outside the main Activity thread
   - Synchronous DB read is acceptable because widget rendering happens in a background context
   - Keep the query fast (LoadBudgetWidgetSummaryUseCase should be O(1) or O(n accounts) depending on join strategy)

3. **Request code uniqueness:**
   - Android requires distinct request codes for PendingIntents with different extras
   - BudgetWidgetProvider reserves 10 request code slots per widget instance (`ACTIONS_PER_WIDGET = 10`)
   - This allows up to 10 button actions per widget without collisions
   - Currently uses 2 slots (open, add); buffer enables future expansion without reworking the scheme

4. **German locale:**
   - Widget displays button labels and content in German per project convention
   - See CLAUDE.md: all user-facing text is in German

5. **Update period configuration:**
   - Widget update frequency is defined in two places and must be kept in sync:
     - `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS` (shared code constant)
     - `widget_budget_info.xml` (Android widget manifest entry)
   - Default is 30 minutes (`1800000` ms)

## Public References

- [Android RemoteViews](https://developer.android.com/guide/topics/appwidgets/overview)
- [AppWidgetProvider lifecycle](https://developer.android.com/reference/android/appwidget/AppWidgetProvider)
- [Pending intents and widget click handling](https://developer.android.com/guide/topics/appwidgets/overview#Intents)
- [App widgets design guidelines](https://developer.android.com/guide/topics/appwidgets/design-guidelines)

## Entry Points for Reading

1. Start with `BudgetWidgetProvider.onUpdate()` — see how the widget is triggered
2. Then `BudgetWidgetProvider.updateWidget()` — understand RemoteViews building and intent wiring
3. Check `LoadBudgetWidgetSummaryUseCase` — see what data the widget displays
4. Then `notifyWidgetUpdate()` — understand how the app refreshes the widget after user actions

## See Also

- `CLAUDE.md` — Project glossary and architecture overview
- `src/main/java/com/autosecretary/features/budget/ui/README.md` — Budget UI package conventions
- `src/main/java/com/autosecretary/features/budget/application/LoadBudgetWidgetSummaryUseCase.java` — Data source for widget
- `src/main/res/layout/budget_widget.xml` — Widget layout definition
- `src/main/res/xml/widget_budget_info.xml` — Android widget metadata and update period
