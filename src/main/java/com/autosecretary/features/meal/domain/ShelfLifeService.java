package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Haltbarkeitsregeln fuer Zutaten, zubereitete Speisen und Vorratsartikel.
 */
public class ShelfLifeService {

    public static LocalDate calculateExpiryDate(LocalDate purchaseDate, int shelfLifeDays) {
        if (purchaseDate == null || shelfLifeDays <= 0) {
            return null;
        }
        return purchaseDate.plusDays(shelfLifeDays);
    }

    public static boolean isExpired(LocalDate expiryDate, LocalDate referenceDate) {
        if (expiryDate == null || referenceDate == null) {
            return false;
        }
        // Expiry date is the last valid day (MHD semantics); only expired strictly after.
        return referenceDate.isAfter(expiryDate);
    }

    public static int daysUntilExpiry(LocalDate expiryDate, LocalDate referenceDate) {
        if (expiryDate == null || referenceDate == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(referenceDate, expiryDate);
    }

    private ShelfLifeService() {}
}
