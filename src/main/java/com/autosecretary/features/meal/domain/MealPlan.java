package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A meal plan entry linking a recipe to a specific date and meal type.
 *
 * <p>{@code recipeTitle} and {@code estimatedCalories} are denormalized from the {@link Recipe}
 * for display without an extra lookup.
 *
 * <p>{@code itemId} is an opaque foreign key into the task feature's checklist system. When a
 * meal plan is integrated with task tracking, completing the checklist item triggers
 * {@code TaskMealIntegrationService} to mark this plan completed. If not integrated, {@code itemId}
 * is null and {@code isCompleted} must be updated directly.
 *
 * <p>{@code actualServings} and {@code completedAt} are filled in on completion; they remain
 * at their default values (0 / null) until the plan is marked done.
 */
public class MealPlan {

    public String id;
    public LocalDate date;
    public MealType mealType;
    public String recipeId;
    public int plannedServings;
    public boolean isCompleted;
    public int actualServings;
    public LocalDateTime completedAt;
    public String itemId;               // FK into the task feature's checklist item table (opaque id; null when not integrated with task tracking)
    public String recipeTitle;          // Denormalized
    public int estimatedCalories;       // Denormalized

    // Builder
    public static class Builder {
        private final MealPlan p = new MealPlan();

        public Builder(LocalDate date, MealType mealType, String recipeId) {
            p.date = date;
            p.mealType = mealType;
            p.recipeId = recipeId;
        }

        public Builder servings(int v) { p.plannedServings = v; return this; }
        public Builder itemId(String v) { p.itemId = v; return this; }
        public Builder recipeTitle(String v) { p.recipeTitle = v; return this; }
        public Builder calories(int v) { p.estimatedCalories = v; return this; }

        public MealPlan build() { return p; }
    }
}
