package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.Recipe;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Persisted representation of a recipe with its nutritional summary and meal-type classification.
 *
 * {@code mealTypes} is stored as a comma-separated string via the existing {@code Set<MealType>}
 * converter. {@code tags} is also a comma-separated free-form string (e.g. "vegetarian,quick").
 *
 * Nutritional totals ({@code totalCalories}, {@code totalProtein}, {@code totalCarbs},
 * {@code totalFat}) are cached values for the full base recipe — divide by {@code servings}
 * for per-serving values.
 *
 * Ingredient rows are stored separately in {@link MealRecipeIngredientEntity} and member ratings
 * in {@link MealMemberRatingEntity}, both FK'd to this entity's id.
 */
@Entity(tableName = "recipe")
public class MealRecipeEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public String title;

    public String description;

    public String instructions;

    public Set<MealType> mealTypes;

    public int prepTimeMinutes;

    public int cookTimeMinutes;

    public int servings;

    public int minServings;

    public int maxServings;

    public Recipe.ScalingPrecision scalingPrecision;

    public Recipe.PrepEffort prepEffort;

    public String tags;

    public LocalDate lastUsed;

    public int usageCount;

    public boolean isFavorite;

    public int totalCalories;

    public int totalProtein;

    public int totalCarbs;

    public int totalFat;

    public int shelfLifeDays;
}
