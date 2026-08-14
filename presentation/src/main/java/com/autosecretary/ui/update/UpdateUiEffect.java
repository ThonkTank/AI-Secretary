package com.autosecretary.ui.update;

import com.autosecretary.application.update.VerifiedUpdate;

/** One-shot Android hand-offs, separate from repeatable rendering state. */
public sealed interface UpdateUiEffect permits UpdateUiEffect.OpenUnknownSourcesSettings,
        UpdateUiEffect.OpenInstaller {
    long id();

    record OpenUnknownSourcesSettings(long id) implements UpdateUiEffect { }
    record OpenInstaller(long id, VerifiedUpdate update) implements UpdateUiEffect {
        public OpenInstaller {
            if (update == null) throw new IllegalArgumentException("Update fehlt");
        }
    }
}
