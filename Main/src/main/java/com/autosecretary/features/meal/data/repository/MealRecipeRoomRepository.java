package com.autosecretary.features.meal.data.repository;

import com.autosecretary.features.meal.data.dao.MealIngredientDao;
import com.autosecretary.features.meal.data.dao.MealRecipeDao;
import com.autosecretary.features.meal.data.entity.MealIngredientEntity;
import com.autosecretary.features.meal.data.entity.MealMemberRatingEntity;
import com.autosecretary.features.meal.data.entity.MealRecipeEntity;
import com.autosecretary.features.meal.data.entity.MealRecipeIngredientEntity;
import com.autosecretary.features.meal.data.entity.MealStorePackageEntity;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MealRecipeRoomRepository implements RecipeRepository {

    private final MealRecipeDao recipeDao;
    private final MealIngredientDao ingredientDao;

    public MealRecipeRoomRepository(MealRecipeDao recipeDao, MealIngredientDao ingredientDao) {
        this.recipeDao = recipeDao;
        this.ingredientDao = ingredientDao;
    }

    // ---- Recipe ----

    @Override
    public List<Recipe> getRecipes() {
        List<MealRecipeEntity> recipes = recipeDao.findAll();
        if (recipes.isEmpty()) return new ArrayList<>();

        List<String> ids = recipes.stream().map(r -> r.id).collect(Collectors.toList());
        Map<String, List<MealRecipeIngredientEntity>> ingredientsByRecipe =
                recipeDao.findIngredientsByRecipeIds(ids).stream()
                        .collect(Collectors.groupingBy(ri -> ri.recipeId));
        Map<String, List<MealMemberRatingEntity>> ratingsByRecipe =
                recipeDao.findRatingsByRecipeIds(ids).stream()
                        .collect(Collectors.groupingBy(mr -> mr.recipeId));

        return recipes.stream()
                .map(e -> toDomain(e,
                        ingredientsByRecipe.getOrDefault(e.id, Collections.emptyList()),
                        ratingsByRecipe.getOrDefault(e.id, Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    public Recipe findRecipeById(String recipeId) {
        MealRecipeEntity entity = recipeDao.findById(recipeId);
        if (entity == null) return null;
        return toDomain(entity,
                recipeDao.findIngredientsByRecipeId(recipeId),
                recipeDao.findRatingsByRecipeId(recipeId));
    }

    @Override
    public void saveRecipe(Recipe recipe) {
        if (recipe.id == null) recipe.id = UUID.randomUUID().toString();
        MealRecipeEntity entity = toEntity(recipe);
        List<MealRecipeIngredientEntity> ingredientEntities = toIngredientEntities(recipe);
        List<MealMemberRatingEntity> ratingEntities = toRatingEntities(recipe);
        recipeDao.saveRecipeWithChildren(entity, ingredientEntities, ratingEntities);
    }

    @Override
    public void deleteRecipe(String recipeId) {
        recipeDao.deleteById(recipeId);
    }

    private Recipe toDomain(MealRecipeEntity e,
                             List<MealRecipeIngredientEntity> ingredientEntities,
                             List<MealMemberRatingEntity> ratingEntities) {
        Recipe r = new Recipe();
        r.id = e.id;
        r.title = e.title;
        r.description = e.description;
        r.instructions = e.instructions;
        r.mealTypes = e.mealTypes;
        r.prepTimeMinutes = e.prepTimeMinutes;
        r.cookTimeMinutes = e.cookTimeMinutes;
        r.servings = e.servings;
        r.minServings = e.minServings;
        r.maxServings = e.maxServings;
        r.scalingPrecision = e.scalingPrecision;
        r.prepEffort = e.prepEffort;
        r.tags = e.tags;
        r.lastUsed = e.lastUsed;
        r.usageCount = e.usageCount;
        r.isFavorite = e.isFavorite;
        r.totalCalories = e.totalCalories;
        r.totalProtein = e.totalProtein;
        r.totalCarbs = e.totalCarbs;
        r.totalFat = e.totalFat;
        r.shelfLifeDays = e.shelfLifeDays;

        r.ingredients = new ArrayList<>();
        if (ingredientEntities != null) {
            for (MealRecipeIngredientEntity ri : ingredientEntities) {
                r.ingredients.add(new Recipe.RecipeIngredient(
                        ri.ingredientId, ri.ingredientName, ri.amount, ri.unit));
            }
        }

        r.ratings = new ArrayList<>();
        if (ratingEntities != null) {
            for (MealMemberRatingEntity mr : ratingEntities) {
                r.ratings.add(new Recipe.MemberRating(mr.memberId, mr.rating));
            }
        }

        return r;
    }

    private MealRecipeEntity toEntity(Recipe r) {
        MealRecipeEntity e = new MealRecipeEntity();
        e.id = r.id;
        e.title = r.title;
        e.description = r.description;
        e.instructions = r.instructions;
        e.mealTypes = r.mealTypes;
        e.prepTimeMinutes = r.prepTimeMinutes;
        e.cookTimeMinutes = r.cookTimeMinutes;
        e.servings = r.servings;
        e.minServings = r.minServings;
        e.maxServings = r.maxServings;
        e.scalingPrecision = r.scalingPrecision;
        e.prepEffort = r.prepEffort;
        e.tags = r.tags;
        e.lastUsed = r.lastUsed;
        e.usageCount = r.usageCount;
        e.isFavorite = r.isFavorite;
        e.totalCalories = r.totalCalories;
        e.totalProtein = r.totalProtein;
        e.totalCarbs = r.totalCarbs;
        e.totalFat = r.totalFat;
        e.shelfLifeDays = r.shelfLifeDays;
        return e;
    }

    private List<MealRecipeIngredientEntity> toIngredientEntities(Recipe r) {
        List<MealRecipeIngredientEntity> list = new ArrayList<>();
        if (r.ingredients == null) return list;
        for (Recipe.RecipeIngredient ri : r.ingredients) {
            MealRecipeIngredientEntity e = new MealRecipeIngredientEntity();
            e.id = UUID.randomUUID().toString();
            e.recipeId = r.id;
            e.ingredientId = ri.ingredientId();
            e.ingredientName = ri.ingredientName();
            e.amount = ri.amount();
            e.unit = ri.unit();
            list.add(e);
        }
        return list;
    }

    private List<MealMemberRatingEntity> toRatingEntities(Recipe r) {
        List<MealMemberRatingEntity> list = new ArrayList<>();
        if (r.ratings == null) return list;
        for (Recipe.MemberRating mr : r.ratings) {
            MealMemberRatingEntity e = new MealMemberRatingEntity();
            e.id = UUID.randomUUID().toString();
            e.recipeId = r.id;
            e.memberId = mr.memberId();
            e.rating = mr.rating();
            list.add(e);
        }
        return list;
    }

    // ---- Ingredient ----

    @Override
    public List<Ingredient> getIngredients() {
        List<MealIngredientEntity> ingredients = ingredientDao.findAll();
        if (ingredients.isEmpty()) return new ArrayList<>();

        List<String> ids = ingredients.stream().map(i -> i.id).collect(Collectors.toList());
        Map<String, List<MealStorePackageEntity>> packagesByIngredient =
                ingredientDao.findStorePackagesByIngredientIds(ids).stream()
                        .collect(Collectors.groupingBy(sp -> sp.ingredientId));

        return ingredients.stream()
                .map(e -> toDomain(e,
                        packagesByIngredient.getOrDefault(e.id, Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    public Ingredient findIngredientById(String ingredientId) {
        MealIngredientEntity entity = ingredientDao.findById(ingredientId);
        if (entity == null) return null;
        return toDomain(entity, ingredientDao.findStorePackagesByIngredientId(ingredientId));
    }

    @Override
    public void saveIngredient(Ingredient ingredient) {
        if (ingredient.id == null) ingredient.id = UUID.randomUUID().toString();
        MealIngredientEntity entity = toEntity(ingredient);
        List<MealStorePackageEntity> packageEntities = toPackageEntities(ingredient);
        ingredientDao.saveIngredientWithPackages(entity, packageEntities);
    }

    @Override
    public void deleteIngredient(String ingredientId) {
        ingredientDao.deleteById(ingredientId);
    }

    private Ingredient toDomain(MealIngredientEntity e, List<MealStorePackageEntity> packageEntities) {
        Ingredient i = new Ingredient();
        i.id = e.id;
        i.name = e.name;
        i.foodGroup = e.foodGroup;
        i.defaultUnit = e.defaultUnit;
        i.gramsPerUnit = e.gramsPerUnit;
        i.caloriesPer100 = e.caloriesPer100;
        i.proteinPer100 = e.proteinPer100;
        i.carbsPer100 = e.carbsPer100;
        i.fatPer100 = e.fatPer100;
        i.fiberPer100 = e.fiberPer100;
        i.shelfLifeDays = e.shelfLifeDays;
        i.requiresRefrigeration = e.requiresRefrigeration;
        i.isWholeUnit = e.isWholeUnit;
        i.isPerishable = e.isPerishable;

        i.storePackages = new ArrayList<>();
        if (packageEntities != null) {
            for (MealStorePackageEntity pe : packageEntities) {
                Ingredient.StorePackage pkg = new Ingredient.StorePackage();
                pkg.storeName = pe.storeName;
                pkg.unit = pe.unit;
                pkg.packageAmount = pe.packageAmount;
                pkg.priceCents = pe.priceCents;
                pkg.lastPurchased = pe.lastPurchased;
                i.storePackages.add(pkg);
            }
        }

        return i;
    }

    private MealIngredientEntity toEntity(Ingredient i) {
        MealIngredientEntity e = new MealIngredientEntity();
        e.id = i.id;
        e.name = i.name;
        e.foodGroup = i.foodGroup;
        e.defaultUnit = i.defaultUnit;
        e.gramsPerUnit = i.gramsPerUnit;
        e.caloriesPer100 = i.caloriesPer100;
        e.proteinPer100 = i.proteinPer100;
        e.carbsPer100 = i.carbsPer100;
        e.fatPer100 = i.fatPer100;
        e.fiberPer100 = i.fiberPer100;
        e.shelfLifeDays = i.shelfLifeDays;
        e.requiresRefrigeration = i.requiresRefrigeration;
        e.isWholeUnit = i.isWholeUnit;
        e.isPerishable = i.isPerishable;
        return e;
    }

    private List<MealStorePackageEntity> toPackageEntities(Ingredient i) {
        List<MealStorePackageEntity> list = new ArrayList<>();
        if (i.storePackages == null) return list;
        for (Ingredient.StorePackage pkg : i.storePackages) {
            MealStorePackageEntity e = new MealStorePackageEntity();
            e.id = UUID.randomUUID().toString();
            e.ingredientId = i.id;
            e.storeName = pkg.storeName;
            e.unit = pkg.unit;
            e.packageAmount = pkg.packageAmount;
            e.priceCents = pkg.priceCents;
            e.lastPurchased = pkg.lastPurchased;
            list.add(e);
        }
        return list;
    }
}
