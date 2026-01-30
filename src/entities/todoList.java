package entities;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Container fuer alle Todo-bezogenen Objektdefinitionen.
 */
public class todoList {
    
    //Identifikation
    public Long id;
    public LocalDate date;
    public LocalTime start;
    public LocalTime end;
    public List<TimeSlot> timeSlots;

    public static class TimeSlot {
        public Long id;
        public LocalTime start;
        public LocalTime end;
        public List<TimeSlot> timeSlots;
        public Long item;
        public Boolean completed;
        public Boolean isCalendarEvent;
        public String calendarTitle;
        public Integer adjustedPrio;            //Slot-adjusted Prio für Verdrängungsvergleich
        //Für prefTime berechnung
        public LocalTime workStart;         //Wann wurde die bearbeitung begonnen?
        public LocalTime workEnd;           //Wann wurde die bearbeitung des Slots abgeschlossen
    }
}
