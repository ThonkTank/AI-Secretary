package com.autosecretary.features.budget.domain.importing;

import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;

public record ImportCategory(String id, String name, BudgetTransactionEntity.TransactionType type) {}
