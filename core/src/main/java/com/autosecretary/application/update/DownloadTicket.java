package com.autosecretary.application.update;

public record DownloadTicket(long id, int versionCode) {
    public DownloadTicket {
        if (id < 1 || versionCode < 1) {
            throw new IllegalArgumentException("Ungültiges Download-Ticket");
        }
    }
}
