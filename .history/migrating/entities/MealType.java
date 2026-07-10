package entities;

/**
 * Mahlzeit-Typen fuer Rezepte und Meal-Planning.
 */
public enum MealType {
    BREAKFAST("Frühstück", "🍳"),
    LUNCH("Mittagessen", "🍽️"),
    DINNER("Abendessen", "🍲"),
    SNACK("Snack", "🍎");

    public final String label;
    public final String icon;

    MealType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }
}
