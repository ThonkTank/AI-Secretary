package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.autosecretary.features.meal.data.entity.MealPantryItemEntity;
import com.autosecretary.features.meal.data.entity.MealShoppingListItemEntity;

@Dao
public interface MealPantryDao {

    @Query("SELECT * FROM pantry_item ORDER BY ingredientName")
    List<MealPantryItemEntity> findAll();

    @Query("SELECT * FROM pantry_item WHERE id = :id LIMIT 1")
    MealPantryItemEntity findById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MealPantryItemEntity entity);

    @Query("DELETE FROM pantry_item WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT * FROM shopping_list_item WHERE periodKey = :periodKey ORDER BY ingredientName")
    List<MealShoppingListItemEntity> findShoppingItemsByPeriodKey(String periodKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertShoppingItem(MealShoppingListItemEntity entity);

    @Query("DELETE FROM shopping_list_item WHERE id = :id")
    void deleteShoppingItemById(String id);
}
