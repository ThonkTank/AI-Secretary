package entities;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Container fuer alle Todo-bezogenen Objektdefinitionen.
 */
public class TodoList {
    
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
        public Long chainId;                    //Chain-Zugehörigkeit (ID des ersten Items, null für Einzelitems)
        //Für Zeiterfassung
        public LocalTime workStart;         //Wann wurde die bearbeitung begonnen?
        public LocalTime workEnd;           //Wann wurde die bearbeitung des Slots abgeschlossen
        //Für Progress-Tracking
        public Integer progressDelta;       //Heute gemachter Fortschritt (+/- vom UI, wird bei Mitternacht in Item übertragen)
        public Long previousCompletedItemId; //Item das direkt vor diesem Slot completed wurde (Geschwister)
    }
}
