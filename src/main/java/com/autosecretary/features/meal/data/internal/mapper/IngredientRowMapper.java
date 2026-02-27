package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.Ingredient;

import java.util.HashMap;
import java.util.Map;

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
        row.put(MealFieldKeys.Ingredient.STORE_PACKAGES, ingredient.storePackages);
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
        ingredient.storePackages = MapperSupport.asList(row.get(MealFieldKeys.Ingredient.STORE_PACKAGES));
        return ingredient;
    }
}
