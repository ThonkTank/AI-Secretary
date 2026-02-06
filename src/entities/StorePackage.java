package entities;

import java.time.LocalDate;

/**
 * Repräsentiert eine Packungsgröße eines Produkts in einem bestimmten Laden.
 * User trägt nach dem Einkauf die tatsächliche Packungsgröße + Laden ein.
 * Hilft bei künftigen Einkaufslisten-Optimierungen (Single-Store-Shopping).
 */
public class StorePackage {

    public Long id;
    public Long ingredientId;              // FK zu Ingredient
    public String storeName;               // "Edeka", "Rewe", "Aldi"
    public double packageSize;             // z.B. 500 (für 500g Gouda)
    public String unit;                    // "g", "ml", "Stück"
    public int priceCents;                 // Preis in Cents (optional, 0 = unbekannt)
    public LocalDate lastPurchased;        // Wann zuletzt dort gekauft

    // Builder
    public static class Builder {
        private final StorePackage pkg = new StorePackage();

        public Builder(Long ingredientId, String storeName, double packageSize, String unit) {
            pkg.ingredientId = ingredientId;
            pkg.storeName = storeName;
            pkg.packageSize = packageSize;
            pkg.unit = unit;
        }

        public Builder price(int cents) { pkg.priceCents = cents; return this; }
        public Builder lastPurchased(LocalDate date) { pkg.lastPurchased = date; return this; }

        public StorePackage build() { return pkg; }
    }

    // ============== UTILITY ==============

    /**
     * Berechnet wie viele Packungen benötigt werden für eine gegebene Menge.
     * @param neededAmount Benötigte Menge
     * @return Anzahl Packungen (aufgerundet)
     */
    public int calculatePackagesNeeded(double neededAmount) {
        return (int) Math.ceil(neededAmount / packageSize);
    }

    /**
     * Berechnet die Gesamtmenge beim Kauf einer bestimmten Anzahl Packungen.
     */
    public double getTotalAmount(int packages) {
        return packages * packageSize;
    }

    /**
     * Berechnet den Überschuss beim Kauf für eine benötigte Menge.
     */
    public double getExcess(double neededAmount) {
        int packages = calculatePackagesNeeded(neededAmount);
        return getTotalAmount(packages) - neededAmount;
    }

    /**
     * Formatiert den Preis für die Anzeige.
     * @return z.B. "4,99 €" oder null wenn kein Preis bekannt
     */
    public String getFormattedPrice() {
        if (priceCents <= 0) return null;
        return String.format("%.2f €", priceCents / 100.0).replace('.', ',');
    }
}
