package com.autosecretary.features.task.ui.assistant;

import android.content.Context;

import com.autosecretary.R;
import com.autosecretary.features.task.application.assistant.AssistantProposals.IngredientsProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.MealPlansProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.PendingProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.RecipesProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TaskChangesProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionImportProposal;

/**
 * Turns a {@link PendingProposal} into the German summary text shown on its confirmation card.
 * Task changes reuse {@link TaskAssistantDiffFormatter}; the other domains show a short count line.
 */
final class AssistantProposalFormatter {

    private AssistantProposalFormatter() {
    }

    static String summary(Context context, PendingProposal proposal) {
        if (proposal instanceof TaskChangesProposal task) {
            return TaskAssistantDiffFormatter.format(context, task.snapshot(), task.proposal());
        }
        if (proposal instanceof RecipesProposal recipes) {
            return context.getString(R.string.task_assistant_proposal_recipes, recipes.recipes().size());
        }
        if (proposal instanceof IngredientsProposal ingredients) {
            return context.getString(R.string.task_assistant_proposal_ingredients, ingredients.ingredients().size());
        }
        if (proposal instanceof MealPlansProposal mealPlans) {
            return context.getString(R.string.task_assistant_proposal_meal_plans, mealPlans.entries().size());
        }
        if (proposal instanceof TransactionImportProposal importProposal) {
            return context.getString(R.string.task_assistant_proposal_import,
                    importProposal.transactions().size(), importProposal.duplicatePreviewCount());
        }
        return "";
    }
}
