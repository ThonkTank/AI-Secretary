# Review Backlog — budget/application

## Open Issues

[warning] importing/ClaudeStatementApiClient.java:227–239 — `extractJsonFromMarkdown` only strips one leading and one trailing fence. Does not handle missing trailing fence, multiple fenced blocks, or indented content. Use `lastIndexOf` for trailing fence; add a debug log of `text` on `JSONException`.

[warning] importing/ClaudeStatementApiClient.java:43–73 — `parsePdf` mixes HTTP setup, request building, response reading, and parsing in one 30-line method. Extract `sendRequest(JSONObject body)` returning raw response to make the HTTP layer swappable.

[nit] importing/ClaudeStatementApiClient.java:191–204 — `readResponseBody` appends trailing `\n` to every line including the last, creating invisible trailing newline in error messages.

[nit] BudgetSeedService.java:55-84 — Demo data uses string-literal category names ("Gehalt", "Miete") for lookup that must match insertions at lines 55-61 — silent coupling with no compile-time check. Refactor to use the inserted entity references directly or a lookup map keyed by the same constants.
