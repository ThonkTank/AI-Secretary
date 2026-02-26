package com.autosecretary.features.meal.domain;

import java.time.LocalDate;

/**
 * Naehrwert-Tracking: protokolliert konsumierte Mahlzeiten pro Mitglied.
 */
public class ConsumptionLog {

    public Long id;
    public LocalDate date;
    public long itemId;
    public long memberId;
    public long recipeId;
    public double servingsConsumed;
    public int calories;
    public int protein;
    public int carbs;
    public int fat;

    // Builder
    public static class Builder {
        private final ConsumptionLog l = new ConsumptionLog();

        public Builder(LocalDate date, long itemId, long memberId) {
            l.date = date;
            l.itemId = itemId;
            l.memberId = memberId;
        }

        public Builder recipeId(long v) { l.recipeId = v; return this; }
        public Builder servings(double v) { l.servingsConsumed = v; return this; }
        public Builder calories(int v) { l.calories = v; return this; }
        public Builder protein(int v) { l.protein = v; return this; }
        public Builder carbs(int v) { l.carbs = v; return this; }
        public Builder fat(int v) { l.fat = v; return this; }

        public ConsumptionLog build() { return l; }
    }
}
