package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.data.dao.BudgetLimitDao;
import com.autosecretary.features.budget.data.dao.TransactionDao;
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
        return budgetLimitDao.getCategorySpendTotals(yearMonth);
    }
}
