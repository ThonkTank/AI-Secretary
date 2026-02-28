package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.stream.Collectors;

/**
 * {@link RowMapper} for {@link Recipe}.
 *
 * <p>Notable serialization: two fields use custom delimited-string formats:
 * <ul>
 *   <li>{@code ingredients} — {@code "id|name|amount|unit"} records joined by {@code ";"}.
 *   <li>{@code ratings} — {@code "memberId|rating"} records joined by {@code ","}.
 * </ul>
 * Both use {@link MapperSupport#asListOrParse} for "both-paths" deserialization.
 * Values stored in these fields must not contain {@code '|'} or {@code ';'}.
 */
public class RecipeRowMapper implements RowMapper<Recipe> {
    @Override
    public Map<String, Object> toRow(Recipe recipe) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.Recipe.ID, recipe.id);
        row.put(MealFieldKeys.Recipe.TITLE, recipe.title);
        row.put(MealFieldKeys.Recipe.DESCRIPTION, recipe.description);
        row.put(MealFieldKeys.Recipe.INSTRUCTIONS, recipe.instructions);
        row.put(MealFieldKeys.Recipe.MEAL_TYPES, MapperSupport.serializeEnumSet(recipe.mealTypes));
        row.put(MealFieldKeys.Recipe.PREP_TIME_MINUTES, recipe.prepTimeMinutes);
        row.put(MealFieldKeys.Recipe.COOK_TIME_MINUTES, recipe.cookTimeMinutes);
        row.put(MealFieldKeys.Recipe.SERVINGS, recipe.servings);
        row.put(MealFieldKeys.Recipe.MIN_SERVINGS, recipe.minServings);
        row.put(MealFieldKeys.Recipe.MAX_SERVINGS, recipe.maxServings);
        row.put(MealFieldKeys.Recipe.SCALING_PRECISION, MapperSupport.enumNameOrNull(recipe.scalingPrecision));
        row.put(MealFieldKeys.Recipe.PREP_EFFORT, MapperSupport.enumNameOrNull(recipe.prepEffort));
        row.put(MealFieldKeys.Recipe.INGREDIENTS, serializeIngredients(recipe.ingredients));
        row.put(MealFieldKeys.Recipe.TAGS, recipe.tags);
        row.put(MealFieldKeys.Recipe.LAST_USED, MapperSupport.toDateString(recipe.lastUsed));
        row.put(MealFieldKeys.Recipe.USAGE_COUNT, recipe.usageCount);
        row.put(MealFieldKeys.Recipe.IS_FAVORITE, recipe.isFavorite ? 1 : 0);
        row.put(MealFieldKeys.Recipe.TOTAL_CALORIES, recipe.totalCalories);
        row.put(MealFieldKeys.Recipe.TOTAL_PROTEIN, recipe.totalProtein);
        row.put(MealFieldKeys.Recipe.TOTAL_CARBS, recipe.totalCarbs);
        row.put(MealFieldKeys.Recipe.TOTAL_FAT, recipe.totalFat);
        row.put(MealFieldKeys.Recipe.SHELF_LIFE_DAYS, recipe.shelfLifeDays);
        row.put(MealFieldKeys.Recipe.RATINGS, serializeRatings(recipe.ratings));
        return row;
    }

    @Override
    public Recipe fromRow(Map<String, Object> row) {
        Recipe recipe = new Recipe();

        recipe.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.Recipe.ID));
        // String fields use raw casts: the storage layer always serializes them as strings via toRow(),
        // so the cast is safe (no type mismatch). If storage changes, wrap this in MapperSupport.asString().
        recipe.title = (String) row.get(MealFieldKeys.Recipe.TITLE);
        recipe.description = (String) row.get(MealFieldKeys.Recipe.DESCRIPTION);
        recipe.instructions = (String) row.get(MealFieldKeys.Recipe.INSTRUCTIONS);

        recipe.mealTypes = MapperSupport.asEnumSet(MealType.class, row.get(MealFieldKeys.Recipe.MEAL_TYPES));
        recipe.prepTimeMinutes = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.PREP_TIME_MINUTES));
        recipe.cookTimeMinutes = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.COOK_TIME_MINUTES));
        recipe.servings = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.SERVINGS), 2);
        recipe.minServings = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.MIN_SERVINGS), 1);
        recipe.maxServings = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.MAX_SERVINGS), 8);
        recipe.scalingPrecision = MapperSupport.asEnum(Recipe.ScalingPrecision.class,
                row.get(MealFieldKeys.Recipe.SCALING_PRECISION), Recipe.ScalingPrecision.ROUGH);
        recipe.prepEffort = MapperSupport.asEnum(Recipe.PrepEffort.class,
                row.get(MealFieldKeys.Recipe.PREP_EFFORT), Recipe.PrepEffort.MEDIUM);

        recipe.ingredients = MapperSupport.asListOrParse(row.get(MealFieldKeys.Recipe.INGREDIENTS), RecipeRowMapper::parseIngredients);

        recipe.tags = (String) row.get(MealFieldKeys.Recipe.TAGS);
        recipe.lastUsed = MapperSupport.asLocalDate(row.get(MealFieldKeys.Recipe.LAST_USED));
        recipe.usageCount = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.USAGE_COUNT));
        recipe.isFavorite = MapperSupport.asBoolean(row.get(MealFieldKeys.Recipe.IS_FAVORITE));
        recipe.totalCalories = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_CALORIES));
        recipe.totalProtein = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_PROTEIN));
        recipe.totalCarbs = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_CARBS));
        recipe.totalFat = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_FAT));
        recipe.shelfLifeDays = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.SHELF_LIFE_DAYS));

        recipe.ratings = MapperSupport.asListOrParse(row.get(MealFieldKeys.Recipe.RATINGS), RecipeRowMapper::parseRatings);
        return recipe;
    }

    // NOTE: ingredientName and unit values must not contain '|' or ';' — these are the field/record delimiters.
    private static List<Recipe.RecipeIngredient> parseIngredients(String raw) {
        List<Recipe.RecipeIngredient> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split("\\|", 4);
            if (parts.length != 4) continue;
            Long ingredientId = MapperSupport.asNullableLong(parts[0]);
            String ingredientName = parts[1].isEmpty() ? null : parts[1];
            String unit = parts[3].isEmpty() ? null : parts[3];
            result.add(new Recipe.RecipeIngredient(ingredientId, ingredientName, MapperSupport.asDouble(parts[2]), unit));
        }
        return result;
    }

    // NOTE: ingredientName and unit values must not contain '|' or ';' — these are the field/record delimiters.
    private static String serializeIngredients(List<Recipe.RecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "";
        return ingredients.stream()
                .map(i -> Objects.toString(i.ingredientId(), "")
                        + "|" + Objects.toString(i.ingredientName(), "")
                        + "|" + i.amount()
                        + "|" + Objects.toString(i.unit(), ""))
                .collect(Collectors.joining(";"));
    }

    private static List<Recipe.MemberRating> parseRatings(String raw) {
        List<Recipe.MemberRating> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(",")) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length != 2) continue;
            long memberId = MapperSupport.asLong(parts[0]);
            int rating = MapperSupport.asInt(parts[1], 3);
            if (memberId > 0) {
                result.add(new Recipe.MemberRating(memberId, rating));
            }
        }
        return result;
    }

    private static String serializeRatings(List<Recipe.MemberRating> ratings) {
        if (ratings == null || ratings.isEmpty()) return "";
        return ratings.stream().map(r -> r.memberId() + "|" + r.rating()).collect(Collectors.joining(","));
    }
}
