package de.thonktank.autosecretary.domain.model;

/** Selected execution plus its reusable source. Prescription edits always target the source. */
public final class EditableStep {
    public final String id;
    public final TaskStepTemplate source;
    public final String text, note;
    public final boolean once, prescriptionProtected;

    public EditableStep(String id, TaskStepTemplate source, String text, String note,
                        boolean once, boolean prescriptionProtected) {
        this.id = id; this.source = source; this.text = text; this.note = note;
        this.once = once; this.prescriptionProtected = prescriptionProtected;
    }

    public TaskStepDefinition definition() {
        return new TaskStepDefinition(source.id, source.position, text,
                source.weekdayMask, source.intervalDays, source.prescription, note,
                source.activationKind);
    }
}
