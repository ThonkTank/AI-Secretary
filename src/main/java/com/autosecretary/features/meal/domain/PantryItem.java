package com.autosecretary.features.meal.domain;

import com.autosecretary.features.meal.domain.internal.MealAmountFormat;

import java.time.LocalDate;

/**
 * A pantry inventory entry tracking a stored ingredient with its quantity, expiry date,
 * and storage location.
 *
 * <p>{@code ingredientName} is denormalized from the {@link Ingredient} record for display
 * without an extra lookup.
 *
 * <p>Expiry warnings are based on {@link ShelfLifeService} semantics: the expiry date is the
 * <em>last valid day</em> (MHD). {@link #isExpiringSoon()} triggers when fewer than
 * {@code EXPIRY_WARNING_DAYS} (3) days remain.
 *
 * <p>Note: {@link #isExpired()} and {@link #getDaysUntilExpiry()} call {@code LocalDate.now()}
 * internally — they are non-deterministic. For deterministic logic, use {@link ShelfLifeService}
 * directly with an explicit reference date.
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

    private static final int EXPIRY_WARNING_DAYS = 3;

    public boolean isExpiringSoon() {
        int days = getDaysUntilExpiry();
        return days >= 0 && days < EXPIRY_WARNING_DAYS;
    }

    public boolean isExpired() {
        return ShelfLifeService.isExpired(expiryDate, LocalDate.now());
    }

    public int getDaysUntilExpiry() {
        return ShelfLifeService.daysUntilExpiry(expiryDate, LocalDate.now());
    }

    public String getFormattedAmount() {
        return MealAmountFormat.format(amount, unit);
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

        public Builder expiryDate(LocalDate v) { p.expiryDate = v; return this; }
        public Builder purchaseDate(LocalDate v) { p.purchaseDate = v; return this; }
        public Builder location(StorageLocation v) { p.location = v; return this; }

        public PantryItem build() { return p; }
    }
}
