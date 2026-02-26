package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Wochenplan-Eintrag: Verknuepft ein Rezept mit einem Tag und MealType.
 */
public class MealPlan {

    public Long id;
    public LocalDate date;
    public MealType mealType;
    public long recipeId;
    public int plannedServings;
    public boolean isCompleted;
    public int actualServings;
    public LocalDateTime completedAt;
    public Long itemId;                 // FK → TrackedItem (fuer Completion-Tracking)
    public String recipeTitle;          // Denormalized
    public int estimatedCalories;       // Denormalized

    // Builder
    public static class Builder {
        private final MealPlan p = new MealPlan();

        public Builder(LocalDate date, MealType mealType, long recipeId) {
            p.date = date;
            p.mealType = mealType;
            p.recipeId = recipeId;
        }

        public Builder servings(int v) { p.plannedServings = v; return this; }
        public Builder itemId(Long v) { p.itemId = v; return this; }
        public Builder recipeTitle(String v) { p.recipeTitle = v; return this; }
        public Builder calories(int v) { p.estimatedCalories = v; return this; }

        public MealPlan build() { return p; }
    }
}
