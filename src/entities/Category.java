package entities;

/**
 * Repräsentiert eine Transaktionskategorie.
 * User-controlled: kann erstellt/bearbeitet/gelöscht werden.
 * Built-in Kategorien werden beim ersten Start via seedTestData angelegt.
 */
public class Category {

    public Long id;
    public String name;           // z.B. "Lebensmittel"
    public String icon;           // Emoji z.B. "🛒"
    public String color;          // Hex z.B. "#4CAF50"
    public boolean isIncome;      // true = Einnahme, false = Ausgabe
    public boolean isBuiltIn;     // true = System-Kategorie (nicht löschbar)
    public int sortOrder;         // Reihenfolge in Listen
    public boolean isActive;      // false = ausgeblendet

    // Builder
    public static class Builder {
        private final Category cat = new Category();

        public Builder(String name, boolean isIncome) {
            cat.name = name;
            cat.isIncome = isIncome;
            cat.isBuiltIn = false;
            cat.isActive = true;
            cat.sortOrder = 0;
        }

        public Builder icon(String v) { cat.icon = v; return this; }
        public Builder color(String v) { cat.color = v; return this; }
        public Builder builtIn(boolean v) { cat.isBuiltIn = v; return this; }
        public Builder sortOrder(int v) { cat.sortOrder = v; return this; }
        public Builder active(boolean v) { cat.isActive = v; return this; }

        public Category build() { return cat; }
    }

    // Utility: Display-String für Listen
    public String getDisplayName() {
        if (icon != null && !icon.isEmpty()) {
            return icon + " " + name;
        }
        return name;
    }
}
