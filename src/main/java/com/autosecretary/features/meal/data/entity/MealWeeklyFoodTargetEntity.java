package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Persisted weekly DGE food-group targets and planned amounts for a single planning period.
 *
 * {@code periodKey} identifies the planning week (e.g. "2026-W10") and has a unique index
 * to enforce one row per period. The {@code target*} columns store the household's DGE
 * reference amounts in grams for that week, scaled from per-adult values by household
 * composition. The {@code planned*} columns are updated as recipes are added to the meal
 * plan and reflect how much of each food group the current plan already covers.
 */
@Entity(tableName = "weekly_food_target", indices = {@Index(value = "periodKey", unique = true)})
public class MealWeeklyFoodTargetEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public String periodKey;

    public int targetGrain;
    public int targetPotato;
    public int targetVegetable;
    public int targetFruit;
    public int targetDairy;
    public int targetMeat;
    public int targetFish;
    public int targetEgg;
    public int targetFat;
    public int targetLegume;
    public int targetNut;

    public int plannedGrain;
    public int plannedPotato;
    public int plannedVegetable;
    public int plannedFruit;
    public int plannedDairy;
    public int plannedMeat;
    public int plannedFish;
    public int plannedEgg;
    public int plannedFat;
    public int plannedLegume;
    public int plannedNut;
}
