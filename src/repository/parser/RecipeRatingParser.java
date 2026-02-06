package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.RecipeRating;

public class RecipeRatingParser {

    // ============== BUILDER (DB → Java) ==============

    public static RecipeRating fromRow(Map<String, Object> row) {
        RecipeRating r = new RecipeRating();

        r.id = (Long) row.get("id");
        r.recipeId = (Long) row.get("recipe_id");
        r.memberId = (Long) row.get("member_id");
        r.rating = row.get("rating") instanceof Number n ? n.intValue() : 0;
        r.ratedAt = (LocalDate) row.get("rated_at");

        return r;
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
            case "id", "recipe_id", "member_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "rating" -> (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "rated_at" -> LocalDate.parse(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, RecipeRating r) {
        ContentValues cv = new ContentValues();

        if (r.recipeId != null) cv.put("recipe_id", r.recipeId);
        if (r.memberId != null) cv.put("member_id", r.memberId);
        cv.put("rating", r.rating);
        if (r.ratedAt != null) cv.put("rated_at", r.ratedAt.toString());

        if (r.id != null) {
            db.update("recipe_ratings", cv, "id = ?", new String[]{String.valueOf(r.id)});
        } else {
            long newId = db.insert("recipe_ratings", null, cv);
            r.id = newId;
        }
    }
}
