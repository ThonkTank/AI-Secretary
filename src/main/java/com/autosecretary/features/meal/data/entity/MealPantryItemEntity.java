package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.autosecretary.features.meal.domain.PantryItem;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A pantry inventory entry tracking a stored ingredient with its quantity, expiry date,
 * and storage location.
 *
 * {@code ingredientName} is denormalized from the ingredient record for display without
 * an extra lookup. Expiry warnings are computed by the domain's {@code ShelfLifeService};
 * {@code expiryDate} is the last valid day (MHD).
 */
@Entity(tableName = "pantry_item", indices = {@Index("ingredientId"), @Index("expiryDate")})
public class MealPantryItemEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public String ingredientId;

    public String ingredientName;

    public double amount;

    public String unit;

    public LocalDate purchaseDate;

    public LocalDate expiryDate;

    public PantryItem.StorageLocation location;
}
