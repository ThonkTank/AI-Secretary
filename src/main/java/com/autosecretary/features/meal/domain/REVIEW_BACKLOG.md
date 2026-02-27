# Review Backlog — meal/domain

## Open Issues

[simplify] WeeklyFoodTarget.java:17-40 — 22 parallel primitive fields (11 `*Grams` + 11 `*Planned`) bridged by 4 switch-over-FoodGroup dispatch methods (getTargetFor, getPlannedFor, addPlanned, setTargetFor). Every new `FoodGroup` value requires updating 6 sites. Simpler alternative: replace the 22 fields with two `EnumMap<FoodGroup, Integer>` fields (`targets`, `planned`) and remove all switch methods entirely — all dispatch becomes a trivial map lookup. Blocker: Room persists each primitive field as its own column; migrating requires a schema change. Defer until a data-layer migration is feasible.

---

[simplify] CookingPreferences.java:15-22 — 8 parallel fields (4 `max*Cooking` int + 4 `*CookingDays Set<DayOfWeek>`) dispatched through `getMaxCookingPerWeek` and `getAllowedCookingDays` switch methods. Same pattern as WeeklyFoodTarget. Simpler alternative: two `EnumMap<MealType, …>` fields. Same Room persistence blocker — defer alongside WeeklyFoodTarget.

---

[drift] ShoppingPackagingService.java:22-37 — `createShoppingItem()` mixes two responsibilities: packaging arithmetic (already cleanly isolated in `roundToPackage()`) and `ShoppingListItem` domain-object construction. Additionally, `PackagingResult.roundedAmount()` and `PackagingResult.packageCount()` computed in `roundToPackage()` are unused by `createShoppingItem()` — the rounded amount is re-derived indirectly via the Builder's `excess()` setter (`neededAmount + excessAmount`). Low urgency; no callers currently need `packageCount` on `ShoppingListItem`.

---

[drift] PantryItem.java:51-57 — `getExpiryInfo()` returns hardcoded German UI strings ("Abgelaufen", "Heute", "Morgen", "In X Tagen") directly from a domain object. Presentation-formatted strings belong in the UI layer. Also, `isExpired()` and `getDaysUntilExpiry()` call `LocalDate.now()` internally, making the domain object non-deterministic. Fix: when the meal UI is built, handle formatting in a UI-layer mapper and pass the reference date as a parameter. Deferred — no meal UI exists yet.

---

[drift] PantryItem.StorageLocation enum — `label` and `icon` (emoji) fields are UI presentation data embedded in a domain enum. Fix: move display metadata to the UI layer. Deferred — no meal UI exists yet.

---

[nit] ShoppingListItem.java:51-56 — `getFormattedExcess()` returns a hardcoded German UI string from a domain object. Same smell as `PantryItem.getExpiryInfo()`. Fix: when meal UI is built, move formatting to a UI-layer mapper. Deferred — no meal UI exists yet and the method appears unused.

---

[drift] Ingredient.FoodGroup enum — `label` (German UI strings) and `icon` (emoji) fields are UI presentation data embedded in a domain enum, same pattern as `PantryItem.StorageLocation`. `FoodGroup` is referenced across the entire meal domain. Fix: when meal UI is built, move `label` and `icon` to a UI-layer display helper. Deferred — no meal UI exists yet.

---

[nit] Recipe.Builder.ingredient(long, String, double, String) — 4 positional params with abbreviated names (`id`, `name`) that don't match the `RecipeIngredient` record component names (`ingredientId`, `ingredientName`). Swapping adjacent same-type args is compile-silent.
**Fix suggestion:** Rename params to match record, or accept a `RecipeIngredient` directly.
