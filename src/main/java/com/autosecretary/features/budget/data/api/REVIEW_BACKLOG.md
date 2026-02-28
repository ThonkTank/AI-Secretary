# Review Backlog — budget/data/api

## Open Issues

*(none)*

## Fixed Issues

✅ [warning] **ClaudeStatementApiClient — `parseTransaction` uncaught `DateTimeParseException`** —
Wrapped `LocalDate.parse(dateStr)` in a try-catch; re-thrown as `ApiException` with a
user-friendly German message. Previously, a malformed date from Claude would propagate as an
uncaught runtime exception past `parsePdf`'s catch blocks, crashing the import pipeline.
(`ClaudeStatementApiClient.java:parseTransaction`)
