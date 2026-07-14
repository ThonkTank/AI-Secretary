package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.data.repository.BudgetImportRoomRepository;
import com.autosecretary.features.budget.data.repository.BudgetRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRecipeRoomRepository;
import com.autosecretary.features.meal.data.repository.MealRoomRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.task.application.assistant.AssistantProposals.RecipesProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TaskChangesProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionDraft;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionImportProposal;
import com.autosecretary.features.task.application.assistant.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.task.application.internal.budget.AssistantTransactionImportExecutor;
import com.autosecretary.features.task.application.internal.meal.AssistantMealGateway;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.TaskChange;
import com.autosecretary.features.task.domain.assistant.TaskSnapshot;
import com.autosecretary.shared.Priority;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.BudgetFixtures;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Protects the confirm path: recipes persist with their ingredient children, a bank-statement import
 * runs once and dedupes on a repeat confirm, and task changes still route through
 * {@code ApplyTaskChangesUseCase} (registering an undo entry).
 */
public final class AssistantProposalConfirmTest extends AutoSecretaryRobolectricTest {

    private AppDatabase db;
    private SynchronousExecutorService exec;
    private AssistantMealGateway mealGateway;
    private AssistantTransactionImportExecutor importExecutor;
    private TaskChangeUndoHolder undoHolder;
    private ConfirmAssistantProposalUseCase confirmUseCase;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        exec = new SynchronousExecutorService();
        mealGateway = new AssistantMealGateway(
                new MealRecipeRoomRepository(db.mealRecipeDao(), db.mealIngredientDao()),
                new MealRoomRepository(db.mealPlanDao(), db.mealConsumptionLogDao(),
                        db.mealHouseholdMemberDao(), db.mealCookingPreferencesDao(),
                        db.mealWeeklyFoodTargetDao()));
        BudgetRoomRepository budgetRepo = BudgetFixtures.budgetRepository(db);
        BudgetImportRoomRepository importRepo = BudgetFixtures.budgetImportRepository(db);
        importExecutor = new AssistantTransactionImportExecutor(importRepo, budgetRepo);
        undoHolder = new TaskChangeUndoHolder();
        ApplyTaskChangesUseCase applyUseCase = new ApplyTaskChangesUseCase(
                db, db.taskDao(), db.taskCategoryDao(), db.taskCategoryWindowDao(), undoHolder, exec, exec);
        confirmUseCase = new ConfirmAssistantProposalUseCase(applyUseCase, mealGateway, importExecutor, exec, exec);
    }

    @After
    public void tearDown() {
        db.close();
    }

    /** Invariant: confirming a recipe proposal persists the recipe row and its ingredient children. */
    @Test
    public void confirmRecipesPersistsRecipeWithIngredients() {
        Recipe recipe = new Recipe.Builder("Toast").ingredient(null, "Brot", 2, "Scheibe").build();
        confirmUseCase.confirm(new RecipesProposal(List.of(recipe)), s -> {}, e -> {});

        List<Recipe> saved = mealGateway.recipes();
        assertEquals(1, saved.size());
        assertEquals(1, saved.get(0).ingredients.size());
        assertEquals("Brot", saved.get(0).ingredients.get(0).ingredientName());
    }

    /** Invariant: importing once persists the transaction; re-confirming the same import adds nothing. */
    @Test
    public void confirmImportIsIdempotentByFingerprint() {
        BudgetRoomRepository budgetRepo = BudgetFixtures.budgetRepository(db);
        budgetRepo.insertAccount(BudgetFixtures.account("Giro"));
        budgetRepo.insertCategory(BudgetFixtures.expenseCategory("Lebensmittel"));
        String accountId = budgetRepo.findDefaultActiveAccountId();
        BudgetImportRoomRepository importRepo = BudgetFixtures.budgetImportRepository(db);

        TransactionImportProposal proposal = new TransactionImportProposal(
                null, "auszug.pdf", "hash1",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                List.of(new TransactionDraft(LocalDate.of(2026, 7, 1), -1500, "Rewe", "Einkauf", null)),
                0);

        AtomicReference<String> firstSummary = new AtomicReference<>();
        confirmUseCase.confirm(proposal, firstSummary::set, e -> {});
        assertEquals(1, importRepo.findTransactionsForAccount(accountId).size());

        AtomicReference<String> secondSummary = new AtomicReference<>();
        confirmUseCase.confirm(proposal, secondSummary::set, e -> {});
        assertEquals("no new transaction on repeat import", 1,
                importRepo.findTransactionsForAccount(accountId).size());
        assertTrue(secondSummary.get().contains("0 neue"));
        assertTrue(secondSummary.get().contains("1 Duplikat"));
    }

    /** Invariant: task-change proposals apply via ApplyTaskChangesUseCase and register an undo entry. */
    @Test
    public void confirmTaskChangesAppliesAndRegistersUndo() {
        TaskAssistantProposal proposal = new TaskAssistantProposal(
                List.of(),
                List.of(new TaskChange(ChangeOp.CREATE, null, "Neu", null, null, Priority.MEDIUM, null)));
        TaskChangesProposal taskProposal =
                new TaskChangesProposal(new TaskSnapshot(List.of(), List.of()), proposal);

        assertTrue("no undo before confirm", undoHolder.isEmpty());
        confirmUseCase.confirm(taskProposal, s -> {}, e -> {});

        assertEquals(1, db.taskDao().readAll().size());
        assertFalse("apply registers an undo entry", undoHolder.isEmpty());
    }
}
