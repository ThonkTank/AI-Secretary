package com.autosecretary.features.assistant.application;

import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.assistant.application.AssistantProposals.IngredientsProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.MealPlansProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.PendingProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.RecipesProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.TaskChangesProposal;
import com.autosecretary.features.assistant.application.AssistantProposals.TransactionImportProposal;
import com.autosecretary.features.assistant.application.internal.AssistantTransactionImportExecutor;
import com.autosecretary.features.assistant.application.internal.AssistantTransactionImportExecutor.ImportOutcome;
import com.autosecretary.features.assistant.application.internal.AssistantMealGateway;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Executes a user-confirmed {@link PendingProposal} — the only place the assistant actually writes.
 * Task changes go through the unchanged {@link ApplyTaskChangesUseCase} (so they keep their undo
 * snapshot); meal and budget writes run on the {@code dbExecutor} through the cross-feature gateways.
 * A short German summary of what happened is delivered on the {@code main} executor.
 */
public class ConfirmAssistantProposalUseCase {

    private final ApplyTaskChangesUseCase applyTaskChanges;
    private final AssistantMealGateway mealGateway;
    private final AssistantTransactionImportExecutor importExecutor;
    private final ExecutorService dbExecutor;
    private final Executor main;

    public ConfirmAssistantProposalUseCase(ApplyTaskChangesUseCase applyTaskChanges,
                                           AssistantMealGateway mealGateway,
                                           AssistantTransactionImportExecutor importExecutor,
                                           ExecutorService dbExecutor,
                                           Executor main) {
        this.applyTaskChanges = applyTaskChanges;
        this.mealGateway = mealGateway;
        this.importExecutor = importExecutor;
        this.dbExecutor = dbExecutor;
        this.main = main;
    }

    public void confirm(PendingProposal proposal, Consumer<String> onDone, Consumer<String> onError) {
        if (proposal instanceof TaskChangesProposal task) {
            applyTaskChanges.apply(task.proposal(),
                    () -> onDone.accept(taskSummary(task)), onError);
            return;
        }
        dbExecutor.execute(() -> {
            try {
                String summary = execute(proposal);
                main.execute(() -> onDone.accept(summary));
            } catch (RuntimeException e) {
                String message = e.getMessage() != null ? e.getMessage() : "Aktion fehlgeschlagen.";
                main.execute(() -> onError.accept(message));
            }
        });
    }

    private String execute(PendingProposal proposal) {
        if (proposal instanceof RecipesProposal recipes) {
            mealGateway.saveRecipes(recipes.recipes());
            return recipes.recipes().size() + " Rezept(e) gespeichert.";
        }
        if (proposal instanceof IngredientsProposal ingredients) {
            mealGateway.saveIngredients(ingredients.ingredients());
            return ingredients.ingredients().size() + " Zutat(en) gespeichert.";
        }
        if (proposal instanceof MealPlansProposal mealPlans) {
            mealGateway.saveMealPlans(mealPlans.entries());
            return mealPlans.entries().size() + " Wochenplan-Eintrag/Einträge gespeichert.";
        }
        if (proposal instanceof TransactionImportProposal importProposal) {
            ImportOutcome outcome = importExecutor.execute(importProposal);
            return outcome.imported() + " neue Transaktion(en) importiert, "
                    + outcome.duplicates() + " Duplikat(e) übersprungen.";
        }
        throw new IllegalArgumentException("Unbekannter Vorschlagstyp.");
    }

    private static String taskSummary(TaskChangesProposal task) {
        int count = task.proposal().categoryChanges().size() + task.proposal().taskChanges().size();
        return count + " Änderung(en) übernommen.";
    }
}
