package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MealManagementDataService {
    private final MealRepository mealRepository;
    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;
    private final LoadMealHomeUseCase loadMealHomeUseCase;

    public MealManagementDataService(MealRepository mealRepository,
                                     RecipeRepository recipeRepository,
                                     PantryRepository pantryRepository,
                                     LoadMealHomeUseCase loadMealHomeUseCase) {
        this.mealRepository = mealRepository;
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
        this.loadMealHomeUseCase = loadMealHomeUseCase;
    }

    public List<Recipe> loadRecipesForPlanManagement() {
        return loadMealHomeUseCase.loadSortedRecipes();
    }

    public List<Recipe> loadRecipesForManagement() {
        return loadMealHomeUseCase.loadSortedRecipes();
    }

    public List<Ingredient> loadIngredientsForManagement() {
        List<Ingredient> items = new ArrayList<>(recipeRepository.getIngredients());
        items.sort(Comparator.comparing(i -> i.name));
        return items;
    }

    public List<PantryItem> loadPantryItemsForManagement() {
        return loadMealHomeUseCase.loadSortedPantryItems();
    }

    public List<HouseholdMember> loadHouseholdMembersForManagement() {
        List<HouseholdMember> members = mealRepository.getHouseholdMembers();
        members.sort(Comparator.comparing(m -> m.name));
        return members;
    }

    public List<HouseholdMemberOverview> loadHouseholdMemberOverviewsForManagement(LocalDate today) {
        return mealRepository.getHouseholdMembers().stream()
                .sorted(Comparator.comparing(member -> member.name))
                .map(member -> new HouseholdMemberOverview(
                        member,
                        HouseholdEnergyService.calculateAge(member, today),
                        HouseholdEnergyService.calculateTdee(member, today)))
                .toList();
    }

    public void saveRecipe(Recipe recipe) {
        recipeRepository.saveRecipe(recipe);
    }

    public void deleteRecipe(String recipeId) {
        recipeRepository.deleteRecipe(recipeId);
    }

    public void saveIngredient(Ingredient ingredient) {
        recipeRepository.saveIngredient(ingredient);
    }

    public void deleteIngredient(String ingredientId) {
        recipeRepository.deleteIngredient(ingredientId);
    }

    public void savePantryItem(PantryItem pantryItem) {
        pantryRepository.savePantryItem(pantryItem);
    }

    public void deletePantryItem(String pantryItemId) {
        pantryRepository.deletePantryItem(pantryItemId);
    }

    public void saveHouseholdMember(HouseholdMember member) {
        mealRepository.saveHouseholdMember(member);
    }

    public void deleteHouseholdMember(String memberId) {
        mealRepository.deleteHouseholdMember(memberId);
    }

    public CookingPreferences loadCookingPreferences() {
        return mealRepository.getCookingPreferences();
    }

    public void saveCookingPreferences(CookingPreferences prefs) {
        mealRepository.saveCookingPreferences(prefs);
    }
}
