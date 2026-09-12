package de.thonktank.autosecretary;

import static org.junit.Assert.assertThrows;

import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Exercise the release runner's actual row verifier against an isolated in-memory database. */
public final class UpgradeExpectationInstrumentationTest {
    @Test public void absenceRejectsExistingRowsAndPreservesValueChecks() throws Exception {
        try (SQLiteDatabase db = SQLiteDatabase.create(null)) {
            db.execSQL("CREATE TABLE snapshots(id TEXT, note TEXT)");
            db.execSQL("INSERT INTO snapshots VALUES ('kept', NULL), ('duplicate', 'a'), ('duplicate', 'b')");
            verify(db, "{table:'snapshots',where:{id:'missing'},absent:true}");
            verify(db, "{table:'snapshots',where:{id:'kept'},values:{note:null}}");
            rejected(db, "{table:'snapshots',where:{id:'kept'},absent:true}");
            rejected(db, "{table:'snapshots',where:{note:null},absent:true}");
            rejected(db, "{table:'snapshots',where:{id:'missing'},values:{note:null}}");
            rejected(db, "{table:'snapshots',where:{id:'kept'},values:{note:'changed'}}");
            rejected(db, "{table:'snapshots',where:{id:'duplicate'},values:{note:'a'}}");
        }
    }

    @Test public void malformedExpectationsCannotSkipVerification() throws Exception {
        try (SQLiteDatabase db = SQLiteDatabase.create(null)) {
            db.execSQL("CREATE TABLE snapshots(id TEXT, note TEXT)");
            for (String entry : new String[]{
                    "{table:'snapshots',where:{id:'missing'},absent:false}",
                    "{table:'snapshots',where:{id:'missing'},absent:'true'}",
                    "{table:'snapshots',where:{id:'missing'},absent:1}",
                    "{table:'snapshots',where:{},absent:true}",
                    "{table:'snapshots',where:{id:'missing'},absent:true,values:{note:null}}",
                    "{table:'snapshots',where:{id:'missing'},absent:true,unknown:true}",
                    "{table:'snapshots',where:{id:'missing'}}",
                    "{table:'snapshots',where:{id:'missing'},values:{}}"
            }) rejected(db, entry);
        }
    }

    private static void verify(SQLiteDatabase db, String expectation) throws Exception {
        UpgradePersistenceProbe.verifyRows(db, new JSONObject().put("id", "isolated-contract")
                .put("expectedTarget", new JSONArray().put(new JSONObject(expectation))));
    }

    private static void rejected(SQLiteDatabase db, String expectation) {
        assertThrows(AssertionError.class, () -> verify(db, expectation));
    }
}
