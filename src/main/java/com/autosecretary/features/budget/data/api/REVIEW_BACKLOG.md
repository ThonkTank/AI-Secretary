# Review Backlog — budget/data/api

## Open Issues

*(none)*

---

## Fixed Issues

✅ [improve] **ClaudeStatementApiClient — `emptyToNull()` avoids idiomatic `isBlank()` check** —
Simplified method to use `isBlank()` directly instead of creating an intermediate `trimmed` variable
(line 370–371). The new version is more concise and idiomatic for Java 11+: checks if the value is
null or blank in a single condition, then returns the trimmed result or null. Avoids the unnecessary
intermediate variable that only renamed the previous expression without adding meaning.
(`ClaudeStatementApiClient.java:emptyToNull`)

✅ [critical] **ClaudeStatementApiClient — `removeFences()` fails on inline markdown** —
Refactored `removeFences()` (line 345–359) to handle both multi-line markdown (with language
identifier) and inline markdown (no newline after opening fence). Previously, inline markdown
like ` ```{"key":"value"}``` ` would not be stripped, causing JSON parsing to fail.
Covered both cases: newlineAfterOpening found vs. not found, and properly handles edge case
where closing fence is missing.
(`ClaudeStatementApiClient.java:removeFences`)

✅ [warning] **ClaudeStatementApiClient — Hardcoded JSON field names in `buildCategoryArray()`** —
Added constants `JSON_CATEGORY_NAME` and `JSON_CATEGORY_TYPE` (line 88–89) to match the
existing `JSON_*` pattern. Updated `buildCategoryArray()` to use these constants instead of
hardcoded strings "name" and "type". Ensures consistency and prevents typos.
(`ClaudeStatementApiClient.java:buildCategoryArray`)

✅ [nit] **ClaudeStatementApiClient — Unnecessary indirection in `buildSystemPrompt()`** —
Removed the delegating `buildSystemPrompt()` method that only called `buildCategoryArray()`
then `buildSystemPromptText()`. Inlined the call in `buildRequestBody()` to eliminate one
level of indirection without loss of clarity.
(`ClaudeStatementApiClient.java:buildRequestBody`)

✅ [nit] **ClaudeStatementApiClient — Redundant method name `buildSystemPromptText()`** —
Renamed `buildSystemPromptText()` to `buildSystemPrompt()` (line 185–198). With the delegator
method removed, this is now the only system-prompt builder, so the "Text" suffix is redundant.
Added full docstring to explain purpose and parameters.
(`ClaudeStatementApiClient.java:buildSystemPrompt`)

✅ [warning] **ClaudeStatementApiClient — `parseTransaction` uncaught `DateTimeParseException`** —
Wrapped `LocalDate.parse(dateStr)` in a try-catch; re-thrown as `ApiException` with a
user-friendly German message. Previously, a malformed date from Claude would propagate as an
uncaught runtime exception past `parsePdf`'s catch blocks, crashing the import pipeline.
(`ClaudeStatementApiClient.java:parseTransaction`)
