package entities;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Ein Artikel im Vorratsschrank.
 */
public class PantryItem {

    public Long id;
    public Long ingredientId;                // FK zu Ingredient
    public String ingredientName;            // Denormalisiert für schnelle Anzeige
    public double amount;                    // Aktuelle Menge
    public String unit;                      // Einheit

    public LocalDate purchaseDate;           // Kaufdatum
    public LocalDate expiryDate;             // Ablaufdatum (berechnet oder manuell)
    public StorageLocation location;         // FRIDGE, FREEZER, PANTRY

    public enum StorageLocation {
        FRIDGE("Kühlschrank"),
        FREEZER("Gefrierfach"),
        PANTRY("Vorratsschrank");

        public final String label;

        StorageLocation(String label) {
            this.label = label;
        }
    }

    // Builder
    public static class Builder {
        private final PantryItem item = new PantryItem();

        public Builder(Long ingredientId, String ingredientName, double amount, String unit) {
            item.ingredientId = ingredientId;
            item.ingredientName = ingredientName;
            item.amount = amount;
            item.unit = unit;
            item.purchaseDate = LocalDate.now();
            item.location = StorageLocation.PANTRY;
        }

        public Builder purchaseDate(LocalDate v) { item.purchaseDate = v; return this; }
        public Builder expiryDate(LocalDate v) { item.expiryDate = v; return this; }
        public Builder location(StorageLocation v) { item.location = v; return this; }

        public PantryItem build() { return item; }
    }

    // ============== UTILITY ==============

    /**
     * Prüft ob der Artikel bald abläuft (< 3 Tage).
     */
    public boolean isExpiringSoon() {
        if (expiryDate == null) return false;
        long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        return daysUntilExpiry >= 0 && daysUntilExpiry <= 3;
    }

    /**
     * Prüft ob der Artikel bereits abgelaufen ist.
     */
    public boolean isExpired() {
        if (expiryDate == null) return false;
        return LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Tage bis zum Ablauf (negativ wenn abgelaufen).
     */
    public int getDaysUntilExpiry() {
        if (expiryDate == null) return Integer.MAX_VALUE;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /**
     * Verbraucht eine Menge aus dem Vorrat.
     * @return true wenn komplett aufgebraucht
     */
    public boolean consume(double usedAmount) {
        this.amount -= usedAmount;
        return this.amount <= 0;
    }

    /**
     * Formatiert Menge + Einheit für Anzeige.
     */
    public String getFormattedAmount() {
        if (amount == (int) amount) {
            return (int) amount + " " + unit;
        }
        return String.format("%.1f %s", amount, unit);
    }

    /**
     * Formatiert Ablaufdatum für Anzeige.
     */
    public String getExpiryInfo() {
        if (expiryDate == null) return "";
        int days = getDaysUntilExpiry();
        if (days < 0) return "Abgelaufen!";
        if (days == 0) return "Heute";
        if (days == 1) return "Morgen";
        return "in " + days + " Tagen";
    }
}
