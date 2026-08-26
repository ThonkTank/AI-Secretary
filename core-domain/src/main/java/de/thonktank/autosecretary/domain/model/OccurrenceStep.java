package de.thonktank.autosecretary.domain.model;

import java.util.Collections;
import java.util.List;

public final class OccurrenceStep {
    public final String id;
    public final String occurrenceId;
    public final int position;
    public final String text;
    public final boolean done;
    public final StepAmount amount;
    public final RestTimerPolicy restTimerPolicy;
    public final ResistanceLoad plannedLoad;
    public final int targetRir;
    public final String note;
    public final RepetitionProgress repetitionProgress;
    public final String sourceTemplateId;
    public final String comboOwnerId;
    public final String originOccurrenceId;
    public final CarryForwardReason carryForwardReason;

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done) {
        this(id, occurrenceId, position, text, done, StepAmount.none(), "",
                Collections.emptyList(), null, "step:" + id);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, String note, List<Integer> actualRepetitions) {
        this(id, occurrenceId, position, text, done, amount, note, actualRepetitions,
                null, "step:" + id);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, String note, List<Integer> actualRepetitions,
                          String comboOwnerId) {
        this(id, occurrenceId, position, text, done, amount, note, actualRepetitions,
                null, comboOwnerId);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, String note, List<Integer> actualRepetitions,
                          String sourceTemplateId, String comboOwnerId) {
        this(id, occurrenceId, position, text, done, amount, note, actualRepetitions,
                sourceTemplateId, comboOwnerId, null, CarryForwardReason.NONE);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, RestTimerPolicy restTimerPolicy, String note,
                          List<Integer> actualRepetitions, String sourceTemplateId,
                          String comboOwnerId) {
        this(id, occurrenceId, position, text, done, amount, restTimerPolicy, note,
                actualRepetitions, sourceTemplateId, comboOwnerId, null,
                CarryForwardReason.NONE);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, String note, List<Integer> actualRepetitions,
                          String sourceTemplateId, String comboOwnerId,
                          String originOccurrenceId, CarryForwardReason carryForwardReason) {
        this(id, occurrenceId, position, text, done, amount,
                RestTimerPolicy.forAmount(amount), note, actualRepetitions, sourceTemplateId,
                comboOwnerId, originOccurrenceId, carryForwardReason);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, RestTimerPolicy restTimerPolicy, String note,
                          List<Integer> actualRepetitions, String sourceTemplateId,
                          String comboOwnerId, String originOccurrenceId,
                          CarryForwardReason carryForwardReason) {
        this(id, occurrenceId, position, text, done, amount, restTimerPolicy, note,
                actualRepetitions, sourceTemplateId, comboOwnerId, originOccurrenceId,
                carryForwardReason, ResistanceLoad.unspecified(), 2);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, RestTimerPolicy restTimerPolicy, String note,
                          List<Integer> actualRepetitions, String sourceTemplateId,
                          String comboOwnerId, String originOccurrenceId,
                          CarryForwardReason carryForwardReason, ResistanceLoad plannedLoad,
                          int targetRir) {
        if (id == null || id.isEmpty() || occurrenceId == null || occurrenceId.isEmpty()
                || text == null || text.trim().isEmpty() || actualRepetitions == null
                || comboOwnerId == null)
            throw new IllegalArgumentException(
                    "Occurrence step identity, occurrence and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, 0, 0, amount,
                restTimerPolicy, note);
        RepetitionProgress progress = RepetitionProgress.forAmount(
                checked.amount, actualRepetitions, done);
        this.id = id;
        this.occurrenceId = occurrenceId;
        this.position = checked.position;
        this.text = checked.text;
        this.amount = checked.amount;
        this.restTimerPolicy = checked.restTimerPolicy;
        this.plannedLoad = plannedLoad == null ? ResistanceLoad.unspecified() : plannedLoad;
        if (targetRir < 0 || targetRir > 5)
            throw new IllegalArgumentException("Target RIR must be between zero and five");
        this.targetRir = targetRir;
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

    public OccurrenceStep complete() {
        if (done) return this;
        return repetitionProgress == null
                ? new OccurrenceStep(id, occurrenceId, position, text, true,
                        amount, restTimerPolicy, note, Collections.emptyList(), sourceTemplateId,
                        comboOwnerId,
                        originOccurrenceId, carryForwardReason, plannedLoad, targetRir)
                : withProgress(repetitionProgress.completeWithoutResults());
    }

    public OccurrenceStep reopen() {
        if (!done) return this;
        return repetitionProgress == null
                ? new OccurrenceStep(id, occurrenceId, position, text, false, amount,
                        restTimerPolicy, note, Collections.emptyList(), sourceTemplateId, comboOwnerId,
                        originOccurrenceId, carryForwardReason, plannedLoad, targetRir)
                : withProgress(repetitionProgress.reopen());
    }

    public OccurrenceStep recordRepetitionResult(int repetitions) {
        if (repetitionProgress == null || done)
            throw new IllegalStateException("Step does not accept repetition progress");
        return withProgress(repetitionProgress.record(repetitions));
    }

    public OccurrenceStep correctRepetitionResult(int index, int repetitions) {
        if (repetitionProgress == null)
            throw new IllegalStateException("Step does not accept repetition progress");
        return withProgress(repetitionProgress.correct(index, repetitions));
    }

    private OccurrenceStep withProgress(RepetitionProgress progress) {
        return new OccurrenceStep(id, occurrenceId, position, text, progress.completed(), amount,
                restTimerPolicy, note, progress.actualRepetitions, sourceTemplateId, comboOwnerId,
                originOccurrenceId, carryForwardReason, plannedLoad, targetRir);
    }

    public OccurrenceStep withCarryOrigin(String originId, CarryForwardReason reason) {
        return new OccurrenceStep(id, occurrenceId, position, text, done, amount,
                restTimerPolicy, note,
                repetitionProgress == null ? Collections.emptyList()
                        : repetitionProgress.actualRepetitions, sourceTemplateId, comboOwnerId,
                originId, reason, plannedLoad, targetRir);
    }

    public OccurrenceStep relocate(String targetOccurrenceId, int targetPosition) {
        return new OccurrenceStep(id, targetOccurrenceId, targetPosition, text, done,
                amount, restTimerPolicy, note, repetitionProgress == null ? Collections.emptyList()
                : repetitionProgress.actualRepetitions, sourceTemplateId, comboOwnerId,
                originOccurrenceId, carryForwardReason, plannedLoad, targetRir);
    }
}
