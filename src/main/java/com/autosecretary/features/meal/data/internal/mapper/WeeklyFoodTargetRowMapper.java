package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.domain.Ingredient.FoodGroup;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.util.HashMap;
import java.util.List;
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

    private record GroupEntry(FoodGroup group, String gramKey, String plannedKey) {}

    // Maps each food group to its pair of storage field keys (*Grams, *Planned).
    // Adding a new food group requires a single entry here.
    private static final List<GroupEntry> FOOD_GROUPS = List.of(
        new GroupEntry(FoodGroup.GRAIN,     MealFieldKeys.WeeklyFoodTarget.GRAIN_GRAMS,     MealFieldKeys.WeeklyFoodTarget.GRAIN_PLANNED),
        new GroupEntry(FoodGroup.POTATO,    MealFieldKeys.WeeklyFoodTarget.POTATO_GRAMS,    MealFieldKeys.WeeklyFoodTarget.POTATO_PLANNED),
        new GroupEntry(FoodGroup.VEGETABLE, MealFieldKeys.WeeklyFoodTarget.VEGETABLE_GRAMS, MealFieldKeys.WeeklyFoodTarget.VEGETABLE_PLANNED),
        new GroupEntry(FoodGroup.FRUIT,     MealFieldKeys.WeeklyFoodTarget.FRUIT_GRAMS,     MealFieldKeys.WeeklyFoodTarget.FRUIT_PLANNED),
        new GroupEntry(FoodGroup.DAIRY,     MealFieldKeys.WeeklyFoodTarget.DAIRY_GRAMS,     MealFieldKeys.WeeklyFoodTarget.DAIRY_PLANNED),
        new GroupEntry(FoodGroup.MEAT,      MealFieldKeys.WeeklyFoodTarget.MEAT_GRAMS,      MealFieldKeys.WeeklyFoodTarget.MEAT_PLANNED),
        new GroupEntry(FoodGroup.FISH,      MealFieldKeys.WeeklyFoodTarget.FISH_GRAMS,      MealFieldKeys.WeeklyFoodTarget.FISH_PLANNED),
        new GroupEntry(FoodGroup.EGG,       MealFieldKeys.WeeklyFoodTarget.EGG_GRAMS,       MealFieldKeys.WeeklyFoodTarget.EGG_PLANNED),
        new GroupEntry(FoodGroup.FAT,       MealFieldKeys.WeeklyFoodTarget.FAT_GRAMS,       MealFieldKeys.WeeklyFoodTarget.FAT_PLANNED),
        new GroupEntry(FoodGroup.LEGUME,    MealFieldKeys.WeeklyFoodTarget.LEGUME_GRAMS,    MealFieldKeys.WeeklyFoodTarget.LEGUME_PLANNED),
        new GroupEntry(FoodGroup.NUT,       MealFieldKeys.WeeklyFoodTarget.NUT_GRAMS,       MealFieldKeys.WeeklyFoodTarget.NUT_PLANNED)
    );

    @Override
    public Map<String, Object> toRow(WeeklyFoodTarget target) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.WeeklyFoodTarget.ID, target.id);
        row.put(MealFieldKeys.PERIOD_KEY, target.periodKey);
        for (GroupEntry g : FOOD_GROUPS) {
            row.put(g.gramKey(), target.getTargetFor(g.group()));
            row.put(g.plannedKey(), target.getPlannedFor(g.group()));
        }
        return row;
    }

    @Override
    public WeeklyFoodTarget fromRow(Map<String, Object> row) {
        WeeklyFoodTarget target = new WeeklyFoodTarget();
        target.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.WeeklyFoodTarget.ID));
        target.periodKey = (String) row.get(MealFieldKeys.PERIOD_KEY);
        for (GroupEntry g : FOOD_GROUPS) {
            target.setTargetFor(g.group(), MapperSupport.asInt(row.get(g.gramKey())));
            target.setPlannedFor(g.group(), MapperSupport.asInt(row.get(g.plannedKey())));
        }
        return target;
    }
}
