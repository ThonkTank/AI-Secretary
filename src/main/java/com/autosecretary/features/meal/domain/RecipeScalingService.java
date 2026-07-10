package com.autosecretary.features.meal.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stateless service that scales a {@link Recipe} to a requested serving count.
 *
 * <p>Scaling applies three steps in order:
 * <ol>
 *   <li><strong>Clamp</strong> the requested servings to {@code [recipe.minServings, recipe.maxServings]}.</li>
 *   <li><strong>Round</strong> to the recipe's {@link Recipe.ScalingPrecision}:
 *       {@code EXACT} → nearest integer; {@code ROUGH} → nearest 0.5; {@code NONE} → always use base servings.</li>
 *   <li><strong>Scale</strong> each ingredient amount by {@code finalServings / baseServings}.</li>
 * </ol>
 *
 * <p>Usage: call {@link #scaleRecipe} directly — do not instantiate this class.
 */
public class RecipeScalingService {

    /**
     * Scales a recipe to the requested number of servings.
     *
     * <p>Applies clamping and precision rounding (see class Javadoc). If {@code recipe} is null,
     * returns an empty result ({@code servings=0, factor=0, ingredients=[]}).
     *
     * @param recipe            the recipe to scale; may be null (safe, returns empty result)
     * @param requestedServings desired serving count; will be clamped and rounded per recipe settings
     * @return a {@link RecipeScalingResult} with the final serving count, scale factor, and
     *         scaled ingredient amounts
     */
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
