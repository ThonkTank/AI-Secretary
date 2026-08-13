package com.autosecretary.data;

import android.content.Context;

import androidx.room.Room;
import androidx.room.RoomDatabase;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.Executor;

public final class FocusDatabaseFactory {
    private FocusDatabaseFactory() { }

    public static int version() { return FocusDatabase.VERSION; }

    /** Runs on the IO lane before any command may enter the gated database lane. */
    public static void prepare(Context context) {
        LegacyArchiveImporter.installPending(context.getApplicationContext());
        LegacyDatabaseBackup.ensure(context.getApplicationContext());
    }

    public static FocusDatabase open(Context context, Executor databaseExecutor) {
        FocusDatabase database = Room.databaseBuilder(
                        context.getApplicationContext(), FocusDatabase.class, FocusDatabase.NAME)
                .addMigrations(FocusDatabase.migrations())
                .setQueryExecutor(databaseExecutor)
                .setTransactionExecutor(databaseExecutor)
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase database) {
                        LegacyArchiveImporter.verifyMigrated(
                                context.getApplicationContext(), database);
                    }
                })
                .build();
        return database;
    }
}
