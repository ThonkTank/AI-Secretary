package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.EditableStep;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

/** Caller owns the transaction, including the fresh progress/timer check. */
public interface StepEditRepository {
    EditableStep findEditable(String id);
    void updateEditable(EditableStep selected, TaskStepTemplate source,
                        String changedText, String changedNote,
                        StepPrescription changedPrescription, boolean amountChanged, boolean restChanged);
}
