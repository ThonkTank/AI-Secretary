package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.mapper.LegacyMealFieldKeys;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.Recipe;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RecipeRowMapper implements RowMapper<Recipe> {
    @Override
    public Recipe fromRow(Map<String, Object> row) {
        Recipe recipe = new Recipe();

        recipe.id = MapperSupport.asNullableLong(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.ID, null));
        recipe.title = (String) MapperSupport.get(row, LegacyMealFieldKeys.Recipe.TITLE, null);
        recipe.description = (String) MapperSupport.get(row, LegacyMealFieldKeys.Recipe.DESCRIPTION, null);
        recipe.instructions = (String) MapperSupport.get(row, LegacyMealFieldKeys.Recipe.INSTRUCTIONS, null);

        recipe.mealTypes = asSetOrParse(
                MapperSupport.get(row, LegacyMealFieldKeys.Recipe.MEAL_TYPES, "mealTypes"),
                RecipeRowMapper::parseMealTypes);

        recipe.prepTimeMinutes = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.PREP_TIME_MINUTES, "prepTimeMinutes"));
        recipe.cookTimeMinutes = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.COOK_TIME_MINUTES, "cookTimeMinutes"));
        recipe.servings = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.SERVINGS, null), 2);
        recipe.minServings = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.MIN_SERVINGS, "minServings"), 1);
        recipe.maxServings = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.MAX_SERVINGS, "maxServings"), 8);
        recipe.scalingPrecision = MapperSupport.asEnum(Recipe.ScalingPrecision.class,
                MapperSupport.get(row, LegacyMealFieldKeys.Recipe.SCALING_PRECISION, "scalingPrecision"),
                Recipe.ScalingPrecision.ROUGH);
        recipe.prepEffort = MapperSupport.asEnum(Recipe.PrepEffort.class,
                MapperSupport.get(row, LegacyMealFieldKeys.Recipe.PREP_EFFORT, "prepEffort"),
                Recipe.PrepEffort.MEDIUM);

        recipe.ingredients = asListOrParse(
                MapperSupport.get(row, LegacyMealFieldKeys.Recipe.INGREDIENTS_DATA, "ingredients"),
                RecipeRowMapper::parseIngredients);

        recipe.tags = (String) MapperSupport.get(row, LegacyMealFieldKeys.Recipe.TAGS, null);
        recipe.lastUsed = MapperSupport.asLocalDate(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.LAST_USED, "lastUsed"));
        recipe.usageCount = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.USAGE_COUNT, "usageCount"));
        recipe.isFavorite = MapperSupport.asBoolean(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.IS_FAVORITE, "isFavorite"));
        recipe.totalCalories = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_CALORIES, "totalCalories"));
        recipe.totalProtein = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_PROTEIN, "totalProtein"));
        recipe.totalCarbs = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_CARBS, "totalCarbs"));
        recipe.totalFat = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.TOTAL_FAT, "totalFat"));
        recipe.shelfLifeDays = MapperSupport.asInt(MapperSupport.get(row, LegacyMealFieldKeys.Recipe.SHELF_LIFE_DAYS, "shelfLifeDays"));

        recipe.ratings = asListOrParse(
                MapperSupport.get(row, LegacyMealFieldKeys.Recipe.RATINGS_DATA, "ratings"),
                RecipeRowMapper::parseRatings);
        return recipe;
    }

    @Override
    public Map<String, Object> toRow(Recipe recipe) {
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
        row.put(LegacyMealFieldKeys.Recipe.SCALING_PRECISION, MapperSupport.enumNameOrNull(recipe.scalingPrecision));
        row.put(LegacyMealFieldKeys.Recipe.PREP_EFFORT, MapperSupport.enumNameOrNull(recipe.prepEffort));
        row.put(LegacyMealFieldKeys.Recipe.INGREDIENTS_DATA, serializeIngredients(recipe.ingredients));
        row.put(LegacyMealFieldKeys.Recipe.TAGS, recipe.tags);
        row.put(LegacyMealFieldKeys.Recipe.LAST_USED, MapperSupport.toDateString(recipe.lastUsed));
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
            MealType mealType = MapperSupport.asEnum(MealType.class, part.trim(), null);
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
            Long ingredientId = MapperSupport.asNullableLong(parts[0]);
            result.add(new Recipe.RecipeIngredient(ingredientId, parts[1], MapperSupport.asDouble(parts[2]), parts[3]));
        }
        return result;
    }

    private static String serializeIngredients(List<Recipe.RecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "";
        return ingredients.stream()
                .map(i -> Objects.toString(i.ingredientId(), "")
                        + "|" + i.ingredientName() + "|" + i.amount() + "|" + i.unit())
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
        return ratings.stream().map(value -> value.memberId() + "|" + value.rating()).collect(Collectors.joining(","));
    }

    @SuppressWarnings("unchecked")
    private static Set<MealType> asSetOrParse(Object value, Function<String, Set<MealType>> parser) {
        if (value instanceof Set<?> set) {
            return (Set<MealType>) set;
        }
        return parser.apply((String) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> asListOrParse(Object value, Function<String, List<T>> parser) {
        if (value instanceof List<?> list) {
            return (List<T>) list;
        }
        return parser.apply((String) value);
    }

}
