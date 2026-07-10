package com.autosecretary.testing;

import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.time.LocalDate;

public final class MealFixtures {
    private MealFixtures() {
    }

    public static Recipe recipe(String title, String ingredientId) {
        Recipe recipe = new Recipe.Builder(title)
                .mealType(MealType.LUNCH)
                .servings(2)
                .ingredient(ingredientId, "Reis", 100.0, "g")
                .build();
        recipe.totalCalories = 600;
        recipe.totalProtein = 20;
        recipe.totalCarbs = 80;
        recipe.totalFat = 10;
        return recipe;
    }

    public static PantryItem pantryItem(String ingredientId, double amount) {
        return new PantryItem.Builder(ingredientId, "Reis", amount, "g")
                .purchaseDate(LocalDate.now().minusDays(1))
                .expiryDate(LocalDate.now().plusDays(10))
                .location(PantryItem.StorageLocation.PANTRY)
                .build();
    }

    public static MealPlan mealPlan(LocalDate day, MealType mealType, Recipe recipe, int servings) {
        return new MealPlan.Builder(day, mealType, recipe.id)
                .servings(servings)
                .recipeTitle(recipe.title)
                .calories(recipe.totalCalories)
                .build();
    }

    public static ShoppingListItem shoppingItem(String ingredientId, String periodKey) {
        return new ShoppingListItem.Builder(ingredientId, "Reis", 250.0, "g")
                .foodGroup(Ingredient.FoodGroup.GRAIN.label)
                .periodKey(periodKey)
                .price(199)
                .build();
    }

    public static HouseholdMember member(String name) {
        HouseholdMember member = new HouseholdMember();
        member.name = name;
        member.birthYear = 1990;
        member.gender = HouseholdMember.Gender.FEMALE;
        member.weightKg = 70;
        member.heightCm = 170;
        member.activityLevel = HouseholdMember.ActivityLevel.MODERATE;
        member.isActive = true;
        return member;
    }
}
