package de.thonktank.autosecretary.domain.model;

public final class OccurrenceStep {
    public final String id;
    public final String occurrenceId;
    public final int position;
    public final String text;
    public final boolean done;

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done) {
        if (id == null || id.isEmpty() || occurrenceId == null || occurrenceId.isEmpty()
                || text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Occurrence step identity, occurrence and text are required");
        this.id = id;
        this.occurrenceId = occurrenceId;
        this.position = position;
        this.text = text.trim();
        this.done = done;
    }

    public OccurrenceStep toggle() {
        return new OccurrenceStep(id, occurrenceId, position, text, !done);
    }

    public OccurrenceStep complete() {
        return done ? this : new OccurrenceStep(id, occurrenceId, position, text, true);
    }
}
