package entities;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class config {

    // Pro Wochentag: Wann beginnt/endet der aktive Tag?
    private Map<DayOfWeek, DaySchedule> dailySchedules;

    // ============== INNERE KLASSE ==============

    public static class DaySchedule {
        public LocalTime start;  // z.B. 06:00
        public LocalTime end;    // z.B. 18:00

        public DaySchedule(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }  // ← DaySchedule ENDE

    // ============== KONSTRUKTOR ==============

    public config() {
        // Mit Default-Werten initialisieren
        this.dailySchedules = getDefaultSchedules();
    }

    // ============== GETTER ==============

    public DaySchedule getScheduleFor(DayOfWeek day) {
        return dailySchedules.get(day);
    }

    public DaySchedule getScheduleForToday() {
        return dailySchedules.get(java.time.LocalDate.now().getDayOfWeek());
    }

    public Map<DayOfWeek, DaySchedule> getDailySchedules() {
        return dailySchedules;
    }

    // ============== SETTER ==============

    public void setScheduleFor(DayOfWeek day, LocalTime start, LocalTime end) {
        dailySchedules.put(day, new DaySchedule(start, end));
    }

    // ============== DEFAULTS ==============

    private Map<DayOfWeek, DaySchedule> getDefaultSchedules() {
        Map<DayOfWeek, DaySchedule> defaults = new HashMap<>();

        // Werktage: 06:00 - 18:00
        for (DayOfWeek day : List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY)) {
            defaults.put(day, new DaySchedule(LocalTime.of(6, 0), LocalTime.of(18, 0)));
        }

        // Wochenende: 10:00 - 16:00 / 14:00
        defaults.put(DayOfWeek.SATURDAY, new DaySchedule(LocalTime.of(10, 0), LocalTime.of(16, 0)));
        defaults.put(DayOfWeek.SUNDAY, new DaySchedule(LocalTime.of(10, 0), LocalTime.of(14, 0)));

        return defaults;
    }
} 
