package com.autosecretary.ui;

import com.autosecretary.application.DashboardData;

/** Mutually exclusive dashboard states; loading and failure can no longer masquerade as content. */
public sealed interface MainUiState
        permits MainUiState.Loading, MainUiState.Ready, MainUiState.Failed {
    Surface surface();
    WorkItemFilter filter();

    record Loading(Surface surface, WorkItemFilter filter) implements MainUiState { }

    record Ready(
            DashboardData dashboard,
            Surface surface,
            WorkItemFilter filter) implements MainUiState {
        public Ready {
            if (dashboard == null) throw new IllegalArgumentException("dashboard fehlt");
        }
    }

    record Failed(
            Surface surface,
            WorkItemFilter filter,
            String message) implements MainUiState {
        public Failed {
            if (message == null || message.isBlank()) message = "Unbekannter Fehler";
        }
    }
}
