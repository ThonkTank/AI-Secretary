package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Rezept mit Zutaten, Naehrwerten und Meal-Type-Zuordnung.
 */
public class Recipe {

    public Long id;
    public String title;
    public String description;
    public String instructions;
    public Set<MealType> mealTypes;
    public int prepTimeMinutes;
    public int cookTimeMinutes;
    public int servings;                // Basis-Portionen
    public int minServings;
    public int maxServings;
    public ScalingPrecision scalingPrecision;
    public PrepEffort prepEffort;
    public List<RecipeIngredient> ingredients;
    public String tags;                 // Comma-separated
    public LocalDate lastUsed;
    public int usageCount;
    public boolean isFavorite;
    public int totalCalories;           // Cached: pro Portion
    public int totalProtein;
    public int totalCarbs;
    public int totalFat;
    public int shelfLifeDays;           // Haltbarkeit nach Zubereitung
    public List<MemberRating> ratings;

    public enum ScalingPrecision {
        EXACT,      // Ganzzahlige Skalierung, exakte Verhaeltnisse
        ROUGH,      // Ungefaehr, tolerant
        NONE        // Nicht skalierbar
    }

    public enum PrepEffort {
        QUICK,      // < 15 min
        MEDIUM,     // 15-45 min
        ELABORATE   // > 45 min
    }

    /**
     * Zutat innerhalb eines Rezepts.
     */
    public record RecipeIngredient(Long ingredientId, String ingredientName, double amount, String unit) {}

    /**
     * Bewertung eines Rezepts durch ein Haushaltsmitglied (1-5 Sterne).
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
        for (int i = 0; i < ratings.size(); i++) {
            if (ratings.get(i).memberId() == memberId) {
                ratings.set(i, new MemberRating(memberId, clamped));
                return;
            }
        }
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
        public Builder mealTypes(Set<MealType> v) { r.mealTypes = EnumSet.copyOf(v); return this; }
        public Builder prepTime(int min) { r.prepTimeMinutes = min; return this; }
        public Builder cookTime(int min) { r.cookTimeMinutes = min; return this; }
        public Builder servings(int v) { r.servings = v; return this; }
        public Builder minServings(int v) { r.minServings = v; return this; }
        public Builder maxServings(int v) { r.maxServings = v; return this; }
        public Builder precision(ScalingPrecision v) { r.scalingPrecision = v; return this; }
        public Builder effort(PrepEffort v) { r.prepEffort = v; return this; }
        public Builder ingredient(long id, String name, double amount, String unit) {
            r.ingredients.add(new RecipeIngredient(id, name, amount, unit));
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
