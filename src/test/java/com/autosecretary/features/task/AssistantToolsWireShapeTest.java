package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.task.application.assistant.AssistantConversation;
import com.autosecretary.features.task.application.assistant.internal.AssistantTool;
import com.autosecretary.features.task.application.assistant.internal.AssistantToolRegistry;
import com.autosecretary.features.task.application.assistant.internal.BudgetTools;
import com.autosecretary.features.task.application.assistant.internal.DbCalls;
import com.autosecretary.features.task.application.assistant.internal.MealTools;
import com.autosecretary.features.task.application.assistant.internal.TaskTools;
import com.autosecretary.features.task.application.internal.budget.AssistantBudgetGateway;
import com.autosecretary.features.task.application.internal.budget.AssistantTransactionImportExecutor;
import com.autosecretary.features.task.application.internal.meal.AssistantMealGateway;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the wire-level tool catalogue against drift introduced by the decomposition into per-domain
 * handlers. The {@code tools} array order, every tool description, and every input schema are
 * prompt-sensitive: a silent change would alter model behaviour. The golden file was captured from
 * the pre-refactor {@code AssistantTools.toolsJson()} and must stay byte-identical.
 */
public final class AssistantToolsWireShapeTest extends AutoSecretaryRobolectricTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** Invariant: the assembled registry's tools JSON equals the captured golden wire bytes. */
    @Test
    public void toolsJsonMatchesGolden() throws JSONException {
        assertEquals(golden(), registry().toolsJson().toString());
    }

    private AssistantToolRegistry registry() {
        SynchronousExecutorService exec = new SynchronousExecutorService();
        AssistantConversation conversation = new AssistantConversation();
        AssistantMealGateway mealGateway = new AssistantMealGateway(
                new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                        db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                        db.mealWeeklyFoodTargetDao()));
        AssistantBudgetGateway budgetGateway = new AssistantBudgetGateway(BudgetFixtures.budgetRepository(db));
        AssistantTransactionImportExecutor importExecutor = new AssistantTransactionImportExecutor(
                BudgetFixtures.budgetImportRepository(db), BudgetFixtures.budgetRepository(db));
        DbCalls dbCalls = new DbCalls(exec);
        List<AssistantTool> tools = new ArrayList<>();
        tools.addAll(new TaskTools(db.taskDao(), db.taskCategoryDao(), dbCalls).tools());
        tools.addAll(new MealTools(mealGateway, dbCalls).tools());
        tools.addAll(new BudgetTools(budgetGateway, importExecutor,
                conversation::currentStatement, dbCalls).tools());
        return new AssistantToolRegistry(tools);
    }

    private static String golden() {
        try (InputStream in = AssistantToolsWireShapeTest.class
                .getResourceAsStream("/assistant/tools-golden.json")) {
            if (in == null) {
                throw new IllegalStateException("golden resource missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
