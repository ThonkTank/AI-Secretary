package de.thonktank.autosecretary;

import java.time.LocalDate;

final class SystemClock implements Clock { @Override public LocalDate today() { return LocalDate.now(); } }
