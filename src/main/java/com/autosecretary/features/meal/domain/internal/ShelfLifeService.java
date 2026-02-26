package com.autosecretary.features.meal.domain.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Haltbarkeitsregeln fuer Zutaten, zubereitete Speisen und Vorratsartikel.
 */
public class ShelfLifeService {

    public LocalDate calculateExpiryDate(LocalDate purchaseDate, int shelfLifeDays) {
        if (purchaseDate == null || shelfLifeDays <= 0) {
            return null;
        }
        return purchaseDate.plusDays(shelfLifeDays);
    }

    public boolean isExpired(LocalDate expiryDate, LocalDate referenceDate) {
        if (expiryDate == null || referenceDate == null) {
            return false;
        }
        return !referenceDate.isBefore(expiryDate);
    }

    public int daysUntilExpiry(LocalDate expiryDate, LocalDate referenceDate) {
        if (expiryDate == null || referenceDate == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(referenceDate, expiryDate);
    }
}
