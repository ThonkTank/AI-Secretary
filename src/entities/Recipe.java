package entities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repräsentiert ein Rezept mit Zutaten, Nährwerten und Koch-Metadaten.
 */
public class Recipe {

    public Long id;
    public String title;                     // "Hähnchen mit Gemüse"
    public String description;               // Optional
    public String instructions;              // Zubereitungsschritte (Markdown)

    public int servings;                     // Basis-Portionen (für Nährwertberechnung)
    public int prepTimeMinutes;              // Zubereitungszeit (aktive Arbeit)
    public int cookTimeMinutes;              // Kochzeit (passiv, z.B. im Ofen)

    // Koch-Aufwand-Tracking
    public boolean requiresCooking;          // true = Herd/Ofen nötig, false = kalt zubereitet
    public PrepEffort prepEffort;            // NONE, MINIMAL, MODERATE, SIGNIFICANT
    public int servingsPerCooking;           // Wie viele Portionen pro Kochvorgang (z.B. 4)
    public int shelfLifeDays;                // Wie lange hält das fertige Gericht (1-5 Tage)

    // Skalierung
    public int minServings;                  // Minimum sinnvolle Portionen (z.B. 2 für Auflauf)
    public int maxServings;                  // Maximum sinnvolle Portionen (z.B. 8)
    public ScalingPrecision scalingPrecision; // Wie genau müssen Verhältnisse eingehalten werden?

    public MealType mealType;                // BREAKFAST, LUNCH, DINNER, SNACK
    public List<RecipeIngredient> ingredients;  // Zutaten mit Mengen

    public String tags;                      // Comma-separated: "schnell,vegetarisch,lowcarb"
    public LocalDate lastUsed;
    public int usageCount;
    public boolean isFavorite;

    // Berechnete Nährwerte (cached, neu berechnet bei Änderung)
    public int totalCalories;
    public int totalProtein;
    public int totalCarbs;
    public int totalFat;

    // Koch-Aufwand
    public enum PrepEffort {
        NONE(0, "Fertigprodukt"),       // Fertigprodukt, nur auspacken
        MINIMAL(10, "Minimal"),          // Sandwich, Joghurt mit Obst, Müsli
        MODERATE(30, "Moderat"),         // Spiegeleier, Salat, Aufschnitt-Platte
        SIGNIFICANT(60, "Aufwändig");    // Richtiges Kochen

        public final int typicalMinutes;
        public final String label;

        PrepEffort(int minutes, String label) {
            this.typicalMinutes = minutes;
            this.label = label;
        }
    }

    // Skalierungs-Präzision
    public enum ScalingPrecision {
        EXACT("Exakt"),      // Backen — Verhältnisse müssen stimmen
        ROUGH("Ungefähr"),   // Pfannengerichte — grobe Verhältnisse reichen
        NONE("Beliebig");    // Eintopf — alles geht

        public final String label;

        ScalingPrecision(String label) {
            this.label = label;
        }
    }

    // Zutat in einem Rezept
    public record RecipeIngredient(
        Long ingredientId,
        String ingredientName,
        double amount,
        String unit
    ) {}

    // Builder
    public static class Builder {
        private final Recipe r = new Recipe();

        public Builder(String title, MealType mealType) {
            r.title = title;
            r.mealType = mealType;
            r.servings = 2;
            r.prepEffort = PrepEffort.MODERATE;
            r.requiresCooking = true;
            r.servingsPerCooking = 2;
            r.shelfLifeDays = 1;
            r.minServings = 1;
            r.maxServings = 10;
            r.scalingPrecision = ScalingPrecision.ROUGH;
            r.ingredients = new ArrayList<>();
        }

        public Builder description(String v) { r.description = v; return this; }
        public Builder instructions(String v) { r.instructions = v; return this; }
        public Builder servings(int v) { r.servings = v; return this; }
        public Builder prepTime(int minutes) { r.prepTimeMinutes = minutes; return this; }
        public Builder cookTime(int minutes) { r.cookTimeMinutes = minutes; return this; }
        public Builder requiresCooking(boolean v) { r.requiresCooking = v; return this; }
        public Builder prepEffort(PrepEffort v) { r.prepEffort = v; return this; }
        public Builder servingsPerCooking(int v) { r.servingsPerCooking = v; return this; }
        public Builder shelfLife(int days) { r.shelfLifeDays = days; return this; }
        public Builder minServings(int v) { r.minServings = v; return this; }
        public Builder maxServings(int v) { r.maxServings = v; return this; }
        public Builder scalingPrecision(ScalingPrecision v) { r.scalingPrecision = v; return this; }
        public Builder tags(String v) { r.tags = v; return this; }
        public Builder favorite() { r.isFavorite = true; return this; }

        public Builder addIngredient(Long id, String name, double amount, String unit) {
            r.ingredients.add(new RecipeIngredient(id, name, amount, unit));
            return this;
        }

        public Recipe build() { return r; }
    }

    // ============== UTILITY ==============

    /**
     * Gesamtzeit (Zubereitung + Kochen).
     */
    public int getTotalTime() {
        return prepTimeMinutes + cookTimeMinutes;
    }

    /**
     * Kalorien pro Portion.
     */
    public int getCaloriesPerServing() {
        return servings > 0 ? totalCalories / servings : 0;
    }

    /**
     * Prüft ob ein Tag als Tag hat.
     */
    public boolean hasTag(String tag) {
        return tags != null && tags.toLowerCase().contains(tag.toLowerCase());
    }

    /**
     * Prüft ob das Rezept vegetarisch ist.
     */
    public boolean isVegetarian() {
        return hasTag("vegetarisch") || hasTag("vegan");
    }

    /**
     * Prüft ob das Rezept ein Schnellgericht ist (< 15min aktive Zeit).
     */
    public boolean isQuick() {
        return prepTimeMinutes <= 15;
    }
}
