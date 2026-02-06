package entities;

import java.time.LocalDate;

/**
 * Repräsentiert ein Finanzkonto (Girokonto, Sparkonto, Bargeld, Kreditkarte).
 */
public class Account {

    public Long id;
    public String name;                      // "Girokonto DKB"
    public AccountType type;
    public int currentBalanceCents;          // Aktueller Saldo in Cents
    public int initialBalanceCents;          // Startsaldo (zum Abgleich)
    public String currency;                  // "EUR"
    public String institution;               // Bank-Name (optional)
    public String accountNumber;             // IBAN/Kontonummer (optional)
    public String color;                     // Hex-Farbcode für UI (z.B. "#FF4CAF50")
    public String icon;                      // Emoji-Icon (z.B. "🏦")
    public boolean isActive;
    public boolean includeInTotal;           // Soll in Gesamtbudget gezählt werden?
    public LocalDate created;

    public enum AccountType {
        CHECKING,   // Girokonto
        SAVINGS,    // Sparkonto
        CASH,       // Bargeld
        CREDIT      // Kreditkarte
    }

    // Builder
    public static class Builder {
        private final Account account = new Account();

        public Builder(String name, AccountType type) {
            account.name = name;
            account.type = type;
            account.currency = "EUR";
            account.isActive = true;
            account.includeInTotal = true;
            account.created = LocalDate.now();
        }

        public Builder initialBalance(int cents) {
            account.initialBalanceCents = cents;
            account.currentBalanceCents = cents;
            return this;
        }
        public Builder currency(String v) { account.currency = v; return this; }
        public Builder institution(String v) { account.institution = v; return this; }
        public Builder accountNumber(String v) { account.accountNumber = v; return this; }
        public Builder color(String v) { account.color = v; return this; }
        public Builder icon(String v) { account.icon = v; return this; }
        public Builder includeInTotal(boolean v) { account.includeInTotal = v; return this; }

        public Account build() { return account; }
    }

    // Utility: Balance als formatierter String
    public String getFormattedBalance() {
        return String.format("%.2f %s", currentBalanceCents / 100.0, currency);
    }
}
