package de.thonktank.autosecretary;

import java.time.LocalDate;

public final class SystemClock implements Clock { @Override public LocalDate today() { return LocalDate.now(); } }
