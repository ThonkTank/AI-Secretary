# listmodel/ — REVIEW_BACKLOG

## [consider] calendarEvent() factory fills 16 null/zero/false placeholder args
**File:** TaskListItem.java:96-119
**Why:** The static factory `calendarEvent()` constructs a `TaskListItem` using the full 22-parameter constructor, passing 16 null/zero/false placeholders for fields irrelevant to calendar events.
**Simpler fix (if pursued):** Add a private constructor with only calendar-event-relevant parameters. Tradeoff: adds a second constructor.
**Verdict:** Defer. The problem is contained inside the factory; callers are unaffected.
