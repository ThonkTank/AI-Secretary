[warning] WeeklyFoodTarget.java:17-47 — 22 parallel primitive fields (11 `*Grams` + 11 `*Planned`) plus 4 switch-over-FoodGroup methods. Every new `FoodGroup` value requires updating 6 places (2 field declarations + 4 switch arms). Migrate to `EnumMap<FoodGroup, Integer>` for both targets and planned values — requires data-layer coordination to keep Room columns aligned.

[warning] CookingPreferences.java:15-24 — 8 parallel fields (4 `max*Cooking` int + 4 `*CookingDays Set<DayOfWeek>`) plus switch methods. Any new `MealType` requires 4-place updates. Migrate to `EnumMap<MealType, Integer>` and `EnumMap<MealType, Set<DayOfWeek>>`.

[nit] PantryItem.java:49 / ShoppingListItem.java:46 — identical `getFormattedAmount()` body: `if (amount == (int) amount) return (int) amount + " " + unit; return String.format("%.1f %s", amount, unit);`. Volatile duplication: if the format string changes (e.g. locale-aware), one copy will lag. Extract to a shared static utility.

