# Review Backlog — meal/domain

## Open Issues

[simplify] WeeklyFoodTarget.java:17-40 — 22 parallel primitive fields (11 `*Grams` + 11 `*Planned`) bridged by 4 switch-over-FoodGroup dispatch methods (getTargetFor, getPlannedFor, addPlanned, setTargetFor). Every new `FoodGroup` value requires updating 6 sites. Simpler alternative: replace the 22 fields with two `EnumMap<FoodGroup, Integer>` fields (`targets`, `planned`) and remove all switch methods entirely — all dispatch becomes a trivial map lookup. Blocker: Room persists each primitive field as its own column; migrating requires a schema change. Defer until a data-layer migration is feasible.

---

[simplify] CookingPreferences.java:15-22 — 8 parallel fields (4 `max*Cooking` int + 4 `*CookingDays Set<DayOfWeek>`) dispatched through `getMaxCookingPerWeek` and `getAllowedCookingDays` switch methods. Same pattern as WeeklyFoodTarget. Simpler alternative: two `EnumMap<MealType, …>` fields. Same Room persistence blocker — defer alongside WeeklyFoodTarget.

---

[consider] PantryItem.java:48 / ShoppingListItem.java:46 — identical `getFormattedAmount()` body in two unrelated domain classes. Volatile duplication: if the format string changes, one copy will lag. Simpler alternative: a one-method `MealAmountFormat` static helper class in this package. Tradeoff: new file for 2 lines of logic; methods appear unused by current callers, so divergence risk is low. Defer until both methods are actively used in UI.

---

[drift] ShoppingPackagingService.java:22-37 — `createShoppingItem()` mixes two responsibilities: packaging arithmetic (already cleanly isolated in `roundToPackage()`) and `ShoppingListItem` domain-object construction. Additionally, `PackagingResult.roundedAmount()` and `PackagingResult.packageCount()` computed in `roundToPackage()` are unused by `createShoppingItem()` — the rounded amount is re-derived indirectly via the Builder's `excess()` setter (`neededAmount + excessAmount`). Low urgency; no callers currently need `packageCount` on `ShoppingListItem`.

---

[drift] PantryItem.java:51-57 — `getExpiryInfo()` returns hardcoded German UI strings ("Abgelaufen", "Heute", "Morgen", "In X Tagen") directly from a domain object. Presentation-formatted strings belong in the UI layer. Also, `isExpired()` and `getDaysUntilExpiry()` call `LocalDate.now()` internally, making the domain object non-deterministic. Fix: when the meal UI is built, handle formatting in a UI-layer mapper and pass the reference date as a parameter. Deferred — no meal UI exists yet.

---

[drift] PantryItem.StorageLocation enum — `label` and `icon` (emoji) fields are UI presentation data embedded in a domain enum. Fix: move display metadata to the UI layer. Deferred — no meal UI exists yet.
