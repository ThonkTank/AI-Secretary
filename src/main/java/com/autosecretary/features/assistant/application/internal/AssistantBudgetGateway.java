package com.autosecretary.features.assistant.application.internal;

import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.BudgetTransaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The assistant's read door into the budget feature: account/category context for the
 * {@code get_budget_context} tool and category-joined transactions for {@code get_transactions}.
 * Depends only on the budget {@code domain} repository.
 *
 * <p><strong>Threading:</strong> every method touches Room and must be called on the
 * {@code dbExecutor}; callers arrange that.
 */
public class AssistantBudgetGateway {

    /** An account with its current computed balance (cents). */
    public record AccountInfo(String id, String name, long balanceCents) {
    }

    /** A category with its transaction direction ({@code INCOME}/{@code EXPENSE}). */
    public record CategoryInfo(String id, String name, String direction) {
    }

    /** The accounts + categories a user can book against. */
    public record BudgetContext(List<AccountInfo> accounts, List<CategoryInfo> categories) {
    }

    /** One transaction; {@code amountCents} is the absolute value, {@code direction} carries the sign. */
    public record TransactionInfo(LocalDate date, long amountCents, String direction,
                                  String payee, String note, String categoryName) {
    }

    private final BudgetRepository budgetRepository;

    public AssistantBudgetGateway(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public BudgetContext accountsContext() {
        List<AccountInfo> accounts = new ArrayList<>();
        for (BudgetAccount account : budgetRepository.findActiveAccounts()) {
            accounts.add(new AccountInfo(account.id(), account.name(),
                    budgetRepository.getCurrentBalanceCents(account.id())));
        }
        List<CategoryInfo> categories = new ArrayList<>();
        for (BudgetCategory category : budgetRepository.findActiveCategories()) {
            categories.add(new CategoryInfo(category.id(), category.name(), category.direction().name()));
        }
        return new BudgetContext(accounts, categories);
    }

    /**
     * Transactions within {@code [from, to]} (inclusive), optionally restricted to one account,
     * with the category name joined in. Date filtering happens here so the tool contract is stable
     * regardless of the repository's query surface.
     */
    public List<TransactionInfo> transactions(LocalDate from, LocalDate to, String accountId) {
        Map<String, String> categoryNames = new HashMap<>();
        for (BudgetCategory category : budgetRepository.findActiveCategories()) {
            categoryNames.put(category.id(), category.name());
        }
        List<BudgetTransaction> source = (accountId != null && !accountId.isBlank())
                ? budgetRepository.findTransactionsForAccount(accountId)
                : budgetRepository.findAllTransactions();
        List<TransactionInfo> result = new ArrayList<>();
        for (BudgetTransaction tx : source) {
            LocalDate date = tx.bookingDate();
            if (date == null || (from != null && date.isBefore(from)) || (to != null && date.isAfter(to))) {
                continue;
            }
            result.add(new TransactionInfo(
                    date,
                    tx.amountCents(),
                    tx.direction() != null ? tx.direction().name() : null,
                    tx.payee(),
                    tx.note(),
                    categoryNames.get(tx.categoryId())));
        }
        return result;
    }
}
