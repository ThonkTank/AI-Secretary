package com.autosecretary.application.model;

import java.nio.file.Path;

public interface ModelRepository {
    ModelStatus status();
    ModelDownloadTicket enqueue();
    ModelDownloadProgress query(ModelDownloadTicket ticket);
    Path verifyAndActivate(ModelDownloadTicket ticket);
    void cancel(ModelDownloadTicket ticket);
}
