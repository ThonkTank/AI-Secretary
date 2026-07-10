package com.autosecretary.features.meal.domain;

import java.time.LocalDate;

/**
 * A nutrition log entry recording what a household member actually ate on a given day.
 *
 * <p>Each {@code ConsumptionLog} is linked to a {@link MealPlan} via {@code itemId} (the
 * task feature's checklist item id). When a meal-plan task is completed,
 * The task-owned {@code TaskMealCompletionService} adapter creates a {@code ConsumptionLog} for each active
 * household member, recording the calories and macros for that serving.
 *
 * <p>{@code calories}, {@code protein}, {@code carbs}, and {@code fat} are plain integer
 * values (kcal and grams respectively) — no ×10 fixed-point scaling.
 */
public class ConsumptionLog {

    public String id;
    public LocalDate date;
    public String itemId;           // FK into the task feature's checklist item table (links this log entry to a MealPlan's itemId)
    public String memberId;
    public String recipeId;
    public double servingsConsumed;
    public int calories;
    public int protein;
    public int carbs;
    public int fat;

    // Builder
    public static class Builder {
        private final ConsumptionLog l = new ConsumptionLog();

        public Builder(LocalDate date, String itemId, String memberId) {
            l.date = date;
            l.itemId = itemId;
            l.memberId = memberId;
        }

        public Builder recipeId(String v) { l.recipeId = v; return this; }
        public Builder servings(double v) { l.servingsConsumed = v; return this; }
        public Builder calories(int v) { l.calories = v; return this; }
        public Builder protein(int v) { l.protein = v; return this; }
        public Builder carbs(int v) { l.carbs = v; return this; }
        public Builder fat(int v) { l.fat = v; return this; }

        public ConsumptionLog build() { return l; }
    }
}
