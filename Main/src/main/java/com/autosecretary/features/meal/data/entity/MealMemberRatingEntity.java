package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * A household member's star rating (1–5) for a specific recipe.
 *
 * Ratings are stored per member so per-member preferences can drive recipe suggestions.
 * The parent recipe row is referenced via {@code recipeId}; deletion of the parent recipe
 * cascades to all its rating rows.
 */
@Entity(
        tableName = "member_rating",
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
public class MealMemberRatingEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String recipeId;

    public String memberId;

    public int rating;
}
