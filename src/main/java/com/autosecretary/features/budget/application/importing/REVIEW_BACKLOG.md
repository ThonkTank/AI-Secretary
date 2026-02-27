# Review Backlog — budget/application/importing

## Open Issues

### [warning] ClaudeStatementApiClient.java:43–73 — `parsePdf` mixes HTTP setup, request building, response reading, and parsing
One 30-line method handles the entire HTTP lifecycle. Extract `sendRequest(JSONObject body)`
returning raw response to make the HTTP layer swappable and the method easier to test.

