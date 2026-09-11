package de.thonktank.autosecretary;

/** Blocking persistence capability; the editor invokes it only on its worker. */
public interface FlowEditorGateway {
    FlowEditorDraft load(String taskId);
    String save(FlowEditorDraft draft, String requestKey);
}
