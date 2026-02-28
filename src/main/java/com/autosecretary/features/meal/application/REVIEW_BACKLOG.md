# Review Backlog — meal/application

## Open Issues

### [consider] EntityLookupHelper: one-method utility class
**File:** internal/EntityLookupHelper.java
**Why complex:** `EntityLookupHelper` is a utility class containing exactly one public static
method, `requireFound`. It has three callers, all within this package.
**Simpler alternative:** Could be inlined at each of the three call sites as a two-line null
check + `IllegalArgumentException` with a consistent message. Alternatively, could be
replaced with `Objects.requireNonNullElseThrow` + lambda.
**Tradeoff:** The class provides a centralised error-message format ("Entity not found:
id=X") and a named concept for "mandatory entity lookup" — arguably worth keeping for
discoverability. Three callers is enough to justify a helper. Mark as `[consider]` not
`[simplify]`.
