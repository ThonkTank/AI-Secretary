package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.BudgetTransaction;
import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures the app has default budget data on first launch.
 *
 * <p><strong>"Seed data"</strong> means the initial, out-of-the-box data that a new app instance
 * needs to be functional. For the budget feature, this includes:
 * <ul>
 *   <li><strong>Accounts:</strong> "Girokonto" (checking), "Tagesgeld" (savings)</li>
 *   <li><strong>Categories:</strong> Expense categories (housing, groceries, mobility, leisure, other)
 *       and income category (salary), each with emoji icons and colors</li>
 *   <li><strong>Demo transactions:</strong> 7-8 sample transactions across different categories
 *       to give new users a feel for the app's functionality (seeded only if no transactions exist)</li>
 * </ul>
 *
 * <p><strong>When called:</strong>
 * Typically during app initialization (see AppCompositionRoot). Safe to call multiple times;
 * only creates data if not already present.
 *
 * <p><strong>Effect:</strong> Idempotent — if categories exist, no categories are created;
 * if transactions exist, no demo transactions are seeded.
 *
 * @see SeedResult for the return value (categories, accounts, and selected account ID)
 */
public class BudgetSeedService {

    private static final String CAT_SONSTIGES    = "Sonstiges";
    private static final String CAT_MIETE        = "Miete";
    private static final String CAT_LEBENSMITTEL = "Lebensmittel";
    private static final String CAT_MOBILITAET   = "Mobilität";
    private static final String CAT_FREIZEIT     = "Freizeit";
    private static final String CAT_GEHALT       = "Gehalt";

    public record SeedResult(
            List<BudgetCategory> categories,
            List<BudgetAccount> accounts,
            String selectedAccountId
    ) {
    }

    private final BudgetRepository repository;

    public BudgetSeedService(BudgetRepository repository) {
        this.repository = repository;
    }

    public SeedResult ensureDefaultData(String selectedAccountId) {
        List<BudgetAccount> accountList = repository.findActiveAccounts();
        if (accountList.isEmpty()) {
            repository.insertAccount(BudgetAccount.create("Girokonto"));
            repository.insertAccount(BudgetAccount.create("Tagesgeld"));
            accountList = repository.findActiveAccounts();
        }
        ensureDefaultCategories();
        if (repository.findAllTransactions().isEmpty() && !accountList.isEmpty()) {
            String accountId = accountList.get(0).id();
            seedDemoTransactions(accountId, LocalDate.now());
        }

        List<BudgetCategory> categories = repository.findActiveCategories();
        String effectiveAccount = (selectedAccountId == null || selectedAccountId.isBlank()) && !accountList.isEmpty()
                ? accountList.get(0).id()
                : selectedAccountId;

        return new SeedResult(categories, accountList, effectiveAccount);
    }

    private void ensureDefaultCategories() {
        List<BudgetCategory> existing = repository.findActiveCategories();
        if (!existing.isEmpty()) {
            return;
        }
        repository.insertCategory(BudgetCategory.create(CAT_SONSTIGES,    TransactionDirection.EXPENSE, "🏷️", "#9E9E9E"));
        repository.insertCategory(BudgetCategory.create(CAT_MIETE,        TransactionDirection.EXPENSE, "🏠", "#CF8F4F"));
        repository.insertCategory(BudgetCategory.create(CAT_LEBENSMITTEL, TransactionDirection.EXPENSE, "🛒", "#5C7A4D"));
        repository.insertCategory(BudgetCategory.create(CAT_MOBILITAET,   TransactionDirection.EXPENSE, "🚗", "#6B8E7B"));
        repository.insertCategory(BudgetCategory.create(CAT_FREIZEIT,     TransactionDirection.EXPENSE, "🎉", "#7A4DA8"));
        repository.insertCategory(BudgetCategory.create(CAT_GEHALT,       TransactionDirection.INCOME,  "💰", "#4CAF50"));
    }

    private record DemoEntry(String categoryName, int day, TransactionDirection direction,
                             long amountCents, String note) {}

    private void seedDemoTransactions(String accountId, LocalDate reference) {
        List<DemoEntry> entries = List.of(
                new DemoEntry(CAT_GEHALT,       1,  TransactionDirection.INCOME,   240000, "Gehalt"),
                new DemoEntry(CAT_MIETE,        2,  TransactionDirection.EXPENSE,   85000, "Miete"),
                new DemoEntry(CAT_LEBENSMITTEL, 3,  TransactionDirection.EXPENSE,   7840,  "Lebensmittel"),
                new DemoEntry(CAT_SONSTIGES,    5,  TransactionDirection.EXPENSE,   4290,  "Strom"),
                new DemoEntry(CAT_SONSTIGES,    8,  TransactionDirection.EXPENSE,   2999,  "Internet"),
                new DemoEntry(CAT_FREIZEIT,     10, TransactionDirection.EXPENSE,   1990,  "Fitnessstudio"),
                new DemoEntry(CAT_FREIZEIT,     15, TransactionDirection.EXPENSE,   3450,  "Restaurant"),
                new DemoEntry(CAT_MOBILITAET,   18, TransactionDirection.EXPENSE,   6520,  "Tankstelle")
        );
        List<BudgetCategory> categories = repository.findActiveCategories();
        List<BudgetTransaction> entities = new ArrayList<>();
        for (DemoEntry entry : entries) {
            if (entry.day() > reference.getDayOfMonth()) {
                continue;
            }
            String categoryId = findCategoryIdByName(categories, entry.categoryName());
            LocalDate date = reference.withDayOfMonth(entry.day());
            entities.add(BudgetTransaction.create(
                    accountId, categoryId, entry.direction(), entry.amountCents(), date, entry.note()));
        }
        repository.saveTransactions(entities);
    }

    private String findCategoryIdByName(List<BudgetCategory> categories, String name) {
        return categories.stream()
                .filter(c -> name.equals(c.name()))
                .map(BudgetCategory::id)
                .findFirst()
                .orElse(null);
    }
}
