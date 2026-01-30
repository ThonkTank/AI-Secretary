package test;

import entities.trackedItem;
import entities.trackedItem.*;
import entities.CalendarEvent;
import entities.todoList;
import entities.todoList.TimeSlot;
import usecases.dailyPlanning.buildToDoV2;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone-Test für den V2-Scheduling-Algorithmus.
 * Kein Android nötig — direkt in VSCode ausführbar.
 *
 * Testet:
 *   - PrefTime-basierte Platzierung
 *   - Parent-Cooldown / gegenseitige Goal-Exklusivität
 *   - REPS_PER_TIME Re-Priorisierung
 *   - Verdrängung (höhere Prio verdrängt niedrigere)
 *   - Goal→Task Pipeline (fillGoalSlot)
 */
public class TestBuildToDo {

    public static void main(String[] args) {
        MockRepo repo = new MockRepo();
        seedSchedules(repo);
        seedItems(repo);

        // Leerer Kalender (keine Events)
        buildToDoV2 planner = new buildToDoV2(repo, (day, start, end) -> new ArrayList<>());

        System.out.println("=== Starte V2-Scheduling ===\n");
        planner.makeToDoList();

        // Ergebnis ausgeben
        printPlan(repo);
    }

    // ========================================================================
    // Seed: Schedules (Mo-Fr 06:00-18:00, Sa 09:00-16:00, So 09:00-14:00)
    // ========================================================================
    private static void seedSchedules(MockRepo repo) {
        repo.addSchedule("MONDAY",    "06:00", "18:00");
        repo.addSchedule("TUESDAY",   "06:00", "18:00");
        repo.addSchedule("WEDNESDAY", "06:00", "18:00");
        repo.addSchedule("THURSDAY",  "06:00", "18:00");
        repo.addSchedule("FRIDAY",    "06:00", "18:00");
        repo.addSchedule("SATURDAY",  "09:00", "16:00");
        repo.addSchedule("SUNDAY",    "09:00", "14:00");
    }

    // ========================================================================
    // Seed: Items (Project → Goals → Tasks)
    // ========================================================================
    private static void seedItems(MockRepo repo) {

        // === PROJECT: Fitness (cooldown=2 → Goals gegenseitig exklusiv) ===
        trackedItem pFitness = new Builder(ItemType.PROJECT, "Fitness", Priority.HIGH)
            .created("2025-12-15").cooldown(2).build();
        repo.write(pFitness);

        // Goal: Kraft (prefTime morgens)
        trackedItem gKraft = new Builder(ItemType.GOAL, "Kraft", Priority.HIGH)
            .timeToComplete(45).prefTime("07:00").parent(pFitness.id).created("2025-12-15").build();
        repo.write(gKraft);

        trackedItem tLiegestuetze = new Builder(ItemType.TASK, "Liegestuetze", Priority.HIGH)
            .timeToComplete(15).prefTime("07:00").parent(gKraft.id).created("2025-12-15")
            .lastCompletion("2026-01-27").repetition(RepetitionType.INTERVAL, 2, RepUnits.DAY).build();
        trackedItem tKniebeugen = new Builder(ItemType.TASK, "Kniebeugen", Priority.MODERATE)
            .timeToComplete(15).prefTime("07:15").parent(gKraft.id).created("2025-12-15")
            .lastCompletion("2026-01-26").repetition(RepetitionType.REPS_PER_TIME, 3, RepUnits.WEEK).build();
        trackedItem tKlimmzuege = new Builder(ItemType.TASK, "Klimmzuege", Priority.HIGH)
            .timeToComplete(15).prefTime("07:30").parent(gKraft.id).created("2025-12-15")
            .lastCompletion("2026-01-20").repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.MONDAY).build();
        repo.write(tLiegestuetze); repo.write(tKniebeugen); repo.write(tKlimmzuege);
        gKraft.children = List.of(tLiegestuetze.id, tKniebeugen.id, tKlimmzuege.id);
        repo.write(gKraft);

        // Goal: Ausdauer (prefTime nachmittags — wird durch cooldown von Kraft blockiert)
        trackedItem gAusdauer = new Builder(ItemType.GOAL, "Ausdauer", Priority.MODERATE)
            .timeToComplete(40).prefTime("17:00").parent(pFitness.id).created("2025-12-15").build();
        repo.write(gAusdauer);

        trackedItem tJoggen = new Builder(ItemType.TASK, "Joggen", Priority.HIGH)
            .timeToComplete(30).prefTime("17:00").parent(gAusdauer.id).created("2025-12-15")
            .lastCompletion("2026-01-25").repetition(RepetitionType.INTERVAL, 3, RepUnits.DAY).build();
        trackedItem tSeilspringen = new Builder(ItemType.TASK, "Seilspringen", Priority.MODERATE)
            .timeToComplete(15).prefTime("17:30").parent(gAusdauer.id).created("2025-12-15")
            .lastCompletion("2026-01-26").repetition(RepetitionType.REPS_PER_TIME, 2, RepUnits.WEEK).build();
        repo.write(tJoggen); repo.write(tSeilspringen);
        gAusdauer.children = List.of(tJoggen.id, tSeilspringen.id);
        repo.write(gAusdauer);

        pFitness.children = List.of(gKraft.id, gAusdauer.id);
        repo.write(pFitness);

        // === GOAL: Lernen (standalone, kein Parent) ===
        trackedItem gLernen = new Builder(ItemType.GOAL, "Lernen", Priority.HIGH)
            .timeToComplete(60).prefTime("14:00").created("2025-12-20").build();
        repo.write(gLernen);

        trackedItem tJava = new Builder(ItemType.TASK, "Java lernen", Priority.HIGH)
            .timeToComplete(30).prefTime("14:00").parent(gLernen.id).created("2025-12-20")
            .lastCompletion("2026-01-29").repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
        trackedItem tMathe = new Builder(ItemType.TASK, "Mathe ueben", Priority.HIGH)
            .timeToComplete(25).prefTime("15:00").parent(gLernen.id).created("2025-12-20")
            .lastCompletion("2026-01-27").repetition(RepetitionType.REPS_PER_TIME, 5, RepUnits.WEEK).build();
        repo.write(tJava); repo.write(tMathe);
        gLernen.children = List.of(tJava.id, tMathe.id);
        repo.write(gLernen);

        // === GOAL: Morgenroutine (täglich, CRITICAL) ===
        trackedItem gMorgen = new Builder(ItemType.GOAL, "Morgenroutine", Priority.CRITICAL)
            .timeToComplete(30).prefTime("06:00").created("2025-12-01").build();
        repo.write(gMorgen);

        trackedItem tTabletten = new Builder(ItemType.TASK, "Tabletten nehmen", Priority.CRITICAL)
            .timeToComplete(2).prefTime("06:00").parent(gMorgen.id).created("2025-12-01")
            .lastCompletion("2026-01-29").repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
        trackedItem tFrueh = new Builder(ItemType.TASK, "Fruehstuecken", Priority.CRITICAL)
            .timeToComplete(20).prefTime("06:05").parent(gMorgen.id).created("2025-12-01")
            .lastCompletion("2026-01-29").repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
        trackedItem tDuschen = new Builder(ItemType.TASK, "Duschen", Priority.HIGH)
            .timeToComplete(10).prefTime("06:25").parent(gMorgen.id).created("2025-12-01")
            .lastCompletion("2026-01-29").repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
        repo.write(tTabletten); repo.write(tFrueh); repo.write(tDuschen);
        gMorgen.children = List.of(tTabletten.id, tFrueh.id, tDuschen.id);
        repo.write(gMorgen);

        // === GOAL: Haushalt (niedrige Prio, soll verdrängt werden wenn nötig) ===
        trackedItem gHaushalt = new Builder(ItemType.GOAL, "Haushalt", Priority.LOW)
            .timeToComplete(30).prefTime("16:00").created("2026-01-03").build();
        repo.write(gHaushalt);

        trackedItem tKueche = new Builder(ItemType.TASK, "Kueche putzen", Priority.MODERATE)
            .timeToComplete(15).prefTime("16:00").parent(gHaushalt.id).created("2026-01-03")
            .lastCompletion("2026-01-25").repetition(RepetitionType.INTERVAL, 3, RepUnits.DAY).build();
        trackedItem tStaub = new Builder(ItemType.TASK, "Staubsaugen", Priority.LOW)
            .timeToComplete(20).prefTime("16:30").parent(gHaushalt.id).created("2026-01-03")
            .lastCompletion("2026-01-20").repetition(RepetitionType.INTERVAL, 1, RepUnits.WEEK).build();
        repo.write(tKueche); repo.write(tStaub);
        gHaushalt.children = List.of(tKueche.id, tStaub.id);
        repo.write(gHaushalt);

        System.out.println("Seed: " + repo.items.size() + " Items geladen");
    }

    // ========================================================================
    // Ausgabe: 7-Tage Plan formatiert drucken
    // ========================================================================
    private static void printPlan(MockRepo repo) {
        List<String> sortedDates = new ArrayList<>(repo.todos.keySet());
        sortedDates.sort(String::compareTo);

        for (String dateStr : sortedDates) {
            todoList list = repo.todos.get(dateStr);
            LocalDate date = list.date;
            String dayName = date.getDayOfWeek().toString();

            System.out.printf("\n=== %s %s (%s - %s) ===%n",
                dayName, date, list.start, list.end);

            if (list.timeSlots == null || list.timeSlots.isEmpty()) {
                System.out.println("  (keine Eintraege)");
                continue;
            }

            for (TimeSlot slot : list.timeSlots) {
                printSlot(repo, slot, "  ");
            }
        }

        // Statistik
        int totalSlots = 0;
        for (todoList list : repo.todos.values()) {
            if (list.timeSlots != null) totalSlots += list.timeSlots.size();
        }
        System.out.printf("%n=== Zusammenfassung: %d Tage, %d Goal-Slots ===%n",
            repo.todos.size(), totalSlots);
    }

    private static void printSlot(MockRepo repo, TimeSlot slot, String indent) {
        if (slot.isCalendarEvent != null && slot.isCalendarEvent) {
            System.out.printf("%s%s-%s  [KALENDER] %s%n",
                indent, slot.start, slot.end, slot.calendarTitle);
            return;
        }

        String title = "???";
        String type = "";
        String prio = "";
        if (slot.item != null) {
            trackedItem item = repo.items.get(slot.item);
            if (item != null) {
                title = item.title;
                type = item.type.name();
                prio = item.priority.name();
            }
        }

        String adjPrio = slot.adjustedPrio != null ? ", score=" + slot.adjustedPrio : "";
        System.out.printf("%s%s-%s  %s [%s, %s%s]%n",
            indent, slot.start, slot.end, title, type, prio, adjPrio);

        // Verschachtelte Task-Slots
        if (slot.timeSlots != null) {
            for (TimeSlot child : slot.timeSlots) {
                printSlot(repo, child, indent + "    ");
            }
        }
    }
}
