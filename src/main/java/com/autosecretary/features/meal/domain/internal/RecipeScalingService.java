package com.autosecretary.features.meal.domain.internal;

import com.autosecretary.features.meal.domain.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Skalierung von Rezepten inkl. Precision-Regeln und Portion-Grenzen.
 */
public class RecipeScalingService {

    public static ScalingResult scaleRecipe(Recipe recipe, double requestedServings) {
        if (recipe == null) {
            return new ScalingResult(0, 0, new ArrayList<>());
        }
        double finalServings = resolveServings(recipe, requestedServings);
        double baseServings = Math.max(1.0, recipe.servings);
        double factor = finalServings / baseServings;

        List<ScaledIngredient> ingredients = new ArrayList<>();
        if (recipe.ingredients != null) {
            for (Recipe.RecipeIngredient ingredient : recipe.ingredients) {
                ingredients.add(new ScaledIngredient(
                        ingredient.ingredientId(),
                        ingredient.ingredientName(),
                        ingredient.amount() * factor,
                        ingredient.unit()
                ));
            }
        }
        return new ScalingResult(finalServings, factor, ingredients);
    }

    private static double resolveServings(Recipe recipe, double requestedServings) {
        double minServings = Math.max(0.1, recipe.minServings);
        double maxServings = Math.max(minServings, recipe.maxServings);
        double clamped = Math.min(maxServings, Math.max(minServings, requestedServings));

        Recipe.ScalingPrecision precision = recipe.scalingPrecision == null
                ? Recipe.ScalingPrecision.ROUGH
                : recipe.scalingPrecision;

        return switch (precision) {
            case NONE -> Math.min(maxServings, Math.max(minServings, recipe.servings));
            case EXACT -> Math.min(maxServings, Math.max(minServings, Math.rint(clamped)));
            case ROUGH -> Math.min(maxServings, Math.max(minServings, Math.round(clamped * 2.0) / 2.0));
        };
    }

    public record ScaledIngredient(Long ingredientId, String ingredientName, double amount, String unit) {}

    public record ScalingResult(double servings, double factor, List<ScaledIngredient> ingredients) {}

    private RecipeScalingService() {}
}
