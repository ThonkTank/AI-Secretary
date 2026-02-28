package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.Ingredient.FoodGroup;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link RowMapper} for {@link WeeklyFoodTarget}.
 *
 * <p>Each food group (grain, potato, vegetable, …) is represented by two fields:
 * {@code *Grams} (the weekly gram target for that group) and {@code *Planned} (how many
 * units are already accounted for in the current week's meal plan). See
 * {@link WeeklyFoodTarget} for the full semantic meaning of each field pair.
 *
 * <p>The {@code periodKey} field uses the shared top-level {@link MealFieldKeys#PERIOD_KEY}
 * constant (also used by {@link ShoppingListItemRowMapper}).
 */
public class WeeklyFoodTargetRowMapper implements RowMapper<WeeklyFoodTarget> {

    @Override
    public Map<String, Object> toRow(WeeklyFoodTarget target) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.WeeklyFoodTarget.ID, target.id);
        row.put(MealFieldKeys.PERIOD_KEY, target.periodKey);

        // Serialize all food group gram targets
        FoodGroupFields.serializeGrams(row, target);
        FoodGroupFields.serializePlanned(row, target);

        return row;
    }

    @Override
    public WeeklyFoodTarget fromRow(Map<String, Object> row) {
        WeeklyFoodTarget target = new WeeklyFoodTarget();
        target.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.WeeklyFoodTarget.ID));
        // String fields use raw casts: the storage layer always serializes them as strings via toRow(),
        // so the cast is safe (no type mismatch). If storage changes, wrap this in MapperSupport.asString().
        target.periodKey = (String) row.get(MealFieldKeys.PERIOD_KEY);

        // Deserialize all food group gram targets and planned amounts
        FoodGroupFields.deserializeGrams(row, target);
        FoodGroupFields.deserializePlanned(row, target);

        return target;
    }

    /**
     * Helper class to manage the 11 food group field pairs (*Grams and *Planned),
     * eliminating repetitive serialization/deserialization code.
     */
    private static class FoodGroupFields {
        static void serializeGrams(Map<String, Object> row, WeeklyFoodTarget target) {
            row.put(MealFieldKeys.WeeklyFoodTarget.GRAIN_GRAMS,     target.getTargetFor(FoodGroup.GRAIN));
            row.put(MealFieldKeys.WeeklyFoodTarget.POTATO_GRAMS,    target.getTargetFor(FoodGroup.POTATO));
            row.put(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_GRAMS, target.getTargetFor(FoodGroup.VEGETABLE));
            row.put(MealFieldKeys.WeeklyFoodTarget.FRUIT_GRAMS,     target.getTargetFor(FoodGroup.FRUIT));
            row.put(MealFieldKeys.WeeklyFoodTarget.DAIRY_GRAMS,     target.getTargetFor(FoodGroup.DAIRY));
            row.put(MealFieldKeys.WeeklyFoodTarget.MEAT_GRAMS,      target.getTargetFor(FoodGroup.MEAT));
            row.put(MealFieldKeys.WeeklyFoodTarget.FISH_GRAMS,      target.getTargetFor(FoodGroup.FISH));
            row.put(MealFieldKeys.WeeklyFoodTarget.EGG_GRAMS,       target.getTargetFor(FoodGroup.EGG));
            row.put(MealFieldKeys.WeeklyFoodTarget.FAT_GRAMS,       target.getTargetFor(FoodGroup.FAT));
            row.put(MealFieldKeys.WeeklyFoodTarget.LEGUME_GRAMS,    target.getTargetFor(FoodGroup.LEGUME));
            row.put(MealFieldKeys.WeeklyFoodTarget.NUT_GRAMS,       target.getTargetFor(FoodGroup.NUT));
        }

        static void serializePlanned(Map<String, Object> row, WeeklyFoodTarget target) {
            row.put(MealFieldKeys.WeeklyFoodTarget.GRAIN_PLANNED,     target.getPlannedFor(FoodGroup.GRAIN));
            row.put(MealFieldKeys.WeeklyFoodTarget.POTATO_PLANNED,    target.getPlannedFor(FoodGroup.POTATO));
            row.put(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_PLANNED, target.getPlannedFor(FoodGroup.VEGETABLE));
            row.put(MealFieldKeys.WeeklyFoodTarget.FRUIT_PLANNED,     target.getPlannedFor(FoodGroup.FRUIT));
            row.put(MealFieldKeys.WeeklyFoodTarget.DAIRY_PLANNED,     target.getPlannedFor(FoodGroup.DAIRY));
            row.put(MealFieldKeys.WeeklyFoodTarget.MEAT_PLANNED,      target.getPlannedFor(FoodGroup.MEAT));
            row.put(MealFieldKeys.WeeklyFoodTarget.FISH_PLANNED,      target.getPlannedFor(FoodGroup.FISH));
            row.put(MealFieldKeys.WeeklyFoodTarget.EGG_PLANNED,       target.getPlannedFor(FoodGroup.EGG));
            row.put(MealFieldKeys.WeeklyFoodTarget.FAT_PLANNED,       target.getPlannedFor(FoodGroup.FAT));
            row.put(MealFieldKeys.WeeklyFoodTarget.LEGUME_PLANNED,    target.getPlannedFor(FoodGroup.LEGUME));
            row.put(MealFieldKeys.WeeklyFoodTarget.NUT_PLANNED,       target.getPlannedFor(FoodGroup.NUT));
        }

        static void deserializeGrams(Map<String, Object> row, WeeklyFoodTarget target) {
            target.setTargetFor(FoodGroup.GRAIN,     MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.GRAIN_GRAMS)));
            target.setTargetFor(FoodGroup.POTATO,    MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.POTATO_GRAMS)));
            target.setTargetFor(FoodGroup.VEGETABLE, MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_GRAMS)));
            target.setTargetFor(FoodGroup.FRUIT,     MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FRUIT_GRAMS)));
            target.setTargetFor(FoodGroup.DAIRY,     MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.DAIRY_GRAMS)));
            target.setTargetFor(FoodGroup.MEAT,      MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.MEAT_GRAMS)));
            target.setTargetFor(FoodGroup.FISH,      MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FISH_GRAMS)));
            target.setTargetFor(FoodGroup.EGG,       MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.EGG_GRAMS)));
            target.setTargetFor(FoodGroup.FAT,       MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FAT_GRAMS)));
            target.setTargetFor(FoodGroup.LEGUME,    MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.LEGUME_GRAMS)));
            target.setTargetFor(FoodGroup.NUT,       MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.NUT_GRAMS)));
        }

        static void deserializePlanned(Map<String, Object> row, WeeklyFoodTarget target) {
            target.setPlannedFor(FoodGroup.GRAIN,     MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.GRAIN_PLANNED)));
            target.setPlannedFor(FoodGroup.POTATO,    MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.POTATO_PLANNED)));
            target.setPlannedFor(FoodGroup.VEGETABLE, MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_PLANNED)));
            target.setPlannedFor(FoodGroup.FRUIT,     MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FRUIT_PLANNED)));
            target.setPlannedFor(FoodGroup.DAIRY,     MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.DAIRY_PLANNED)));
            target.setPlannedFor(FoodGroup.MEAT,      MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.MEAT_PLANNED)));
            target.setPlannedFor(FoodGroup.FISH,      MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FISH_PLANNED)));
            target.setPlannedFor(FoodGroup.EGG,       MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.EGG_PLANNED)));
            target.setPlannedFor(FoodGroup.FAT,       MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FAT_PLANNED)));
            target.setPlannedFor(FoodGroup.LEGUME,    MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.LEGUME_PLANNED)));
            target.setPlannedFor(FoodGroup.NUT,       MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.NUT_PLANNED)));
        }
    }
}
