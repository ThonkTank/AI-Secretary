package com.autosecretary.application.model;

public record ModelDownloadTicket(long id, String modelId, String revision) {
    public ModelDownloadTicket {
        if (id < 0 || modelId == null || modelId.isBlank()
                || revision == null || revision.isBlank()) {
            throw new IllegalArgumentException("Ungültiges Modell-Ticket");
        }
    }
}
