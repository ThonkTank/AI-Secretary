package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A store-specific packaging variant of an ingredient.
 *
 * For example, "Rewe Fleischtomaten 500 g" is one MealStorePackageEntity for ingredient "Tomaten".
 * Multiple packages per ingredient allow tracking which shop and package size is cheapest.
 * The parent ingredient row is referenced via {@code ingredientId}; deletion of the parent
 * ingredient cascades to all its package rows.
 */
@Entity(
        tableName = "store_package",
        foreignKeys = {
                @ForeignKey(
                        entity = MealIngredientEntity.class,
                        parentColumns = "id",
                        childColumns = "ingredientId",
                        onDelete = ForeignKey.CASCADE,
                        onUpdate = ForeignKey.CASCADE
                )
        },
        indices = {@Index("ingredientId")}
)
public class MealStorePackageEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String ingredientId;

    public String storeName;

    public String unit;

    public int packageAmount;

    public Integer priceCents;

    public LocalDate lastPurchased;
}
