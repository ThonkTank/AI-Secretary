package com.autosecretary.application;

import java.io.InputStream;

public interface LegacyImportPort {
    @FunctionalInterface
    interface ArchiveSource {
        InputStream open() throws Exception;
    }

    boolean requiresUserDecision();
    void importArchive(ArchiveSource source);
    void chooseEmptyDatabase();
}
