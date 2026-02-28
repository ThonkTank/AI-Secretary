# Review Backlog — meal/application/internal

## Resolved Issues (This Run)

✅ **[improve]** Condensed try-catch blocks reduce clarity — LegacyMealImportService.java:527, 538, 544
- Expanded single-line try-catch in `asLong(Object)` to multi-line format (lines 527-530)
- Expanded single-line try-catch in `asInt(Object, int)` to multi-line format (lines 538-543)
- Expanded single-line try-catch in `asDouble(Object, double)` to multi-line format (lines 544-549)
- Improves visual consistency with existing multi-line try-catch blocks (lines 498-502, 509-513)

✅ **[nit]** Short variable names obscure intent — LegacyMealImportService.java:365, 390
- Renamed `p` → `prefs` in `importPreferences()`
- Renamed `t` → `target` in `importWeeklyTargets()`

✅ **[nit]** Magic defaults for height and weight — LegacyMealImportService.java:352-353
- Extracted `DEFAULT_HEIGHT_CM = 170` and `DEFAULT_WEIGHT_KG = 70` as class-level constants with comments

✅ **[warning]** Fragile uninitialized field fallbacks — LegacyMealImportService.java:367-375
- Replaced uninitialized field fallbacks with explicit `0` defaults in `importPreferences()`
- Added clarifying comment: "Use explicit 0 defaults for numeric fields (not implicit uninitialized values)"

---

## Open Issues

### [consider] Long methods with repetitive field assignments — LegacyMealImportService.java:201-236, 386-423
**What:** Two methods exceed 30 lines with many repetitive field assignments:
- `importRecipes()` (lines 201-236, 35 lines): 20+ `recipe.x = asType(...)` assignments
- `importWeeklyTargets()` (lines 386-423, 37 lines): 22 assignments in parallel pairs

**Why it matters:** Long methods are harder to scan. Repetitive patterns can invite copy-paste bugs.

**Analysis:** Extraction attempted but rejected:
- `importRecipes()` assigns fields with different types (asInt, asString, asEnum, etc.) and different defaults—no uniform pattern
- `importWeeklyTargets()` has parallel structure (actual + planned values), but extracting a helper would require reflection, varargs, or map-based approaches
- All extraction attempts increase complexity (type-safety loss, readability harm) without meaningful LOC reduction

**Verdict:** Keep as-is. Current code is clear and maintainable for a data migration service. The repetition is intentional and safe (not error-prone).

---

## Resolved Issues (Completed in previous runs)

✅ **[blocker]** No README.md — Added comprehensive module documentation.

✅ **[friction]** LegacyMealImportService javadoc — Enhanced with usage examples, error semantics, and data structure documentation.

✅ **[nit]** EntityLookupHelper.java Javadocs — Translated to English, improved clarity.

✅ **[warning]** memberId <= 0 validation — Fixed to allow 0 as a valid "unassigned" sentinel.

