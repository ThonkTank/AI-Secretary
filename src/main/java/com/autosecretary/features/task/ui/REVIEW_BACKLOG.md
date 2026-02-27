# Review Backlog — task/ui

## Open Issues

*(none)*

---

## Structural Notes

### [keep] `TaskScheduleConfigDialog` at `ui/` root — single file, intentional placement

**Path:** `TaskScheduleConfigDialog.java`

**Observation:** `TaskScheduleConfigDialog` is the only `.java` file at the `ui/` root level. It is
currently called only from `TaskListFragment`. This could suggest it belongs in `list/` instead.

**Why keep here:** Schedule configuration affects the entire task scheduling system, not just the
list view. The `ui/` root README explicitly documents this level as the home for "cross-surface UI
elements shared across multiple task surfaces." Keeping it here makes the correct target location
obvious if the widget, settings screen, or a future surface also needs to launch this dialog.
Moving it into `list/` would encode a false dependency and require a second move later.

**No action needed.** The thin package is a temporary state, not a structural problem.
