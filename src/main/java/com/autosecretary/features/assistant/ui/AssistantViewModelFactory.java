package com.autosecretary.features.assistant.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.features.assistant.application.AssistantChatUseCase;
import com.autosecretary.features.assistant.application.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.task.application.UndoTaskChangesUseCase;

/**
 * Factory for {@link AssistantViewModel}.
 */
public class AssistantViewModelFactory implements ViewModelProvider.Factory {
    private final AssistantChatUseCase chatUseCase;
    private final ConfirmAssistantProposalUseCase confirmUseCase;
    private final UndoTaskChangesUseCase undoUseCase;

    public AssistantViewModelFactory(AssistantChatUseCase chatUseCase,
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
        if (modelClass.isAssignableFrom(AssistantViewModel.class)) {
            return (T) new AssistantViewModel(chatUseCase, confirmUseCase, undoUseCase);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
