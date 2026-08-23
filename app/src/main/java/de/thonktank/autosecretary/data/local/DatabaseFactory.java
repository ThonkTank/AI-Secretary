package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.DatabaseContract;

import android.content.Context;

import androidx.room.Room;


public final class DatabaseFactory {
    public AppDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                        "auto_secretary.db")
                // 0.2.80 ships schema 8. Earlier migrations remain executable test fixtures,
                // but are intentionally not part of the supported production graph.
                .addMigrations(DatabaseMigrations.from(
                        DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION))
                .build();
    }
}
