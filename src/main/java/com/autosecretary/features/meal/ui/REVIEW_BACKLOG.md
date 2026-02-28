# Review Backlog — meal/ui

## Completed in this review cycle

✅ **[critical] Input validation missing in dialog handlers** — FIXED
- Added safe parsing methods: `safeParse()`, `safeParseInt()`, `safeParseDouble()`
- Wrapped all input parsing in try-catch with user-friendly Toast error messages
- Added validation for empty fields before parsing
- Updated all three dialog handlers: `showPlanDialog()`, `showNeedDialog()`, `showPantryDialog()`

✅ **[nit] Vague variable names** — FIXED
- Renamed `View row` → `planRow` in renderMealPlans()
- Renamed `Button row` → `recipeButton` in renderRecipes()
- Renamed `TextView row` → `pantryRow` and `shoppingRow` in renderStock()
- Renamed `View content` → `planDialogContent`, `needDialogContent`, `pantryDialogContent` in all three dialogs
- Added more descriptive field names: `recipeSpinner`, `dateField`, `nameField`, `amountField`, etc.

✅ **[nit] No null checks on presenter calls** — PARTIALLY FIXED
- Added null/empty check in showPlanDialog() before building recipe spinner
- Shows user-friendly error message if no recipes are available

---

## Open Issues

*(none)*

## Fixed This Run

✅ **[warning] MealPlannerFragment.java — inconsistent parse error conventions + duplicate validation** — FIXED
- Changed `safeParseInt` and `safeParseDouble` from returning `-1` on error to returning `null` (boxed `Integer`/`Double`), consistent with `safeParse` returning `null`.
- Extracted `requireNonEmpty(EditText, String)` helper to eliminate 6 duplicate "check empty → toast → abort" blocks across the 3 dialog handlers.
- All callers now use `== null || <= 0` / `== null || < 0` checks consistently.

✅ **[nit] MealPlannerFragment.java:337 — auto-unboxing nullable `Recipe.id` to `long`** — FIXED
Added null guard: `Long recipeId = recipes.get(recipeIndex).id; if (recipeId == null) return;` before passing to `planRecipe()`. Eliminates the theoretical NPE from auto-unboxing a null `Long`.

---

## Deferred — Not Worth Fixing

### [warning] Duplicate dialog creation pattern across 3 methods
- **Reason**: The pattern is stable and self-documenting. Extracting a generic dialog helper would introduce unnecessary abstraction for code that is unlikely to change. The app is feature-complete and not expected to grow. The duplicate validation within the dialogs has been addressed by extracting `requireNonEmpty()` and fixing inconsistent parse return types.

### [warning] Duplication in renderStock() — pantry and shopping loops
- **Reason**: The duplication is small and acceptable. Both loops follow identical structure but operate on different data and containers. Extracting a helper would not significantly reduce complexity. The pattern is clear as-is.

### [warning] Feature envy: string formatting in renderMealPlans() and buildRecipeDetails()
- **Reason**: The formatting logic is simple, stable, and tightly coupled to display. Moving it to a separate formatter class would be over-engineering for stable display rules. Prefer to keep presentation logic in the presentation layer.

### [warning] No error handling for presenter calls
- **Reason**: Requires API change to presenter (return status or throw exceptions). Out of scope for this review without broader refactoring.

### [nit] Magic strings not in string resources
- **Reason**: Bullet separator " · " and newline "\n\n" are presentation artifacts, not user-facing labels. Moving to strings.xml would create false localization overhead for structural formatting. The German label "Portionen" could be moved but the benefit is minimal for stable code.

---

## Acknowledged Good Patterns

[keep] Fragment-based UI architecture — Consistent with task feature. Familiar to Android developers.

[keep] Presenter pattern for business logic separation — Clear boundary between UI and domain.

[keep] Three-tab visibility toggle pattern — Preserves scroll/state on tab switch; prefer to fragment replacement.

[keep] Dialog pattern documentation — Clear javadoc on showPlanDialog explains pattern; other dialogs reference it.

