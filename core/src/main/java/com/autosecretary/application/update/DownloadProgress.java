package com.autosecretary.application.update;

public sealed interface DownloadProgress permits DownloadProgress.Pending,
        DownloadProgress.Running, DownloadProgress.Complete, DownloadProgress.Failed {
    record Pending() implements DownloadProgress { }

    record Running(long downloadedBytes, long totalBytes) implements DownloadProgress {
        public Running {
            if (downloadedBytes < 0 || totalBytes < -1) {
                throw new IllegalArgumentException("Ungültiger Downloadfortschritt");
            }
        }
    }

    record Complete() implements DownloadProgress { }

    record Failed(UpdateFailure failure) implements DownloadProgress {
        public Failed {
            if (failure == null) throw new IllegalArgumentException("Downloadfehler fehlt");
        }
    }
}
