package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;

import android.content.Context;

import androidx.room.Room;


public final class DatabaseFactory {
    public AppDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                        "auto_secretary.db")
                // 0.2.80 ships schema 8. Earlier migrations remain executable test fixtures,
                // but are intentionally not part of the supported production graph.
                .addMigrations(DatabaseMigrations.MIGRATION_8_9, DatabaseMigrations.MIGRATION_9_10,
                        DatabaseMigrations.MIGRATION_10_11, DatabaseMigrations.MIGRATION_11_12,
                        DatabaseMigrations.MIGRATION_12_13, DatabaseMigrations.MIGRATION_13_14,
                        DatabaseMigrations.MIGRATION_14_15)
                .build();
    }
}
