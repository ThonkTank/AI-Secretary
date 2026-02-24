package data;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Zentrale Daten-Transformation und Formatierung für Budget-Anzeige.
 *
 * HINWEIS: Kategorie-Labels und Icons werden jetzt aus der DB geladen
 * (entities.Category). Die alten getCategoryLabel/getCategoryIcon Methoden
 * wurden entfernt.
 */
public class BudgetDisplayData {

    /**
     * Formatiert Cents als "1.234,56 EUR" (deutsches Format).
     */
    public static String formatCents(int cents) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        return nf.format(cents / 100.0);
    }

    /**
     * Formatiert Cents mit Vorzeichen als "+1.234,56 EUR" oder "-1.234,56 EUR".
     */
    public static String formatCentsWithSign(int cents) {
        String formatted = formatCents(Math.abs(cents));
        return (cents >= 0 ? "+" : "-") + formatted.replace("-", "");
    }

    /**
     * Formatiert ein Datum als "02.02.2026" (deutsches Format).
     */
    public static String formatDate(java.time.LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    /**
     * Formatiert ein Datum kurz als "02.02." (ohne Jahr).
     */
    public static String formatDateShort(java.time.LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM."));
    }

    /**
     * Gibt den deutschen Monatsnamen zurück (z.B. "Februar 2026").
     */
    public static String formatYearMonth(String yearMonth) {
        String[] parts = yearMonth.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        String[] monthNames = {"Januar", "Februar", "März", "April", "Mai", "Juni",
                               "Juli", "August", "September", "Oktober", "November", "Dezember"};
        return monthNames[month - 1] + " " + year;
    }

    /**
     * Konvertiert ein LocalDate zu yearMonth-String im Format "2026-02".
     */
    public static String toYearMonth(java.time.LocalDate date) {
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    /**
     * Konvertiert ein YearMonth zu yearMonth-String im Format "2026-02".
     */
    public static String toYearMonth(java.time.YearMonth ym) {
        return ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
    }
}
