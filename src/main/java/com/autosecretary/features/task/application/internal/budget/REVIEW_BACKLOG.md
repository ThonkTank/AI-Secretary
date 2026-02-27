# Budget Integration Review Backlog

## Issues

### [consider] Single-implementation interface may be over-engineered
**File:** TaskBudgetEligibilityFromBudgetLookup.java:13 (implements TaskBudgetEligibilityService)
**Why:** `TaskBudgetEligibilityService` is an interface with only one implementation. Introducing an interface-implementation pair for a single strategy adds abstraction overhead.

**Observation:**
The interface is explicitly mentioned in CLAUDE.md as a "domain contract used by task scheduling," suggesting intentional design for future extensibility. However, by strict KISS principles, abstraction should be added only when needed (e.g., when a second implementation emerges).

**Trade-off:**
- Removing the interface would eliminate the layer and make task scheduling directly depend on the concrete implementation
- Keeping it enables swapping implementations later (calendar-based, AI-predicted eligibility, etc.)
- The cost of the abstraction is low (one interface, one implementation), so over-engineering is mild

**Status:** Deferred pending product decision. If eligibility strategies remain singular, consider removing the interface pattern when no longer needed.

