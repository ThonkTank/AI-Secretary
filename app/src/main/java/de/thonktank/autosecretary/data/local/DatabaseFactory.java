package de.thonktank.autosecretary.data.local;

import android.content.Context;

import androidx.room.Room;

import de.thonktank.autosecretary.AppDatabase;

public final class DatabaseFactory {
    public AppDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                        "auto_secretary.db")
                .addMigrations(DatabaseMigrations.MIGRATION_1_2,
                        DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4,
                        DatabaseMigrations.MIGRATION_4_5, DatabaseMigrations.MIGRATION_5_6,
                        DatabaseMigrations.MIGRATION_6_7, DatabaseMigrations.MIGRATION_7_8)
                .build();
    }
}
