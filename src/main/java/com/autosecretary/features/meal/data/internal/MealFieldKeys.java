package com.autosecretary.features.meal.data.internal;

/**
 * Field-name constants for the meal feature's untyped {@code Map<String, Object>} storage rows.
 *
 * <p>Each key in these interfaces is a string that maps to a field slot in a storage row.
 * When a {@link com.autosecretary.features.meal.data.internal.mapper.RowMapper} writes a row via
 * {@code row.put(MealFieldKeys.Recipe.TITLE, recipe.title)}, and later reads it via
 * {@code row.get(MealFieldKeys.Recipe.TITLE)}, these constants ensure the same string is
 * used in both directions.
 *
 * <h3>Organisation: nested interfaces per entity</h3>
 * <p>Keys are grouped into nested interfaces — one per entity type (e.g., {@code Recipe},
 * {@code Ingredient}) — so that accessing a key always requires the entity name as a qualifier:
 * {@code MealFieldKeys.Recipe.TITLE} instead of a global {@code RECIPE_TITLE}. This prevents
 * accidental cross-entity key reuse and makes IDE autocomplete entity-scoped.
 *
 * <h3>Top-level {@code PERIOD_KEY}</h3>
 * <p>{@code PERIOD_KEY} lives outside all nested interfaces because it is shared by two entity
 * types: {@code ShoppingListItem} and {@code WeeklyFoodTarget}. Placing it here (at the outer
 * level) avoids duplicating the constant in two nested interfaces and making it clear that it
 * is a cross-entity key. The value is a {@link java.time.LocalDate#toString()} date string
 * (ISO-8601, e.g. {@code "2026-02-28"}) that identifies which week the record belongs to.
 *
 * <p>When adding a new entity type, add a corresponding nested interface here and a collection
 * constant in {@link MealCollections}.
 */
public final class MealFieldKeys {
    // Shared row-id key: all entities use "id" as their storage-level id field.
    // Referenced by InMemoryMealStorage to inject the canonical id into every stored row.
    public static final String ROW_ID = "id";

    // Shared across ShoppingListItem and WeeklyFoodTarget — kept at top level to avoid duplication.
    public static final String PERIOD_KEY = "periodKey";

    private MealFieldKeys() {
    }

    public interface HouseholdMember {
        String ID = "id";
        String NAME = "name";
        String BIRTH_YEAR = "birthYear";
        String GENDER = "gender";
        String WEIGHT_KG = "weightKg";
        String HEIGHT_CM = "heightCm";
        String TARGET_WEIGHT_KG = "targetWeightKg";
        String ACTIVITY_LEVEL = "activityLevel";
        String IS_ACTIVE = "isActive";
    }

    public interface Ingredient {
        String ID = "id";
        String NAME = "name";
        String FOOD_GROUP = "foodGroup";
        String DEFAULT_UNIT = "defaultUnit";
        String GRAMS_PER_UNIT = "gramsPerUnit";
        String CALORIES_PER_100 = "caloriesPer100";
        String PROTEIN_PER_100 = "proteinPer100";
        String CARBS_PER_100 = "carbsPer100";
        String FAT_PER_100 = "fatPer100";
        String FIBER_PER_100 = "fiberPer100";
        String SHELF_LIFE_DAYS = "shelfLifeDays";
        String REQUIRES_REFRIGERATION = "requiresRefrigeration";
        String IS_WHOLE_UNIT = "isWholeUnit";
        String IS_PERISHABLE = "isPerishable";
        String STORE_PACKAGES = "storePackages";
    }

    public interface PantryItem {
        String ID = "id";
        String INGREDIENT_ID = "ingredientId";
        String INGREDIENT_NAME = "ingredientName";
        String AMOUNT = "amount";
        String UNIT = "unit";
        String PURCHASE_DATE = "purchaseDate";
        String EXPIRY_DATE = "expiryDate";
        String LOCATION = "location";
    }

    public interface ShoppingListItem {
        // periodKey is shared with WeeklyFoodTarget — use MealFieldKeys.PERIOD_KEY.
        String ID = "id";
        String INGREDIENT_ID = "ingredientId";
        String INGREDIENT_NAME = "ingredientName";
        String AMOUNT = "amount";
        String NEEDED_AMOUNT = "neededAmount";
        String EXCESS_AMOUNT = "excessAmount";
        String UNIT = "unit";
        String FOOD_GROUP_LABEL = "foodGroupLabel";
        String SUGGESTED_STORE = "suggestedStore";
        String IS_PURCHASED = "isPurchased";
        String ESTIMATED_PRICE_CENTS = "estimatedPriceCents";
    }

    public interface CookingPreferences {
        String ID = "id";
        String MAX_BREAKFAST_COOKING = "maxBreakfastCooking";
        String MAX_LUNCH_COOKING = "maxLunchCooking";
        String MAX_DINNER_COOKING = "maxDinnerCooking";
        String MAX_SNACK_COOKING = "maxSnackCooking";
        String BREAKFAST_COOKING_DAYS = "breakfastCookingDays";
        String LUNCH_COOKING_DAYS = "lunchCookingDays";
        String DINNER_COOKING_DAYS = "dinnerCookingDays";
        String SNACK_COOKING_DAYS = "snackCookingDays";
        String QUICK_PREP_MAX_MINUTES = "quickPrepMaxMinutes";
    }

    public interface WeeklyFoodTarget {
        // periodKey is shared with ShoppingListItem — use MealFieldKeys.PERIOD_KEY.
        String ID = "id";
        String GRAIN_GRAMS = "grainGrams";
        String POTATO_GRAMS = "potatoGrams";
        String VEGETABLE_GRAMS = "vegetableGrams";
        String FRUIT_GRAMS = "fruitGrams";
        String DAIRY_GRAMS = "dairyGrams";
        String MEAT_GRAMS = "meatGrams";
        String FISH_GRAMS = "fishGrams";
        String EGG_GRAMS = "eggGrams";
        String FAT_GRAMS = "fatGrams";
        String LEGUME_GRAMS = "legumeGrams";
        String NUT_GRAMS = "nutGrams";
        String GRAIN_PLANNED = "grainPlanned";
        String POTATO_PLANNED = "potatoPlanned";
        String VEGETABLE_PLANNED = "vegetablePlanned";
        String FRUIT_PLANNED = "fruitPlanned";
        String DAIRY_PLANNED = "dairyPlanned";
        String MEAT_PLANNED = "meatPlanned";
        String FISH_PLANNED = "fishPlanned";
        String EGG_PLANNED = "eggPlanned";
        String FAT_PLANNED = "fatPlanned";
        String LEGUME_PLANNED = "legumePlanned";
        String NUT_PLANNED = "nutPlanned";
    }

    public interface Recipe {
        String ID = "id";
        String TITLE = "title";
        String DESCRIPTION = "description";
        String INSTRUCTIONS = "instructions";
        String MEAL_TYPES = "mealTypes";
        String PREP_TIME_MINUTES = "prepTimeMinutes";
        String COOK_TIME_MINUTES = "cookTimeMinutes";
        String SERVINGS = "servings";
        String MIN_SERVINGS = "minServings";
        String MAX_SERVINGS = "maxServings";
        String SCALING_PRECISION = "scalingPrecision";
        String PREP_EFFORT = "prepEffort";
        String INGREDIENTS = "ingredients";
        String TAGS = "tags";
        String LAST_USED = "lastUsed";
        String USAGE_COUNT = "usageCount";
        String IS_FAVORITE = "isFavorite";
        String TOTAL_CALORIES = "totalCalories";
        String TOTAL_PROTEIN = "totalProtein";
        String TOTAL_CARBS = "totalCarbs";
        String TOTAL_FAT = "totalFat";
        String SHELF_LIFE_DAYS = "shelfLifeDays";
        String RATINGS = "ratings";
    }

    public interface MealPlan {
        String ID = "id";
        String DATE = "date";
        String MEAL_TYPE = "mealType";
        String RECIPE_ID = "recipeId";
        String PLANNED_SERVINGS = "plannedServings";
        String IS_COMPLETED = "isCompleted";
        String ACTUAL_SERVINGS = "actualServings";
        String COMPLETED_AT = "completedAt";
        String ITEM_ID = "itemId";
        String RECIPE_TITLE = "recipeTitle";
        String ESTIMATED_CALORIES = "estimatedCalories";
    }

    public interface ConsumptionLog {
        String ID = "id";
        String DATE = "date";
        String ITEM_ID = "itemId";
        String MEMBER_ID = "memberId";
        String RECIPE_ID = "recipeId";
        String SERVINGS_CONSUMED = "servingsConsumed";
        String CALORIES = "calories";
        String PROTEIN = "protein";
        String CARBS = "carbs";
        String FAT = "fat";
    }
}
