package com.autosecretary.features.budget.data;

import com.autosecretary.features.budget.domain.BudgetWidgetRepository;

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
    public List<CategorySpendTotal> getCategorySpendTotals(String yearMonth) {
        return budgetLimitDao.getCategorySpendTotals(yearMonth);
    }
}

