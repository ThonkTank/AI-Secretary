package com.autosecretary.features.meal.application.usecase;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.internal.ShelfLifeService;

import java.time.LocalDate;

/**
 * Application-Use-Case zum Ableiten von Ablaufdaten aus shelfLifeDays.
 */
public class CreatePantryItemUseCase {

    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;
    private final ShelfLifeService shelfLifeService;

    public CreatePantryItemUseCase(RecipeRepository recipeRepository,
                                   PantryRepository pantryRepository) {
        this(recipeRepository, pantryRepository, new ShelfLifeService());
    }

    public CreatePantryItemUseCase(RecipeRepository recipeRepository,
                                   PantryRepository pantryRepository,
                                   ShelfLifeService shelfLifeService) {
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
        this.shelfLifeService = shelfLifeService;
    }

    public PantryItem execute(long ingredientId, double amount, LocalDate purchaseDate) {
        Ingredient ingredient = recipeRepository.findIngredientById(ingredientId);
        if (ingredient == null) {
            throw new IllegalArgumentException("Ingredient not found: id=" + ingredientId);
        }

        if (ingredient.id == null) {
            throw new IllegalStateException("Ingredient found by id=" + ingredientId + " has null id");
        }
        LocalDate safePurchaseDate = purchaseDate == null ? LocalDate.now() : purchaseDate;
        PantryItem pantryItem = new PantryItem.Builder(
                ingredient.id,
                ingredient.name,
                amount,
                ingredient.defaultUnit
        )
                .purchaseDate(safePurchaseDate)
                .expiryDate(shelfLifeService.calculateExpiryDate(safePurchaseDate, ingredient.shelfLifeDays))
                .build();

        pantryRepository.savePantryItem(pantryItem);
        return pantryItem;
    }
}
