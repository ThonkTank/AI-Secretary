package com.autosecretary.features.meal.domain;

import java.util.List;

/**
 * Ergebnis einer Rezept-Skalierung: finale Portionen, Skalierungsfaktor und skalierte Zutaten.
 */
public record RecipeScalingResult(double servings, double factor, List<ScaledIngredient> ingredients) {

    public record ScaledIngredient(Long ingredientId, String ingredientName, double amount, String unit) {}
}
