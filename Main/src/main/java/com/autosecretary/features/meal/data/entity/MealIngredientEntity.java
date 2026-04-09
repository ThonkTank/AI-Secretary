package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.autosecretary.features.meal.domain.Ingredient;

import java.util.UUID;

/**
 * Persisted representation of a food ingredient with nutritional values and DGE classification.
 *
 * <p><strong>Nutrition encoding (×10 fixed-point):</strong> Macro fields ({@code proteinPer100},
 * {@code carbsPer100}, {@code fatPer100}, {@code fiberPer100}) store one decimal place scaled by 10.
 * Example: 12.5 g protein per 100 g is stored as {@code proteinPer100 = 125}.
 * {@code caloriesPer100} is plain kcal (no ×10 scaling).
 *
 * <p>Store-specific packaging variants are stored separately in {@link MealStorePackageEntity},
 * FK'd to this entity's id.
 */
@Entity(tableName = "ingredient")
public class MealIngredientEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public String name;

    public Ingredient.FoodGroup foodGroup;

    public String defaultUnit;

    public int gramsPerUnit;

    public int caloriesPer100;

    public int proteinPer100;

    public int carbsPer100;

    public int fatPer100;

    public int fiberPer100;

    public int shelfLifeDays;

    public boolean requiresRefrigeration;

    public boolean isWholeUnit;

    public boolean isPerishable;
}
