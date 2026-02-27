# REVIEW_BACKLOG — database/

## Open Issues

### [keep] Enum converter pairs repeat identical `name()`/`valueOf()` structure 12 times
**File:** `Converters.java:64-153`
**Note:** All twelve enum type pairs follow the same `return x != null ? x.name() : null` / `return value != null ? Enum.valueOf(value) : null` pattern. A generic private helper could reduce the body of each method to one line, but Room requires individually annotated concrete methods regardless — the methods themselves cannot be removed. The current form is idiomatic Android/Room and easy to scan. No change recommended.
