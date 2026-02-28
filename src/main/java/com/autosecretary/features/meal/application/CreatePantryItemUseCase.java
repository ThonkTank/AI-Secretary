package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.application.internal.EntityLookupHelper;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShelfLifeService;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Creates a pantry item for a known ingredient, automatically deriving its expiry date.
 *
 * <p>Expiry date = {@code purchaseDate + ingredient.shelfLifeDays}, computed by
 * {@link com.autosecretary.features.meal.domain.ShelfLifeService#calculateExpiryDate}.
 * If {@code purchaseDate} is null, today is used.
 *
 * <p>The unit and packaging values are taken from the ingredient record, so the caller only
 * needs to supply the ingredient ID, amount, and purchase date.
 *
 * <p>Throws {@link IllegalArgumentException} if no ingredient with the given ID exists.
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
        Ingredient ingredient = EntityLookupHelper.requireFound(
                recipeRepository.findIngredientById(ingredientId), "Ingredient", ingredientId);

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
