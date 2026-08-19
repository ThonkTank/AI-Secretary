package de.thonktank.autosecretary.testing;

import androidx.sqlite.db.SupportSQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Builds historical databases from Room's checked-in schema export, the migration contract. */
public final class ExportedRoomSchemaFixture {
    private static final String DATABASE = "de.thonktank.autosecretary.AppDatabase";

    private ExportedRoomSchemaFixture() { }

    public static void create(SupportSQLiteDatabase database, int version) {
        try {
            String json = new String(Files.readAllBytes(schema(version).toPath()),
                    StandardCharsets.UTF_8);
            JSONObject exported = new JSONObject(json).getJSONObject("database");
            JSONArray entities = exported.getJSONArray("entities");
            for (int index = 0; index < entities.length(); index++) {
                JSONObject entity = entities.getJSONObject(index);
                String table = entity.getString("tableName");
                database.execSQL(resolve(entity.getString("createSql"), table));
                JSONArray indices = entity.optJSONArray("indices");
                if (indices == null) continue;
                for (int entry = 0; entry < indices.length(); entry++)
                    database.execSQL(resolve(indices.getJSONObject(entry)
                            .getString("createSql"), table));
            }
            JSONArray views = exported.optJSONArray("views");
            if (views != null) for (int index = 0; index < views.length(); index++)
                database.execSQL(views.getJSONObject(index).getString("createSql"));
            JSONArray setup = exported.getJSONArray("setupQueries");
            for (int index = 0; index < setup.length(); index++)
                database.execSQL(setup.getString(index));
        } catch (Exception failure) {
            throw new AssertionError("Cannot build exported Room schema " + version, failure);
        }
    }

    private static String resolve(String sql, String table) {
        return sql.replace("${TABLE_NAME}", table);
    }

    private static File schema(int version) {
        File fromModule = new File("schemas/" + DATABASE + "/" + version + ".json");
        if (fromModule.isFile()) return fromModule;
        File fromRoot = new File("app", fromModule.getPath());
        if (fromRoot.isFile()) return fromRoot;
        throw new IllegalStateException("Missing exported Room schema " + fromModule);
    }
}
