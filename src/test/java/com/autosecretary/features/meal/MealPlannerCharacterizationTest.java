package com.autosecretary.features.meal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.meal.application.MealHomeModel;
import com.autosecretary.features.meal.application.MealPlannerDataService;
import com.autosecretary.features.meal.application.WeeklyProgressOverview;
import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.data.repository.MealPantryRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.MealFixtures;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.EnumSet;

public final class MealPlannerCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private MealRoomRepository mealRepository;
    private MealRecipeRoomRepository recipeRepository;
    private MealPantryRoomRepository pantryRepository;
    private MealPlannerDataService dataService;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        mealRepository = new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(), db.mealWeeklyFoodTargetDao());
        recipeRepository = new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao());
        pantryRepository = new MealPantryRoomRepository(db.mealPantryDao());
        SynchronousExecutorService executor = new SynchronousExecutorService();
        dataService = new MealPlannerDataService(mealRepository, recipeRepository, pantryRepository, executor, executor);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void mealPlannerHomeAggregationAndCrudChainStayObservableInvariant() {
        LocalDate today = LocalDate.now();
        String ingredientId = "ingredient-rice";
        Recipe recipe = MealFixtures.recipe("Reis Bowl", ingredientId);
        recipeRepository.saveRecipe(recipe);
        PantryItem pantryItem = MealFixtures.pantryItem(ingredientId, 500.0);
        pantryRepository.savePantryItem(pantryItem);
        ShoppingListItem shoppingItem = MealFixtures.shoppingItem(ingredientId, today.toString());
        pantryRepository.saveShoppingListItem(shoppingItem);
        HouseholdMember member = MealFixtures.member("Ada");
        mealRepository.saveHouseholdMember(member);
        MealPlan plan = MealFixtures.mealPlan(today, MealType.LUNCH, recipe, 2);
        mealRepository.saveMealPlan(plan);

        CallbackProbe<MealHomeModel> homeProbe = new CallbackProbe<>();
        dataService.loadHome(homeProbe.consumer());
        MealHomeModel home = homeProbe.value();

        assertEquals(1, home.weekPlans.size());
        assertEquals(1, home.recipes.size());
        assertEquals(1, home.pantryItems.size());
        assertEquals(1, home.shoppingItems.size());
        assertEquals(1, home.weekPlanSnapshot.plannedCount);

        Recipe newRecipe = MealFixtures.recipe("Nudel Bowl", ingredientId);
        dataService.saveRecipe(newRecipe, new CallbackProbe<Void>().runnable());
        dataService.planRecipe(newRecipe.id, today, MealType.LUNCH, 3, new CallbackProbe<Void>().runnable());

        MealPlan createdPlan = mealRepository.getMealPlans(today, today).stream()
                .filter(candidate -> newRecipe.id.equals(candidate.recipeId))
                .findFirst()
                .orElseThrow();
        dataService.toggleMealCompleted(createdPlan.id, new CallbackProbe<Void>().runnable());

        MealPlan completed = mealRepository.findMealPlanById(createdPlan.id);
        assertNotNull(completed);
        assertTrue(completed.isCompleted);
        assertEquals(3, completed.actualServings);
        assertNotNull(completed.completedAt);
    }

    @Test
    public void householdManagementKeepsAgeGenderAndTdeeInvariant() {
        HouseholdMember member = MealFixtures.member("Ada");
        mealRepository.saveHouseholdMember(member);

        CallbackProbe<java.util.List<HouseholdMember>> probe = new CallbackProbe<>();
        dataService.loadHouseholdMembersForManagement(probe.consumer());
        java.util.List<HouseholdMember> members = probe.value();

        assertEquals(1, members.size());
        HouseholdMember loaded = members.get(0);
        assertEquals("Ada", loaded.name);
        assertTrue(loaded.isActive);
        LocalDate today = LocalDate.now();
        assertEquals(today.getYear() - 1990, HouseholdEnergyService.calculateAge(loaded, today));
        assertEquals(2204, HouseholdEnergyService.calculateTdee(loaded, LocalDate.of(2026, 7, 10)));
    }

    @Test
    public void weeklyProgressShoppingPantryCookingPrefsAndDeleteStayObservableInvariant() {
        LocalDate today = LocalDate.now();
        HouseholdMember member = MealFixtures.member("Ada");
        mealRepository.saveHouseholdMember(member);
        String ingredientId = "ingredient-rice";
        Recipe recipe = MealFixtures.recipe("Reis Bowl", ingredientId);
        recipeRepository.saveRecipe(recipe);
        MealPlan plan = MealFixtures.mealPlan(today, MealType.LUNCH, recipe, 2);
        mealRepository.saveMealPlan(plan);
        ShoppingListItem shoppingItem = MealFixtures.shoppingItem(ingredientId, today.toString());
        pantryRepository.saveShoppingListItem(shoppingItem);

        CallbackProbe<WeeklyProgressOverview> progressProbe = new CallbackProbe<>();
        dataService.getWeeklyProgressOverview(progressProbe.consumer());
        WeeklyProgressOverview progress = progressProbe.value();

        assertTrue(!progress.foodGroups.isEmpty());
        assertEquals(2204 * 7, progress.calorieTarget);
        assertEquals(0, progress.calorieActual);
        assertEquals(0, progress.calorieCompletionPercent);

        dataService.updateShoppingItemStatus(shoppingItem.id, ShoppingItemStatus.DONE,
                new CallbackProbe<Void>().runnable());
        ShoppingListItem updatedShoppingItem = pantryRepository.getShoppingListItems(today.toString()).stream()
                .filter(item -> shoppingItem.id.equals(item.id))
                .findFirst()
                .orElseThrow();
        assertEquals(ShoppingItemStatus.DONE, updatedShoppingItem.status);

        dataService.createShoppingItemFromNeed("Bohnen", 125.0, "g",
                new CallbackProbe<Void>().runnable());
        ShoppingListItem createdShoppingItem = pantryRepository.getShoppingListItems(today.toString()).stream()
                .filter(item -> "Bohnen".equals(item.ingredientName))
                .findFirst()
                .orElseThrow();
        assertEquals(125.0, createdShoppingItem.neededAmount, 0.01);
        assertEquals("g", createdShoppingItem.unit);

        dataService.createPantryItem("Bohnen", 200.0, "g", PantryItem.StorageLocation.FRIDGE, 5,
                new CallbackProbe<Void>().runnable());
        PantryItem createdPantryItem = pantryRepository.getPantryItems().stream()
                .filter(item -> "Bohnen".equals(item.ingredientName))
                .findFirst()
                .orElseThrow();
        assertEquals(200.0, createdPantryItem.amount, 0.01);
        assertEquals(PantryItem.StorageLocation.FRIDGE, createdPantryItem.location);
        assertEquals(LocalDate.now().plusDays(5), createdPantryItem.expiryDate);

        CookingPreferences prefs = new CookingPreferences();
        prefs.maxDinnerCooking = 2;
        prefs.dinnerCookingDays = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        dataService.saveCookingPreferences(prefs, new CallbackProbe<Void>().runnable());

        CallbackProbe<CookingPreferences> prefsProbe = new CallbackProbe<>();
        dataService.loadCookingPreferences(prefsProbe.consumer());
        CookingPreferences loadedPrefs = prefsProbe.value();
        assertEquals(2, loadedPrefs.maxDinnerCooking);
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), loadedPrefs.dinnerCookingDays);

        dataService.deleteMealPlan(plan.id, new CallbackProbe<Void>().runnable());
        assertTrue(mealRepository.getMealPlans(today, today).stream()
                .noneMatch(candidate -> plan.id.equals(candidate.id)));
    }
}
