package com.autosecretary.features.meal.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Skalierung von Rezepten inkl. Precision-Regeln und Portion-Grenzen.
 */
public class RecipeScalingService {

    public static RecipeScalingResult scaleRecipe(Recipe recipe, double requestedServings) {
        if (recipe == null) {
            return new RecipeScalingResult(0, 0, new ArrayList<>());
        }
        double finalServings = resolveServings(recipe, requestedServings);
        double baseServings = Math.max(1.0, recipe.servings);
        double factor = finalServings / baseServings;

        List<RecipeScalingResult.ScaledIngredient> ingredients = new ArrayList<>();
        if (recipe.ingredients != null) {
            for (Recipe.RecipeIngredient ingredient : recipe.ingredients) {
                ingredients.add(new RecipeScalingResult.ScaledIngredient(
                        ingredient.ingredientId(),
                        ingredient.ingredientName(),
                        ingredient.amount() * factor,
                        ingredient.unit()
                ));
            }
        }
        return new RecipeScalingResult(finalServings, factor, ingredients);
    }

    private static double resolveServings(Recipe recipe, double requestedServings) {
        double minServings = Math.max(0.1, recipe.minServings);
        double maxServings = Math.max(minServings, recipe.maxServings);
        double clamped = clamp(requestedServings, minServings, maxServings);

        Recipe.ScalingPrecision precision = Objects.requireNonNullElse(recipe.scalingPrecision, Recipe.ScalingPrecision.ROUGH);

        double toPrecision = switch (precision) {
            case NONE -> recipe.servings;
            case EXACT -> Math.rint(clamped);
            case ROUGH -> Math.round(clamped * 2.0) / 2.0; // rounds to nearest 0.5 serving
        };
        return clamp(toPrecision, minServings, maxServings);
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private RecipeScalingService() {}
}
