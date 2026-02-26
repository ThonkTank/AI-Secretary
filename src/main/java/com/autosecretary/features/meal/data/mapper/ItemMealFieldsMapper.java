package com.autosecretary.features.meal.data.mapper;

import com.autosecretary.features.meal.domain.MealType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ItemMealFieldsMapper {
    private ItemMealFieldsMapper() {
    }

    public static ItemMealFields fromStorageRow(Map<String, Object> row) {
        MealType mealType = LegacyMapperSupport.asEnum(
                MealType.class,
                LegacyMapperSupport.get(row, LegacyMealFieldKeys.Item.MEAL_TYPE, "mealType"),
                null
        );
        String plannedMealsRaw = (String) LegacyMapperSupport.get(row, LegacyMealFieldKeys.Item.PLANNED_MEALS, "plannedMeals");
        return new ItemMealFields(mealType, parsePlannedMeals(plannedMealsRaw));
    }

    public static void toStorageRow(ItemMealFields fields, Map<String, Object> targetRow) {
        targetRow.put(LegacyMealFieldKeys.Item.MEAL_TYPE, fields.mealType() == null ? null : fields.mealType().name());
        targetRow.put(LegacyMealFieldKeys.Item.PLANNED_MEALS, serializePlannedMeals(fields.plannedMeals()));
    }

    private static List<PlannedMealEntry> parsePlannedMeals(String raw) {
        List<PlannedMealEntry> meals = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return meals;
        }
        for (String entry : raw.split(",")) {
            String[] parts = entry.trim().split(";");
            if (parts.length != 5) continue;
            LocalDate date = LegacyMapperSupport.asLocalDate(parts[0]);
            Long recipeId = LegacyMapperSupport.asNullableLong(parts[1]);
            if (date == null || recipeId == null) continue;
            int servings = LegacyMapperSupport.asInt(parts[2], 1);
            boolean completed = LegacyMapperSupport.asBoolean(parts[3], false);
            int actualServings = LegacyMapperSupport.asInt(parts[4], 0);
            meals.add(new PlannedMealEntry(date, recipeId, servings, completed, actualServings));
        }
        return meals;
    }

    private static String serializePlannedMeals(List<PlannedMealEntry> plannedMeals) {
        if (plannedMeals == null || plannedMeals.isEmpty()) {
            return null;
        }
        return plannedMeals.stream()
                .map(value -> value.date() + ";" + value.recipeId() + ";" + value.servings() + ";"
                        + (value.completed() ? 1 : 0) + ";" + value.actualServings())
                .collect(Collectors.joining(","));
    }

    public record ItemMealFields(MealType mealType, List<PlannedMealEntry> plannedMeals) {
    }

    public record PlannedMealEntry(LocalDate date, long recipeId, int servings, boolean completed, int actualServings) {
    }
}
