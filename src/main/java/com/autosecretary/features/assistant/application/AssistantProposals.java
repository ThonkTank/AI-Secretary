package com.autosecretary.features.assistant.application;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskSnapshot;
import com.autosecretary.shared.MealType;

import java.time.LocalDate;
import java.util.List;

/**
 * The parked write-intents the assistant produces. The assistant never writes directly: every
 * {@code propose_*} tool call is validated and turned into a {@link PendingProposal}, shown to the
 * user as a card, and only executed by {@link ConfirmAssistantProposalUseCase} once the user taps
 * "Übernehmen".
 *
 * <p>These are plain value types over {@code task.domain}, {@code meal.domain} and {@code shared}
 * only (no {@code *UseCase}/{@code *Service} names), so the UI may reference {@link PendingProposal}
 * directly without breaking the Fragment-must-not-reference-use-cases architecture rule.
 */
public final class AssistantProposals {

    private AssistantProposals() {
    }

    /** One parked write-intent, awaiting user confirmation. */
    public sealed interface PendingProposal
            permits TaskChangesProposal, RecipesProposal, IngredientsProposal,
                    MealPlansProposal, TransactionImportProposal {
    }

    /** Category/task reorganisation, applied via the existing {@code ApplyTaskChangesUseCase} (keeps undo). */
    public record TaskChangesProposal(TaskSnapshot snapshot, TaskAssistantProposal proposal)
            implements PendingProposal {
    }

    /** New recipes (with resolved-or-null ingredient ids) to persist on confirm. */
    public record RecipesProposal(List<Recipe> recipes) implements PendingProposal {
    }

    /** New ingredients to persist on confirm. */
    public record IngredientsProposal(List<Ingredient> ingredients) implements PendingProposal {
    }

    /** Meal-plan entries; recipe resolution (by id or title) happens at confirm time. */
    public record MealPlansProposal(List<MealPlanDraft> entries) implements PendingProposal {
    }

    /**
     * A bank-statement import; account resolution, deduplication and category fallback happen at
     * confirm time. {@code duplicatePreviewCount} is a best-effort preview shown on the card.
     */
    public record TransactionImportProposal(String accountId, String fileName, String fileHash,
                                            LocalDate periodStart, LocalDate periodEnd,
                                            List<TransactionDraft> transactions,
                                            int duplicatePreviewCount) implements PendingProposal {
    }

    /** One proposed meal-plan entry. Exactly one of {@code recipeId}/{@code recipeTitle} identifies the recipe. */
    public record MealPlanDraft(LocalDate date, MealType mealType, String recipeId,
                                String recipeTitle, int servings) {
    }

    /** One proposed transaction. {@code signedAmountCents} is negative for expenses, positive for income. */
    public record TransactionDraft(LocalDate date, long signedAmountCents, String payee,
                                   String note, String categoryId) {
    }
}
