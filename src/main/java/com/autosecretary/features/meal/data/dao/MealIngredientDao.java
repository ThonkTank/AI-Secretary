package com.autosecretary.features.meal.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import com.autosecretary.features.meal.data.entity.MealIngredientEntity;
import com.autosecretary.features.meal.data.entity.MealStorePackageEntity;

@Dao
public interface MealIngredientDao {

    @Query("SELECT * FROM ingredient ORDER BY name")
    List<MealIngredientEntity> findAll();

    @Query("SELECT * FROM ingredient WHERE id = :id LIMIT 1")
    MealIngredientEntity findById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MealIngredientEntity entity);

    @Query("DELETE FROM ingredient WHERE id = :id")
    void deleteById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStorePackages(List<MealStorePackageEntity> packages);

    @Query("DELETE FROM store_package WHERE ingredientId = :ingredientId")
    void deleteStorePackagesByIngredientId(String ingredientId);

    @Query("SELECT * FROM store_package WHERE ingredientId = :ingredientId")
    List<MealStorePackageEntity> findStorePackagesByIngredientId(String ingredientId);

    @Query("SELECT * FROM store_package WHERE ingredientId IN (:ingredientIds)")
    List<MealStorePackageEntity> findStorePackagesByIngredientIds(List<String> ingredientIds);

    @Transaction
    default void saveIngredientWithPackages(MealIngredientEntity ingredient,
                                             List<MealStorePackageEntity> packages) {
        insert(ingredient);
        deleteStorePackagesByIngredientId(ingredient.id);
        if (packages != null && !packages.isEmpty()) insertStorePackages(packages);
    }
}
