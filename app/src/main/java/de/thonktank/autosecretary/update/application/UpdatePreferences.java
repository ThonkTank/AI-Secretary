package de.thonktank.autosecretary.update.application;

/** Persistence port for automatic checks and postponed prompts. */
public interface UpdatePreferences {
    boolean shouldCheckUpdates(long nowMillis);
    void markUpdateCheck(long nowMillis);
    boolean shouldPromptForUpdate(long versionCode, long nowMillis);
    void postponeUpdate(long versionCode, long nowMillis);
}
