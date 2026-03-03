package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * A single line item on the shopping list for a given planning period.
 *
 * {@code periodKey} groups items by the planning week (e.g. "2026-W10").
 * {@code neededAmount} is the total required by the meal plan; {@code excessAmount} is
 * the portion already covered by pantry stock; {@code amount} is the net quantity to buy.
 * {@code foodGroupLabel} is denormalized for display without an ingredient lookup.
 */
@Entity(tableName = "shopping_list_item", indices = {@Index("periodKey")})
public class MealShoppingListItemEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public String ingredientId;

    public String ingredientName;

    public double amount;

    public double neededAmount;

    public double excessAmount;

    public String unit;

    public String foodGroupLabel;

    public String suggestedStore;

    public int status;

    public String periodKey;

    public int estimatedPriceCents;
}
