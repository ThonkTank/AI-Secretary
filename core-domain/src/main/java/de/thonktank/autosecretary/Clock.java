package de.thonktank.autosecretary;

import java.time.LocalDate;
import java.time.LocalTime;

public interface Clock {
    LocalDate today();
    LocalTime time();
}
