package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * A single ingredient entry within a recipe, stored as a child row of {@link MealRecipeEntity}.
 *
 * {@code ingredientName} is denormalized from {@link MealIngredientEntity} for display without
 * an extra lookup. The parent recipe row is referenced via {@code recipeId}; deletion of
 * the parent recipe cascades to all its ingredient rows.
 */
@Entity(
        tableName = "recipe_ingredient",
        foreignKeys = {
                @ForeignKey(
                        entity = MealRecipeEntity.class,
                        parentColumns = "id",
                        childColumns = "recipeId",
                        onDelete = ForeignKey.CASCADE,
                        onUpdate = ForeignKey.CASCADE
                )
        },
        indices = {@Index("recipeId")}
)
public class MealRecipeIngredientEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String recipeId;

    public String ingredientId;

    public String ingredientName;

    public double amount;

    public String unit;
}
