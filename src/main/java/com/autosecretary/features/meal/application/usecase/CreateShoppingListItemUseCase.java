package com.autosecretary.features.meal.application.usecase;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.domain.internal.ShoppingPackagingService;

/**
 * Application-Use-Case fuer Einkaufslisteneintraege mit Packungsrundung/Ueberschuss.
 */
public class CreateShoppingListItemUseCase {

    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;
    private final ShoppingPackagingService shoppingPackagingService;

    public CreateShoppingListItemUseCase(RecipeRepository recipeRepository,
                                         PantryRepository pantryRepository) {
        this(recipeRepository, pantryRepository, new ShoppingPackagingService());
    }

    public CreateShoppingListItemUseCase(RecipeRepository recipeRepository,
                                         PantryRepository pantryRepository,
                                         ShoppingPackagingService shoppingPackagingService) {
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
        this.shoppingPackagingService = shoppingPackagingService;
    }

    public ShoppingListItem execute(long ingredientId, double neededAmount, String periodKey) {
        Ingredient ingredient = recipeRepository.findIngredientById(ingredientId);
        if (ingredient == null) {
            return null;
        }
        ShoppingListItem shoppingListItem = shoppingPackagingService.createShoppingItem(
                ingredient,
                neededAmount,
                periodKey
        );
        pantryRepository.saveShoppingListItem(shoppingListItem);
        return shoppingListItem;
    }
}
