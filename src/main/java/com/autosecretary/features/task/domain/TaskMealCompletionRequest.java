package com.autosecretary.features.task.domain;

import com.autosecretary.shared.MealType;

import java.time.LocalDate;
import java.util.Objects;

public record TaskMealCompletionRequest(
        MealType mealType,
        String recipeId,
        LocalDate completionDate,
        int plannedServings,
        int actualServings) {

    public TaskMealCompletionRequest {
        Objects.requireNonNull(mealType, "mealType");
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(completionDate, "completionDate");
    }
}
