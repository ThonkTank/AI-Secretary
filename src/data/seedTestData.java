package data;

import repository.SQLrepo;

import java.sql.*;
import java.time.DayOfWeek;
import java.util.List;

import entities.trackedItem;
import entities.trackedItem.*;

/**
 * Erstellt Testdaten in der Datenbank: Goals mit Tasks + config_schedules.
 * Ausführen: java -cp "bin:sqlite-jdbc.jar:slf4j-api.jar:slf4j-simple.jar" Data.seedTestData
 */
public class seedTestData {

    private static SQLrepo repo;

    public static void main(String[] args) {
        repo = new SQLrepo(constants.DB_PATH);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + constants.DB_PATH);
             Statement stmt = conn.createStatement()) {

            // Bestehende Daten löschen und ID-Counter zurücksetzen
            stmt.execute("DELETE FROM items");
            stmt.execute("DELETE FROM sqlite_sequence WHERE name='items'");
            stmt.execute("DELETE FROM config_schedules");

            // === CONFIG SCHEDULES ===
            String insertSchedule = "INSERT INTO config_schedules (day_of_week, start_time, end_time) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSchedule)) {
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
                    ps.setString(1, s[0]);
                    ps.setString(2, s[1]);
                    ps.setString(3, s[2]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // ===== PROJECT: Fitness =====
            trackedItem pFitness = new Builder(ItemType.PROJECT, "Fitness", Priority.HIGH)
                .created("2025-12-15").minIntervalDays(2).build();
            repo.write(pFitness);

            // --- Goal: Kraft ---
            trackedItem gKraft = new Builder(ItemType.GOAL, "Kraft", Priority.HIGH)
                .timeToComplete(45).prefTime("07:00").parent(pFitness.id).created("2025-12-15").build();
            repo.write(gKraft);

            trackedItem tLiegestuetze = new Builder(ItemType.TASK, "Liegestütze", Priority.HIGH)
                .timeToComplete(15).prefTime("06:30").parent(gKraft.id).created("2025-12-15")
                .lastCompletion("2026-01-10").repetition(RepetitionType.INTERVAL, 2, RepUnits.DAY).build();
            trackedItem tKniebeugen = new Builder(ItemType.TASK, "Kniebeugen", Priority.MODERATE)
                .timeToComplete(15).prefTime("07:00").parent(gKraft.id).created("2025-12-15")
                .lastCompletion("2026-01-20").repetition(RepetitionType.REPS_PER_TIME, 3, RepUnits.WEEK).build();
            trackedItem tKlimmzuege = new Builder(ItemType.TASK, "Klimmzuege", Priority.HIGH)
                .timeToComplete(15).prefTime("07:30").parent(gKraft.id).created("2025-12-15")
                .lastCompletion("2026-01-13").repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.MONDAY).build();
            repo.write(tLiegestuetze); repo.write(tKniebeugen); repo.write(tKlimmzuege);
            gKraft.children = List.of(tLiegestuetze.id, tKniebeugen.id, tKlimmzuege.id);
            repo.write(gKraft);

            // --- Goal: Ausdauer ---
            trackedItem gAusdauer = new Builder(ItemType.GOAL, "Ausdauer", Priority.MODERATE)
                .timeToComplete(50).prefTime("17:00").parent(pFitness.id).created("2025-12-15").build();
            repo.write(gAusdauer);

            trackedItem tJoggen = new Builder(ItemType.TASK, "Joggen", Priority.HIGH)
                .timeToComplete(30).prefTime("17:30").parent(gAusdauer.id).created("2025-12-15")
                .lastCompletion("2026-01-19").repetition(RepetitionType.INTERVAL, 2, RepUnits.DAY).build();
            trackedItem tSeilspringen = new Builder(ItemType.TASK, "Seilspringen", Priority.MODERATE)
                .timeToComplete(15).prefTime("17:00").parent(gAusdauer.id).created("2025-12-15")
                .lastCompletion("2026-01-21").repetition(RepetitionType.REPS_PER_TIME, 4, RepUnits.WEEK).build();
            trackedItem tTreppen = new Builder(ItemType.TASK, "Treppensteigen", Priority.LOW)
                .timeToComplete(10).prefTime("12:00").parent(gAusdauer.id).created("2025-12-15")
                .lastCompletion("2026-01-15").repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.WEDNESDAY).build();
            repo.write(tJoggen); repo.write(tSeilspringen); repo.write(tTreppen);
            gAusdauer.children = List.of(tJoggen.id, tSeilspringen.id, tTreppen.id);
            repo.write(gAusdauer);

            // --- Goal: Beweglichkeit ---
            trackedItem gBeweglich = new Builder(ItemType.GOAL, "Beweglichkeit", Priority.LOW)
                .timeToComplete(40).prefTime("21:00").parent(pFitness.id).created("2025-12-15").build();
            repo.write(gBeweglich);

            trackedItem tStretching = new Builder(ItemType.TASK, "Stretching", Priority.MODERATE)
                .timeToComplete(15).prefTime("21:00").parent(gBeweglich.id).created("2025-12-15")
                .lastCompletion("2026-01-20").repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            trackedItem tYoga = new Builder(ItemType.TASK, "Yoga", Priority.LOW)
                .timeToComplete(20).prefTime("20:30").parent(gBeweglich.id).created("2025-12-15")
                .lastCompletion("2026-01-18").repetition(RepetitionType.REPS_PER_TIME, 2, RepUnits.WEEK).build();
            trackedItem tFoam = new Builder(ItemType.TASK, "Foam Rolling", Priority.LOW)
                .timeToComplete(10).prefTime("21:30").parent(gBeweglich.id).created("2025-12-15")
                .lastCompletion("2026-01-17").repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.FRIDAY).build();
            repo.write(tStretching); repo.write(tYoga); repo.write(tFoam);
            gBeweglich.children = List.of(tStretching.id, tYoga.id, tFoam.id);
            repo.write(gBeweglich);

            pFitness.children = List.of(gKraft.id, gAusdauer.id, gBeweglich.id);
            repo.write(pFitness);

            // ===== PROJECT: Projekte (cooldown=14) =====
            trackedItem pProjekte = new Builder(ItemType.PROJECT, "Projekte", Priority.HIGH)
                .created("2025-12-01").cooldown(14).build();
            repo.write(pProjekte);

            trackedItem gSaltmarcher = new Builder(ItemType.GOAL, "Saltmarcher Coden", Priority.HIGH)
                .timeToComplete(180).prefTime("09:00").parent(pProjekte.id).created("2025-12-01").build();
            trackedItem gSecretary = new Builder(ItemType.GOAL, "Secretary Coden", Priority.HIGH)
                .timeToComplete(180).prefTime("09:00").parent(pProjekte.id).created("2025-12-01")
                .lastCompletion("2026-01-19").build();
            trackedItem gSchreiben = new Builder(ItemType.GOAL, "Schreiben ueben", Priority.HIGH)
                .timeToComplete(180).prefTime("09:00").parent(pProjekte.id).created("2025-12-01")
                .lastCompletion("2026-01-16").build();
            repo.write(gSaltmarcher); repo.write(gSecretary); repo.write(gSchreiben);
            pProjekte.children = List.of(gSaltmarcher.id, gSecretary.id, gSchreiben.id);
            repo.write(pProjekte);

            // ===== GOAL: Haushalt =====
            trackedItem gHaushalt = new Builder(ItemType.GOAL, "Haushalt", Priority.MODERATE)
                .timeToComplete(45).prefTime("16:00").created("2026-01-03").build();
            repo.write(gHaushalt);

            trackedItem tKueche = new Builder(ItemType.TASK, "Kueche putzen", Priority.MODERATE)
                .timeToComplete(15).prefTime("18:30").parent(gHaushalt.id).created("2026-01-03")
                .lastCompletion("2026-01-18").repetition(RepetitionType.INTERVAL, 2, RepUnits.DAY).build();
            trackedItem tStaub = new Builder(ItemType.TASK, "Staubsaugen", Priority.LOW)
                .timeToComplete(20).prefTime("10:00").parent(gHaushalt.id).created("2026-01-03")
                .lastCompletion("2026-01-12").repetition(RepetitionType.INTERVAL, 1, RepUnits.WEEK).build();
            trackedItem tWaesche = new Builder(ItemType.TASK, "Waesche waschen", Priority.MODERATE)
                .timeToComplete(10).prefTime("11:00").parent(gHaushalt.id).created("2026-01-03")
                .repetition(RepetitionType.REPS_PER_TIME, 2, RepUnits.MONTH).build();
            trackedItem tMuell = new Builder(ItemType.TASK, "Muell rausbringen", Priority.LOW)
                .timeToComplete(5).prefTime("19:00").parent(gHaushalt.id).created("2026-01-03")
                .lastCompletion("2026-01-09").repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.THURSDAY).build();
            repo.write(tKueche); repo.write(tStaub); repo.write(tWaesche); repo.write(tMuell);
            gHaushalt.children = List.of(tKueche.id, tStaub.id, tWaesche.id, tMuell.id);
            repo.write(gHaushalt);

            // ===== GOAL: Lernen =====
            trackedItem gLernen = new Builder(ItemType.GOAL, "Lernen", Priority.HIGH)
                .timeToComplete(90).prefTime("14:00").created("2025-12-20").build();
            repo.write(gLernen);

            trackedItem tJava = new Builder(ItemType.TASK, "Java lernen", Priority.HIGH)
                .timeToComplete(30).prefTime("14:00").parent(gLernen.id).created("2025-12-20")
                .lastCompletion("2026-01-22").repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            trackedItem tMathe = new Builder(ItemType.TASK, "Mathe ueben", Priority.HIGH)
                .timeToComplete(25).prefTime("15:00").parent(gLernen.id).created("2025-12-20")
                .lastCompletion("2026-01-20").repetition(RepetitionType.REPS_PER_TIME, 5, RepUnits.WEEK).build();
            trackedItem tVokabel = new Builder(ItemType.TASK, "Vokabeln lernen", Priority.MODERATE)
                .timeToComplete(20).prefTime("22:00").parent(gLernen.id).created("2025-12-20")
                .lastCompletion("2026-01-15").repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            trackedItem tArtikel = new Builder(ItemType.TASK, "Artikel lesen", Priority.LOW)
                .timeToComplete(15).prefTime("11:00").parent(gLernen.id).created("2025-12-20")
                .lastCompletion("2026-01-12").repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.SUNDAY).build();
            repo.write(tJava); repo.write(tMathe); repo.write(tVokabel); repo.write(tArtikel);
            gLernen.children = List.of(tJava.id, tMathe.id, tVokabel.id, tArtikel.id);
            repo.write(gLernen);

            // ===== GOAL: Morgenroutine =====
            trackedItem gMorgen = new Builder(ItemType.GOAL, "Morgenroutine", Priority.HIGH)
                .timeToComplete(75).prefTime("06:00").created("2025-12-01").build();
            repo.write(gMorgen);

            trackedItem t1  = new Builder(ItemType.TASK, "Tabletten nehmen",     Priority.CRITICAL).timeToComplete(2) .prefTime("06:00").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-22").repetition(RepetitionType.INTERVAL, 1,  RepUnits.DAY).build();
            trackedItem t2  = new Builder(ItemType.TASK, "Fruehstuecken",        Priority.CRITICAL).timeToComplete(20).prefTime("06:05").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-22").repetition(RepetitionType.INTERVAL, 1,  RepUnits.DAY).build();
            trackedItem t3  = new Builder(ItemType.TASK, "Rasieren",             Priority.MODERATE).timeToComplete(5) .prefTime("06:30").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-22").repetition(RepetitionType.INTERVAL, 1,  RepUnits.DAY).build();
            trackedItem t4  = new Builder(ItemType.TASK, "Naegel klippsen",      Priority.LOW)     .timeToComplete(5) .prefTime("06:33").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-10").repetition(RepetitionType.INTERVAL, 7,  RepUnits.DAY).build();
            trackedItem t5  = new Builder(ItemType.TASK, "Achseln rasieren",     Priority.MODERATE).timeToComplete(3) .prefTime("06:35").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-20").repetition(RepetitionType.INTERVAL, 3,  RepUnits.DAY).build();
            trackedItem t6  = new Builder(ItemType.TASK, "Brust rasieren",       Priority.MODERATE).timeToComplete(5) .prefTime("06:38").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-19").repetition(RepetitionType.INTERVAL, 3,  RepUnits.DAY).build();
            trackedItem t7  = new Builder(ItemType.TASK, "Bauch rasieren",       Priority.MODERATE).timeToComplete(3) .prefTime("06:41").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-18").repetition(RepetitionType.INTERVAL, 3,  RepUnits.DAY).build();
            trackedItem t8  = new Builder(ItemType.TASK, "Schritt rasieren",     Priority.MODERATE).timeToComplete(5) .prefTime("06:44").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-20").repetition(RepetitionType.INTERVAL, 3,  RepUnits.DAY).build();
            trackedItem t9  = new Builder(ItemType.TASK, "Hintern rasieren",     Priority.MODERATE).timeToComplete(5) .prefTime("06:47").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-17").repetition(RepetitionType.INTERVAL, 3,  RepUnits.DAY).build();
            trackedItem t10 = new Builder(ItemType.TASK, "Nasenhaare entfernen", Priority.MODERATE).timeToComplete(3) .prefTime("06:50").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-21").repetition(RepetitionType.INTERVAL, 3,  RepUnits.DAY).build();
            trackedItem t11 = new Builder(ItemType.TASK, "Duschen",              Priority.HIGH)    .timeToComplete(10).prefTime("06:55").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-22").repetition(RepetitionType.INTERVAL, 1,  RepUnits.DAY).build();
            trackedItem t12 = new Builder(ItemType.TASK, "Haare waschen",        Priority.HIGH)    .timeToComplete(5) .prefTime("07:05").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-10").repetition(RepetitionType.INTERVAL, 10, RepUnits.DAY).build();
            trackedItem t13 = new Builder(ItemType.TASK, "Strecken",             Priority.HIGH)    .timeToComplete(10).prefTime("07:15").parent(gMorgen.id).created("2025-12-01").lastCompletion("2026-01-21").repetition(RepetitionType.INTERVAL, 1,  RepUnits.DAY).build();
            repo.write(t1); repo.write(t2); repo.write(t3); repo.write(t4); repo.write(t5);
            repo.write(t6); repo.write(t7); repo.write(t8); repo.write(t9); repo.write(t10);
            repo.write(t11); repo.write(t12); repo.write(t13);
            gMorgen.children = List.of(t1.id, t2.id, t3.id, t4.id, t5.id, t6.id, t7.id, t8.id, t9.id, t10.id, t11.id, t12.id, t13.id);
            repo.write(gMorgen);

            System.out.println("Testdaten erfolgreich erstellt!");

            // Zusammenfassung ausgeben
            ResultSet rs = stmt.executeQuery("SELECT type, COUNT(*) FROM items GROUP BY type");
            while (rs.next()) {
                System.out.printf("  %s: %d%n", rs.getString(1), rs.getInt(2));
            }

        } catch (SQLException e) {
            System.err.println("Fehler beim Erstellen der Testdaten: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
