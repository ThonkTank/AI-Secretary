package com.autosecretary.features.meal.application;

import android.util.Log;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPlannedMeal;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Bridges meal plans to the task scheduling system.
 *
 * <p>When a {@link MealPlan} is created (manually or via {@code MealPlanGenerator}), this
 * service creates a corresponding {@link Task} so the meal appears in the task checklist.
 * The task is a regular {@code TASK} (not TERMIN), allowing the scheduler to place it
 * flexibly around calendar appointments while respecting preferred meal times via
 * {@link TaskPrefSlot}.
 *
 * <p>Also handles bidirectional completion sync: when the meal planner UI toggles a
 * plan's completion state, the associated task is updated accordingly.
 *
 * <p><strong>Threading:</strong> All methods must be called on the shared worker executor.
 */
public class MealTaskBridgeService {

    private static final String TAG = "MealTaskBridge";

    private final TaskDataService taskDataService;
    private final RecipeRepository recipeRepository;
    private final MealRepository mealRepository;
    private final TaskMealIntegrationService taskMealIntegrationService;

    public MealTaskBridgeService(TaskDataService taskDataService,
                                 RecipeRepository recipeRepository,
                                 MealRepository mealRepository,
                                 TaskMealIntegrationService taskMealIntegrationService) {
        this.taskDataService = taskDataService;
        this.recipeRepository = recipeRepository;
        this.mealRepository = mealRepository;
        this.taskMealIntegrationService = taskMealIntegrationService;
    }

    /**
     * Creates a task for each meal plan that does not already have one.
     */
    public void createTasksForMealPlans(List<MealPlan> plans) {
        for (MealPlan plan : plans) {
            createTaskForMealPlan(plan);
        }
    }

    /**
     * Creates a task for a single meal plan. Skips if the plan already has an associated task.
     *
     * @param plan the meal plan to bridge; {@code plan.itemId} is set to the new task's ID
     *             and the plan is re-saved to persist the back-link
     */
    public void createTaskForMealPlan(MealPlan plan) {
        if (plan.itemId != null) return;

        Recipe recipe = recipeRepository.findRecipeById(plan.recipeId);
        if (recipe == null) {
            Log.w(TAG, "Recipe not found for meal plan " + plan.id + ", skipping task creation");
            return;
        }

        Task task = buildMealTask(plan, recipe);
        taskDataService.writeSync(task);

        plan.itemId = task.core.id;
        mealRepository.saveMealPlan(plan);
    }

    /**
     * Syncs the completion state of a meal plan to its associated task.
     *
     * <p>When completing: triggers the full meal completion cascade (pantry deduction,
     * consumption log) via {@link TaskMealIntegrationService}. When reopening: resets
     * the task and planned meal state (pantry changes are not reversed).
     */
    public void syncMealPlanCompletion(MealPlan plan, boolean completed) {
        if (plan.itemId == null) return;

        Task task = taskDataService.readSync(plan.itemId);
        if (task == null) return;

        if (completed) {
            taskMealIntegrationService.completeMealTask(task, plan.date, plan.actualServings);
            completeTaskSlot(task, plan.date);
            task.core.completed = true;
            taskDataService.writeSync(task);
        } else {
            reopenTask(task, plan.date);
            taskDataService.writeSync(task);
        }
    }

    /**
     * Removes the task associated with a meal plan.
     */
    public void deleteTaskForMealPlan(String mealPlanItemId) {
        if (mealPlanItemId == null) return;
        taskDataService.deleteTaskGraphSync(mealPlanItemId);
    }

    // --- Task construction ---

    private Task buildMealTask(MealPlan plan, Recipe recipe) {
        Task task = new Task();
        task.core = new TaskCore();
        task.core.title = plan.mealType.icon + " " + recipe.title;
        task.core.mealType = plan.mealType;
        task.core.schedulingType = TaskCore.SchedulingType.TASK;
        task.core.goalIcon = plan.mealType.icon;
        task.core.priority = Priority.MEDIUM;
        task.core.closeOnMiss = true;
        task.core.deadline = plan.date;
        task.core.created = LocalDate.now();

        int duration = Math.max(15, recipe.getTotalTime());
        task.core.minDuration = duration;
        task.core.maxDuration = duration;
        task.core.cooldown = 1;

        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = plan.date;

        TaskPlannedMeal plannedMeal = new TaskPlannedMeal();
        plannedMeal.taskId = task.core.id;
        plannedMeal.day = plan.date;
        plannedMeal.recipeId = plan.recipeId;
        plannedMeal.plannedServings = Math.max(1, plan.plannedServings);
        plannedMeal.completed = false;
        task.plannedMeals = new ArrayList<>();
        task.plannedMeals.add(plannedMeal);

        TaskPrefSlot prefSlot = TaskPrefSlotFactory.create(
                task.core.id,
                EnumSet.of(plan.date.getDayOfWeek()),
                defaultStartTimeFor(plan.mealType));
        task.prefSlots = new ArrayList<>();
        task.prefSlots.add(prefSlot);

        task.slots = new ArrayList<>();
        task.parents = new ArrayList<>();
        task.prerequisites = new ArrayList<>();

        return task;
    }

    private static LocalTime defaultStartTimeFor(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> LocalTime.of(7, 0);
            case LUNCH -> LocalTime.of(12, 0);
            case DINNER -> LocalTime.of(18, 0);
            case SNACK -> LocalTime.of(15, 0);
        };
    }

    // --- Completion sync helpers ---

    private void completeTaskSlot(Task task, LocalDate date) {
        for (TaskSlot slot : task.slots) {
            if (date.equals(slot.day) && !slot.completed) {
                LocalTime now = LocalTime.now();
                if (slot.realStart == null) slot.realStart = now;
                slot.realEnd = now;
                slot.completed = true;
                return;
            }
        }
    }

    private void reopenTask(Task task, LocalDate date) {
        task.core.completed = false;

        TaskPlannedMeal plannedMeal = task.getPlannedMealForDate(date);
        if (plannedMeal != null) {
            plannedMeal.completed = false;
            plannedMeal.actualServings = 0;
        }

        for (TaskSlot slot : task.slots) {
            if (date.equals(slot.day) && slot.completed) {
                slot.completed = false;
                slot.realEnd = null;
                return;
            }
        }
    }
}
