# Review Backlog — meal/domain/internal

## Resolved This Run

✅ **[improve]** MealAmountFormat.java:20-22 — Changed `rounded` from `double` to `long` to match the actual return type of `Math.round()`. Eliminates redundant cast to `int` and makes the code more idiomatic.

✅ **[simplify]** HouseholdEnergyService.java:81-84 — Inlined unnecessary intermediate variables (`weightTerm`, `heightTerm`, `ageTerm`) into return statement. Formula now expressed as single calculation, reducing from 5 lines to 1 line while maintaining clarity through documented Javadoc.

✅ **[simplify]** ShoppingListItem.java:65-70 — Refactored getFormattedExcess() to reuse MealAmountFormat.format() instead of duplicating the formatting logic. Also fixes floating-point robustness by replacing `excessAmount == (int) excessAmount` with the tolerance-based check in MealAmountFormat. Reduced from 4 lines to 2 lines.

---

## KISS Analysis Summary

**Scope**: /home/aaron/Schreibtisch/AutoSecretary/src/main/java/com/autosecretary/features/meal/domain/internal (and dependent domain callers)

**Result**: No remaining issues. All files exhibit clean KISS principles.

**Directory reviewed:** meal/domain/internal and dependent callers
**Files analyzed:** HouseholdEnergyService.java, MealAmountFormat.java, ShoppingListItem.java (caller), PantryItem.java (caller), WeeklyFoodTargetService.java (caller)

### HouseholdEnergyService.java
- **Complexity**: All methods are short (<15 lines), single-purpose, with clear intent
- **Null handling**: Consistent defensive checks at public method boundaries
- **Constants**: Well-named with clear documentation (MIFFLIN_*, DGE_*, gender intercepts)
- **Formula correctness**: Mifflin-St Jeor formula correctly implemented with proper coefficients
- **Design**: Stateless utility class with static methods — appropriate pattern
- **KISS verdict**: ✅ Clean, no improvements needed

### MealAmountFormat.java
- **Simplicity**: Single responsibility, minimal complexity
- **Logic clarity**: Clear intent — format whole numbers as integers, fractional values with 1 decimal
- **Javadoc**: Documents the formatting behavior and floating-point tolerance handling
- **Floating-point tolerance**: Uses 1e-9 (0.000000001) for near-equality checks — standard practice, used once in clear context
- **Type clarity**: Identified opportunity to use `long` for rounded value (actual return type of `Math.round()`) instead of `double` with cast to `int`
- **KISS verdict**: ✅ Clean, already optimized; caller duplicates this logic (see ShoppingListItem below)

### ShoppingListItem.java (caller analysis)
- **Concern**: getFormattedExcess() (lines 65-70) reimplements the same amount formatting logic as MealAmountFormat but:
  1. Uses less robust floating-point comparison: `excessAmount == (int) excessAmount` instead of tolerance-based check
  2. Forces code duplication rather than reusing the existing utility
- **Simplification**: Replace getFormattedExcess() logic to wrap MealAmountFormat.format() with German suffix, eliminating duplication

---
