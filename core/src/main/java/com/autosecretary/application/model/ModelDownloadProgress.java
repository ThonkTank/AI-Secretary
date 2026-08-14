package com.autosecretary.application.model;

public sealed interface ModelDownloadProgress permits ModelDownloadProgress.Pending,
        ModelDownloadProgress.Running, ModelDownloadProgress.Complete,
        ModelDownloadProgress.Failed {
    record Pending() implements ModelDownloadProgress { }
    record Running(long downloadedBytes, long totalBytes) implements ModelDownloadProgress { }
    record Complete() implements ModelDownloadProgress { }
    record Failed(String detail, boolean retryable) implements ModelDownloadProgress { }
}
