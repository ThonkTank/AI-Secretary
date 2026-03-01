package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.autosecretary.features.meal.domain.MealType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single planned meal entry on the meal plan calendar.
 *
 * Each entry links a recipe (or a standalone item) to a specific date and meal type.
 * {@code recipeId} and {@code itemId} are mutually exclusive: a plan entry is either
 * recipe-based (recipeId set) or item-based (itemId set). {@code recipeTitle} is
 * denormalized for display without an extra lookup.
 *
 * {@code plannedServings} is the portion count at plan time; {@code actualServings}
 * is recorded on completion and may differ if the household ate more or less.
 */
@Entity(tableName = "meal_plan", indices = {@Index("date"), @Index("recipeId")})
public class MealPlanEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public LocalDate date;

    public MealType mealType;

    public String recipeId;

    public int plannedServings;

    public boolean isCompleted;

    public int actualServings;

    public LocalDateTime completedAt;

    public String itemId;

    public String recipeTitle;

    public int estimatedCalories;
}
