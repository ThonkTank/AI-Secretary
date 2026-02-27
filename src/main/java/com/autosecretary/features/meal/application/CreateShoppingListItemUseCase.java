package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.application.internal.EntityLookupHelper;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.domain.ShoppingPackagingService;

/**
 * Application-Use-Case fuer Einkaufslisteneintraege mit Packungsrundung/Ueberschuss.
 */
public class CreateShoppingListItemUseCase {

    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;

    public CreateShoppingListItemUseCase(RecipeRepository recipeRepository,
                                         PantryRepository pantryRepository) {
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
    }

    public ShoppingListItem execute(long ingredientId, double neededAmount, String periodKey) {
        Ingredient ingredient = EntityLookupHelper.requireFound(
                recipeRepository.findIngredientById(ingredientId), "Ingredient", ingredientId);
        ShoppingListItem shoppingListItem = ShoppingPackagingService.createShoppingItem(
                ingredient,
                neededAmount,
                periodKey
        );
        pantryRepository.saveShoppingListItem(shoppingListItem);
        return shoppingListItem;
    }
}
