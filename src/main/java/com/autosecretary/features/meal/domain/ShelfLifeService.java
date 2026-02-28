package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Stateless service for shelf-life and expiry calculations for pantry items.
 *
 * <p><strong>MHD semantics:</strong> The expiry date (German: MHD — Mindesthaltbarkeitsdatum,
 * "best before date") is treated as the <em>last valid day</em>. An item expires strictly
 * <em>after</em> that date, not on it. For example, an item with {@code expiryDate = 2026-03-01}
 * is still valid on {@code 2026-03-01} and first considered expired on {@code 2026-03-02}.
 *
 * <p>Usage: call static methods directly — do not instantiate this class.
 *
 * <p>See also {@code meal/domain/README.md} for the MHD concept overview.
 */
public class ShelfLifeService {

    /**
     * Calculates the expiry date given a purchase date and shelf-life length.
     *
     * @param purchaseDate  date the item was purchased; may be null
     * @param shelfLifeDays number of days the item stays fresh after purchase
     * @return {@code purchaseDate + shelfLifeDays}, or {@code null} if {@code purchaseDate}
     *         is null or {@code shelfLifeDays <= 0} (meaning "no expiry tracked")
     */
    public static LocalDate calculateExpiryDate(LocalDate purchaseDate, int shelfLifeDays) {
        if (purchaseDate == null || shelfLifeDays <= 0) {
            return null;
        }
        return purchaseDate.plusDays(shelfLifeDays);
    }

    /**
     * Returns whether a pantry item has expired as of {@code referenceDate}.
     *
     * <p>MHD semantics: the expiry date is the <em>last valid day</em>. An item is expired
     * only when {@code referenceDate} is strictly after {@code expiryDate}.
     *
     * @param expiryDate    the item's expiry date; if null, the item never expires → returns false
     * @param referenceDate the date to check against; if null → returns false
     * @return {@code true} iff the item has expired
     */
    public static boolean isExpired(LocalDate expiryDate, LocalDate referenceDate) {
        if (expiryDate == null || referenceDate == null) {
            return false;
        }
        // Expiry date is the last valid day (MHD semantics); only expired strictly after.
        return referenceDate.isAfter(expiryDate);
    }

    /**
     * Returns the number of days from {@code referenceDate} until {@code expiryDate}.
     *
     * <p>Negative values mean the item has already expired (e.g. -1 = expired yesterday).
     * Zero means it expires today. Positive values mean it is still valid.</p>
     *
     * @return days until expiry, or {@link Integer#MAX_VALUE} if either argument is null
     *         (i.e. "no expiry set" — treat as never expiring)
     */
    public static int daysUntilExpiry(LocalDate expiryDate, LocalDate referenceDate) {
        if (expiryDate == null || referenceDate == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(referenceDate, expiryDate);
    }

    private ShelfLifeService() {}
}
