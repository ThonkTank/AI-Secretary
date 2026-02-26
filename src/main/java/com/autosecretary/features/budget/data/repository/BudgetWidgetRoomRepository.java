package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.data.dao.BudgetLimitDao;
import com.autosecretary.features.budget.data.dao.TransactionDao;
import com.autosecretary.features.budget.data.projection.CategorySpendTotal;
import com.autosecretary.features.budget.domain.BudgetWidgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

import java.util.List;

public class BudgetWidgetRoomRepository implements BudgetWidgetRepository {
    private final TransactionDao transactionDao;
    private final BudgetLimitDao budgetLimitDao;

    public BudgetWidgetRoomRepository(TransactionDao transactionDao, BudgetLimitDao budgetLimitDao) {
        this.transactionDao = transactionDao;
        this.budgetLimitDao = budgetLimitDao;
    }

    @Override
    public long getNetBalanceCents() {
        return transactionDao.getNetBalanceCents();
    }

    @Override
    public List<CategorySpendSummary> getCategorySpendTotals(String yearMonth) {
        return budgetLimitDao.getCategorySpendTotals(yearMonth).stream().map(this::mapSpendTotal).toList();
    }

    private CategorySpendSummary mapSpendTotal(CategorySpendTotal total) {
        CategorySpendSummary summary = new CategorySpendSummary();
        summary.categoryId = total.categoryId;
        summary.categoryName = total.categoryName;
        summary.categoryIcon = total.categoryIcon;
        summary.categoryColorHex = total.categoryColorHex;
        summary.spentCents = total.spentCents;
        summary.limitAmount = total.limitAmount;
        return summary;
    }
}
