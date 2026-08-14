package com.autosecretary.platform;

import com.autosecretary.application.TimeProvider;

import java.time.Instant;
import java.time.ZoneId;

public final class SystemTimeProvider implements TimeProvider {
    @Override public Instant now() { return Instant.now(); }
    @Override public ZoneId zone() { return ZoneId.systemDefault(); }
}
