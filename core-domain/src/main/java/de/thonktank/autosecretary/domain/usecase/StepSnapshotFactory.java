package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CarryForwardReason;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.util.Collections;

/** Atomic creation boundary for immutable step payloads. */
final class StepSnapshotFactory {
    private final IdGenerator ids;

    StepSnapshotFactory(IdGenerator ids) {
        if (ids == null) throw new IllegalArgumentException("Snapshot IDs are required");
        this.ids = ids;
    }

    OccurrenceStep fromTemplate(TaskStepTemplate template, String occurrenceId, int position) {
        return OccurrenceStep.rehydrate(ids.nextId(), occurrenceId, position, template.text,
                false, template.prescription, template.note, Collections.emptyList(), template.id,
                ComboProgress.stepOwner(template.id), null, CarryForwardReason.NONE);
    }

    OccurrenceStep carryForward(OccurrenceStep source, String occurrenceId, int position,
                                String originOccurrenceId) {
        return OccurrenceStep.rehydrate(ids.nextId(), occurrenceId, position, source.text,
                false, source.prescription, source.note,
                source.repetitionProgress == null ? Collections.emptyList()
                        : source.repetitionProgress.results,
                source.sourceTemplateId, source.comboOwnerId, originOccurrenceId,
                CarryForwardReason.UNFINISHED_STEP);
    }

    OccurrenceStep fromFlow(FlowRunStepSnapshot source, String occurrenceId, int position) {
        return OccurrenceStep.rehydrate(ids.nextId(), occurrenceId, position, source.text,
                false, source.prescription, source.note, Collections.emptyList(),
                source.sourceTemplateId, ComboProgress.stepOwner(source.sourceTemplateId), null,
                CarryForwardReason.NONE);
    }

    FlowRunStepSnapshot flowRun(TaskStepTemplate template, String runId, int position,
                                FlowDelayPolicy delayAfter) {
        return FlowRunStepSnapshot.rehydrate(ids.nextId(), runId, position, template.id,
                template.text, template.prescription, template.note, delayAfter, null);
    }
}
