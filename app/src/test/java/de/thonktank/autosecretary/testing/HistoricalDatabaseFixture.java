package de.thonktank.autosecretary.testing;

import android.content.Context;
import android.database.Cursor;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.data.local.DatabaseMigrations;

/** Builds a database through its real migration lineage instead of recreating a later export. */
public final class HistoricalDatabaseFixture {
    private HistoricalDatabaseFixture() { }

    public static void create(Context context, String name, int version, DatabaseAction seed) {
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                        .callback(new SupportSQLiteOpenHelper.Callback(version) {
                            @Override public void onCreate(SupportSQLiteDatabase database) {
                                ExportedRoomSchemaFixture.create(database, version);
                                seed.run(database);
                            }

                            @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                            int oldVersion, int newVersion) { }
                        }).build());
        helper.getWritableDatabase();
        helper.close();
    }

    public static void migrate(Context context, String name, int sourceVersion, int targetVersion,
                               VersionCheckpoint checkpoint) {
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                        .callback(new SupportSQLiteOpenHelper.Callback(targetVersion) {
                            @Override public void onCreate(SupportSQLiteDatabase database) {
                                throw new AssertionError("Historical source database is missing");
                            }

                            @Override public void onUpgrade(SupportSQLiteDatabase database,
                                                            int oldVersion, int newVersion) {
                                if (oldVersion != sourceVersion || newVersion != targetVersion) {
                                    throw new AssertionError("Unexpected lineage " + oldVersion
                                            + " to " + newVersion);
                                }
                                for (Migration migration : DatabaseMigrations.from(sourceVersion)) {
                                    if (migration.endVersion > targetVersion) break;
                                    migration.migrate(database);
                                    checkpoint.after(migration.endVersion, database);
                                }
                            }
                        }).build());
        helper.getWritableDatabase();
        helper.close();
    }

    public static List<String> columns(SupportSQLiteDatabase database, String table) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = database.query("PRAGMA table_info(`" + table + "`)")) {
            int name = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) result.add(cursor.getString(name));
        }
        return result;
    }

    @FunctionalInterface public interface DatabaseAction {
        void run(SupportSQLiteDatabase database);
    }

    @FunctionalInterface public interface VersionCheckpoint {
        void after(int version, SupportSQLiteDatabase database);
    }
}
