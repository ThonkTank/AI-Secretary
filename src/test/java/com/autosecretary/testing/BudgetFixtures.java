package com.autosecretary.testing;

import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.database.AppDatabase;

public final class BudgetFixtures {
    private BudgetFixtures() {
    }

    public static BudgetRoomRepository budgetRepository(AppDatabase db) {
        return new BudgetRoomRepository(
                db.budgetAccountCategoryDao(),
                db.budgetTransactionDao(),
                db.budgetLimitDao(),
                db.budgetRecurringTemplateDao(),
                db);
    }

    public static BudgetImportRoomRepository budgetImportRepository(AppDatabase db) {
        return new BudgetImportRoomRepository(
                db.budgetImportDao(),
                db.budgetRecurringTemplateDao(),
                db.budgetTransactionDao(),
                db.budgetAccountCategoryDao());
    }

    public static BudgetAccount account(String name) {
        return BudgetAccount.create(name);
    }

    public static BudgetCategory expenseCategory(String name) {
        return BudgetCategory.create(name, TransactionDirection.EXPENSE, "T", "#336699");
    }
}
