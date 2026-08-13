package com.autosecretary.platform;

import com.autosecretary.application.AppClock;

import java.time.LocalDateTime;

public final class SystemAppClock implements AppClock {
    @Override public LocalDateTime now() { return LocalDateTime.now(); }
}
