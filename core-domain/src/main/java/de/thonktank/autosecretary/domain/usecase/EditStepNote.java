package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepNote;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Updates only notes in the selected execution and its reusable source. */
public final class EditStepNote {
    private final StepRepository steps;
    private final TransactionRunner transactions;

    public EditStepNote(StepRepository steps, TransactionRunner transactions) {
        this.steps = steps; this.transactions = transactions;
    }

    public StepNote load(String stepId) {
        return transactions.inTransaction(() -> steps.findNote(stepId));
    }

    public void save(String stepId, String note) {
        if (note == null) throw new IllegalArgumentException("Note text is required");
        transactions.inTransaction(() -> {
            if (!steps.updateNote(stepId, note))
                throw new IllegalArgumentException("Der Schritt ist nicht mehr vorhanden.");
            return null;
        });
    }
}
