package controller;

import android.content.Context;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import data.BudgetDisplayData;
import entities.Account;
import entities.BudgetLimit;
import entities.Category;
import entities.Transaction;
import repository.SQLrepo;
import repository.Table;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * BUDGET MANAGER - UI-Controller für die Budget-Anzeige
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Stellt die Schnittstelle zwischen Budget-UI und Datenbank dar.
 *   Lädt Konten, Transaktionen und Budget-Limits und konvertiert sie in
 *   UI-freundliche Records.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * DATENFLUSS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │                           SQLite DB                                     │
 *   │  accounts, transactions, budget_limits                                  │
 *   └───────────────────────────────┬─────────────────────────────────────────┘
 *                                   │ provideX() Methoden
 *                                   ▼
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │                    UI Records (AccountEntry, etc.)                      │
 *   │  Flache, formatierte Daten für die Anzeige                              │
 *   └───────────────────────────────┬─────────────────────────────────────────┘
 *                                   │ UI zeigt Daten an
 *                                   ▼
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │                    createTransaction() etc.                             │
 *   │  User erstellt Transaktion → DB Update → Listener benachrichtigt        │
 *   └─────────────────────────────────────────────────────────────────────────┘
 */
public class budgetManager {

    private Context context;
    private SQLrepo repo;
    private BudgetListener listener;

    public budgetManager(Context context) {
        this.context = context;
        this.repo = SQLrepo.getInstance(context);
    }

    // ============================================================================
    // LISTENER INTERFACE
    // ============================================================================

    public interface BudgetListener {
        void onDataUpdated();
    }

    public void setListener(BudgetListener listener) {
        this.listener = listener;
    }

    private void notifyListener() {
        if (listener != null) listener.onDataUpdated();
        // Budget-Widget aktualisieren via WidgetUpdateManager (entkoppelt)
        WidgetUpdateManager.notifyUpdate(context, WidgetUpdateManager.DataDomain.BUDGET);
    }

    // ============================================================================
    // UI RECORDS
    // ============================================================================

    /**
     * Konto-Zusammenfassung für die Anzeige.
     */
    public record AccountEntry(
        Long accountId,
        String name,
        String icon,
        String color,
        Account.AccountType type,
        int balanceCents,
        String formatted,
        boolean includeInTotal
    ) {}

    /**
     * Transaktion für die Anzeige.
     */
    public record TransactionEntry(
        Long id,
        Long accountId,
        String accountName,
        int amountCents,
        String formatted,
        boolean isIncome,
        LocalDate date,
        String dateFormatted,
        Long categoryId,
        String categoryLabel,
        String categoryIcon,
        String description,
        String payee,
        boolean isRecurring
    ) {}

    /**
     * Budget-Status für die Anzeige.
     */
    public record BudgetEntry(
        Long limitId,
        Long categoryId,
        String categoryLabel,
        String categoryIcon,
        String yearMonth,
        int limitCents,
        int spentCents,
        int rolloverCents,
        int remainingCents,
        double percentUsed,
        boolean isOverBudget,
        String formattedLimit,
        String formattedSpent,
        String formattedRemaining
    ) {}

    /**
     * Gesamtübersicht für die Anzeige.
     */
    public record BudgetSummary(
        int totalBalanceCents,
        int monthlyIncomeCents,
        int monthlyExpensesCents,
        int monthlyNetCents,
        String formattedTotal,
        String formattedIncome,
        String formattedExpenses,
        String formattedNet
    ) {}

    /**
     * Kategorie-Option für Dropdown/Spinner.
     */
    public record CategoryOption(
        Long categoryId,
        String label,
        String icon,
        boolean isIncome
    ) {}

    // ============================================================================
    // READ OPERATIONS
    // ============================================================================

    /**
     * Liefert alle aktiven Konten als AccountEntry-Liste.
     */
    public List<AccountEntry> provideAccounts() {
        List<AccountEntry> entries = new ArrayList<>();
        List<Account> accounts = repo.fetchAll(Table.ACCOUNTS, Map.of("is_active", "1"));

        for (Account acc : accounts) {
            entries.add(new AccountEntry(
                acc.id,
                acc.name,
                acc.icon,
                acc.color,
                acc.type,
                acc.currentBalanceCents,
                BudgetDisplayData.formatCents(acc.currentBalanceCents),
                acc.includeInTotal
            ));
        }

        return entries;
    }

    /**
     * Liefert die Gesamtübersicht für einen Monat.
     */
    public BudgetSummary provideSummary(String yearMonth) {
        // Gesamtsaldo aller Konten (mit includeInTotal=true)
        int totalBalance = 0;
        List<Account> accounts = repo.fetchAll(Table.ACCOUNTS, Map.of("is_active", "1"));
        for (Account acc : accounts) {
            if (acc.includeInTotal) {
                totalBalance += acc.currentBalanceCents;
            }
        }

        // Monatliche Einnahmen und Ausgaben berechnen
        int monthlyIncome = 0;
        int monthlyExpenses = 0;

        List<Transaction> allTx = repo.fetchAll(Table.TRANSACTIONS, null);
        for (Transaction tx : allTx) {
            if (tx.transactionDate == null) continue;

            // Nur Transaktionen im angegebenen Monat
            String txMonth = BudgetDisplayData.toYearMonth(tx.transactionDate);
            if (!txMonth.equals(yearMonth)) continue;

            if (tx.isIncome) {
                monthlyIncome += tx.amountCents;
            } else {
                monthlyExpenses += Math.abs(tx.amountCents);
            }
        }

        int monthlyNet = monthlyIncome - monthlyExpenses;

        return new BudgetSummary(
            totalBalance,
            monthlyIncome,
            monthlyExpenses,
            monthlyNet,
            BudgetDisplayData.formatCents(totalBalance),
            BudgetDisplayData.formatCents(monthlyIncome),
            BudgetDisplayData.formatCents(monthlyExpenses),
            BudgetDisplayData.formatCentsWithSign(monthlyNet)
        );
    }

    /**
     * Liefert die letzten N Transaktionen.
     */
    public List<TransactionEntry> provideRecentTransactions(int limit) {
        List<TransactionEntry> entries = new ArrayList<>();

        // Alle Transaktionen laden und nach Datum sortieren (optimiert via fetchAll)
        List<Transaction> allTx = repo.fetchAll(Table.TRANSACTIONS, null);
        allTx.sort((a, b) -> {
            if (a.transactionDate == null && b.transactionDate == null) return 0;
            if (a.transactionDate == null) return 1;
            if (b.transactionDate == null) return -1;
            return b.transactionDate.compareTo(a.transactionDate);
        });

        // Nur die letzten N zurückgeben
        int count = 0;
        for (Transaction tx : allTx) {
            if (count >= limit) break;
            entries.add(transactionToEntry(tx));
            count++;
        }

        return entries;
    }

    /**
     * Liefert Transaktionen mit optionalen Filtern.
     */
    public List<TransactionEntry> provideTransactions(
            Long accountId, String yearMonth, Long categoryId) {

        List<TransactionEntry> entries = new ArrayList<>();
        List<Transaction> allTx = repo.fetchAll(Table.TRANSACTIONS, null);

        for (Transaction tx : allTx) {
            // Filter: Account
            if (accountId != null && !accountId.equals(tx.accountId)) continue;

            // Filter: Monat
            if (yearMonth != null && tx.transactionDate != null) {
                String txMonth = BudgetDisplayData.toYearMonth(tx.transactionDate);
                if (!txMonth.equals(yearMonth)) continue;
            }

            // Filter: Kategorie
            if (categoryId != null && !categoryId.equals(tx.categoryId)) continue;

            entries.add(transactionToEntry(tx));
        }

        // Nach Datum sortieren (neueste zuerst)
        entries.sort((a, b) -> b.date().compareTo(a.date()));
        return entries;
    }

    /**
     * Liefert Budget-Limits für einen Monat.
     */
    public List<BudgetEntry> provideBudgetLimits(String yearMonth) {
        List<BudgetEntry> entries = new ArrayList<>();
        List<BudgetLimit> limits = repo.fetchAll(Table.BUDGET_LIMITS, Map.of("year_month", yearMonth));

        for (BudgetLimit limit : limits) {

            // Kategorie laden
            Category cat = limit.categoryId != null ? repo.fetch(Table.CATEGORIES, limit.categoryId) : null;
            String catLabel = cat != null ? cat.name : "Unbekannt";
            String catIcon = cat != null ? cat.icon : "";

            // Ausgaben für diese Kategorie im Monat berechnen
            int spent = calculateSpentForCategory(yearMonth, limit.categoryId);

            int remaining = limit.limitCents + limit.rolloverCents - spent;
            double percentUsed = (limit.limitCents + limit.rolloverCents) > 0
                ? (double) spent / (limit.limitCents + limit.rolloverCents)
                : 0.0;

            entries.add(new BudgetEntry(
                limit.id,
                limit.categoryId,
                catLabel,
                catIcon,
                limit.yearMonth,
                limit.limitCents,
                spent,
                limit.rolloverCents,
                remaining,
                percentUsed,
                remaining < 0,
                BudgetDisplayData.formatCents(limit.limitCents),
                BudgetDisplayData.formatCents(spent),
                BudgetDisplayData.formatCents(remaining)
            ));
        }

        // Nach Kategorie-Label sortieren
        entries.sort((a, b) -> a.categoryLabel().compareTo(b.categoryLabel()));
        return entries;
    }

    /**
     * Liefert alle Kategorien als Options für Dropdown.
     */
    public List<CategoryOption> provideCategories() {
        List<CategoryOption> options = new ArrayList<>();
        List<Category> categories = repo.fetchAll(Table.CATEGORIES, Map.of("is_active", "1"));

        for (Category cat : categories) {
            options.add(new CategoryOption(
                cat.id,
                cat.name,
                cat.icon,
                cat.isIncome
            ));
        }

        // Sortieren nach sortOrder, dann Name
        options.sort((a, b) -> a.label().compareTo(b.label()));
        return options;
    }

    /**
     * Liefert nur Ausgaben-Kategorien (für Budget-Limits).
     */
    public List<CategoryOption> provideExpenseCategories() {
        List<CategoryOption> options = new ArrayList<>();
        List<Category> categories = repo.fetchAll(Table.CATEGORIES, Map.of("is_active", "1", "is_income", "0"));

        for (Category cat : categories) {
            options.add(new CategoryOption(
                cat.id,
                cat.name,
                cat.icon,
                false
            ));
        }

        options.sort((a, b) -> a.label().compareTo(b.label()));
        return options;
    }

    /**
     * Gibt den aktuellen Monat als yearMonth-String zurück (z.B. "2026-02").
     */
    public String getCurrentYearMonth() {
        return BudgetDisplayData.toYearMonth(YearMonth.now());
    }

    // ============================================================================
    // BUDGET CALCULATIONS (shared by Widget + Scheduling)
    // ============================================================================

    /**
     * Berechnet den Gesamtsaldo aller aktiven Konten (mit includeInTotal=true).
     * @return Gesamtsaldo in Cents
     */
    public int getTotalBalanceCents() {
        return calculateTotalBalance(repo);
    }

    /**
     * Statische Version fuer Scheduling (akzeptiert Repo Interface).
     */
    public static int calculateTotalBalance(repository.Repo repo) {
        int total = 0;
        List<Account> accounts = repo.fetchAll(Table.ACCOUNTS, Map.of("is_active", "1"));
        for (Account acc : accounts) {
            if (acc.includeInTotal) {
                total += acc.currentBalanceCents;
            }
        }
        return total;
    }

    /**
     * Berechnet das freie Budget.
     */
    public int getFreeBudgetCents(Long accountId, LocalDate referenceDate) {
        return calculateFreeBudget(repo, accountId, referenceDate);
    }

    /**
     * Convenience-Methode: Freies Budget ab heute.
     */
    public int getFreeBudgetCents() {
        return getFreeBudgetCents(null, LocalDate.now());
    }

    /**
     * Statische Version fuer Scheduling (akzeptiert Repo Interface).
     * HINWEIS: Fuer Scheduling zusaetzlich committedBudgetCents abziehen!
     */
    public static int calculateFreeBudget(repository.Repo repo, Long accountId, LocalDate referenceDate) {
        int freeBudget = 0;

        // 1. Summe aller aktiven Konten (oder nur spezifisches Konto)
        if (accountId != null) {
            Account acc = repo.fetch(Table.ACCOUNTS, accountId);
            if (acc != null) freeBudget = acc.currentBalanceCents;
        } else {
            List<Account> accounts = repo.fetchAll(Table.ACCOUNTS, Map.of("is_active", "1"));
            for (Account acc : accounts) {
                if (acc.includeInTotal) {
                    freeBudget += acc.currentBalanceCents;
                }
            }
        }

        // 2. Abzug: Wiederkehrende Ausgaben der naechsten 7 Tage
        List<Transaction> recurringTx = repo.fetchAll(Table.TRANSACTIONS, Map.of("is_recurring", "1"));
        for (Transaction tx : recurringTx) {
            if (tx.isIncome) continue;

            LocalDate nextDue = tx.nextDue;
            if (nextDue != null && !nextDue.isBefore(referenceDate) && !nextDue.isAfter(referenceDate.plusDays(7))) {
                int predicted = Math.abs(tx.amountMaxCents != null ? tx.amountMaxCents : tx.amountCents);
                freeBudget -= predicted;
            }
        }

        return Math.max(0, freeBudget);
    }

    // ============================================================================
    // WRITE OPERATIONS
    // ============================================================================

    /**
     * Erstellt eine neue Transaktion und aktualisiert Konto-Saldo.
     */
    public void createTransaction(Transaction tx) {
        repo.write(tx);
        recalculateAccountBalance(tx.accountId);
        if (tx.categoryId != null && !tx.isIncome && tx.transactionDate != null) {
            String yearMonth = BudgetDisplayData.toYearMonth(tx.transactionDate);
            recalculateBudgetSpent(yearMonth, tx.categoryId);
        }
        notifyListener();
    }

    /**
     * Aktualisiert eine bestehende Transaktion.
     */
    public void updateTransaction(Transaction tx) {
        // Altes Konto-Saldo ggf. anpassen (falls Konto geändert wurde)
        Transaction old = repo.fetch(Table.TRANSACTIONS, tx.id);
        repo.write(tx);

        recalculateAccountBalance(tx.accountId);
        if (old != null && !old.accountId.equals(tx.accountId)) {
            recalculateAccountBalance(old.accountId);
        }

        notifyListener();
    }

    /**
     * Löscht eine Transaktion und aktualisiert Konto-Saldo.
     */
    public void deleteTransaction(Long transactionId) {
        Transaction tx = repo.fetch(Table.TRANSACTIONS, transactionId);
        if (tx == null) return;

        repo.getWritableDatabase().delete("transactions", "id = ?",
            new String[]{String.valueOf(transactionId)});
        recalculateAccountBalance(tx.accountId);
        notifyListener();
    }

    /**
     * Erstellt oder aktualisiert ein Budget-Limit.
     */
    public void setBudgetLimit(BudgetLimit limit) {
        if (limit.categoryId == null) return;

        // Prüfen ob bereits ein Limit für diese Kategorie/Monat existiert
        List<Long> existingIds = repo.lookups("budget_limits",
            Map.of("category_id", String.valueOf(limit.categoryId), "year_month", limit.yearMonth), "id");

        if (!existingIds.isEmpty()) {
            limit.id = existingIds.get(0);
        }

        repo.write(limit);
        notifyListener();
    }

    /**
     * Löscht ein Budget-Limit.
     */
    public void deleteBudgetLimit(Long limitId) {
        repo.getWritableDatabase().delete("budget_limits", "id = ?",
            new String[]{String.valueOf(limitId)});
        notifyListener();
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Konvertiert eine Transaction-Entity in ein TransactionEntry-Record.
     */
    private TransactionEntry transactionToEntry(Transaction tx) {
        // Konto-Name laden
        String accountName = "";
        Account acc = repo.fetch(Table.ACCOUNTS, tx.accountId);
        if (acc != null) accountName = acc.name;

        // Kategorie laden
        Category cat = tx.categoryId != null ? repo.fetch(Table.CATEGORIES, tx.categoryId) : null;
        String catLabel = cat != null ? cat.name : "";
        String catIcon = cat != null ? cat.icon : "";

        return new TransactionEntry(
            tx.id,
            tx.accountId,
            accountName,
            tx.amountCents,
            BudgetDisplayData.formatCentsWithSign(tx.amountCents),
            tx.isIncome,
            tx.transactionDate,
            BudgetDisplayData.formatDateShort(tx.transactionDate),
            tx.categoryId,
            catLabel,
            catIcon,
            tx.description,
            tx.payee,
            tx.isRecurring
        );
    }

    /**
     * Berechnet den Konto-Saldo aus allen Transaktionen neu.
     */
    private void recalculateAccountBalance(Long accountId) {
        Account acc = repo.fetch(Table.ACCOUNTS, accountId);
        if (acc == null) return;

        int balance = acc.initialBalanceCents;
        List<Transaction> accountTx = repo.fetchAll(Table.TRANSACTIONS, Map.of("account_id", String.valueOf(accountId)));

        for (Transaction tx : accountTx) {
            balance += tx.amountCents;
        }

        acc.currentBalanceCents = balance;
        repo.write(acc);
    }

    /**
     * Berechnet die Ausgaben für eine Kategorie im Monat.
     */
    private int calculateSpentForCategory(String yearMonth, Long categoryId) {
        if (categoryId == null) return 0;

        int spent = 0;
        List<Transaction> allTx = repo.fetchAll(Table.TRANSACTIONS, null);

        for (Transaction tx : allTx) {
            if (tx.isIncome) continue;
            if (!categoryId.equals(tx.categoryId)) continue;
            if (tx.transactionDate == null) continue;

            String txMonth = BudgetDisplayData.toYearMonth(tx.transactionDate);
            if (!txMonth.equals(yearMonth)) continue;

            spent += Math.abs(tx.amountCents);
        }

        return spent;
    }

    /**
     * Aktualisiert spentCents in einem Budget-Limit.
     */
    private void recalculateBudgetSpent(String yearMonth, Long categoryId) {
        if (categoryId == null) return;

        List<Long> limitIds = repo.lookups("budget_limits",
            Map.of("category_id", String.valueOf(categoryId), "year_month", yearMonth), "id");

        if (limitIds.isEmpty()) return;

        BudgetLimit limit = repo.fetch(Table.BUDGET_LIMITS, limitIds.get(0));
        if (limit == null) return;

        limit.spentCents = calculateSpentForCategory(yearMonth, categoryId);
        repo.write(limit);
    }

    // ============================================================================
    // IMPORT OPERATIONS
    // ============================================================================

    /**
     * Liefert alle aktiven Kategorien als Liste (für Claude-Prompt).
     */
    public List<Category> provideAllCategories() {
        List<Category> categories = repo.fetchAll(Table.CATEGORIES, Map.of("is_active", "1"));

        // Sortieren nach sortOrder
        categories.sort((a, b) -> {
            if (a.sortOrder != b.sortOrder) return Integer.compare(a.sortOrder, b.sortOrder);
            return a.name.compareTo(b.name);
        });

        return categories;
    }

    /**
     * Prüft ob eine Transaktion mit diesem Hash bereits existiert.
     */
    public boolean isDuplicateTransaction(String importHash) {
        if (importHash == null || importHash.isEmpty()) return false;
        List<Long> ids = repo.lookups("transactions", Map.of("import_hash", importHash), "id");
        return !ids.isEmpty();
    }

    /**
     * Speichert einen Import-Datensatz.
     */
    public void saveImport(entities.Import imp) {
        repo.write(imp);
    }

    /**
     * Liefert das Repository (für direkten Zugriff in ImportProcessor).
     */
    public SQLrepo getRepo() {
        return repo;
    }

    /**
     * Erstellt eine Transaktion aus Import-Daten (ohne Listener-Benachrichtigung).
     * Für Batch-Imports optimiert - ruft notifyListener() NICHT auf.
     */
    public void createTransactionQuiet(Transaction tx) {
        repo.write(tx);
        recalculateAccountBalance(tx.accountId);
    }

    /**
     * Benachrichtigt Listener nach Batch-Import.
     */
    public void notifyDataUpdated() {
        notifyListener();
    }

    /**
     * Liefert alle Transaktionen für ein Konto (für Pattern-Erkennung).
     */
    public List<Transaction> getAllTransactionsForAccount(Long accountId) {
        Map<String, String> filters = accountId != null
            ? Map.of("account_id", String.valueOf(accountId))
            : null;

        List<Transaction> transactions = repo.fetchAll(Table.TRANSACTIONS, filters);

        // Nach Datum sortieren
        transactions.sort((a, b) -> {
            if (a.transactionDate == null && b.transactionDate == null) return 0;
            if (a.transactionDate == null) return 1;
            if (b.transactionDate == null) return -1;
            return a.transactionDate.compareTo(b.transactionDate);
        });
        return transactions;
    }

    // ============================================================================
    // RECURRING TEMPLATE OPERATIONS
    // ============================================================================

    /**
     * Erstellt ein wiederkehrendes Template aus einem erkannten Pattern.
     * @param candidate Der erkannte Kandidat
     * @param accountId Ziel-Konto
     * @return ID des neuen Templates
     */
    public Long createRecurringTemplate(RecurringPatternDetector.RecurringCandidate candidate, Long accountId) {
        Transaction.Builder builder = new Transaction.Builder(
            accountId,
            candidate.avgAmountCents(),
            LocalDate.now(),
            candidate.categoryId()
        ).payee(candidate.displayPayee());

        // Recurring-Typ konfigurieren
        switch (candidate.suggestedType()) {
            case MONTHLY_DAY:
                builder.monthlyOnDay(candidate.suggestedValue());
                break;
            case MONTHLY_LAST:
                builder.monthlyLast();
                break;
            case WEEKLY:
                builder.weekly(candidate.suggestedDayOfWeek());
                break;
            case INTERVAL:
                builder.interval(candidate.suggestedValue(), Transaction.RepUnits.DAY);
                break;
        }

        // Nächstes Fälligkeitsdatum berechnen
        builder.nextDue(calculateNextDue(candidate));

        Transaction template = builder.build();

        // Varianz-Statistiken initialisieren
        template.amountMinCents = candidate.minAmountCents();
        template.amountMaxCents = candidate.maxAmountCents();
        template.amountAvgCents = candidate.avgAmountCents();
        template.occurrenceCount = candidate.transactionIds().size();

        repo.write(template);
        return template.id;
    }

    /**
     * Verknüpft existierende Transaktionen mit einem wiederkehrenden Template.
     * @param transactionIds Liste der zu verknüpfenden Transaktions-IDs
     * @param templateId ID des Templates
     */
    public void linkTransactionsToTemplate(List<Long> transactionIds, Long templateId) {
        for (Long txId : transactionIds) {
            Transaction tx = repo.fetch(Table.TRANSACTIONS, txId);
            if (tx != null) {
                tx.parentRecurringId = templateId;
                repo.write(tx);
            }
        }
    }

    /**
     * Berechnet das nächste Fälligkeitsdatum basierend auf dem Kandidaten.
     */
    private LocalDate calculateNextDue(RecurringPatternDetector.RecurringCandidate candidate) {
        LocalDate today = LocalDate.now();

        switch (candidate.suggestedType()) {
            case MONTHLY_DAY:
                int targetDay = candidate.suggestedValue();
                LocalDate thisMonth = today.withDayOfMonth(
                    Math.min(targetDay, today.lengthOfMonth()));
                if (!thisMonth.isAfter(today)) {
                    return today.plusMonths(1).withDayOfMonth(
                        Math.min(targetDay, today.plusMonths(1).lengthOfMonth()));
                }
                return thisMonth;

            case MONTHLY_LAST:
                LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
                if (!endOfMonth.isAfter(today)) {
                    return today.plusMonths(1).withDayOfMonth(
                        today.plusMonths(1).lengthOfMonth());
                }
                return endOfMonth;

            case WEEKLY:
                java.time.DayOfWeek targetDow = candidate.suggestedDayOfWeek();
                LocalDate nextOccurrence = today;
                while (nextOccurrence.getDayOfWeek() != targetDow) {
                    nextOccurrence = nextOccurrence.plusDays(1);
                }
                if (!nextOccurrence.isAfter(today)) {
                    nextOccurrence = nextOccurrence.plusWeeks(1);
                }
                return nextOccurrence;

            case INTERVAL:
                // Nächstes Fälligkeitsdatum = heute + Intervall
                return today.plusDays(candidate.suggestedValue());

            default:
                return today.plusMonths(1);
        }
    }
}
