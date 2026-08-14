package com.autosecretary.application.update;

import java.io.File;

public record VerifiedUpdate(UpdateInfo info, File apk) {
    public VerifiedUpdate {
        if (info == null || apk == null || !apk.isFile()) {
            throw new IllegalArgumentException("Verifiziertes Update ist unvollständig");
        }
    }
}
