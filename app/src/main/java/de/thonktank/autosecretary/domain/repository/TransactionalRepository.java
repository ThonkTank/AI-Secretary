package de.thonktank.autosecretary.domain.repository;

/** Shared transaction boundary for focused repository ports. */
public interface TransactionalRepository {
    @FunctionalInterface
    interface Transaction<T> { T execute(); }

    <T> T inTransaction(Transaction<T> operation);
}
