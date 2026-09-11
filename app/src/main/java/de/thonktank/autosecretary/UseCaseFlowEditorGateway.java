package de.thonktank.autosecretary;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.usecase.LoadFlowGraph;
import de.thonktank.autosecretary.domain.usecase.SaveFlowGraph;

public final class UseCaseFlowEditorGateway implements FlowEditorGateway {
    private final LoadFlowGraph load;
    private final SaveFlowGraph save;

    public UseCaseFlowEditorGateway(LoadFlowGraph load, SaveFlowGraph save) { this.load = load; this.save = save; }

    @Override public FlowEditorDraft load(String taskId) {
        return FlowEditorDraft.from(load.execute(taskId == null ? null : TaskId.of(taskId)));
    }

    @Override public String save(FlowEditorDraft draft, String requestKey) {
        return save.execute(draft.edit(), requestKey).value;
    }
}
