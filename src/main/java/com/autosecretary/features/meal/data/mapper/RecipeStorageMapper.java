package com.autosecretary.features.meal.data.mapper;

import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.Recipe;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecipeStorageMapper {
    private RecipeStorageMapper() {
    }

    @SuppressWarnings("unchecked")
    public static Recipe fromStorageRow(Map<String, Object> row) {
        Recipe recipe = new Recipe();
        recipe.id = LegacyMapperSupport.asNullableLong(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.ID, null));
        recipe.title = (String) LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.TITLE, "title");
        recipe.description = (String) LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.DESCRIPTION, "description");
        recipe.instructions = (String) LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.INSTRUCTIONS, "instructions");

        Object mealTypesRaw = LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.MEAL_TYPES, "mealTypes");
        recipe.mealTypes = mealTypesRaw instanceof Set<?> set
                ? (Set<MealType>) set
                : parseMealTypes((String) mealTypesRaw);

        recipe.prepTimeMinutes = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.PREP_TIME_MINUTES, "prepTimeMinutes"), 0);
        recipe.cookTimeMinutes = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.COOK_TIME_MINUTES, "cookTimeMinutes"), 0);
        recipe.servings = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.SERVINGS, "servings"), 2);
        recipe.minServings = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.MIN_SERVINGS, "minServings"), 1);
        recipe.maxServings = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.MAX_SERVINGS, "maxServings"), 8);
        recipe.scalingPrecision = LegacyMapperSupport.asEnum(Recipe.ScalingPrecision.class,
                LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.SCALING_PRECISION, "scalingPrecision"),
                Recipe.ScalingPrecision.ROUGH);
        recipe.prepEffort = LegacyMapperSupport.asEnum(Recipe.PrepEffort.class,
                LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.PREP_EFFORT, "prepEffort"),
                Recipe.PrepEffort.MEDIUM);

        Object ingredientRaw = LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.INGREDIENTS_DATA, "ingredients");
        recipe.ingredients = ingredientRaw instanceof List<?> list
                ? (List<Recipe.RecipeIngredient>) list
                : parseIngredients((String) ingredientRaw);

        recipe.tags = (String) LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.TAGS, "tags");
        recipe.lastUsed = LegacyMapperSupport.asLocalDate(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.LAST_USED, "lastUsed"));
        recipe.usageCount = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.USAGE_COUNT, "usageCount"), 0);
        recipe.isFavorite = LegacyMapperSupport.asBoolean(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.IS_FAVORITE, "isFavorite"), false);
        recipe.totalCalories = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_CALORIES, "totalCalories"), 0);
        recipe.totalProtein = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_PROTEIN, "totalProtein"), 0);
        recipe.totalCarbs = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_CARBS, "totalCarbs"), 0);
        recipe.totalFat = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_FAT, "totalFat"), 0);
        recipe.shelfLifeDays = LegacyMapperSupport.asInt(LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.SHELF_LIFE_DAYS, "shelfLifeDays"), 0);

        Object ratingsRaw = LegacyMapperSupport.get(row, LegacyMealFieldKeys.Recipe.RATINGS_DATA, "ratings");
        recipe.ratings = ratingsRaw instanceof List<?> list
                ? (List<Recipe.MemberRating>) list
                : parseRatings((String) ratingsRaw);
        return recipe;
    }

    public static Map<String, Object> toStorageRow(Recipe recipe) {
        Map<String, Object> row = new HashMap<>();
        row.put(LegacyMealFieldKeys.Recipe.ID, recipe.id);
        row.put(LegacyMealFieldKeys.Recipe.TITLE, recipe.title);
        row.put(LegacyMealFieldKeys.Recipe.DESCRIPTION, recipe.description);
        row.put(LegacyMealFieldKeys.Recipe.INSTRUCTIONS, recipe.instructions);
        row.put(LegacyMealFieldKeys.Recipe.MEAL_TYPES, serializeMealTypes(recipe.mealTypes));
        row.put(LegacyMealFieldKeys.Recipe.PREP_TIME_MINUTES, recipe.prepTimeMinutes);
        row.put(LegacyMealFieldKeys.Recipe.COOK_TIME_MINUTES, recipe.cookTimeMinutes);
        row.put(LegacyMealFieldKeys.Recipe.SERVINGS, recipe.servings);
        row.put(LegacyMealFieldKeys.Recipe.MIN_SERVINGS, recipe.minServings);
        row.put(LegacyMealFieldKeys.Recipe.MAX_SERVINGS, recipe.maxServings);
        row.put(LegacyMealFieldKeys.Recipe.SCALING_PRECISION, recipe.scalingPrecision == null ? null : recipe.scalingPrecision.name());
        row.put(LegacyMealFieldKeys.Recipe.PREP_EFFORT, recipe.prepEffort == null ? null : recipe.prepEffort.name());
        row.put(LegacyMealFieldKeys.Recipe.INGREDIENTS_DATA, serializeIngredients(recipe.ingredients));
        row.put(LegacyMealFieldKeys.Recipe.TAGS, recipe.tags);
        row.put(LegacyMealFieldKeys.Recipe.LAST_USED, recipe.lastUsed == null ? null : recipe.lastUsed.toString());
        row.put(LegacyMealFieldKeys.Recipe.USAGE_COUNT, recipe.usageCount);
        row.put(LegacyMealFieldKeys.Recipe.IS_FAVORITE, recipe.isFavorite ? 1 : 0);
        row.put(LegacyMealFieldKeys.Recipe.TOTAL_CALORIES, recipe.totalCalories);
        row.put(LegacyMealFieldKeys.Recipe.TOTAL_PROTEIN, recipe.totalProtein);
        row.put(LegacyMealFieldKeys.Recipe.TOTAL_CARBS, recipe.totalCarbs);
        row.put(LegacyMealFieldKeys.Recipe.TOTAL_FAT, recipe.totalFat);
        row.put(LegacyMealFieldKeys.Recipe.SHELF_LIFE_DAYS, recipe.shelfLifeDays);
        row.put(LegacyMealFieldKeys.Recipe.RATINGS_DATA, serializeRatings(recipe.ratings));
        return row;
    }

    private static Set<MealType> parseMealTypes(String raw) {
        Set<MealType> result = EnumSet.noneOf(MealType.class);
        if (raw == null || raw.isBlank()) return result;
        for (String part : raw.split(",")) {
            MealType mealType = LegacyMapperSupport.asEnum(MealType.class, part.trim(), null);
            if (mealType != null) result.add(mealType);
        }
        return result;
    }

    private static String serializeMealTypes(Set<MealType> mealTypes) {
        if (mealTypes == null || mealTypes.isEmpty()) return "";
        return mealTypes.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static List<Recipe.RecipeIngredient> parseIngredients(String raw) {
        List<Recipe.RecipeIngredient> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split("\\|", 4);
            if (parts.length != 4) continue;
            Long ingredientId = LegacyMapperSupport.asNullableLong(parts[0]);
            result.add(new Recipe.RecipeIngredient(ingredientId, parts[1], LegacyMapperSupport.asDouble(parts[2], 0), parts[3]));
        }
        return result;
    }

    private static String serializeIngredients(List<Recipe.RecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "";
        return ingredients.stream()
                .map(value -> (value.ingredientId() == null ? "" : value.ingredientId())
                        + "|" + value.ingredientName() + "|" + value.amount() + "|" + value.unit())
                .collect(Collectors.joining(";"));
    }

    private static List<Recipe.MemberRating> parseRatings(String raw) {
        List<Recipe.MemberRating> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(",")) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length != 2) continue;
            long memberId = LegacyMapperSupport.asLong(parts[0], 0);
            int rating = LegacyMapperSupport.asInt(parts[1], 3);
            if (memberId > 0) {
                result.add(new Recipe.MemberRating(memberId, rating));
            }
        }
        return result;
    }

    private static String serializeRatings(List<Recipe.MemberRating> ratings) {
        if (ratings == null || ratings.isEmpty()) return "";
        return ratings.stream().map(value -> value.memberId() + "|" + value.rating()).collect(Collectors.joining(","));
    }
}
