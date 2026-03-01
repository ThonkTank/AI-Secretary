package com.autosecretary.features.meal.domain;

import java.util.List;

/**
 * The result of scaling a {@link Recipe} via {@link RecipeScalingService}.
 *
 * <p>{@code servings} is the final serving count after clamping and precision rounding.
 * {@code factor} is the scale factor applied to each ingredient amount:
 * {@code factor = servings / recipe.servings} (where {@code recipe.servings} is the base serving count).
 * A {@code factor > 1.0} means scaled up; {@code < 1.0} means scaled down.
 *
 * <p>{@code ingredients} contains each ingredient with its scaled amount. The list is empty
 * (not null) when the input recipe had no ingredients or was null.
 */
public record RecipeScalingResult(double servings, double factor, List<ScaledIngredient> ingredients) {

    /** A single ingredient with its amount scaled to the requested serving count. */
    public record ScaledIngredient(String ingredientId, String ingredientName, double amount, String unit) {}
}
