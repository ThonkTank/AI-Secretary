package com.autosecretary.features.meal.ui.state;

import com.autosecretary.shared.ui.UiStateCarrier;

import java.time.LocalDate;
import java.util.List;

public record WeeklyProgressState(
        LocalDate fromDate,
        LocalDate toDate,
        int calorieActual,
        int calorieTarget,
        int calorieCompletionPercent,
        List<WeeklyProgressFoodGroupState> foodGroups) implements UiStateCarrier {
}
