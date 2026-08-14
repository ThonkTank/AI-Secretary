package com.autosecretary.application.ai;

/** User consent required before downloading or running the separately distributed Gemma model. */
public interface AiConsentPort {
    boolean accepted();
    void accept();
}
