package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.StepEditRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Patches one step and its source without rewriting its task or execution history. */
public final class EditStep {
    private final StepEditRepository steps;
    private final TransactionRunner transactions;

    public EditStep(StepEditRepository steps, TransactionRunner transactions) {
        this.steps = steps; this.transactions = transactions;
    }

    public EditableStep load(String id) {
        return transactions.inTransaction(() -> required(id));
    }

    /** Returns whether changed prescription values were deferred to future executions. */
    public boolean save(String id, TaskStepDefinition original, TaskStepDefinition desired) {
        return transactions.inTransaction(() -> {
            EditableStep latest = required(id);
            TaskStepTemplate source = latest.source;
            if (!source.id.equals(original.id) || !source.id.equals(desired.id))
                throw new IllegalArgumentException("Die Schrittvorlage hat sich geändert. Bitte erneut öffnen.");
            String text = original.text.equals(desired.text) ? null : desired.text;
            String note = original.note.equals(desired.note) ? null : desired.note;
            boolean amountChanged = !original.prescription.amount.equals(desired.prescription.amount);
            boolean restChanged = !original.prescription.rest.equals(desired.prescription.rest);
            StepAmount amount = amountChanged ? desired.prescription.amount : source.prescription.amount;
            RestTimerPolicy rest = restChanged ? desired.prescription.rest : source.prescription.rest;
            if (!(amount instanceof StepAmount.SetsReps)) rest = RestTimerPolicy.off();
            StepPrescription prescription = new StepPrescription(amount, rest);
            boolean cadenceChanged = original.weekdayMask != desired.weekdayMask
                    || original.intervalDays != desired.intervalDays;
            boolean hideCadence = latest.once || source.activationKind == StepActivationKind.FOLLOW_UP;
            TaskStepTemplate updated = new TaskStepTemplate(source.id, source.taskId, source.position,
                    text == null ? source.text : text,
                    cadenceChanged && !hideCadence ? desired.weekdayMask : source.weekdayMask,
                    cadenceChanged && !hideCadence ? desired.intervalDays : source.intervalDays,
                    prescription, note == null ? source.note : note, source.activationKind);
            boolean prescriptionChanged = amountChanged || restChanged;
            steps.updateEditable(latest, updated, text, note,
                    prescriptionChanged && !latest.prescriptionProtected ? prescription : null, amountChanged, restChanged);
            return prescriptionChanged && latest.prescriptionProtected;
        });
    }

    private EditableStep required(String id) {
        EditableStep value = steps.findEditable(id);
        if (value == null)
            throw new IllegalArgumentException("Schritt oder dauerhafte Vorlage ist nicht mehr vorhanden.");
        return value;
    }
}
