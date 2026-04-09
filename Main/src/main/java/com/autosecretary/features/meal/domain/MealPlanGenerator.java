package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stateless domain service that generates a weekly meal plan using a greedy slot-filling algorithm.
 *
 * <p>For each day in the planning window and each {@link MealType}, the generator scores all
 * eligible recipes and picks the best fit. Scoring considers member ratings, DGE food-group
 * targets, pantry availability (with expiry bonus), and variety (penalty for recent repeats).
 *
 * <p>Hard constraints from {@link CookingPreferences} are respected: allowed cooking days per
 * meal type, maximum cooking sessions per week (recipes under {@code quickPrepMaxMinutes} do
 * not consume a session), and existing plans are preserved (never overwritten).
 *
 * <p>Usage: call {@link #generateWeekPlan} directly — do not instantiate this class.
 */
public class MealPlanGenerator {

    /**
     * Generates meal plan entries for the given date window.
     *
     * <p>Existing plans within the window are preserved — only empty slots are filled.
     * Returns an empty list if no recipes are available.
     *
     * @param startDate     first day of the planning window (inclusive)
     * @param days          number of days to plan (typically 7)
     * @param recipes       all available recipes
     * @param ingredients   all ingredient master data (for food-group lookup)
     * @param cookingPrefs  cooking session limits and allowed days
     * @param weeklyTarget  DGE food-group targets for the household
     * @param pantryItems   current pantry inventory
     * @param existingPlans plans already in the window (will not be overwritten)
     * @param householdSize number of active household members (drives serving count)
     * @return newly generated {@link MealPlan} entries (caller must persist them)
     */
    public static List<MealPlan> generateWeekPlan(
            LocalDate startDate, int days,
            List<Recipe> recipes,
            List<Ingredient> ingredients,
            CookingPreferences cookingPrefs,
            WeeklyFoodTarget weeklyTarget,
            List<PantryItem> pantryItems,
            List<MealPlan> existingPlans,
            int householdSize) {

        if (recipes == null || recipes.isEmpty()) return new ArrayList<>();

        // Index recipes by MealType
        Map<MealType, List<Recipe>> recipesByType = new EnumMap<>(MealType.class);
        for (MealType type : MealType.values()) {
            recipesByType.put(type, new ArrayList<>());
        }
        for (Recipe recipe : recipes) {
            if (recipe.mealTypes != null) {
                for (MealType type : recipe.mealTypes) {
                    recipesByType.get(type).add(recipe);
                }
            }
        }

        // Index ingredients by ID for food-group lookup
        Map<String, Ingredient> ingredientById = new HashMap<>();
        if (ingredients != null) {
            for (Ingredient ing : ingredients) {
                if (ing.id != null) ingredientById.put(ing.id, ing);
            }
        }

        // Index pantry items by ingredientId
        Map<String, List<PantryItem>> pantryByIngredient = new HashMap<>();
        if (pantryItems != null) {
            for (PantryItem item : pantryItems) {
                if (item.ingredientId != null) {
                    pantryByIngredient.computeIfAbsent(item.ingredientId, k -> new ArrayList<>()).add(item);
                }
            }
        }

        // Mark existing slots as occupied
        Set<String> occupiedSlots = new HashSet<>();
        if (existingPlans != null) {
            for (MealPlan plan : existingPlans) {
                occupiedSlots.add(slotKey(plan.date, plan.mealType));
            }
        }

        // Tracking state across the planning window
        Map<MealType, Integer> cookingSessionsUsed = new EnumMap<>(MealType.class);
        for (MealType type : MealType.values()) cookingSessionsUsed.put(type, 0);
        Set<String> recipesPlannedThisWeek = new HashSet<>();
        Map<Ingredient.FoodGroup, Integer> remainingTargets = weeklyTarget != null
                ? weeklyTarget.toRemainingMap()
                : new EnumMap<>(Ingredient.FoodGroup.class);

        int effectiveHouseholdSize = Math.max(1, householdSize);

        List<MealPlan> result = new ArrayList<>();

        for (int d = 0; d < days; d++) {
            LocalDate day = startDate.plusDays(d);

            for (MealType mealType : MealType.values()) {
                // Skip if slot already occupied
                if (occupiedSlots.contains(slotKey(day, mealType))) continue;

                // Skip if cooking not allowed on this day for this meal type
                if (!cookingPrefs.canCookOn(day.getDayOfWeek(), mealType)) continue;

                int maxSessions = cookingPrefs.getMaxCookingPerWeek(mealType);
                int sessionsUsed = cookingSessionsUsed.get(mealType);
                boolean sessionBudgetExhausted = sessionsUsed >= maxSessions;

                List<Recipe> candidates = recipesByType.get(mealType);
                if (candidates == null || candidates.isEmpty()) continue;

                Recipe bestRecipe = null;
                double bestScore = 0;

                for (Recipe recipe : candidates) {
                    boolean isQuickPrep = recipe.getTotalTime() <= cookingPrefs.quickPrepMaxMinutes;

                    // If session budget exhausted, only allow quick-prep recipes
                    if (sessionBudgetExhausted && !isQuickPrep) continue;

                    double score = scoreRecipe(recipe, day, ingredientById,
                            remainingTargets, pantryByIngredient, recipesPlannedThisWeek);

                    if (score > bestScore) {
                        bestScore = score;
                        bestRecipe = recipe;
                    }
                }

                if (bestRecipe == null) continue;

                // Calculate servings based on household size, clamped to recipe range
                int servings = clampServings(effectiveHouseholdSize, bestRecipe);

                MealPlan plan = new MealPlan.Builder(day, mealType, bestRecipe.id)
                        .servings(servings)
                        .recipeTitle(bestRecipe.title)
                        .calories(bestRecipe.totalCalories)
                        .build();
                plan.id = UUID.randomUUID().toString();
                result.add(plan);

                // Update tracking state
                recipesPlannedThisWeek.add(bestRecipe.id);
                boolean isQuickPrep = bestRecipe.getTotalTime() <= cookingPrefs.quickPrepMaxMinutes;
                if (!isQuickPrep) {
                    cookingSessionsUsed.merge(mealType, 1, Integer::sum);
                }
                updateFoodTargets(remainingTargets, bestRecipe, servings, ingredientById);
            }
        }

        return result;
    }

    // --- Scoring ---

    private static double scoreRecipe(
            Recipe recipe, LocalDate day,
            Map<String, Ingredient> ingredientById,
            Map<Ingredient.FoodGroup, Integer> remainingTargets,
            Map<String, List<PantryItem>> pantryByIngredient,
            Set<String> recipesPlannedThisWeek) {

        double score = 1.0;

        // 1. Rating: 0.4–2.0 (default 3.0/5 if unrated → 1.2)
        double rating = recipe.getAverageRating() > 0 ? recipe.getAverageRating() : 3.0;
        score *= (rating / 5.0) * 2.0;

        // 2. Favorite bonus
        if (recipe.isFavorite) score *= 1.5;

        // 3. Nutrition: how well does recipe fill DGE target gaps?
        double nutritionScore = calculateNutritionScore(recipe, remainingTargets, ingredientById);
        score *= (1.0 + nutritionScore);

        // 4. Pantry availability + expiry bonus
        double pantryScore = calculatePantryScore(recipe, pantryByIngredient, day);
        score *= (1.0 + pantryScore);

        // 5. Variety penalty
        if (recipesPlannedThisWeek.contains(recipe.id)) {
            score *= 0.2;
        }
        if (recipe.lastUsed != null) {
            long daysSinceUsed = ChronoUnit.DAYS.between(recipe.lastUsed, day);
            if (daysSinceUsed < 3) score *= 0.3;
            else if (daysSinceUsed < 7) score *= 0.6;
        }

        // 6. Quick-prep bonus (conserves cooking session budget)
        if (recipe.prepEffort == Recipe.PrepEffort.QUICK) score *= 1.2;

        return score;
    }

    /**
     * Scores how well a recipe's ingredients fill remaining DGE food-group targets.
     * Returns 0.0–1.0: higher = fills more gaps.
     */
    private static double calculateNutritionScore(
            Recipe recipe, Map<Ingredient.FoodGroup, Integer> remainingTargets,
            Map<String, Ingredient> ingredientById) {

        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) return 0.0;

        double totalContribution = 0;
        double totalTarget = 0;

        for (Ingredient.FoodGroup group : Ingredient.FoodGroup.values()) {
            int remaining = remainingTargets.getOrDefault(group, 0);
            if (remaining > 0) totalTarget += remaining;
        }
        if (totalTarget == 0) return 0.0;

        for (Recipe.RecipeIngredient ri : recipe.ingredients) {
            Ingredient ingredient = ingredientById.get(ri.ingredientId());
            if (ingredient == null || ingredient.foodGroup == null) continue;

            int remaining = remainingTargets.getOrDefault(ingredient.foodGroup, 0);
            if (remaining <= 0) continue;

            double grams = ingredient.getFoodGroupGrams(ri.amount(), ri.unit());
            totalContribution += Math.min(grams, remaining);
        }

        return Math.min(1.0, totalContribution / totalTarget);
    }

    /**
     * Scores pantry availability for a recipe's ingredients.
     * Returns 0.0–1.0: proportion of ingredients found in pantry, with bonus for expiring items.
     */
    private static double calculatePantryScore(
            Recipe recipe,
            Map<String, List<PantryItem>> pantryByIngredient,
            LocalDate referenceDate) {

        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) return 0.0;

        int totalIngredients = recipe.ingredients.size();
        double matchScore = 0;

        for (Recipe.RecipeIngredient ri : recipe.ingredients) {
            List<PantryItem> pantryItems = pantryByIngredient.get(ri.ingredientId());
            if (pantryItems == null || pantryItems.isEmpty()) continue;

            matchScore += 1.0;

            // Expiry bonus: if any matching pantry item expires within 3 days
            for (PantryItem item : pantryItems) {
                if (item.expiryDate != null) {
                    int daysUntilExpiry = ShelfLifeService.daysUntilExpiry(item.expiryDate, referenceDate);
                    if (daysUntilExpiry >= 0 && daysUntilExpiry <= 3) {
                        matchScore += 0.5;
                        break;
                    }
                }
            }
        }

        return matchScore / totalIngredients;
    }

    // --- State updates ---

    private static void updateFoodTargets(
            Map<Ingredient.FoodGroup, Integer> remainingTargets,
            Recipe recipe, int servings,
            Map<String, Ingredient> ingredientById) {

        if (recipe.ingredients == null) return;
        double factor = recipe.servings > 0 ? (double) servings / recipe.servings : 1.0;

        for (Recipe.RecipeIngredient ri : recipe.ingredients) {
            Ingredient ingredient = ingredientById.get(ri.ingredientId());
            if (ingredient == null || ingredient.foodGroup == null) continue;

            double grams = ingredient.getFoodGroupGrams(ri.amount() * factor, ri.unit());
            int current = remainingTargets.getOrDefault(ingredient.foodGroup, 0);
            remainingTargets.put(ingredient.foodGroup, Math.max(0, current - (int) grams));
        }
    }

    // --- Helpers ---

    private static String slotKey(LocalDate date, MealType mealType) {
        return date.toString() + ":" + mealType.name();
    }

    private static int clampServings(int householdSize, Recipe recipe) {
        int servings = householdSize;
        if (recipe.minServings > 0) servings = Math.max(servings, recipe.minServings);
        if (recipe.maxServings > 0) servings = Math.min(servings, recipe.maxServings);
        return Math.max(1, servings);
    }

    private MealPlanGenerator() {}
}
