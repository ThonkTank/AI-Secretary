# Recurring Budget Patterns

This package detects and manages recurring transaction patterns from historical budget data.

## Overview

The recurring subsystem answers: **"Which of my transactions are actually recurring subscriptions or regular payments?"**

It analyzes historical transactions to:
1. **Detect date patterns** — when does this transaction occur? (monthly on day 15, every Monday, every 30 days, etc.)
2. **Group similar payees** — are "Netflix Inc.", "NETFLIX", and "Netflix Services" the same recurring transaction?
3. **Score confidence** — is this pattern strong enough to surface as a suggestion?
4. **Schedule future occurrences** — what's the next due date, and should this template stay active?

## Key Concepts

| Term | Definition |
|------|-----------|
| **RecurringType** | The type of repetition: `MONTHLY_DAY` (same day each month), `MONTHLY_LAST` (last day of month), `WEEKLY` (same day of week), `INTERVAL` (fixed day gap) |
| **RecurringSuggestion** | A high-confidence pattern detected from historical data; ready for user review or auto-application |
| **RecurringTemplate** | A persisted suggestion that the user has accepted; generates forecasted future transactions |
| **Pattern Detection** | The analysis phase: grouping payees, checking date consistency, and scoring confidence |
| **Scheduling** | The execution phase: computing next-due dates and maintaining active/inactive status |

## Entry Points

### Pattern Detection (Analysis Phase)
**`RecurringPatternDetector.detectPatterns(transactions)`**

Analyzes a list of historical transactions and returns suggestions.

**Flow:**
1. Filter eligible transactions (not recurring, not predicted, have payee and date)
2. Group by similar payee using fuzzy matching (`PayeeGrouper`)
3. Check if amounts are consistent across the group (`AMOUNT_VARIANCE_THRESHOLD = ±15%`)
4. Detect date pattern (`DatePatternDetector`)
5. Score confidence (`SuggestionScorer`)
6. Sort by confidence; return list of `RecurringSuggestion`

**Example (pseudocode):**
```java
List<RecurringBudgetTransaction> historical = // from import/manual entry
List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(historical);
// Suggestions are sorted by confidence; user reviews and accepts/rejects them
```

### Scheduling (Execution Phase)
**`RecurringTemplateScheduler.computeNextDue(params, referenceDate)`**

Given a recurring template's schedule parameters and a reference date, computes the next due date.

**Example:**
```java
RecurringScheduleParams params = // from persisted template
LocalDate nextDue = RecurringTemplateScheduler.computeNextDue(params, LocalDate.now());
// Returns the next due date, or null if the template should be deactivated
```

## Data Types

### RecurringBudgetTransaction
Represents a single historical transaction as an immutable record with fields indicating:
- **`isRecurring`** — true if generated from an active template (already classified; skip detection)
- **`isPredicted`** — true if this is a forecasted future occurrence not yet booked by the bank
- **`parentRecurringId`** — ID of the template that produced this transaction (null for manual/imported)

Transactions are eligible for pattern detection only if `isRecurring=false && isPredicted=false`.

### RecurringSuggestion
Result of pattern detection. Contains:
- `normalizedPayee` — standardized payee name for matching and deduplication
- `displayPayee` — original payee name for UI display
- `avgAmountCents`, `minAmountCents`, `maxAmountCents` — transaction amounts
- `suggestedType` — the detected `RecurringType` (MONTHLY_DAY, WEEKLY, etc.)
- `suggestedValue` — type-dependent: day of month for `MONTHLY_DAY`, interval days for `INTERVAL`, unused for others
- `suggestedDayOfWeek` — non-null only for `WEEKLY`
- `confidenceScore` — 0–1 score; ≥0.7 suitable for auto-apply

### RecurringScheduleParams
Domain-level parameters for scheduling a template (decouples scheduling logic from storage layer).

### TemplateStatusUpdate
Carries updated scheduling state (next-due date and active/inactive flag) after a scheduling run.

## Internal Details

### PayeeGrouper
Groups transactions by payee using fuzzy string matching (Levenshtein distance, similarity threshold ≥0.75). Normalizes payee names to uppercase and strips transaction IDs, reference numbers, and noise.

### DatePatternDetector
Detects one of four recurring patterns (in priority order):
1. **MONTHLY_DAY** — same day of month (±2 days, with month-end wrap handling)
2. **MONTHLY_LAST** — within last 3 days of month
3. **WEEKLY** — same day of week (≥80% consistency, 5–9 day average interval)
4. **INTERVAL** — fixed interval ≥3 days (±20% relative tolerance + ±2 day absolute)

Returns `null` if fewer than 2 transactions or no pattern is detected.

### SuggestionScorer
Calculates confidence (0–1) using weighted components:
- Occurrence count (30%) — capped at 10 samples
- Amount variance (30%) — low spread scores higher
- Pattern type bonus (30%) — always included if a date pattern was confirmed
- Known subscription (10%) — tie-breaker for recognized services

Threshold: ≥0.7 is considered high-confidence (suitable for auto-apply).

## When This Module Runs

1. **Import flow** — After CSV/PDF import, `BudgetImportUseCase` calls `detectPatterns()` to surface recurring suggestions to the user
2. **Daily scheduling** — At application start or daily alarm, `computeStatusUpdates()` re-evaluates template next-due dates and active status

## Troubleshooting for Developers

| Issue | Check |
|-------|-------|
| Suggestion not detected even though pattern exists | Check `AMOUNT_VARIANCE_THRESHOLD` (15%) — if amounts vary more, increase threshold or check the amount parsing logic |
| Too many false-positive suggestions | Lower `MIN_OCCURRENCES_DEFAULT` (currently 3) or increase the confidence score threshold for user-facing surfaces |
| Payee grouping too aggressive or too lenient | Adjust `PAYEE_SIMILARITY_THRESHOLD` (currently 0.75) in `PayeeGrouper` |
| Next-due date incorrect | Review month-end handling in `MONTHLY_DAY` (wrap-around) or month-length calculation in `MONTHLY_LAST` |
| Known subscriptions not recognized | Add pattern to `KNOWN_SUBSCRIPTION_PATTERNS` in `SuggestionScorer` |

## References

- [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance) (string similarity algorithm used by `PayeeGrouper`)
- `features/budget/domain/importing/` — import pipeline that feeds transactions to pattern detection
- `features/budget/data/repository/` — persistence layer for recurring templates
