# Recurring Pattern Detection (Internal)

This package implements the core algorithm for detecting recurring transaction patterns from bank statement history. It is orchestrated by [`RecurringPatternDetector`](../RecurringPatternDetector.java) (the public API) and consists of three independent analysis stages:

## The Pipeline

```
RecurringPatternDetector.detectPatterns()
  ↓
  1. PayeeGrouper.groupBySimilarPayee()
     └─ Group transactions by payee name, using fuzzy string matching
        (handles bank statement noise: IDs, reference numbers, etc.)
  ↓
  2. DatePatternDetector.detectDatePattern()
     └─ Analyze the booking dates within each group
        (find: monthly-same-day, monthly-last, weekly, or fixed-interval patterns)
  ↓
  3. SuggestionScorer.calculateConfidence()
     └─ Rate each candidate pattern on a 0–1 scale
        (factors: occurrence count, amount consistency, pattern strength, known subscriptions)
  ↓
  RecurringSuggestion (output: normalized payee, amount stats, detected pattern, confidence)
```

## Why Three Classes?

- **PayeeGrouper** — Payee matching is a self-contained fuzzy-string problem (Levenshtein distance).
- **DatePatternDetector** — Date pattern recognition is independent; reusable logic unrelated to payees.
- **SuggestionScorer** — Confidence scoring uses both date and amount statistics; separate from detection.

Keeping them separate makes each class focused, testable, and easier to understand independently.

## Key Concepts

### Normalized Payee
`PayeeGrouper` converts raw payee strings into a canonical form:
- Uppercase
- Strip digits and `#*` characters (transaction IDs)
- Replace non-letter/non-space characters with spaces (special chars in names)
- Collapse whitespace

Example: `"NETFLIX IE1 GRBringing"` → `"NETFLIX IE GR"` (normalized form used for grouping).

### Pattern Types
Detected by `DatePatternDetector`:
- **`MONTHLY_DAY`** — Recurs on the same day of month (e.g., rent on the 1st). Handles month-end wrap-around (28th in Feb → 31st in Jan).
- **`MONTHLY_LAST`** — Recurs in the last 3 days of each month (e.g., utility bills).
- **`WEEKLY`** — Recurs on the same day of week, 5–9 days apart, in ≥80% of samples (e.g., weekly gym).
- **`INTERVAL`** — Fixed interval (≥3 days), all gaps within ±20% of average ±2 days (e.g., every 14 days).

### Confidence Thresholds
`SuggestionScorer` produces scores 0–1:
- **≥ 0.7** — High confidence; auto-apply without user confirmation.
- **0.4–0.7** — Suggest to user for review.
- **< 0.4** — Low confidence; may be filtered out at the UI layer.

## Reading Order

1. **Start here:** [`RecurringPatternDetector`](../RecurringPatternDetector.java) — understand the overall flow and see how the three stages connect.
2. **Then read:** [`PayeeGrouper`](PayeeGrouper.java) — learn how payees are grouped and normalized.
3. **Then read:** [`DatePatternDetector`](DatePatternDetector.java) — understand the pattern detection algorithm and tolerance tuning.
4. **Finally:** [`SuggestionScorer`](SuggestionScorer.java) — see how the confidence score is calculated.

## Common Customization Points

If you need to adjust recurring detection behavior:

- **Payee matching tolerance:** `PayeeGrouper.PAYEE_SIMILARITY_THRESHOLD` (0.75 = 75% similarity)
- **Date pattern tolerances:** `DatePatternDetector` constants (e.g., `MONTHLY_DAY_TOLERANCE`, `INTERVAL_RELATIVE_TOLERANCE`)
- **Confidence weights:** `SuggestionScorer.OCCURRENCE_WEIGHT`, `AMOUNT_VARIANCE_WEIGHT`, etc.
- **Known subscriptions:** `SuggestionScorer.KNOWN_SUBSCRIPTION_PATTERNS` array

All constants are clearly named and documented with inline comments.

## Limitations & Future Work

- **No seasonal patterns** — only detects recurring intervals within a sliding window.
- **No trend detection** — recurring patterns with amounts that slowly increase/decrease are treated as inconsistent.
- **No handling of rounding errors** — bank statements with slight variations in cents may be rejected as inconsistent.

See [`../REVIEW_BACKLOG.md`](../REVIEW_BACKLOG.md) for deferred improvements.
