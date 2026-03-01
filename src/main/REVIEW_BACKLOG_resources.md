# REVIEW_BACKLOG.md — Resource Values and Layouts

## Open Issues

### [warning] Repeated label+value summary row pattern in budget_overview_fragment.xml
**File:** layout/budget_overview_fragment.xml:151–241
**What:** Income/Expense/Free Budget/Net rows all repeat the same two-column `LinearLayout`
(label TextView weight=1 + value TextView) four times with near-identical XML.
**Why:** If row style changes all four copies must be updated. The Net row differs only in
textAppearance (Subtitle1 vs Body1), making a pure `<include>` impractical without overrides.
**Deferred reason:** Would require a new include file. App is feature-complete; stable duplication.

---

### [warning] FixedSchedulingContainer is unreachable UI in task_editor_dialog.xml
**File:** layout/task_editor_dialog.xml:142–206
**What:** Container is `visibility="gone"` with comment noting TERMIN scheduling is "not yet
exposed." 65 lines of XML for fields (EditFixedDate/Start/End/Duration) never shown to users.
**Why:** Dead UI accumulates — if TERMIN scheduling stays unimplemented, this is ballast.
**Deferred reason:** Need to verify TaskEditFormInputReader/TaskEditSectionBinder don't bind
these IDs before deleting. Requires reading Java files outside the layout scope.

---

### [warning] Label+Spinner pattern duplicated 7+ times across dialog files
**Files:** layout/budget_add_transaction_dialog.xml:43–67, budget_transfer_dialog.xml:9–32,
budget_edit_limit_dialog.xml:12–22, meal_plan_create_dialog.xml:14–22 and 37–48,
meal_pantry_create_dialog.xml:63–75
**What:** `TextView Caption` + `Spinner layout_marginTop=spacing_xs` repeated 7+ times.
**Why:** Style/margin change requires updating many files.
**Deferred reason:** Stable; app is feature-complete. Low risk of divergence.

---

### [nit] ScrollView uses wrap_content height in budget_recurring_suggestions_dialog.xml
**File:** layout/budget_recurring_suggestions_dialog.xml:19–30
**What:** Inner `ScrollView` has `layout_height="wrap_content"`. Android dialog clipping
should handle it in practice, but behaviour is device-dependent.
**Why:** Large suggestion lists may appear cut off rather than scrollable on some devices.
**Deferred reason:** No reports of this failing; fix requires design decision on max height.

---

### [nit] Repeated min/max range row duplicated twice in task_editor_dialog.xml
**File:** layout/task_editor_dialog.xml (Duration section and Per-Rep section)
**What:** Horizontal `min | — | max` TextInputLayout pair appears twice. Can't share via
`<include>` because field IDs differ between the two uses (EditMinDuration vs EditMinPerRep).
**Why:** A copy-paste bug (wrong ID, wrong hint) would be invisible until runtime.
**Deferred reason:** Stable; two uses. Accept and maintain with cross-referencing comments.

---

### [warning] Disabled color variants are brittle and manually calculated
**File:** task_colors.xml, lines 24–26 and 34–38
**What:** Disabled color variants (`task_color_primary_disabled_50`, `task_color_primary_disabled_8`, `task_color_primary_disabled_25`) are hard-coded hex values. The comment warns: "If task_color_primary changes, recalculate disabled color opacities."
**Why:** Error-prone. Developers may update the primary color and forget (or get wrong) the opacity recalculation. The manual hex arithmetic (0.08*255 = 0x14, etc.) is a maintenance burden.
**Deferred reason:** Requires design-system architectural change (color state lists or overlay approach). Low-risk as primary color is stable and the warning comment is present. Future refactor candidate if primary color changes frequently or dark mode is added.
