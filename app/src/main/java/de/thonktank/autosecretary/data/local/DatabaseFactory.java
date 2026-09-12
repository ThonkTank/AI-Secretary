package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.DatabaseContract;
import de.thonktank.autosecretary.DiagnosticBootstrap;

import android.content.Context;

import androidx.room.Room;


public final class DatabaseFactory {
    public AppDatabase create(Context context) {
        DiagnosticBootstrap.requireNormalDatabaseAccess();
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class,
                        "auto_secretary.db")
                // 0.2.80 ships schema 8. Earlier migrations remain executable test fixtures,
                // but are intentionally not part of the supported production graph.
                .addMigrations(DatabaseMigrations.from(
                        DatabaseContract.PRODUCTION_UPGRADE_SOURCE_VERSION))
                .addCallback(new androidx.room.RoomDatabase.Callback() {
                    @Override public void onOpen(androidx.sqlite.db.SupportSQLiteDatabase db) {
                        OrphanFlowRecovery.report(db);
                    }
                })
                .build();
    }
}
