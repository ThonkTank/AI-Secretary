package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import entities.Ingredient;
import entities.StorePackage;

public class IngredientParser {

    public static Ingredient fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        Ingredient i = new Ingredient();

        i.id = (Long) typed.get("id");
        i.name = (String) typed.get("name");
        i.foodGroup = ParseUtils.safeEnum(Ingredient.FoodGroup.class, (String) typed.get("food_group"), Ingredient.FoodGroup.OTHER);
        i.defaultUnit = (String) typed.get("default_unit");
        i.gramsPerUnit = ParseUtils.safeInt(typed.get("grams_per_unit"), 1);
        i.caloriesPer100 = ParseUtils.safeInt(typed.get("calories_per_100"), 0);
        i.proteinPer100 = ParseUtils.safeInt(typed.get("protein_per_100"), 0);
        i.carbsPer100 = ParseUtils.safeInt(typed.get("carbs_per_100"), 0);
        i.fatPer100 = ParseUtils.safeInt(typed.get("fat_per_100"), 0);
        i.fiberPer100 = ParseUtils.safeInt(typed.get("fiber_per_100"), 0);
        i.shelfLifeDays = ParseUtils.safeInt(typed.get("shelf_life_days"), 0);
        i.requiresRefrigeration = ParseUtils.safeBoolean(typed.get("requires_refrigeration"), false);
        i.isWholeUnit = ParseUtils.safeBoolean(typed.get("is_whole_unit"), false);
        i.isPerishable = ParseUtils.safeBoolean(typed.get("is_perishable"), false);
        i.storePackages = parseStorePackages((String) typed.get("store_packages"));

        return i;
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
            case "grams_per_unit", "calories_per_100", "protein_per_100", "carbs_per_100",
                 "fat_per_100", "fiber_per_100", "shelf_life_days" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "requires_refrigeration", "is_whole_unit", "is_perishable" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "store_packages" -> v.toString();
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, Ingredient i) {
        ContentValues cv = new ContentValues();

        if (i.name != null) cv.put("name", i.name);
        if (i.foodGroup != null) cv.put("food_group", i.foodGroup.name());
        if (i.defaultUnit != null) cv.put("default_unit", i.defaultUnit);
        cv.put("grams_per_unit", i.gramsPerUnit);
        cv.put("calories_per_100", i.caloriesPer100);
        cv.put("protein_per_100", i.proteinPer100);
        cv.put("carbs_per_100", i.carbsPer100);
        cv.put("fat_per_100", i.fatPer100);
        cv.put("fiber_per_100", i.fiberPer100);
        cv.put("shelf_life_days", i.shelfLifeDays);
        cv.put("requires_refrigeration", i.requiresRefrigeration ? 1 : 0);
        cv.put("is_whole_unit", i.isWholeUnit ? 1 : 0);
        cv.put("is_perishable", i.isPerishable ? 1 : 0);
        cv.put("store_packages", serializeStorePackages(i.storePackages));

        if (i.id != null) {
            db.update("ingredients", cv, "id = ?", new String[]{String.valueOf(i.id)});
        } else {
            i.id = db.insert("ingredients", null, cv);
        }
    }

    /**
     * Parst StorePackages aus serialisiertem Format.
     * Format: "store|amount|unit|priceCents|lastPurchased;..."
     */
    static List<StorePackage> parseStorePackages(String raw) {
        List<StorePackage> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;

        for (String entry : raw.split(";")) {
            String[] parts = entry.split("\\|", 5);
            if (parts.length >= 4) {
                String store = parts[0];
                double amount = ParseUtils.safeDouble(parts[1], 0);
                String unit = parts[2];
                int priceCents = ParseUtils.safeInt(parts[3], 0);
                LocalDate lastPurchased = parts.length >= 5 && !parts[4].isEmpty()
                    ? ParseUtils.safeLocalDate(parts[4]) : null;

                result.add(new StorePackage.Builder(store, amount, unit)
                    .price(priceCents)
                    .lastPurchased(lastPurchased)
                    .build());
            }
        }
        return result;
    }

    /**
     * Serialisiert StorePackages.
     * Format: "store|amount|unit|priceCents|lastPurchased;..."
     */
    static String serializeStorePackages(List<StorePackage> packages) {
        if (packages == null || packages.isEmpty()) return "";
        return packages.stream()
            .map(p -> p.storeName + "|" + p.packageAmount + "|" + p.unit + "|"
                + p.priceCents + "|" + (p.lastPurchased != null ? p.lastPurchased : ""))
            .collect(Collectors.joining(";"));
    }
}
