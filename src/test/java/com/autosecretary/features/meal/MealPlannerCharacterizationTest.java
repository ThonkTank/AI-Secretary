package com.autosecretary.features.meal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.meal.application.MealHomeModel;
import com.autosecretary.features.meal.application.MealPlannerPresenter;
import com.autosecretary.features.meal.data.repository.MealPantryRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
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

public final class MealPlannerCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private MealRoomRepository mealRepository;
    private MealRecipeRoomRepository recipeRepository;
    private MealPantryRoomRepository pantryRepository;
    private MealPlannerPresenter presenter;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        mealRepository = new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(), db.mealWeeklyFoodTargetDao());
        recipeRepository = new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao());
        pantryRepository = new MealPantryRoomRepository(db.mealPantryDao());
        SynchronousExecutorService executor = new SynchronousExecutorService();
        presenter = new MealPlannerPresenter(mealRepository, recipeRepository, pantryRepository, executor, executor);
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
        presenter.loadHome(homeProbe.consumer());
        MealHomeModel home = homeProbe.value();

        assertEquals(1, home.weekPlans.size());
        assertEquals(1, home.recipes.size());
        assertEquals(1, home.pantryItems.size());
        assertEquals(1, home.shoppingItems.size());
        assertEquals(1, home.weekPlanSnapshot.plannedCount);

        Recipe newRecipe = MealFixtures.recipe("Nudel Bowl", ingredientId);
        presenter.saveRecipe(newRecipe, new CallbackProbe<Void>().runnable());
        presenter.planRecipe(newRecipe.id, today, MealType.LUNCH, 3, new CallbackProbe<Void>().runnable());

        MealPlan createdPlan = mealRepository.getMealPlans(today, today).stream()
                .filter(candidate -> newRecipe.id.equals(candidate.recipeId))
                .findFirst()
                .orElseThrow();
        presenter.toggleMealCompleted(createdPlan.id, new CallbackProbe<Void>().runnable());

        MealPlan completed = mealRepository.findMealPlanById(createdPlan.id);
        assertNotNull(completed);
        assertTrue(completed.isCompleted);
        assertEquals(3, completed.actualServings);
        assertNotNull(completed.completedAt);
    }
}
