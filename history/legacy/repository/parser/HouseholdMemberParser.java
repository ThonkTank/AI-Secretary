package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.HouseholdMember;

public class HouseholdMemberParser {

    public static HouseholdMember fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        HouseholdMember m = new HouseholdMember();

        m.id = (Long) typed.get("id");
        m.name = (String) typed.get("name");
        m.birthYear = ParseUtils.safeInt(typed.get("birth_year"), 1990);
        m.gender = ParseUtils.safeEnum(HouseholdMember.Gender.class, (String) typed.get("gender"), HouseholdMember.Gender.OTHER);
        m.weightKg = ParseUtils.safeInt(typed.get("weight_kg"), 70);
        m.heightCm = ParseUtils.safeInt(typed.get("height_cm"), 170);
        m.targetWeightKg = ParseUtils.safeInt(typed.get("target_weight_kg"), 0);
        m.activityLevel = ParseUtils.safeEnum(HouseholdMember.ActivityLevel.class, (String) typed.get("activity_level"), HouseholdMember.ActivityLevel.MODERATE);
        m.isActive = ParseUtils.safeBoolean(typed.get("is_active"), true);

        return m;
    }

    public static Map<String, Object> convertRow(Map<String, Object> raw) {
        Map<String, Object> typed = new HashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            typed.put(e.getKey(), convertValue(e.getKey(), e.getValue()));
        }
        return typed;
    }

    public static Object convertValue(String column, Object v) {
        if (v == null) return null;
        return switch (column) {
            case "id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "birth_year", "weight_kg", "height_cm", "target_weight_kg" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_active" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, HouseholdMember m) {
        ContentValues cv = new ContentValues();

        if (m.name != null) cv.put("name", m.name);
        cv.put("birth_year", m.birthYear);
        if (m.gender != null) cv.put("gender", m.gender.name());
        cv.put("weight_kg", m.weightKg);
        cv.put("height_cm", m.heightCm);
        cv.put("target_weight_kg", m.targetWeightKg);
        if (m.activityLevel != null) cv.put("activity_level", m.activityLevel.name());
        cv.put("is_active", m.isActive ? 1 : 0);

        if (m.id != null) {
            db.update("household_members", cv, "id = ?", new String[]{String.valueOf(m.id)});
        } else {
            m.id = db.insert("household_members", null, cv);
        }
    }
}
