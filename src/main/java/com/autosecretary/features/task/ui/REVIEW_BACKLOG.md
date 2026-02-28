# Review Backlog — task/ui

## Open Issues

### [nit] Duplicated `TIME_FORMATTER` constant across three files in different packages

**Files:**
- `list/ListRowAdapter.java:44`
- `widget/TaskWidgetFactory.java:41`
- `edit/internal/editor/PrefSlotUIBuilder.java:36`

**What:** All three define `DateTimeFormatter.ofPattern("HH:mm")` independently.

**Why it matters:** If the time format ever changes (different pattern, locale, seconds),
all three copies need updating and a missed one silently produces inconsistent display.

**Fix:** Extract to a single constant in a shared location (e.g. `shared/UiFormatters.java`
or a utility accessible to all three). Deferred — creating a new file for one constant goes
against project conventions; format is stable today and all three packages are independent
enough that the coupling of a shared constant introduces its own risks.

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
