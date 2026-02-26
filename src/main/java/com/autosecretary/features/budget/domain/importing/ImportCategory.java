package com.autosecretary.features.budget.domain.importing;

import com.autosecretary.features.budget.domain.TransactionDirection;

public record ImportCategory(String id, String name, TransactionDirection type) {}
