package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.StepEditRepository;
import java.util.*;

/** Resolves Today row targets exactly like the note editor, with fresh execution protection. */
public final class RoomStepEditRepository implements StepEditRepository {
    private final AppDatabase db;
    private final StepDao dao;
    private final TaskEntityMapper mapper = new TaskEntityMapper();

    public RoomStepEditRepository(AppDatabase db) { this.db = db; this.dao = db.steps(); }

    @Override public EditableStep findEditable(String id) {
        OccurrenceStepEntity occurrence = dao.occurrenceStep(id);
        GraphRunStepEntity graph = occurrence != null && occurrence.flowRunStepId != null
                ? dao.graphStep(occurrence.flowRunStepId) : dao.graphStep(id);
        String sourceId = occurrence != null ? occurrence.sourceTemplateId
                : graph != null ? graph.sourceTemplateId : id;
        TaskStepEntity source = sourceId == null ? null : dao.template(sourceId);
        if (source == null) return null;
        TaskEntity task = db.catalog().task(source.taskId);
        if (task == null) return null;
        List<OccurrenceStepEntity> executions = executions(occurrence, graph);
        boolean protectedValues = false;
        for (OccurrenceStepEntity row : executions)
            protectedValues |= row.done || !dao.repetitionResults(row.id).isEmpty()
                    || !db.timers().forStep(row.id).isEmpty();
        // Action/wait/completion on a graph step also freezes its prescription.
        if (graph != null)
            protectedValues |= graph.actionAtEpochMillis != null
                    || "DONE".equals(graph.state) || "WAITING_TIME".equals(graph.state);
        return new EditableStep(id, mapper.toDomain(source),
                occurrence != null ? occurrence.text : graph != null ? graph.text : source.text,
                occurrence != null ? occurrence.note : graph != null ? graph.note : source.note,
                "ONCE".equals(task.recurrence), protectedValues);
    }

    @Override public void updateEditable(EditableStep selected, TaskStepTemplate source,
                                         String changedText, String changedNote,
                                         StepPrescription changedPrescription, boolean amountChanged, boolean restChanged) {
        dao.updateTemplate(mapper.toEntity(source));
        OccurrenceStepEntity occurrence = dao.occurrenceStep(selected.id);
        GraphRunStepEntity graph = occurrence != null && occurrence.flowRunStepId != null
                ? dao.graphStep(occurrence.flowRunStepId) : dao.graphStep(selected.id);
        for (OccurrenceStepEntity row : executions(occurrence, graph)) {
            dao.updateOccurrenceDetails(details(row.id, row.text, row.note, row.amountKind,
                    row.plannedSets, row.plannedReps, row.plannedDurationSeconds,
                    row.restTimerMode, row.restTimerSeconds, changedText, changedNote, changedPrescription, amountChanged, restChanged));
        }
        if (graph != null)
            dao.updateGraphDetails(details(graph.id, graph.text, graph.note, graph.amountKind,
                    graph.plannedSets, graph.plannedReps, graph.plannedDurationSeconds,
                    graph.restTimerMode, graph.restTimerSeconds, changedText, changedNote, changedPrescription, amountChanged, restChanged));
    }

    private List<OccurrenceStepEntity> executions(OccurrenceStepEntity occurrence, GraphRunStepEntity graph) {
        if (graph != null) return dao.runtimeOccurrences(graph.id);
        return occurrence == null ? Collections.emptyList() : Collections.singletonList(occurrence);
    }

    private StepDetailsUpdate details(String id, String text, String note, String kind,
                                      Integer sets, Integer reps, Integer duration,
                                      String rest, Integer restSeconds, String changedText,
                                      String changedNote, StepPrescription prescription, boolean amountChanged, boolean restChanged) {
        if (prescription != null) {
            StepAmount amount = prescription.amount;
            boolean initialiseRest = amountChanged && !"SETS_REPS".equals(kind)
                    && amount instanceof StepAmount.SetsReps;
            if (amountChanged) {
                kind = amount.kind().name(); sets = null; reps = null; duration = null;
                if (amount instanceof StepAmount.SetsReps) {
                    sets = ((StepAmount.SetsReps) amount).sets;
                    reps = ((StepAmount.SetsReps) amount).repetitions;
                } else if (amount instanceof StepAmount.Repetitions) {
                    reps = ((StepAmount.Repetitions) amount).repetitions;
                } else if (amount instanceof StepAmount.Duration) {
                    duration = ((StepAmount.Duration) amount).seconds;
                }
            }
            if (restChanged || initialiseRest || !"SETS_REPS".equals(kind)) {
                rest = "SETS_REPS".equals(kind) ? prescription.rest.mode.name() : "OFF";
                restSeconds = "SETS_REPS".equals(kind) ? prescription.rest.customSeconds : null;
            }
        }
        return new StepDetailsUpdate(id, changedText == null ? text : changedText,
                changedNote == null ? note : changedNote, kind, sets, reps, duration, rest, restSeconds);
    }
}
