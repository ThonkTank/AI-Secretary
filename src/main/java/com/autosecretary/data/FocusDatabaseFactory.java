package com.autosecretary.data;

import android.content.Context;

import androidx.room.Room;

import java.util.concurrent.Executor;

public final class FocusDatabaseFactory {
    private FocusDatabaseFactory() { }

    public static FocusDatabase open(Context context, Executor databaseExecutor) {
        return Room.databaseBuilder(
                        context.getApplicationContext(), FocusDatabase.class, FocusDatabase.NAME)
                // A pre-stable schema reset is deliberate until v35 is declared stable.
                .fallbackToDestructiveMigration()
                .setQueryExecutor(databaseExecutor)
                .setTransactionExecutor(databaseExecutor)
                .build();
    }
}
