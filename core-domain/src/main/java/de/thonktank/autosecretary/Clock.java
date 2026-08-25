package de.thonktank.autosecretary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface Clock {
    LocalDate today();
    LocalTime time();

    /** One coherent wall-clock reading for projections that need both date and time. */
    default LocalDateTime now() {
        return LocalDateTime.of(today(), time());
    }
}
