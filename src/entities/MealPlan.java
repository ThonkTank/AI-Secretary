package entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein geplanter Essenstermin im Wochenplan.
 */
public class MealPlan {

    public Long id;
    public LocalDate date;                   // Datum
    public MealType mealType;                // BREAKFAST, LUNCH, DINNER, SNACK
    public Long recipeId;                    // FK zu Recipe
    public int plannedServings;              // Geplante Portionen

    public boolean isCompleted;              // Wurde gekocht?
    public int actualServings;               // Tatsächlich gekocht (kann abweichen)
    public LocalDateTime completedAt;

    // Verknüpfung mit recurring Meal-TrackedItem
    public Long itemId;                      // FK zu items.id (recurring Meal-Task)

    // Für schnelle Anzeige (denormalisiert)
    public String recipeTitle;
    public int estimatedCalories;

    // Builder
    public static class Builder {
        private final MealPlan mp = new MealPlan();

        public Builder(LocalDate date, MealType mealType, Long recipeId) {
            mp.date = date;
            mp.mealType = mealType;
            mp.recipeId = recipeId;
            mp.plannedServings = 2;  // Default
        }

        public Builder servings(int v) { mp.plannedServings = v; return this; }
        public Builder itemId(Long v) { mp.itemId = v; return this; }
        public Builder recipeTitle(String v) { mp.recipeTitle = v; return this; }
        public Builder estimatedCalories(int v) { mp.estimatedCalories = v; return this; }

        public MealPlan build() { return mp; }
    }

    // ============== UTILITY ==============

    /**
     * Markiert die Mahlzeit als erledigt.
     */
    public void complete(int actualServings) {
        this.isCompleted = true;
        this.actualServings = actualServings;
        this.completedAt = LocalDateTime.now();
    }
}
