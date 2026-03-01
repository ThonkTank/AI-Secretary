package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link RowMapper} for {@link Ingredient}.
 *
 * <p>Notable serialization: {@code storePackages} is stored as a delimited string —
 * {@code "storeName|unit|packageAmount|priceCents|lastPurchased"} records joined by {@code ";"}.
 * Uses {@link MapperSupport#parseListFromString} for deserialization.
 * Values stored in {@code storeName} and {@code unit} must not contain {@code '|'} or {@code ';'}.
 */
public class IngredientRowMapper implements RowMapper<Ingredient> {
    @Override
    public Map<String, Object> toRow(Ingredient ingredient) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.Ingredient.ID, ingredient.id);
        row.put(MealFieldKeys.Ingredient.NAME, ingredient.name);
        row.put(MealFieldKeys.Ingredient.FOOD_GROUP, MapperSupport.enumNameOrNull(ingredient.foodGroup));
        row.put(MealFieldKeys.Ingredient.DEFAULT_UNIT, ingredient.defaultUnit);
        row.put(MealFieldKeys.Ingredient.GRAMS_PER_UNIT, ingredient.gramsPerUnit);
        row.put(MealFieldKeys.Ingredient.CALORIES_PER_100, ingredient.caloriesPer100);
        row.put(MealFieldKeys.Ingredient.PROTEIN_PER_100, ingredient.proteinPer100);
        row.put(MealFieldKeys.Ingredient.CARBS_PER_100, ingredient.carbsPer100);
        row.put(MealFieldKeys.Ingredient.FAT_PER_100, ingredient.fatPer100);
        row.put(MealFieldKeys.Ingredient.FIBER_PER_100, ingredient.fiberPer100);
        row.put(MealFieldKeys.Ingredient.SHELF_LIFE_DAYS, ingredient.shelfLifeDays);
        row.put(MealFieldKeys.Ingredient.REQUIRES_REFRIGERATION, ingredient.requiresRefrigeration ? 1 : 0);
        row.put(MealFieldKeys.Ingredient.IS_WHOLE_UNIT, ingredient.isWholeUnit ? 1 : 0);
        row.put(MealFieldKeys.Ingredient.IS_PERISHABLE, ingredient.isPerishable ? 1 : 0);
        row.put(MealFieldKeys.Ingredient.STORE_PACKAGES, serializeStorePackages(ingredient.storePackages));
        return row;
    }

    @Override
    public Ingredient fromRow(Map<String, Object> row) {
        Ingredient ingredient = new Ingredient();
        ingredient.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.Ingredient.ID));
        ingredient.name = (String) row.get(MealFieldKeys.Ingredient.NAME);
        ingredient.foodGroup = MapperSupport.asEnum(Ingredient.FoodGroup.class, row.get(MealFieldKeys.Ingredient.FOOD_GROUP), null);
        ingredient.defaultUnit = (String) row.get(MealFieldKeys.Ingredient.DEFAULT_UNIT);
        ingredient.gramsPerUnit = MapperSupport.asInt(row.get(MealFieldKeys.Ingredient.GRAMS_PER_UNIT));
        ingredient.caloriesPer100 = MapperSupport.asInt(row.get(MealFieldKeys.Ingredient.CALORIES_PER_100));
        ingredient.proteinPer100 = MapperSupport.asInt(row.get(MealFieldKeys.Ingredient.PROTEIN_PER_100));
        ingredient.carbsPer100 = MapperSupport.asInt(row.get(MealFieldKeys.Ingredient.CARBS_PER_100));
        ingredient.fatPer100 = MapperSupport.asInt(row.get(MealFieldKeys.Ingredient.FAT_PER_100));
        ingredient.fiberPer100 = MapperSupport.asInt(row.get(MealFieldKeys.Ingredient.FIBER_PER_100));
        ingredient.shelfLifeDays = MapperSupport.asInt(row.get(MealFieldKeys.Ingredient.SHELF_LIFE_DAYS));
        ingredient.requiresRefrigeration = MapperSupport.asBoolean(row.get(MealFieldKeys.Ingredient.REQUIRES_REFRIGERATION));
        ingredient.isWholeUnit = MapperSupport.asBoolean(row.get(MealFieldKeys.Ingredient.IS_WHOLE_UNIT));
        ingredient.isPerishable = MapperSupport.asBoolean(row.get(MealFieldKeys.Ingredient.IS_PERISHABLE));
        ingredient.storePackages = MapperSupport.parseListFromString(row.get(MealFieldKeys.Ingredient.STORE_PACKAGES), IngredientRowMapper::parseStorePackages);
        return ingredient;
    }

    // NOTE: storeName and unit values must not contain '|' or ';' — these are the field/record delimiters.
    private static String serializeStorePackages(List<Ingredient.StorePackage> packages) {
        if (packages == null || packages.isEmpty()) return "";
        return packages.stream()
                .map(p -> Objects.toString(p.storeName, "")
                        + "|" + Objects.toString(p.unit, "")
                        + "|" + p.packageAmount
                        + "|" + Objects.toString(p.priceCents, "")
                        + "|" + Objects.toString(p.lastPurchased, ""))
                .collect(Collectors.joining(";"));
    }

    // NOTE: storeName and unit values must not contain '|' or ';' — these are the field/record delimiters.
    private static List<Ingredient.StorePackage> parseStorePackages(String raw) {
        List<Ingredient.StorePackage> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split("\\|", 5);
            if (parts.length != 5) continue;
            Ingredient.StorePackage pkg = new Ingredient.StorePackage();
            pkg.storeName = parts[0].isEmpty() ? null : parts[0];
            pkg.unit = parts[1].isEmpty() ? null : parts[1];
            pkg.packageAmount = MapperSupport.asInt(parts[2]);
            pkg.priceCents = MapperSupport.asNullableInt(parts[3]);
            pkg.lastPurchased = MapperSupport.asLocalDate(parts[4]);
            result.add(pkg);
        }
        return result;
    }
}
