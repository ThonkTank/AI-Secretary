# Review Backlog — features/task/ui/edit/internal/mapper

## Open Issues

### [keep] `fromTask` lines 49-50 — defensive `orDefault` for Room `@NonNull` goal fields

**File:** `TaskEditStateMapper.java:49-50`

**Decision:** Worth preserving. `TaskCore.goalIcon` and `goalColorHex` carry `@NonNull`
annotations but these are lint hints, not runtime guarantees. Room will happily return null for
a corrupt or schema-migrated row. Falling back to the canonical default is the correct recovery.
The defensive pattern is proportionate and intentional.
