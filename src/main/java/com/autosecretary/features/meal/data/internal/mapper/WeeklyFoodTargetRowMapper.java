package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
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
            row.put(MealFieldKeys.WeeklyFoodTarget.GRAIN_GRAMS, target.grainGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.POTATO_GRAMS, target.potatoGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_GRAMS, target.vegetableGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.FRUIT_GRAMS, target.fruitGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.DAIRY_GRAMS, target.dairyGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.MEAT_GRAMS, target.meatGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.FISH_GRAMS, target.fishGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.EGG_GRAMS, target.eggGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.FAT_GRAMS, target.fatGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.LEGUME_GRAMS, target.legumeGrams);
            row.put(MealFieldKeys.WeeklyFoodTarget.NUT_GRAMS, target.nutGrams);
        }

        static void serializePlanned(Map<String, Object> row, WeeklyFoodTarget target) {
            row.put(MealFieldKeys.WeeklyFoodTarget.GRAIN_PLANNED, target.grainPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.POTATO_PLANNED, target.potatoPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_PLANNED, target.vegetablePlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.FRUIT_PLANNED, target.fruitPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.DAIRY_PLANNED, target.dairyPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.MEAT_PLANNED, target.meatPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.FISH_PLANNED, target.fishPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.EGG_PLANNED, target.eggPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.FAT_PLANNED, target.fatPlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.LEGUME_PLANNED, target.legumePlanned);
            row.put(MealFieldKeys.WeeklyFoodTarget.NUT_PLANNED, target.nutPlanned);
        }

        static void deserializeGrams(Map<String, Object> row, WeeklyFoodTarget target) {
            target.grainGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.GRAIN_GRAMS));
            target.potatoGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.POTATO_GRAMS));
            target.vegetableGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_GRAMS));
            target.fruitGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FRUIT_GRAMS));
            target.dairyGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.DAIRY_GRAMS));
            target.meatGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.MEAT_GRAMS));
            target.fishGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FISH_GRAMS));
            target.eggGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.EGG_GRAMS));
            target.fatGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FAT_GRAMS));
            target.legumeGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.LEGUME_GRAMS));
            target.nutGrams = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.NUT_GRAMS));
        }

        static void deserializePlanned(Map<String, Object> row, WeeklyFoodTarget target) {
            target.grainPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.GRAIN_PLANNED));
            target.potatoPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.POTATO_PLANNED));
            target.vegetablePlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.VEGETABLE_PLANNED));
            target.fruitPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FRUIT_PLANNED));
            target.dairyPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.DAIRY_PLANNED));
            target.meatPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.MEAT_PLANNED));
            target.fishPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FISH_PLANNED));
            target.eggPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.EGG_PLANNED));
            target.fatPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.FAT_PLANNED));
            target.legumePlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.LEGUME_PLANNED));
            target.nutPlanned = MapperSupport.asInt(row.get(MealFieldKeys.WeeklyFoodTarget.NUT_PLANNED));
        }
    }
}
