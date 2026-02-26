package com.autosecretary.features.meal.data.mapper;

/**
 * Stable legacy storage keys for reproducible imports from parser-era rows.
 */
public final class LegacyMealFieldKeys {
    private LegacyMealFieldKeys() {
    }

    public static final class MealPlan {
        private MealPlan() {
        }

        public static final String ID = "id";
        public static final String DATE = "date";
        public static final String MEAL_TYPE = "meal_type";
        public static final String RECIPE_ID = "recipe_id";
        public static final String PLANNED_SERVINGS = "planned_servings";
        public static final String IS_COMPLETED = "is_completed";
        public static final String ACTUAL_SERVINGS = "actual_servings";
        public static final String COMPLETED_AT = "completed_at";
        public static final String ITEM_ID = "item_id";
        public static final String RECIPE_TITLE = "recipe_title";
        public static final String ESTIMATED_CALORIES = "estimated_calories";
    }

    public static final class Recipe {
        private Recipe() {
        }

        public static final String ID = "id";
        public static final String TITLE = "title";
        public static final String DESCRIPTION = "description";
        public static final String INSTRUCTIONS = "instructions";
        public static final String MEAL_TYPES = "meal_types";
        public static final String PREP_TIME_MINUTES = "prep_time_minutes";
        public static final String COOK_TIME_MINUTES = "cook_time_minutes";
        public static final String SERVINGS = "servings";
        public static final String MIN_SERVINGS = "min_servings";
        public static final String MAX_SERVINGS = "max_servings";
        public static final String SCALING_PRECISION = "scaling_precision";
        public static final String PREP_EFFORT = "prep_effort";
        public static final String INGREDIENTS_DATA = "ingredients_data";
        public static final String TAGS = "tags";
        public static final String LAST_USED = "last_used";
        public static final String USAGE_COUNT = "usage_count";
        public static final String IS_FAVORITE = "is_favorite";
        public static final String TOTAL_CALORIES = "total_calories";
        public static final String TOTAL_PROTEIN = "total_protein";
        public static final String TOTAL_CARBS = "total_carbs";
        public static final String TOTAL_FAT = "total_fat";
        public static final String SHELF_LIFE_DAYS = "shelf_life_days";
        public static final String RATINGS_DATA = "ratings_data";
    }

    public static final class Consumption {
        private Consumption() {
        }

        public static final String ID = "id";
        public static final String DATE = "date";
        public static final String ITEM_ID = "item_id";
        public static final String MEMBER_ID = "member_id";
        public static final String RECIPE_ID = "recipe_id";
        public static final String SERVINGS_CONSUMED = "servings_consumed";
        public static final String CALORIES = "calories";
        public static final String PROTEIN = "protein";
        public static final String CARBS = "carbs";
        public static final String FAT = "fat";
    }

    public static final class Item {
        private Item() {
        }

        public static final String MEAL_TYPE = "meal_type";
        public static final String PLANNED_MEALS = "planned_meals";
    }
}
