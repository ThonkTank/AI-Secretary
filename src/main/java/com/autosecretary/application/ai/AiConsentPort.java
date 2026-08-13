package com.autosecretary.application.ai;

/** User consent required before installing or running the bundled Gemma model. */
public interface AiConsentPort {
    boolean accepted();
    void accept();
}
