package entities;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Vorratsartikel im Haushalt.
 */
public class PantryItem {

    public Long id;
    public long ingredientId;
    public String ingredientName;       // Denormalized
    public double amount;
    public String unit;
    public LocalDate purchaseDate;
    public LocalDate expiryDate;
    public StorageLocation location;

    public enum StorageLocation {
        FRIDGE("Kühlschrank", "🧊"),
        FREEZER("Tiefkühler", "❄️"),
        PANTRY("Vorratskammer", "🏠");

        public final String label;
        public final String icon;

        StorageLocation(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public boolean isExpiringSoon() {
        return getDaysUntilExpiry() >= 0 && getDaysUntilExpiry() < 3;
    }

    public boolean isExpired() {
        return expiryDate != null && !LocalDate.now().isBefore(expiryDate);
    }

    public int getDaysUntilExpiry() {
        if (expiryDate == null) return Integer.MAX_VALUE;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public String getFormattedAmount() {
        if (amount == (int) amount) return (int) amount + " " + unit;
        return String.format("%.1f %s", amount, unit);
    }

    public String getExpiryInfo() {
        if (expiryDate == null) return "Kein Ablaufdatum";
        int days = getDaysUntilExpiry();
        if (days < 0) return "Abgelaufen";
        if (days == 0) return "Heute";
        if (days == 1) return "Morgen";
        return "In " + days + " Tagen";
    }

    // Builder
    public static class Builder {
        private final PantryItem p = new PantryItem();

        public Builder(long ingredientId, String ingredientName, double amount, String unit) {
            p.ingredientId = ingredientId;
            p.ingredientName = ingredientName;
            p.amount = amount;
            p.unit = unit;
            p.purchaseDate = LocalDate.now();
            p.location = StorageLocation.PANTRY;
        }

        public Builder expiry(LocalDate v) { p.expiryDate = v; return this; }
        public Builder expiryDate(LocalDate v) { p.expiryDate = v; return this; }
        public Builder purchaseDate(LocalDate v) { p.purchaseDate = v; return this; }
        public Builder location(StorageLocation v) { p.location = v; return this; }

        public PantryItem build() { return p; }
    }
}
