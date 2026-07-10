package com.autosecretary.testing;

import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPlannedMeal;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.shared.Period;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public final class TaskFixtures {
    private TaskFixtures() {
    }

    public static Task taskWithSlot(String title, LocalDate day) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = day;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = day;
        task.core.minDuration = 30;
        task.core.maxDuration = 60;

        TaskSlot slot = new TaskSlot();
        slot.taskId = task.core.id;
        slot.day = day;
        slot.start = LocalTime.of(10, 0);
        slot.end = LocalTime.of(10, 30);
        slot.scheduled = true;
        task.slots.add(slot);
        return task;
    }

    public static TaskPrefSlot prefSlot(Task task, LocalDate day, LocalTime start) {
        TaskPrefSlot prefSlot = new TaskPrefSlot();
        prefSlot.taskId = task.core.id;
        prefSlot.days = Set.of(day.getDayOfWeek());
        prefSlot.start = start;
        task.prefSlots.add(prefSlot);
        return prefSlot;
    }

    public static TaskPlannedMeal plannedMeal(Task task, LocalDate day, String recipeId, int servings) {
        TaskPlannedMeal plannedMeal = new TaskPlannedMeal();
        plannedMeal.taskId = task.core.id;
        plannedMeal.day = day;
        plannedMeal.recipeId = recipeId;
        plannedMeal.plannedServings = servings;
        task.plannedMeals.add(plannedMeal);
        return plannedMeal;
    }
}
