package entities;

/**
 * Monatliches Budgetlimit pro Kategorie.
 */
public class BudgetLimit {

    public Long id;
    public Long categoryId;                  // FK zu categories
    public String yearMonth;                 // "2026-02" für Februar 2026
    public int limitCents;
    public int spentCents;                   // Cache, berechnet aus Transaktionen
    public int rolloverCents;                // Übertrag vom Vormonat (positiv = gespart)
    public String notes;

    // ============== BUILDER ==============

    public static class Builder {
        private final BudgetLimit bl = new BudgetLimit();

        public Builder(Long categoryId, String yearMonth, int limitCents) {
            bl.categoryId = categoryId;
            bl.yearMonth = yearMonth;
            bl.limitCents = limitCents;
            bl.spentCents = 0;
            bl.rolloverCents = 0;
        }

        public Builder rollover(int cents) { bl.rolloverCents = cents; return this; }
        public Builder notes(String v) { bl.notes = v; return this; }

        public BudgetLimit build() { return bl; }
    }

    // ============== UTILITY METHODS ==============

    public int getRemainingCents() {
        return limitCents + rolloverCents - spentCents;
    }

    public double getSpentPercentage() {
        int total = limitCents + rolloverCents;
        return total > 0 ? (spentCents * 100.0 / total) : 0;
    }

    public boolean isOverBudget() {
        return spentCents > (limitCents + rolloverCents);
    }

    public String getFormattedLimit() {
        return String.format("%.2f EUR", limitCents / 100.0);
    }

    public String getFormattedRemaining() {
        return String.format("%.2f EUR", getRemainingCents() / 100.0);
    }
}
