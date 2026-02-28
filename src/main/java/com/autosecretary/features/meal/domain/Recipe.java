package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A recipe with ingredients, nutritional summary, and meal-type classification.
 *
 * <p>{@code servings} is the base serving count the recipe was designed for.
 * {@code minServings} and {@code maxServings} define the valid scaling range for
 * {@link RecipeScalingService}. {@code scalingPrecision} controls how the serving count
 * is rounded during scaling — see {@link ScalingPrecision}.
 *
 * <p>{@code totalCalories}, {@code totalProtein}, {@code totalCarbs}, and {@code totalFat}
 * are cached values computed from ingredient nutrition data and stored denormalized for display;
 * they represent the <em>total for the base recipe as written</em> — for all {@code servings}
 * portions combined, not per individual serving. Divide by {@code servings} for per-serving values.
 *
 * <p>{@code tags} is a comma-separated string of free-form tags (e.g. {@code "vegetarian,quick"}).
 */
public class Recipe {

    public Long id;
    public String title;
    public String description;
    public String instructions;
    public Set<MealType> mealTypes;
    public int prepTimeMinutes;
    public int cookTimeMinutes;
    public int servings;                // base portion count this recipe is designed for
    public int minServings;
    public int maxServings;
    public ScalingPrecision scalingPrecision;
    public PrepEffort prepEffort;
    public List<RecipeIngredient> ingredients;
    public String tags;                 // comma-separated free-form tags (e.g. "vegetarian,quick")
    public LocalDate lastUsed;
    public int usageCount;
    public boolean isFavorite;
    public int totalCalories;           // total kcal for the full base recipe (all base servings combined; divide by servings for per-serving value)
    public int totalProtein;
    public int totalCarbs;
    public int totalFat;
    public int shelfLifeDays;           // how long the cooked dish stays fresh (days)
    public List<MemberRating> ratings;

    /**
     * How requested serving counts are rounded by {@link RecipeScalingService}.
     * The rounding is applied after clamping to [minServings, maxServings].
     */
    public enum ScalingPrecision {
        EXACT,      // Rounds to nearest whole integer (e.g. 2.7 → 3)
        ROUGH,      // Rounds to nearest 0.5 serving (e.g. 2.7 → 2.5, 2.9 → 3.0)
        NONE        // Not scalable: always uses the recipe's base servings count
    }

    public enum PrepEffort {
        QUICK,      // < 15 min
        MEDIUM,     // 15-45 min
        ELABORATE   // > 45 min
    }

    /**
     * An ingredient entry within a recipe, referencing an {@link Ingredient} by id.
     * {@code ingredientName} is denormalized for display without an extra lookup.
     */
    public record RecipeIngredient(Long ingredientId, String ingredientName, double amount, String unit) {}

    /**
     * A household member's star rating for this recipe (1–5 stars).
     * Ratings are stored per member so per-member preferences can drive recipe suggestions.
     */
    public record MemberRating(long memberId, int rating) {}

    public int getTotalTime() {
        return prepTimeMinutes + cookTimeMinutes;
    }

    public double getAverageRating() {
        if (ratings == null || ratings.isEmpty()) return 0.0;
        return ratings.stream().mapToInt(MemberRating::rating).average().orElse(0.0);
    }

    public int getRatingByMember(long memberId) {
        if (ratings == null) return 0;
        return ratings.stream()
            .filter(r -> r.memberId() == memberId)
            .mapToInt(MemberRating::rating)
            .findFirst()
            .orElse(0);
    }

    public void setRatingByMember(long memberId, int rating) {
        if (ratings == null) ratings = new ArrayList<>();
        int clamped = clampRating(rating);
        ratings.removeIf(r -> r.memberId() == memberId);
        ratings.add(new MemberRating(memberId, clamped));
    }

    private static int clampRating(int rating) {
        return Math.max(1, Math.min(5, rating));
    }

    // Builder
    public static class Builder {
        private final Recipe r = new Recipe();

        public Builder(String title) {
            r.title = title;
            r.mealTypes = EnumSet.noneOf(MealType.class);
            r.ingredients = new ArrayList<>();
            r.ratings = new ArrayList<>();
            r.scalingPrecision = ScalingPrecision.ROUGH;
            r.prepEffort = PrepEffort.MEDIUM;
            r.servings = 2;
            r.minServings = 1;
            r.maxServings = 8;
        }

        public Builder description(String v) { r.description = v; return this; }
        public Builder instructions(String v) { r.instructions = v; return this; }
        public Builder mealType(MealType v) { r.mealTypes.add(v); return this; }
        public Builder mealTypes(Set<MealType> v) { r.mealTypes = (v == null || v.isEmpty()) ? EnumSet.noneOf(MealType.class) : EnumSet.copyOf(v); return this; }
        public Builder prepTime(int min) { r.prepTimeMinutes = min; return this; }
        public Builder cookTime(int min) { r.cookTimeMinutes = min; return this; }
        public Builder servings(int v) { r.servings = v; return this; }
        public Builder minServings(int v) { r.minServings = v; return this; }
        public Builder maxServings(int v) { r.maxServings = v; return this; }
        public Builder precision(ScalingPrecision v) { r.scalingPrecision = v; return this; }
        public Builder effort(PrepEffort v) { r.prepEffort = v; return this; }
        public Builder ingredient(long ingredientId, String ingredientName, double amount, String unit) {
            r.ingredients.add(new RecipeIngredient(ingredientId, ingredientName, amount, unit));
            return this;
        }
        public Builder tags(String v) { r.tags = v; return this; }
        public Builder favorite() { r.isFavorite = true; return this; }
        public Builder shelfLife(int days) { r.shelfLifeDays = days; return this; }
        public Builder rating(long memberId, int rating) {
            r.ratings.add(new MemberRating(memberId, clampRating(rating)));
            return this;
        }

        public Recipe build() { return r; }
    }
}
