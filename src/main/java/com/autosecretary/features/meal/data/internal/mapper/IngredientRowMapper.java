package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IngredientRowMapper implements RowMapper<Ingredient> {
    @Override
    public Map<String, Object> toRow(Ingredient ingredient) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", ingredient.id);
        row.put("name", ingredient.name);
        row.put("foodGroup", ingredient.foodGroup);
        row.put("defaultUnit", ingredient.defaultUnit);
        row.put("gramsPerUnit", ingredient.gramsPerUnit);
        row.put("caloriesPer100", ingredient.caloriesPer100);
        row.put("proteinPer100", ingredient.proteinPer100);
        row.put("carbsPer100", ingredient.carbsPer100);
        row.put("fatPer100", ingredient.fatPer100);
        row.put("fiberPer100", ingredient.fiberPer100);
        row.put("shelfLifeDays", ingredient.shelfLifeDays);
        row.put("requiresRefrigeration", ingredient.requiresRefrigeration);
        row.put("isWholeUnit", ingredient.isWholeUnit);
        row.put("isPerishable", ingredient.isPerishable);
        row.put("storePackages", ingredient.storePackages);
        return row;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Ingredient fromRow(Map<String, Object> row) {
        Ingredient ingredient = new Ingredient();
        ingredient.id = MapperSupport.asNullableLong(row.get("id"));
        ingredient.name = (String) row.get("name");
        ingredient.foodGroup = (Ingredient.FoodGroup) row.get("foodGroup");
        ingredient.defaultUnit = (String) row.get("defaultUnit");
        ingredient.gramsPerUnit = MapperSupport.asInt(row.get("gramsPerUnit"));
        ingredient.caloriesPer100 = MapperSupport.asInt(row.get("caloriesPer100"));
        ingredient.proteinPer100 = MapperSupport.asInt(row.get("proteinPer100"));
        ingredient.carbsPer100 = MapperSupport.asInt(row.get("carbsPer100"));
        ingredient.fatPer100 = MapperSupport.asInt(row.get("fatPer100"));
        ingredient.fiberPer100 = MapperSupport.asInt(row.get("fiberPer100"));
        ingredient.shelfLifeDays = MapperSupport.asInt(row.get("shelfLifeDays"));
        ingredient.requiresRefrigeration = Boolean.TRUE.equals(MapperSupport.asBoolean(row.get("requiresRefrigeration")));
        ingredient.isWholeUnit = Boolean.TRUE.equals(MapperSupport.asBoolean(row.get("isWholeUnit")));
        ingredient.isPerishable = Boolean.TRUE.equals(MapperSupport.asBoolean(row.get("isPerishable")));
        ingredient.storePackages = (java.util.List<Ingredient.StorePackage>) row.getOrDefault("storePackages", new ArrayList<>());
        return ingredient;
    }
}
