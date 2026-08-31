package de.thonktank.autosecretary.domain.transaction;

/** Executes one application operation atomically without widening its persistence ports. */
public interface TransactionRunner {
    @FunctionalInterface
    interface Transaction<T> { T execute(); }

    <T> T inTransaction(Transaction<T> operation);
}
