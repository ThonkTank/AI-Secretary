package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.concurrent.Callable;

/** Room-owned transaction boundary, independent from every persistence capability. */
public final class RoomTransactionRunner implements TransactionRunner {
    private final AppDatabase database;

    public RoomTransactionRunner(AppDatabase database) {
        this.database = database;
    }

    @Override public <T> T inTransaction(Transaction<T> operation) {
        return database.runInTransaction((Callable<T>) operation::execute);
    }
}
