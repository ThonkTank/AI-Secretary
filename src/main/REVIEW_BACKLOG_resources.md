# REVIEW_BACKLOG.md — Resource Values

## Open Issues

### [warning] Disabled color variants are brittle and manually calculated
**File:** task_colors.xml, lines 24–26 and 34–38
**What:** Disabled color variants (`task_color_primary_disabled_50`, `task_color_primary_disabled_8`, `task_color_primary_disabled_25`) are hard-coded hex values. The comment warns: "If task_color_primary changes, recalculate disabled color opacities."
**Why:** Error-prone. Developers may update the primary color and forget (or get wrong) the opacity recalculation. The manual hex arithmetic (0.08*255 = 0x14, etc.) is a maintenance burden.
**Deferred reason:** Requires design-system architectural change (color state lists or overlay approach). Low-risk as primary color is stable and the warning comment is present. Future refactor candidate if primary color changes frequently or dark mode is added.
