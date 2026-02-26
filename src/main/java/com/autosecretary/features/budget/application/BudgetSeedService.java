package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BudgetSeedService {

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
            repository.insertAccount(new BudgetAccount("Girokonto"));
            repository.insertAccount(new BudgetAccount("Tagesgeld"));
            repository.insertCategory(new BudgetCategory("Sonstiges", TransactionDirection.EXPENSE));
            repository.insertCategory(new BudgetCategory("Gehalt", TransactionDirection.INCOME));
            accountList = repository.findActiveAccounts();
        }
        ensureDefaultCategories();
        if (repository.findAllTransactions().isEmpty() && !accountList.isEmpty()) {
            String accountId = accountList.get(0).id;
            seedDemoTransactions(accountId, LocalDate.now());
        }

        List<BudgetCategory> categories = repository.getActiveCategories();
        List<BudgetAccount> accounts = repository.findActiveAccounts();
        String resolvedSelected = selectedAccountId;
        if ((resolvedSelected == null || resolvedSelected.isBlank()) && !accounts.isEmpty()) {
            resolvedSelected = accounts.get(0).id;
        }

        return new SeedResult(categories, accounts, resolvedSelected);
    }

    private void ensureDefaultCategories() {
        List<BudgetCategory> existing = repository.getActiveCategories();
        if (!existing.isEmpty()) {
            return;
        }
        repository.insertCategory(new BudgetCategory("Sonstiges", TransactionDirection.EXPENSE, "🏷️", "#9E9E9E"));
        repository.insertCategory(new BudgetCategory("Miete", TransactionDirection.EXPENSE, "🏠", "#FF7043"));
        repository.insertCategory(new BudgetCategory("Lebensmittel", TransactionDirection.EXPENSE, "🛒", "#8BC34A"));
        repository.insertCategory(new BudgetCategory("Mobilität", TransactionDirection.EXPENSE, "🚗", "#03A9F4"));
        repository.insertCategory(new BudgetCategory("Freizeit", TransactionDirection.EXPENSE, "🎉", "#AB47BC"));
        repository.insertCategory(new BudgetCategory("Gehalt", TransactionDirection.INCOME, "💰", "#4CAF50"));
    }

    private void seedDemoTransactions(String accountId, LocalDate reference) {
        List<BudgetCategory> categories = repository.getActiveCategories();
        String incomeCategoryId = findCategoryIdByName(categories, "Gehalt");
        String housingCategoryId = findCategoryIdByName(categories, "Miete");
        String groceryCategoryId = findCategoryIdByName(categories, "Lebensmittel");
        String mobilityCategoryId = findCategoryIdByName(categories, "Mobilität");
        String leisureCategoryId = findCategoryIdByName(categories, "Freizeit");
        String otherCategoryId = findCategoryIdByName(categories, "Sonstiges");

        int maxDay = reference.getDayOfMonth();
        TransactionDirection income = TransactionDirection.INCOME;
        TransactionDirection expense = TransactionDirection.EXPENSE;
        List<BudgetTransactionEntity> entities = new ArrayList<>();
        addDemoTx(entities, accountId, incomeCategoryId, reference, 1, income, 240000, "Gehalt", maxDay);
        addDemoTx(entities, accountId, housingCategoryId, reference, 2, expense, 85000, "Miete", maxDay);
        addDemoTx(entities, accountId, groceryCategoryId, reference, 3, expense, 7840, "Lebensmittel", maxDay);
        addDemoTx(entities, accountId, otherCategoryId, reference, 5, expense, 4290, "Strom", maxDay);
        addDemoTx(entities, accountId, otherCategoryId, reference, 8, expense, 2999, "Internet", maxDay);
        addDemoTx(entities, accountId, leisureCategoryId, reference, 10, expense, 1990, "Fitnessstudio", maxDay);
        addDemoTx(entities, accountId, leisureCategoryId, reference, 15, expense, 3450, "Restaurant", maxDay);
        addDemoTx(entities, accountId, mobilityCategoryId, reference, 18, expense, 6520, "Tankstelle", maxDay);
        repository.saveTransactions(entities);
    }

    private void addDemoTx(List<BudgetTransactionEntity> out,
                           String accountId,
                           String categoryId,
                           LocalDate ref,
                           int day,
                           TransactionDirection type,
                           long amountCents,
                           String note,
                           int maxDay) {
        if (day > maxDay) {
            return;
        }
        LocalDate date = ref.withDayOfMonth(day);
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId,
                categoryId,
                type,
                amountCents,
                date);
        entity.note = note;
        out.add(entity);
    }

    private String findCategoryIdByName(List<BudgetCategory> categories, String name) {
        for (BudgetCategory category : categories) {
            if (name.equals(category.name)) {
                return category.id;
            }
        }
        return null;
    }
}
