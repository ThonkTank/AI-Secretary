package com.autosecretary.application.model;

import java.nio.file.Path;

public sealed interface ModelStatus permits ModelStatus.Missing, ModelStatus.Ready,
        ModelStatus.Downloading, ModelStatus.Failed {
    long sizeBytes();

    record Missing(long sizeBytes) implements ModelStatus { }
    record Ready(Path path, long sizeBytes) implements ModelStatus {
        public Ready {
            if (path == null) throw new IllegalArgumentException("Modellpfad fehlt");
        }
    }
    record Downloading(
            ModelDownloadTicket ticket,
            ModelDownloadProgress progress,
            long sizeBytes) implements ModelStatus { }
    record Failed(String detail, long sizeBytes) implements ModelStatus { }
}
