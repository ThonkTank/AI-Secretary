package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import com.autosecretary.features.meal.data.entity.MealMemberRatingEntity;
import com.autosecretary.features.meal.data.entity.MealRecipeEntity;
import com.autosecretary.features.meal.data.entity.MealRecipeIngredientEntity;

@Dao
public interface MealRecipeDao {

    @Query("SELECT * FROM recipe ORDER BY title")
    List<MealRecipeEntity> findAll();

    @Query("SELECT * FROM recipe WHERE id = :id LIMIT 1")
    MealRecipeEntity findById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MealRecipeEntity entity);

    @Query("DELETE FROM recipe WHERE id = :id")
    void deleteById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertIngredients(List<MealRecipeIngredientEntity> ingredients);

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    void deleteIngredientsByRecipeId(String recipeId);

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId")
    List<MealRecipeIngredientEntity> findIngredientsByRecipeId(String recipeId);

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId IN (:recipeIds)")
    List<MealRecipeIngredientEntity> findIngredientsByRecipeIds(List<String> recipeIds);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRatings(List<MealMemberRatingEntity> ratings);

    @Query("DELETE FROM member_rating WHERE recipeId = :recipeId")
    void deleteRatingsByRecipeId(String recipeId);

    @Query("SELECT * FROM member_rating WHERE recipeId = :recipeId")
    List<MealMemberRatingEntity> findRatingsByRecipeId(String recipeId);

    @Query("SELECT * FROM member_rating WHERE recipeId IN (:recipeIds)")
    List<MealMemberRatingEntity> findRatingsByRecipeIds(List<String> recipeIds);

    @Transaction
    default void saveRecipeWithChildren(MealRecipeEntity recipe,
                                        List<MealRecipeIngredientEntity> ingredients,
                                        List<MealMemberRatingEntity> ratings) {
        insert(recipe);
        deleteIngredientsByRecipeId(recipe.id);
        if (ingredients != null && !ingredients.isEmpty()) insertIngredients(ingredients);
        deleteRatingsByRecipeId(recipe.id);
        if (ratings != null && !ratings.isEmpty()) insertRatings(ratings);
    }
}
