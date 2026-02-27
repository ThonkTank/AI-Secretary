package com.autosecretary.features.meal.application.usecase;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShelfLifeService;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Application-Use-Case zum Ableiten von Ablaufdaten aus shelfLifeDays.
 */
public class CreatePantryItemUseCase {

    private final RecipeRepository recipeRepository;
    private final PantryRepository pantryRepository;

    public CreatePantryItemUseCase(RecipeRepository recipeRepository,
                                   PantryRepository pantryRepository) {
        this.recipeRepository = recipeRepository;
        this.pantryRepository = pantryRepository;
    }

    public PantryItem execute(long ingredientId, double amount, LocalDate purchaseDate) {
        Ingredient ingredient = recipeRepository.findIngredientById(ingredientId);
        if (ingredient == null) {
            throw new IllegalArgumentException("Ingredient not found: id=" + ingredientId);
        }

        LocalDate effectiveDate = Objects.requireNonNullElse(purchaseDate, LocalDate.now());
        PantryItem pantryItem = new PantryItem.Builder(
                ingredient.id,
                ingredient.name,
                amount,
                ingredient.defaultUnit
        )
                .purchaseDate(effectiveDate)
                .expiryDate(ShelfLifeService.calculateExpiryDate(effectiveDate, ingredient.shelfLifeDays))
                .build();

        pantryRepository.savePantryItem(pantryItem);
        return pantryItem;
    }
}
