package com.autosecretary.features.task.application.internal.meal;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.task.application.assistant.AssistantProposals.MealPlanDraft;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The assistant's only door into the meal feature. Reads recipes/ingredients/meal-plans for the
 * {@code get_*} chat tools and persists confirmed recipe/ingredient/meal-plan proposals. Depends
 * only on the meal {@code domain} repositories (cross-feature access is allowed solely through the
 * foreign domain layer).
 *
 * <p><strong>Threading:</strong> every method touches Room and must be called on the
 * {@code dbExecutor}; callers arrange that.
 */
public class AssistantMealGateway {

    private final RecipeRepository recipeRepository;
    private final MealRepository mealRepository;

    public AssistantMealGateway(RecipeRepository recipeRepository, MealRepository mealRepository) {
        this.recipeRepository = recipeRepository;
        this.mealRepository = mealRepository;
    }

    public List<Recipe> recipes() {
        return recipeRepository.getRecipes();
    }

    public List<Ingredient> ingredients() {
        return recipeRepository.getIngredients();
    }

    public List<MealPlan> mealPlans(LocalDate fromInclusive, LocalDate toInclusive) {
        return mealRepository.getMealPlans(fromInclusive, toInclusive);
    }

    public void saveRecipes(List<Recipe> recipes) {
        for (Recipe recipe : recipes) {
            recipeRepository.saveRecipe(recipe);
        }
    }

    public void saveIngredients(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            recipeRepository.saveIngredient(ingredient);
        }
    }

    /**
     * Persists meal-plan entries, resolving each recipe by id (preferred) or by title
     * (case-insensitive). Title resolution reads the recipe list once so plans can reference recipes
     * confirmed earlier in the same chat.
     *
     * @throws IllegalArgumentException if an entry references a recipe that cannot be resolved
     */
    public void saveMealPlans(List<MealPlanDraft> entries) {
        List<Recipe> allRecipes = recipeRepository.getRecipes();
        for (MealPlanDraft entry : entries) {
            Recipe recipe = resolveRecipe(entry, allRecipes);
            int servings = entry.servings() > 0 ? entry.servings() : recipe.servings;
            MealPlan plan = new MealPlan.Builder(entry.date(), entry.mealType(), recipe.id)
                    .servings(servings)
                    .recipeTitle(recipe.title)
                    .calories(perPortionCalories(recipe) * servings)
                    .build();
            mealRepository.saveMealPlan(plan);
        }
    }

    private Recipe resolveRecipe(MealPlanDraft entry, List<Recipe> allRecipes) {
        if (entry.recipeId() != null && !entry.recipeId().isBlank()) {
            Recipe byId = recipeRepository.findRecipeById(entry.recipeId());
            if (byId != null) {
                return byId;
            }
        }
        if (entry.recipeTitle() != null && !entry.recipeTitle().isBlank()) {
            String needle = entry.recipeTitle().trim().toLowerCase(Locale.ROOT);
            for (Recipe recipe : allRecipes) {
                if (recipe.title != null && recipe.title.trim().toLowerCase(Locale.ROOT).equals(needle)) {
                    return recipe;
                }
            }
        }
        String label = entry.recipeTitle() != null ? entry.recipeTitle() : entry.recipeId();
        throw new IllegalArgumentException("Rezept \"" + label + "\" nicht gefunden");
    }

    /** Per-serving kcal, derived from the recipe's base-total calories. */
    private static int perPortionCalories(Recipe recipe) {
        return recipe.servings > 0 ? recipe.totalCalories / recipe.servings : recipe.totalCalories;
    }
}
