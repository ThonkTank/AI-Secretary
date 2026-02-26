package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.Recipe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeRowMapper implements RowMapper<Recipe> {
    @Override
    public Map<String, Object> toRow(Recipe recipe) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", recipe.id);
        row.put("title", recipe.title);
        row.put("description", recipe.description);
        row.put("instructions", recipe.instructions);
        row.put("mealTypes", recipe.mealTypes);
        row.put("prepTimeMinutes", recipe.prepTimeMinutes);
        row.put("cookTimeMinutes", recipe.cookTimeMinutes);
        row.put("servings", recipe.servings);
        row.put("minServings", recipe.minServings);
        row.put("maxServings", recipe.maxServings);
        row.put("scalingPrecision", recipe.scalingPrecision);
        row.put("prepEffort", recipe.prepEffort);
        row.put("ingredients", recipe.ingredients);
        row.put("tags", recipe.tags);
        row.put("lastUsed", recipe.lastUsed == null ? null : recipe.lastUsed.toString());
        row.put("usageCount", recipe.usageCount);
        row.put("isFavorite", recipe.isFavorite);
        row.put("totalCalories", recipe.totalCalories);
        row.put("totalProtein", recipe.totalProtein);
        row.put("totalCarbs", recipe.totalCarbs);
        row.put("totalFat", recipe.totalFat);
        row.put("shelfLifeDays", recipe.shelfLifeDays);
        row.put("ratings", recipe.ratings);
        return row;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Recipe fromRow(Map<String, Object> row) {
        Recipe recipe = new Recipe();
        recipe.id = MapperSupport.asNullableLong(row.get("id"));
        recipe.title = (String) row.get("title");
        recipe.description = (String) row.get("description");
        recipe.instructions = (String) row.get("instructions");
        recipe.mealTypes = (java.util.Set<com.autosecretary.features.meal.domain.MealType>) row.get("mealTypes");
        recipe.prepTimeMinutes = MapperSupport.asInt(row.get("prepTimeMinutes"));
        recipe.cookTimeMinutes = MapperSupport.asInt(row.get("cookTimeMinutes"));
        recipe.servings = MapperSupport.asInt(row.get("servings"));
        recipe.minServings = MapperSupport.asInt(row.get("minServings"));
        recipe.maxServings = MapperSupport.asInt(row.get("maxServings"));
        recipe.scalingPrecision = (Recipe.ScalingPrecision) row.get("scalingPrecision");
        recipe.prepEffort = (Recipe.PrepEffort) row.get("prepEffort");
        recipe.ingredients = (List<Recipe.RecipeIngredient>) row.getOrDefault("ingredients", new ArrayList<>());
        recipe.tags = (String) row.get("tags");
        String lastUsed = (String) row.get("lastUsed");
        recipe.lastUsed = lastUsed == null ? null : LocalDate.parse(lastUsed);
        recipe.usageCount = MapperSupport.asInt(row.get("usageCount"));
        recipe.isFavorite = Boolean.TRUE.equals(MapperSupport.asBoolean(row.get("isFavorite")));
        recipe.totalCalories = MapperSupport.asInt(row.get("totalCalories"));
        recipe.totalProtein = MapperSupport.asInt(row.get("totalProtein"));
        recipe.totalCarbs = MapperSupport.asInt(row.get("totalCarbs"));
        recipe.totalFat = MapperSupport.asInt(row.get("totalFat"));
        recipe.shelfLifeDays = MapperSupport.asInt(row.get("shelfLifeDays"));
        recipe.ratings = (List<Recipe.MemberRating>) row.getOrDefault("ratings", new ArrayList<>());
        return recipe;
    }
}
