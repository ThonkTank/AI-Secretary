package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.HouseholdMember;

public class HouseholdMemberParser {

    // ============== BUILDER (DB → Java) ==============

    public static HouseholdMember fromRow(Map<String, Object> row) {
        HouseholdMember m = new HouseholdMember();

        m.id = (Long) row.get("id");
        m.name = (String) row.get("name");
        m.birthYear = row.get("birth_year") instanceof Number n ? n.intValue() : 0;

        String genderStr = (String) row.get("gender");
        if (genderStr != null) {
            m.gender = ParseUtils.safeEnum(HouseholdMember.Gender.class, genderStr);
        }

        m.weightKg = row.get("weight_kg") instanceof Number n ? n.intValue() : 0;
        m.heightCm = row.get("height_cm") instanceof Number n ? n.intValue() : 0;

        String activityStr = (String) row.get("activity_level");
        if (activityStr != null) {
            m.activityLevel = ParseUtils.safeEnum(HouseholdMember.ActivityLevel.class, activityStr);
        }

        m.isActive = Boolean.TRUE.equals(row.get("is_active"));

        return m;
    }

    // ============== CONVERTER ==============

    public static Map<String, Object> convertRow(Map<String, Object> raw) {
        Map<String, Object> typed = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            typed.put(entry.getKey(), convertValue(entry.getKey(), entry.getValue()));
        }
        return typed;
    }

    public static Object convertValue(String column, Object v) {
        if (v == null) return null;
        return switch (column) {
            case "id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "birth_year", "weight_kg", "height_cm" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_active" -> (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, HouseholdMember m) {
        ContentValues cv = new ContentValues();

        if (m.name != null) cv.put("name", m.name);
        cv.put("birth_year", m.birthYear);
        if (m.gender != null) cv.put("gender", m.gender.name());
        cv.put("weight_kg", m.weightKg);
        cv.put("height_cm", m.heightCm);
        if (m.activityLevel != null) cv.put("activity_level", m.activityLevel.name());
        cv.put("is_active", m.isActive ? 1 : 0);

        if (m.id != null) {
            db.update("household_members", cv, "id = ?", new String[]{String.valueOf(m.id)});
        } else {
            long newId = db.insert("household_members", null, cv);
            m.id = newId;
        }
    }
}
