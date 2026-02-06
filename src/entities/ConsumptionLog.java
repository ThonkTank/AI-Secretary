package entities;

import java.time.LocalDate;

/**
 * Protokolliert Nahrungsaufnahme für Kalorien-/Nährstofftracking.
 */
public class ConsumptionLog {

    public Long id;
    public LocalDate date;
    public Long mealPlanId;                  // FK (null bei manuellem Eintrag)
    public Long memberId;                    // Wer hat gegessen (null = ganzer Haushalt)

    // Entweder Rezept-basiert:
    public Long recipeId;
    public int servingsConsumed;

    // Oder Einzelzutat (Snack):
    public Long ingredientId;
    public double amount;
    public String unit;

    // Berechnete/eingetragene Werte
    public int calories;
    public int protein;                      // g × 10
    public int carbs;                        // g × 10
    public int fat;                          // g × 10

    // Builder für Rezept-basierte Einträge
    public static class RecipeBuilder {
        private final ConsumptionLog log = new ConsumptionLog();

        public RecipeBuilder(LocalDate date, Long recipeId, int servings) {
            log.date = date;
            log.recipeId = recipeId;
            log.servingsConsumed = servings;
        }

        public RecipeBuilder mealPlanId(Long v) { log.mealPlanId = v; return this; }
        public RecipeBuilder memberId(Long v) { log.memberId = v; return this; }
        public RecipeBuilder nutrition(int calories, int protein, int carbs, int fat) {
            log.calories = calories;
            log.protein = protein;
            log.carbs = carbs;
            log.fat = fat;
            return this;
        }

        public ConsumptionLog build() { return log; }
    }

    // Builder für Einzelzutat-Einträge
    public static class IngredientBuilder {
        private final ConsumptionLog log = new ConsumptionLog();

        public IngredientBuilder(LocalDate date, Long ingredientId, double amount, String unit) {
            log.date = date;
            log.ingredientId = ingredientId;
            log.amount = amount;
            log.unit = unit;
        }

        public IngredientBuilder memberId(Long v) { log.memberId = v; return this; }
        public IngredientBuilder nutrition(int calories, int protein, int carbs, int fat) {
            log.calories = calories;
            log.protein = protein;
            log.carbs = carbs;
            log.fat = fat;
            return this;
        }

        public ConsumptionLog build() { return log; }
    }

    // ============== UTILITY ==============

    /**
     * Prüft ob dieser Eintrag rezept-basiert ist.
     */
    public boolean isRecipeBased() {
        return recipeId != null;
    }

    /**
     * Prüft ob dieser Eintrag zutat-basiert ist.
     */
    public boolean isIngredientBased() {
        return ingredientId != null;
    }
}
