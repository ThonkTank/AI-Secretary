package de.thonktank.autosecretary.domain.model;

import java.util.Collections;
import java.util.List;

public final class OccurrenceStep {
    public final String id;
    public final String occurrenceId;
    public final int position;
    public final String text;
    public final boolean done;
    public final StepPrescription prescription;
    public final String note;
    public final RepetitionProgress repetitionProgress;
    public final String sourceTemplateId;
    public final String comboOwnerId;
    public final String originOccurrenceId;
    public final CarryForwardReason carryForwardReason;

    private OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                           StepPrescription prescription, String note,
                           List<SetResult> setResults, String sourceTemplateId,
                           String comboOwnerId, String originOccurrenceId,
                           CarryForwardReason carryForwardReason) {
        if (id == null || id.isEmpty() || occurrenceId == null || occurrenceId.isEmpty()
                || setResults == null || comboOwnerId == null)
            throw new IllegalArgumentException(
                    "Occurrence step identity, occurrence and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, 0, 0,
                prescription, null, note, StepActivationKind.SCHEDULED);
        RepetitionProgress progress = RepetitionProgress.forAmount(
                checked.prescription.amount, setResults, done);
        this.id = id;
        this.occurrenceId = occurrenceId;
        this.position = checked.position;
        this.text = checked.text;
        this.prescription = checked.prescription;
        this.note = checked.note;
        this.repetitionProgress = progress;
        this.done = progress == null ? done : progress.completed();
        this.sourceTemplateId = sourceTemplateId == null || sourceTemplateId.isEmpty()
                ? null : sourceTemplateId;
        this.comboOwnerId = comboOwnerId.isEmpty() ? "step:" + id : comboOwnerId;
        this.originOccurrenceId = originOccurrenceId == null || originOccurrenceId.isEmpty()
                ? null : originOccurrenceId;
        this.carryForwardReason = carryForwardReason == null
                ? CarryForwardReason.NONE : carryForwardReason;
    }

    /** Complete persistence/test-fixture boundary. Product creation uses StepSnapshotFactory. */
    public static OccurrenceStep rehydrate(
            String id, String occurrenceId, int position, String text, boolean done,
            StepPrescription prescription, String note, List<SetResult> setResults,
            String sourceTemplateId, String comboOwnerId, String originOccurrenceId,
            CarryForwardReason carryForwardReason) {
        return new OccurrenceStep(id, occurrenceId, position, text, done, prescription, note,
                setResults, sourceTemplateId, comboOwnerId, originOccurrenceId,
                carryForwardReason);
    }

    public OccurrenceStep complete() {
        if (done) return this;
        return repetitionProgress == null
                ? copy(true, Collections.emptyList())
                : withProgress(repetitionProgress.completeWithoutResults());
    }

    public OccurrenceStep reopen() {
        if (!done) return this;
        return repetitionProgress == null
                ? copy(false, Collections.emptyList()) : withProgress(repetitionProgress.reopen());
    }

    public OccurrenceStep recordRepetitionResult(int repetitions) {
        return recordSetResult(SetResult.repetitions(repetitions));
    }

    public OccurrenceStep recordSetResult(SetResult result) {
        if (repetitionProgress == null || done)
            throw new IllegalStateException("Step does not accept repetition progress");
        return withProgress(repetitionProgress.record(result));
    }

    public OccurrenceStep correctRepetitionResult(int index, int repetitions) {
        TrainingObservation training = repetitionProgress == null
                || index < 0 || index >= repetitionProgress.results.size() ? null
                : repetitionProgress.results.get(index).training;
        return correctSetResult(index, new SetResult(repetitions, training));
    }

    public OccurrenceStep correctSetResult(int index, SetResult result) {
        if (repetitionProgress == null)
            throw new IllegalStateException("Step does not accept repetition progress");
        return withProgress(repetitionProgress.correct(index, result));
    }

    private OccurrenceStep copy(boolean completed, List<SetResult> results) {
        return new OccurrenceStep(id, occurrenceId, position, text, completed, prescription,
                note, results, sourceTemplateId, comboOwnerId, originOccurrenceId,
                carryForwardReason);
    }

    private OccurrenceStep withProgress(RepetitionProgress progress) {
        return copy(progress.completed(), progress.results);
    }

    public OccurrenceStep withCarryOrigin(String originId, CarryForwardReason reason) {
        return new OccurrenceStep(id, occurrenceId, position, text, done, prescription, note,
                results(), sourceTemplateId, comboOwnerId, originId, reason);
    }

    public OccurrenceStep relocate(String targetOccurrenceId, int targetPosition) {
        return new OccurrenceStep(id, targetOccurrenceId, targetPosition, text, done,
                prescription, note, results(), sourceTemplateId, comboOwnerId,
                originOccurrenceId, carryForwardReason);
    }

    private List<SetResult> results() {
        return repetitionProgress == null ? Collections.emptyList()
                : repetitionProgress.results;
    }
}
