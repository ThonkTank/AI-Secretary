package com.autosecretary.platform;

import com.autosecretary.application.TimeProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class SystemTimeProvider implements TimeProvider {
    @Override public LocalDateTime localNow() { return LocalDateTime.now(); }
    @Override public ZoneId zone() { return ZoneId.systemDefault(); }
}
