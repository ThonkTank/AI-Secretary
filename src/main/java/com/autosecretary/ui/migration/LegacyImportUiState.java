package com.autosecretary.ui.migration;

public record LegacyImportUiState(boolean busy, boolean ready, String error) {
    public static LegacyImportUiState initial() { return new LegacyImportUiState(false, false, null); }
}
