package com.autosecretary.features.meal.data.mapper;

/**
 * Stable legacy storage keys for reproducible imports from parser-era rows.
 */
public interface LegacyMealFieldKeys {
    interface MealPlan {
        String ID = "id";
        String DATE = "date";
        String MEAL_TYPE = "meal_type";
        String RECIPE_ID = "recipe_id";
        String PLANNED_SERVINGS = "planned_servings";
        String IS_COMPLETED = "is_completed";
        String ACTUAL_SERVINGS = "actual_servings";
        String COMPLETED_AT = "completed_at";
        String ITEM_ID = "item_id";
        String RECIPE_TITLE = "recipe_title";
        String ESTIMATED_CALORIES = "estimated_calories";
    }

    interface Recipe {
        String ID = "id";
        String TITLE = "title";
        String DESCRIPTION = "description";
        String INSTRUCTIONS = "instructions";
        String MEAL_TYPES = "meal_types";
        String PREP_TIME_MINUTES = "prep_time_minutes";
        String COOK_TIME_MINUTES = "cook_time_minutes";
        String SERVINGS = "servings";
        String MIN_SERVINGS = "min_servings";
        String MAX_SERVINGS = "max_servings";
        String SCALING_PRECISION = "scaling_precision";
        String PREP_EFFORT = "prep_effort";
        String INGREDIENTS_DATA = "ingredients_data";
        String TAGS = "tags";
        String LAST_USED = "last_used";
        String USAGE_COUNT = "usage_count";
        String IS_FAVORITE = "is_favorite";
        String TOTAL_CALORIES = "total_calories";
        String TOTAL_PROTEIN = "total_protein";
        String TOTAL_CARBS = "total_carbs";
        String TOTAL_FAT = "total_fat";
        String SHELF_LIFE_DAYS = "shelf_life_days";
        String RATINGS_DATA = "ratings_data";
    }

    interface Consumption {
        String ID = "id";
        String DATE = "date";
        String ITEM_ID = "item_id";
        String MEMBER_ID = "member_id";
        String RECIPE_ID = "recipe_id";
        String SERVINGS_CONSUMED = "servings_consumed";
        String CALORIES = "calories";
        String PROTEIN = "protein";
        String CARBS = "carbs";
        String FAT = "fat";
    }

}
