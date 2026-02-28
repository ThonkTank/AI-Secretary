# meal/domain/internal — Internal Domain Utilities

This sub-package contains domain-layer helpers that are **not part of the meal feature's
public API**. They are called only from within `meal/domain/` (and its own sub-packages);
nothing outside `meal/domain/` should import from here directly.

## `HouseholdEnergyService`

Stateless calculator for individual energy requirements. Provides three static methods:

| Method | Returns |
|---|---|
| `calculateAge(member, date)` | Age in complete years as of `date` |
| `calculateBmr(member, date)` | Basal Metabolic Rate in kcal/day (Mifflin-St Jeor formula) |
| `calculateTdee(member, date)` | Total Daily Energy Expenditure (BMR × activity factor) |
| `calculateDgeFoodFactor(member, date)` | Ratio of personal TDEE to the DGE 2000 kcal baseline |

**When to use:** `WeeklyFoodTargetService` uses this to scale DGE food group portions for each
active household member. The `referenceDate` parameter keeps calculations deterministic —
pass a fixed date rather than `LocalDate.now()` when reproducibility matters.

**Public references:**
- Mifflin-St Jeor formula: [Wikipedia — Basal metabolic rate](https://en.wikipedia.org/wiki/Basal_metabolic_rate)
- DGE reference values: [dge.de](https://www.dge.de)

## `MealAmountFormat`

Stateless formatter for amount + unit strings (e.g., `"500 g"`, `"1.5 kg"`).
Used by both `PantryItem.getFormattedAmount()` and `ShoppingListItem.getFormattedAmount()`.

**Rule:** whole-number amounts display without a decimal point; fractional amounts display
with exactly one decimal place. See the method Javadoc for the known floating-point edge case.
