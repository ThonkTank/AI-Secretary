package entities;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Buchung - kann Income/Expense + einmalig/recurring sein.
 * Vereinigt alle Transaktionstypen in einer Klasse.
 */
public class Transaction {

    public Long id;
    public Long accountId;                   // FK zu Account
    public int amountCents;                  // Positiv=Einnahme, Negativ=Ausgabe
    public LocalDate transactionDate;
    public LocalDate valueDate;              // Wertstellung
    public Long categoryId;                  // FK zu Category (user-controlled)
    public String subcategory;
    public String description;
    public String payee;

    // Typ-Flags
    public boolean isIncome;
    public boolean isRecurring;

    // Recurring-Felder (nur relevant wenn isRecurring=true)
    public RecurringType repetitionType;
    public int repetitionValue;              // Tag (1-31) oder Intervall
    public RepUnits repetitionUnit;
    public DayOfWeek dayOfWeek;
    public LocalDate nextDue;
    public LocalDate lastOccurrence;

    // Varianz-Tracking (für Predictions bei recurring)
    public Integer amountMinCents;
    public Integer amountMaxCents;
    public Integer amountAvgCents;
    public int occurrenceCount;

    // Verknüpfungen
    public Long parentRecurringId;           // Generiert von welcher recurring Transaction?
    public Long linkedTransactionId;         // Gegenbuchung bei Umbuchungen
    public Long importId;                    // FK zu Import
    public String importHash;                // Für Duplikat-Erkennung
    public boolean isConfirmed;
    public boolean isPredicted;

    public LocalDateTime created;

    // ============== ENUMS ==============

    public enum RecurringType {
        MONTHLY_DAY,    // Am X. jeden Monats (z.B. am 1.)
        MONTHLY_LAST,   // Am letzten Tag des Monats
        WEEKLY,         // Jeden Montag/Dienstag/etc.
        INTERVAL        // Alle X Tage/Wochen/Monate
    }

    public enum RepUnits {
        DAY(1), WEEK(7), MONTH(30);
        public final int value;
        RepUnits(int v) { this.value = v; }
    }

    // TransactionCategory enum entfernt - Kategorien werden jetzt
    // aus der categories-Tabelle geladen (user-controlled)

    // ============== BUILDER ==============

    public static class Builder {
        private final Transaction tx = new Transaction();

        public Builder(Long accountId, int amountCents, LocalDate date, Long categoryId) {
            tx.accountId = accountId;
            tx.amountCents = amountCents;
            tx.transactionDate = date;
            tx.categoryId = categoryId;
            tx.isIncome = amountCents > 0;
            tx.isRecurring = false;
            tx.isConfirmed = true;
            tx.isPredicted = false;
            tx.created = LocalDateTime.now();
        }

        // Einmalige Transaktion
        public Builder valueDate(LocalDate v) { tx.valueDate = v; return this; }
        public Builder subcategory(String v) { tx.subcategory = v; return this; }
        public Builder description(String v) { tx.description = v; return this; }
        public Builder payee(String v) { tx.payee = v; return this; }
        public Builder isConfirmed(boolean v) { tx.isConfirmed = v; return this; }
        public Builder isPredicted(boolean v) { tx.isPredicted = v; return this; }

        // Verknüpfungen
        public Builder parentRecurringId(Long v) { tx.parentRecurringId = v; return this; }
        public Builder linkedTransactionId(Long v) { tx.linkedTransactionId = v; return this; }
        public Builder importId(Long v) { tx.importId = v; return this; }
        public Builder importHash(String v) { tx.importHash = v; return this; }

        // Recurring-Konfiguration
        public Builder monthlyOnDay(int dayOfMonth) {
            tx.isRecurring = true;
            tx.repetitionType = RecurringType.MONTHLY_DAY;
            tx.repetitionValue = dayOfMonth;
            return this;
        }

        public Builder monthlyLast() {
            tx.isRecurring = true;
            tx.repetitionType = RecurringType.MONTHLY_LAST;
            return this;
        }

        public Builder weekly(DayOfWeek dow) {
            tx.isRecurring = true;
            tx.repetitionType = RecurringType.WEEKLY;
            tx.dayOfWeek = dow;
            return this;
        }

        public Builder interval(int value, RepUnits unit) {
            tx.isRecurring = true;
            tx.repetitionType = RecurringType.INTERVAL;
            tx.repetitionValue = value;
            tx.repetitionUnit = unit;
            return this;
        }

        public Builder nextDue(LocalDate v) { tx.nextDue = v; return this; }

        public Transaction build() { return tx; }
    }

    // ============== UTILITY METHODS ==============

    public boolean isExpense() { return amountCents < 0; }
    public boolean isTransfer() { return linkedTransactionId != null; }

    public String getFormattedAmount() {
        return String.format("%+.2f EUR", amountCents / 100.0);
    }

    /**
     * Berechnet das nächste Fälligkeitsdatum basierend auf dem aktuellen.
     */
    public LocalDate calcNextOccurrence(LocalDate from) {
        if (!isRecurring || repetitionType == null) return null;
        return switch (repetitionType) {
            case MONTHLY_DAY -> {
                LocalDate next = from.plusMonths(1);
                int targetDay = Math.min(repetitionValue, next.lengthOfMonth());
                yield next.withDayOfMonth(targetDay);
            }
            case MONTHLY_LAST -> from.plusMonths(1).withDayOfMonth(
                from.plusMonths(1).lengthOfMonth());
            case WEEKLY -> from.plusWeeks(1);
            case INTERVAL -> switch (repetitionUnit) {
                case DAY -> from.plusDays(repetitionValue);
                case WEEK -> from.plusWeeks(repetitionValue);
                case MONTH -> from.plusMonths(repetitionValue);
            };
        };
    }

    /**
     * Aktualisiert Varianz-Statistiken nach neuer Buchung.
     */
    public void updateStats(int actualAmountCents) {
        occurrenceCount++;

        if (amountMinCents == null || actualAmountCents < amountMinCents) {
            amountMinCents = actualAmountCents;
        }
        if (amountMaxCents == null || actualAmountCents > amountMaxCents) {
            amountMaxCents = actualAmountCents;
        }

        // Laufender Durchschnitt
        if (amountAvgCents == null) {
            amountAvgCents = actualAmountCents;
        } else {
            amountAvgCents = (amountAvgCents * (occurrenceCount - 1) + actualAmountCents)
                             / occurrenceCount;
        }
    }
}
