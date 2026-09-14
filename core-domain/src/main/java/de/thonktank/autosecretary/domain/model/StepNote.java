package de.thonktank.autosecretary.domain.model;

/** Editable note for a materialized step, including a graph runtime step. */
public final class StepNote {
    public final String id;
    public final String title;
    public final String note;

    public StepNote(String id, String title, String note) {
        this.id = id; this.title = title; this.note = note == null ? "" : note;
    }
}
