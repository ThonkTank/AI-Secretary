package com.autosecretary.testing;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;

public final class TestDatabases {
    private TestDatabases() {
    }

    public static AppDatabase inMemory() {
        Context context = ApplicationProvider.getApplicationContext();
        return Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }
}
