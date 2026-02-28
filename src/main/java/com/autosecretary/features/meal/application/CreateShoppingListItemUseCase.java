package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.application.internal.EntityLookupHelper;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.domain.ShoppingPackagingService;

/**
 * Creates a shopping-list entry for a known ingredient, applying package-size rounding.
 *
 * <p>Delegates to {@link com.autosecretary.features.meal.domain.ShoppingPackagingService}, which
 * rounds {@code neededAmount} up to the nearest standard package size (e.g., 450 g → 1× 500 g
 * bag) and stores the leftover as {@code ShoppingListItem.excessAmount}. This avoids
 * "buy 1.8 packages" entries on the shopping list.
 *
 * <p>{@code periodKey} groups items by shopping trip; use {@code LocalDate.now().toString()}
 * for the current week.
 *
 * <p>Throws {@link IllegalArgumentException} if no ingredient with the given ID exists.
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
