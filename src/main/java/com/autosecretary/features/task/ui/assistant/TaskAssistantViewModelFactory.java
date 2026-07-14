package com.autosecretary.features.task.ui.assistant;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.task.application.assistant.AssistantChatUseCase;
import com.autosecretary.features.task.application.assistant.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.task.application.UndoTaskChangesUseCase;

/**
 * Factory for {@link TaskAssistantViewModel}.
 */
public class TaskAssistantViewModelFactory implements ViewModelProvider.Factory {
    private final AssistantChatUseCase chatUseCase;
    private final ConfirmAssistantProposalUseCase confirmUseCase;
    private final UndoTaskChangesUseCase undoUseCase;

    public TaskAssistantViewModelFactory(AssistantChatUseCase chatUseCase,
                                         ConfirmAssistantProposalUseCase confirmUseCase,
                                         UndoTaskChangesUseCase undoUseCase) {
        this.chatUseCase = chatUseCase;
        this.confirmUseCase = confirmUseCase;
        this.undoUseCase = undoUseCase;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TaskAssistantViewModel.class)) {
            return (T) new TaskAssistantViewModel(chatUseCase, confirmUseCase, undoUseCase);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
