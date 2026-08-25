package de.thonktank.autosecretary.presentation.navigation;

import de.thonktank.autosecretary.TaskEditorAction;
import de.thonktank.autosecretary.TaskEditorViewModel;

/** Routes typed app destinations into the editor owner without an Activity message broker. */
public final class TaskEditorNavigator implements AppNavigator {
    private final TaskEditorViewModel editor;
    private final Runnable prepareHeaderEntrance;

    public TaskEditorNavigator(TaskEditorViewModel editor, Runnable prepareHeaderEntrance) {
        if (editor == null || prepareHeaderEntrance == null)
            throw new IllegalArgumentException("Editor and entrance hook are required");
        this.editor = editor;
        this.prepareHeaderEntrance = prepareHeaderEntrance;
    }

    @Override public synchronized void navigate(AppDestination destination) {
        if (!(destination instanceof AppDestination.TaskEditor))
            throw new IllegalArgumentException("Unsupported destination " + destination);
        if (editor.state().getValue().content.open) return;
        AppDestination.TaskEditor target = (AppDestination.TaskEditor) destination;
        if (target.entrance == AppDestination.TaskEditor.Entrance.HEADER_FLIGHT)
            prepareHeaderEntrance.run();
        if (target.taskId == null) editor.dispatch(TaskEditorAction.openNew());
        else editor.dispatch(TaskEditorAction.open(target.taskId.value,
                target.stepId == null ? null : target.stepId.value, target.addStep));
    }
}
