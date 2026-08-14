package com.autosecretary.data;

import android.content.Context;

import androidx.room.Room;

import java.util.concurrent.Executor;

public final class FocusDatabaseFactory {
    private FocusDatabaseFactory() { }

    public static FocusDatabase open(Context context, Executor databaseExecutor) {
        return Room.databaseBuilder(
                        context.getApplicationContext(), FocusDatabase.class, FocusDatabase.NAME)
                // v35 is the stable baseline. Only obsolete pre-v35 prototypes may reset.
                .fallbackToDestructiveMigrationFrom(
                        java.util.stream.IntStream.rangeClosed(1, 34).toArray())
                .setQueryExecutor(databaseExecutor)
                .setTransactionExecutor(databaseExecutor)
                .build();
    }
}
