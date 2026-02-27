package com.autosecretary.features.meal.data.internal.mapper;

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

        recipe.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.Recipe.ID));
        recipe.title = (String) row.get(MealFieldKeys.Recipe.TITLE);
        recipe.description = (String) row.get(MealFieldKeys.Recipe.DESCRIPTION);
        recipe.instructions = (String) row.get(MealFieldKeys.Recipe.INSTRUCTIONS);

        recipe.mealTypes = asSetOrParse(row.get(MealFieldKeys.Recipe.MEAL_TYPES));
        recipe.prepTimeMinutes = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.PREP_TIME_MINUTES));
        recipe.cookTimeMinutes = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.COOK_TIME_MINUTES));
        recipe.servings = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.SERVINGS), 2);
        recipe.minServings = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.MIN_SERVINGS), 1);
        recipe.maxServings = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.MAX_SERVINGS), 8);
        recipe.scalingPrecision = MapperSupport.asEnum(Recipe.ScalingPrecision.class,
                row.get(MealFieldKeys.Recipe.SCALING_PRECISION), Recipe.ScalingPrecision.ROUGH);
        recipe.prepEffort = MapperSupport.asEnum(Recipe.PrepEffort.class,
                row.get(MealFieldKeys.Recipe.PREP_EFFORT), Recipe.PrepEffort.MEDIUM);

        recipe.ingredients = asListOrParse(row.get(MealFieldKeys.Recipe.INGREDIENTS), RecipeRowMapper::parseIngredients);

        recipe.tags = (String) row.get(MealFieldKeys.Recipe.TAGS);
        recipe.lastUsed = MapperSupport.asLocalDate(row.get(MealFieldKeys.Recipe.LAST_USED));
        recipe.usageCount = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.USAGE_COUNT));
        recipe.isFavorite = MapperSupport.asBoolean(row.get(MealFieldKeys.Recipe.IS_FAVORITE));
        recipe.totalCalories = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_CALORIES));
        recipe.totalProtein = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_PROTEIN));
        recipe.totalCarbs = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_CARBS));
        recipe.totalFat = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.TOTAL_FAT));
        recipe.shelfLifeDays = MapperSupport.asInt(row.get(MealFieldKeys.Recipe.SHELF_LIFE_DAYS));

        recipe.ratings = asListOrParse(row.get(MealFieldKeys.Recipe.RATINGS), RecipeRowMapper::parseRatings);
        return recipe;
    }

    @Override
    public Map<String, Object> toRow(Recipe recipe) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.Recipe.ID, recipe.id);
        row.put(MealFieldKeys.Recipe.TITLE, recipe.title);
        row.put(MealFieldKeys.Recipe.DESCRIPTION, recipe.description);
        row.put(MealFieldKeys.Recipe.INSTRUCTIONS, recipe.instructions);
        row.put(MealFieldKeys.Recipe.MEAL_TYPES, serializeMealTypes(recipe.mealTypes));
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
    private static Set<MealType> asSetOrParse(Object value) {
        if (value instanceof Set<?> set) {
            return (Set<MealType>) set;
        }
        return parseMealTypes((String) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> asListOrParse(Object value, Function<String, List<T>> parser) {
        if (value instanceof List<?> list) {
            return (List<T>) list;
        }
        return parser.apply((String) value);
    }

}
