package data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.time.DayOfWeek;
import java.util.List;

import entities.trackedItem;
import entities.trackedItem.*;
import repository.SQLrepo;

/**
 * Erstellt minimale Testdaten in der Datenbank.
 *
 * Struktur (11 Items):
 *   PROJECT: Fitness (minIntervalDays=2)
 *   └── GOAL: Workout (30min)
 *       ├── TASK: Stretching (INTERVAL 1d, streak=105 Legendary)
 *       ├── TASK: Liegestuetze (INTERVAL 1d, requiredPredecessor, streak=35 Rare)
 *       └── TASK: Kniebeugen (REPS_PER_TIME 3/week)
 *
 *   GOAL: Lernen (45min)
 *   ├── TASK: Vokabeln (INTERVAL 1d, progressPerRep, streak=65 Epic)
 *   ├── TASK: Lesen (DAY_OF_TIME Freitag, streak=5 Common)
 *   └── TASK: Hausarbeit (NONE, deadline, progress global)
 *
 *   GOAL: Haushalt (30min)
 *   ├── TASK: Putzen (INTERVAL 3d, completeFirst, streak=15 Uncommon)
 *   └── TASK: Einkaufen (REPS_PER_TIME 2/week, CRITICAL)
 *
 * Alle Items sind offen (ueberfaellig), haben aber Streaks fuer Rarity-Demo.
 */
public class seedTestData {

    private SQLrepo repo;

    public seedTestData(Context context) {
        this.repo = new SQLrepo(context);
    }

    public void seed() {
        SQLiteDatabase db = repo.getWritableDatabase();
        db.beginTransaction();
        try {
            // Bestehende Daten loeschen und ID-Counter zuruecksetzen
            db.delete("items", null, null);
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='items'");
            db.delete("config_schedules", null, null);

            // === CONFIG SCHEDULES ===
            String[][] schedules = {
                {"MONDAY",    "06:00", "18:00"},
                {"TUESDAY",   "06:00", "18:00"},
                {"WEDNESDAY", "06:00", "18:00"},
                {"THURSDAY",  "06:00", "18:00"},
                {"FRIDAY",    "06:00", "18:00"},
                {"SATURDAY",  "09:00", "16:00"},
                {"SUNDAY",    "09:00", "14:00"}
            };
            for (String[] s : schedules) {
                ContentValues cv = new ContentValues();
                cv.put("day_of_week", s[0]);
                cv.put("start_time", s[1]);
                cv.put("end_time", s[2]);
                db.insert("config_schedules", null, cv);
            }

            // ===== PROJECT: Fitness =====
            trackedItem pFitness = new Builder(ItemType.PROJECT, "Fitness", Priority.HIGH)
                .created("2025-12-01").minIntervalDays(2).build();
            repo.write(pFitness);

            // --- Goal: Workout ---
            trackedItem gWorkout = new Builder(ItemType.GOAL, "Workout", Priority.HIGH)
                .maxMinutes(30).prefTime("07:00").parent(pFitness.id).created("2025-12-01")
                .goalIcon("\uD83D\uDCAA").goalColor("#FFE53935").build();
            repo.write(gWorkout);

            // Tasks unter Workout
            trackedItem tStretching = new Builder(ItemType.TASK, "Stretching", Priority.MODERATE)
                .maxMinutes(10).prefTime("07:00").parent(gWorkout.id).created("2025-12-01")
                .lastCompletion("2026-02-02").currentStreak(105).totalCompletions(105)
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            repo.write(tStretching);

            trackedItem tLiegestuetze = new Builder(ItemType.TASK, "Liegestuetze", Priority.HIGH)
                .maxMinutes(10).prefTime("07:10").parent(gWorkout.id).created("2025-12-01")
                .lastCompletion("2026-02-02").currentStreak(35).totalCompletions(35)
                .requiredPredecessor(tStretching.id)  // Chain: Stretching -> Liegestuetze
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            repo.write(tLiegestuetze);

            trackedItem tKniebeugen = new Builder(ItemType.TASK, "Kniebeugen", Priority.MODERATE)
                .maxMinutes(10).prefTime("07:20").parent(gWorkout.id).created("2025-12-01")
                .repetition(RepetitionType.REPS_PER_TIME, 3, RepUnits.WEEK).build();
            repo.write(tKniebeugen);

            gWorkout.children = List.of(tStretching.id, tLiegestuetze.id, tKniebeugen.id);
            repo.write(gWorkout);

            pFitness.children = List.of(gWorkout.id);
            repo.write(pFitness);

            // ===== GOAL: Lernen =====
            trackedItem gLernen = new Builder(ItemType.GOAL, "Lernen", Priority.HIGH)
                .maxMinutes(45).prefTime("14:00").created("2025-12-01")
                .goalIcon("\uD83D\uDCDA").goalColor("#FF8E24AA").build();
            repo.write(gLernen);

            trackedItem tVokabeln = new Builder(ItemType.TASK, "Vokabeln", Priority.HIGH)
                .maxMinutes(15).prefTime("14:00").parent(gLernen.id).created("2025-12-01")
                .lastCompletion("2026-02-02").currentStreak(65).totalCompletions(65)
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY)
                .progressTarget(20).progressUnit("Woerter").progressPerRep(true).build();
            repo.write(tVokabeln);

            trackedItem tLesen = new Builder(ItemType.TASK, "Lesen", Priority.LOW)
                .maxMinutes(15).prefTime("14:15").parent(gLernen.id).created("2025-12-01")
                .lastCompletion("2026-01-24").currentStreak(5).totalCompletions(5)
                .repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.FRIDAY).build();
            repo.write(tLesen);

            trackedItem tHausarbeit = new Builder(ItemType.TASK, "Hausarbeit", Priority.HIGH)
                .maxMinutes(15).prefTime("14:30").parent(gLernen.id).created("2026-01-15")
                .noRepetition().deadline("2026-02-15")
                .progressTarget(10).progressCurrent(3).progressUnit("Seiten").build();
            repo.write(tHausarbeit);

            gLernen.children = List.of(tVokabeln.id, tLesen.id, tHausarbeit.id);
            repo.write(gLernen);

            // ===== GOAL: Haushalt =====
            trackedItem gHaushalt = new Builder(ItemType.GOAL, "Haushalt", Priority.MODERATE)
                .maxMinutes(30).prefTime("16:00").created("2025-12-01")
                .goalIcon("\uD83C\uDFE0").goalColor("#FFFB8C00").build();
            repo.write(gHaushalt);

            trackedItem tPutzen = new Builder(ItemType.TASK, "Putzen", Priority.MODERATE)
                .maxMinutes(15).prefTime("16:00").parent(gHaushalt.id).created("2025-12-01")
                .lastCompletion("2026-01-31").currentStreak(15).totalCompletions(15)
                .completeFirst(true)  // completeFirst-Modus
                .repetition(RepetitionType.INTERVAL, 3, RepUnits.DAY).build();
            repo.write(tPutzen);

            trackedItem tEinkaufen = new Builder(ItemType.TASK, "Einkaufen", Priority.CRITICAL)
                .maxMinutes(15).prefTime("16:15").parent(gHaushalt.id).created("2025-12-01")
                .repetition(RepetitionType.REPS_PER_TIME, 2, RepUnits.WEEK).build();
            repo.write(tEinkaufen);

            gHaushalt.children = List.of(tPutzen.id, tEinkaufen.id);
            repo.write(gHaushalt);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
