package de.thonktank.autosecretary.domain.model;

/** Validation feedback points back to the original editor key, never a newly allocated DB ID. */
public final class FlowGraphEditProblem extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    public final String kind;
    public final String key;

    public FlowGraphEditProblem(String kind, String key, String message) {
        super(message); this.kind = kind; this.key = key;
    }
}
