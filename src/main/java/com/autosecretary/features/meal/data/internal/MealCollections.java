package com.autosecretary.features.meal.data.internal;

/**
 * Named collection constants for the meal feature's {@link com.autosecretary.features.meal.data.internal.storage.MealStorage}.
 *
 * <p>In {@link com.autosecretary.features.meal.data.internal.storage.MealStorage}, a "collection"
 * is a named namespace (not a Java Collection) that groups rows of the same entity type.
 * Think of it like a table name in a relational database — each constant here is the key
 * under which one entity type's rows are stored in the underlying map-of-maps structure:
 * <pre>
 *   storage: { "recipes" -&gt; { id -&gt; row }, "ingredients" -&gt; { id -&gt; row }, ... }
 * </pre>
 *
 * <p>Each {@link com.autosecretary.features.meal.data.internal.BaseCollectionDao} is bound to
 * exactly one collection by its first constructor parameter.
 *
 * <p>When adding a new meal entity type, add a new constant here alongside the
 * corresponding entry in {@link MealFieldKeys}.
 */
public final class MealCollections {
    public static final String RECIPES = "recipes";
    public static final String INGREDIENTS = "ingredients";
    public static final String MEAL_PLANS = "meal_plans";
    public static final String PANTRY_ITEMS = "pantry_items";
    public static final String SHOPPING_LIST_ITEMS = "shopping_list_items";
    public static final String CONSUMPTION_LOGS = "consumption_logs";
    public static final String HOUSEHOLD_MEMBERS = "household_members";
    public static final String COOKING_PREFERENCES = "cooking_preferences";
    public static final String WEEKLY_FOOD_TARGETS = "weekly_food_targets";

    private MealCollections() {
    }
}
