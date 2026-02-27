# Review Backlog — budget/data/dao

## Open Issues

### [consider] `BudgetLimitDao`: four unused methods — `BudgetLimitDao.java:17,55,57,61`
`getLimitsForMonth`, `update(BudgetLimit)`, `delete(BudgetLimit)`, and
`deleteByCategoryAndMonth` have no callers. CLAUDE.md acknowledges that `BudgetLimitDao`
limit-based tracking is "not fully surfaced in UI", so these may be intentional scaffolding.

**Simpler alternative:** Remove all four. When budget-limit editing is implemented in UI the
needed methods can be added at that point.

**Tradeoff:** If the UI work is imminent, keeping the plumbing avoids a future re-add. Given
the explicit "Not Yet Implemented" note in CLAUDE.md, deferring this deletion is reasonable.
Mark for removal when the feature is actually built or confirmed dropped.
