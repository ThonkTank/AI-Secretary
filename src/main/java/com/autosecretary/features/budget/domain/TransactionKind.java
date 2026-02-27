package com.autosecretary.features.budget.domain;

/** Discriminates standard transactions from linked transfer legs. */
public enum TransactionKind {
    STANDARD,
    INTERNAL_TRANSFER
}
