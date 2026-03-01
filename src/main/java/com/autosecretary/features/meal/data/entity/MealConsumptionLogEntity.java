package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Records a single consumption event for a household member on a given date.
 *
 * {@code servingsConsumed} supports fractional servings (e.g. 0.5 for a half portion).
 * Nutritional values ({@code calories}, {@code protein}, {@code carbs}, {@code fat}) are
 * snapshot values captured at log time so historical records are unaffected by later
 * changes to the source recipe or ingredient.
 *
 * {@code recipeId} and {@code itemId} identify the source of the consumption; exactly one
 * should be set per log entry.
 */
@Entity(tableName = "consumption_log", indices = {@Index("date"), @Index("memberId")})
public class MealConsumptionLogEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public LocalDate date;

    public String itemId;

    public String memberId;

    public String recipeId;

    public double servingsConsumed;

    public int calories;

    public int protein;

    public int carbs;

    public int fat;
}
