package data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import entities.TrackedItem;
import entities.TrackedItem.*;
import entities.Account;
import entities.Transaction;
import entities.BudgetLimit;
import entities.Category;
import entities.HouseholdMember;
import entities.CookingPreferences;
import entities.Ingredient;
import entities.MealType;
import entities.Recipe;
import entities.MealSchedule;
import repository.SQLrepo;

/**
 * Erstellt Testdaten in der Datenbank, die ALLE App-Funktionen abdecken.
 *
 * Struktur:
 *   PROJECT: Fitness
 *   └── GOAL: Workout (30min)
 *       ├── TASK: Stretching (INTERVAL 1d, streak=105 Legendary)
 *       ├── TASK: Liegestuetze (INTERVAL 1d, streak=35 Rare, chainAfter Stretching)
 *       └── TASK: Kniebeugen (REPS_PER_TIME 3/week)
 *
 *   GOAL: Lernen (45min)
 *   ├── TASK: Vokabeln (INTERVAL 1d, progressPerRep, streak=65 Epic)
 *   ├── TASK: Lesen (DAY_OF_TIME Freitag, streak=5 Common)
 *   └── TASK: Hausarbeit (NONE, deadline, progress global, timePerProgressUnit)
 *
 *   GOAL: Haushalt (30min)
 *   ├── TASK: Putzen (INTERVAL 3d, completeFirst, streak=15 Uncommon)
 *   ├── TASK: Einkaufen (REPS_PER_TIME 2/week, CRITICAL)
 *   └── TASK: Frisör (budget 30€)
 *
 *   GOAL: Einkäufe (Budget-Demo)
 *   └── TASK: AirForce 1 kaufen (budget 150€)
 *
 *   GOAL: Wäsche - DELAYED CHAIN DEMO
 *   ├── TASK: Waschen (erster Step)
 *   ├── TASK: Aufhängen (delayAfter Waschen, 180min = 3h)
 *   └── TASK: Abhängen (delayAfter Aufhängen, 1440min = 24h)
 *
 *   GOAL: Finanzen - ERWEITERTE FEATURES
 *   ├── TASK: Miete zahlen (DAY_OF_TIME am 1., budgetAccountId=Kreditkarte)
 *   └── TASK: Steuern (NONE, minMinutes, cooldown)
 *
 *   GOAL: Programmieren - MIN/MAX PROGRESS DEMO
 *   └── TASK: Coding (minProgress=2, maxProgress=8, timePerProgressUnit=30)
 *
 * Budget-Features:
 *   - AccountType: CHECKING, SAVINGS, CASH, CREDIT
 *   - includeInTotal=false (Sparkonto)
 *   - RecurringType: MONTHLY_DAY, MONTHLY_LAST, WEEKLY, INTERVAL
 *   - isConfirmed=false (unbestätigte Auto-Transactions)
 *   - Varianz-Tracking (min/max/avg)
 *
 * Alle Items sind offen (ueberfaellig), haben aber Streaks fuer Rarity-Demo.
 */
public class SeedTestData {

    private SQLrepo repo;

    public SeedTestData(Context context) {
        this.repo = SQLrepo.getInstance(context);
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

            // === DEFAULT CATEGORIES (Built-In) ===
            db.delete("categories", null, null);
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='categories'");

            // Einnahmen (isIncome=true)
            Category catGehalt = new Category.Builder("Gehalt", true).icon("💰").color("#4CAF50").builtIn(true).sortOrder(1).build();
            repo.write(catGehalt);
            Category catBonus = new Category.Builder("Bonus", true).icon("🎁").color("#8BC34A").builtIn(true).sortOrder(2).build();
            repo.write(catBonus);
            Category catErstattung = new Category.Builder("Erstattung", true).icon("↩️").color("#CDDC39").builtIn(true).sortOrder(3).build();
            repo.write(catErstattung);
            Category catIncomeOther = new Category.Builder("Sonstiges Einkommen", true).icon("➕").color("#9E9E9E").builtIn(true).sortOrder(4).build();
            repo.write(catIncomeOther);

            // Ausgaben (isIncome=false)
            Category catMiete = new Category.Builder("Miete", false).icon("🏠").color("#795548").builtIn(true).sortOrder(10).build();
            repo.write(catMiete);
            Category catNebenkosten = new Category.Builder("Nebenkosten", false).icon("⚡").color("#FF9800").builtIn(true).sortOrder(11).build();
            repo.write(catNebenkosten);
            Category catLebensmittel = new Category.Builder("Lebensmittel", false).icon("🛒").color("#4CAF50").builtIn(true).sortOrder(12).build();
            repo.write(catLebensmittel);
            Category catRestaurant = new Category.Builder("Restaurant", false).icon("🍽️").color("#FF5722").builtIn(true).sortOrder(13).build();
            repo.write(catRestaurant);
            Category catOepnv = new Category.Builder("ÖPNV", false).icon("🚇").color("#2196F3").builtIn(true).sortOrder(14).build();
            repo.write(catOepnv);
            Category catAuto = new Category.Builder("Auto", false).icon("🚗").color("#607D8B").builtIn(true).sortOrder(15).build();
            repo.write(catAuto);
            Category catGesundheit = new Category.Builder("Gesundheit", false).icon("💊").color("#E91E63").builtIn(true).sortOrder(16).build();
            repo.write(catGesundheit);
            Category catKleidung = new Category.Builder("Kleidung", false).icon("👕").color("#9C27B0").builtIn(true).sortOrder(17).build();
            repo.write(catKleidung);
            Category catEntertainment = new Category.Builder("Entertainment", false).icon("🎬").color("#673AB7").builtIn(true).sortOrder(18).build();
            repo.write(catEntertainment);
            Category catAbos = new Category.Builder("Abos", false).icon("📺").color("#3F51B5").builtIn(true).sortOrder(19).build();
            repo.write(catAbos);
            Category catSport = new Category.Builder("Sport", false).icon("🏃").color("#00BCD4").builtIn(true).sortOrder(20).build();
            repo.write(catSport);
            Category catReisen = new Category.Builder("Reisen", false).icon("✈️").color("#009688").builtIn(true).sortOrder(21).build();
            repo.write(catReisen);
            Category catSparen = new Category.Builder("Sparen", false).icon("🏦").color("#FFC107").builtIn(true).sortOrder(22).build();
            repo.write(catSparen);
            Category catGebuehren = new Category.Builder("Gebühren", false).icon("🏛️").color("#9E9E9E").builtIn(true).sortOrder(23).build();
            repo.write(catGebuehren);
            Category catUmbuchung = new Category.Builder("Umbuchung", false).icon("🔄").color("#607D8B").builtIn(true).sortOrder(24).build();
            repo.write(catUmbuchung);
            Category catElektronik = new Category.Builder("Elektronik", false).icon("📱").color("#455A64").builtIn(true).sortOrder(25).build();
            repo.write(catElektronik);
            Category catExpenseOther = new Category.Builder("Sonstiges", false).icon("❓").color("#757575").builtIn(true).sortOrder(99).build();
            repo.write(catExpenseOther);

            // ===== PROJECT: Fitness =====
            TrackedItem pFitness = new Builder(ItemType.PROJECT, "Fitness", Priority.HIGH)
                .created("2025-12-01").build();
            repo.write(pFitness);

            // --- Goal: Workout ---
            TrackedItem gWorkout = new Builder(ItemType.GOAL, "Workout", Priority.HIGH)
                .maxMinutes(30).prefSlots(allDays("07:00")).parent(pFitness.id).created("2025-12-01")
                .goalIcon("\uD83D\uDCAA").goalColor("#FFE53935").build();
            repo.write(gWorkout);

            // Tasks unter Workout
            TrackedItem tStretching = new Builder(ItemType.TASK, "Stretching", Priority.MODERATE)
                .maxMinutes(10).prefSlots(allDays("07:00")).parent(gWorkout.id).created("2025-12-01")
                .lastCompletion("2026-02-02").currentStreak(105).totalCompletions(105)
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            repo.write(tStretching);

            TrackedItem tLiegestuetze = new Builder(ItemType.TASK, "Liegestuetze", Priority.HIGH)
                .maxMinutes(10).prefSlots(allDays("07:10")).parent(gWorkout.id).created("2025-12-01")
                .lastCompletion("2026-02-02").currentStreak(35).totalCompletions(35)
                .chainAfter(tStretching.id)  // Same-Day Chain: direkt nach Stretching
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            repo.write(tLiegestuetze);

            TrackedItem tKniebeugen = new Builder(ItemType.TASK, "Kniebeugen", Priority.MODERATE)
                .maxMinutes(10).prefSlots(allDays("07:20")).parent(gWorkout.id).created("2025-12-01")
                .repetition(RepetitionType.REPS_PER_TIME, 3, RepUnits.WEEK).build();
            repo.write(tKniebeugen);

            gWorkout.children = List.of(tStretching.id, tLiegestuetze.id, tKniebeugen.id);
            repo.write(gWorkout);

            pFitness.children = List.of(gWorkout.id);
            repo.write(pFitness);

            // ===== GOAL: Lernen =====
            TrackedItem gLernen = new Builder(ItemType.GOAL, "Lernen", Priority.HIGH)
                .maxMinutes(45).prefSlots(allDays("14:00")).created("2025-12-01")
                .goalIcon("\uD83D\uDCDA").goalColor("#FF8E24AA").build();
            repo.write(gLernen);

            TrackedItem tVokabeln = new Builder(ItemType.TASK, "Vokabeln", Priority.HIGH)
                .maxMinutes(15).prefSlots(allDays("14:00")).parent(gLernen.id).created("2025-12-01")
                .lastCompletion("2026-02-02").currentStreak(65).totalCompletions(65)
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY)
                .progressTarget(20).progressUnit("Woerter").progressPerRep(true).build();
            repo.write(tVokabeln);

            TrackedItem tLesen = new Builder(ItemType.TASK, "Lesen", Priority.LOW)
                .maxMinutes(15).prefSlots(allDays("14:15")).parent(gLernen.id).created("2025-12-01")
                .lastCompletion("2026-01-24").currentStreak(5).totalCompletions(5)
                .repetition(RepetitionType.DAY_OF_TIME, 0, RepUnits.WEEK, DayOfWeek.FRIDAY).build();
            repo.write(tLesen);

            TrackedItem tHausarbeit = new Builder(ItemType.TASK, "Hausarbeit", Priority.HIGH)
                .minMinutes(30).maxMinutes(90).prefSlots(allDays("14:30")).parent(gLernen.id).created("2026-01-15")
                .description("Wissenschaftliche Arbeit schreiben")
                .noRepetition().deadline("2026-02-15")
                .progressTarget(10).progressCurrent(3).progressUnit("Seiten")
                .timePerProgressUnit(45).progressTimingCount(3)  // 45 min pro Seite (gemessen)
                .build();
            repo.write(tHausarbeit);

            gLernen.children = List.of(tVokabeln.id, tLesen.id, tHausarbeit.id);
            repo.write(gLernen);

            // ===== GOAL: Haushalt =====
            TrackedItem gHaushalt = new Builder(ItemType.GOAL, "Haushalt", Priority.MODERATE)
                .maxMinutes(30).prefSlots(allDays("16:00")).created("2025-12-01")
                .goalIcon("\uD83C\uDFE0").goalColor("#FFFB8C00").build();
            repo.write(gHaushalt);

            TrackedItem tPutzen = new Builder(ItemType.TASK, "Putzen", Priority.MODERATE)
                .maxMinutes(15).prefSlots(allDays("16:00")).parent(gHaushalt.id).created("2025-12-01")
                .lastCompletion("2026-01-31").currentStreak(15).totalCompletions(15)
                .completeFirst(true)  // completeFirst-Modus
                .repetition(RepetitionType.INTERVAL, 3, RepUnits.DAY).build();
            repo.write(tPutzen);

            TrackedItem tEinkaufen = new Builder(ItemType.TASK, "Einkaufen", Priority.CRITICAL)
                .maxMinutes(15).prefSlots(allDays("16:15")).parent(gHaushalt.id).created("2025-12-01")
                .repetition(RepetitionType.REPS_PER_TIME, 2, RepUnits.WEEK).build();
            repo.write(tEinkaufen);

            // Task mit Budget: Frisör (30€)
            TrackedItem tFrisoer = new Builder(ItemType.TASK, "Frisör", Priority.LOW)
                .maxMinutes(60).parent(gHaushalt.id).created("2025-12-01")
                .budgetRequirement(3000)  // 30€
                .budgetCategory(catGesundheit.id)
                .repetition(RepetitionType.INTERVAL, 30, RepUnits.DAY).build();
            repo.write(tFrisoer);

            gHaushalt.children = List.of(tPutzen.id, tEinkaufen.id, tFrisoer.id);
            repo.write(gHaushalt);

            // ===== GOAL: Einkäufe (Budget-Demo) =====
            TrackedItem gEinkaeufe = new Builder(ItemType.GOAL, "Einkäufe", Priority.LOW)
                .maxMinutes(60).prefSlots(allDays("15:00")).created("2026-01-01")
                .goalIcon("\uD83D\uDED2").goalColor("#FF7B1FA2").build();
            repo.write(gEinkaeufe);

            // Einmaliger Kauf: AirForce 1 (150€) - wird nicht eingeplant wenn Budget < 150€
            TrackedItem tAirForce = new Builder(ItemType.TASK, "AirForce 1 kaufen", Priority.LOW)
                .maxMinutes(30).parent(gEinkaeufe.id).created("2026-01-01")
                .budgetRequirement(15000)  // 150€
                .budgetCategory(catKleidung.id)
                .noRepetition().build();
            repo.write(tAirForce);
            // children wird nach Konten-Erstellung aktualisiert (inkl. tOnlineShopping)

            // ===== GOAL: Wäsche (DELAYED CHAIN) =====
            TrackedItem gWaesche = new Builder(ItemType.GOAL, "Wäsche", Priority.MODERATE)
                .maxMinutes(60).prefSlots(allDays("10:00")).created("2025-12-01")
                .goalIcon("\uD83E\uDDFA").goalColor("#FF42A5F5").build();
            repo.write(gWaesche);

            TrackedItem tWaschen = new Builder(ItemType.TASK, "Waschen", Priority.MODERATE)
                .maxMinutes(15).parent(gWaesche.id).created("2025-12-01")
                .repetition(RepetitionType.INTERVAL, 3, RepUnits.DAY).build();
            repo.write(tWaschen);

            TrackedItem tAufhaengen = new Builder(ItemType.TASK, "Aufhängen", Priority.HIGH)
                .maxMinutes(10).parent(gWaesche.id).created("2025-12-01")
                .delayAfter(tWaschen.id, 180)  // 3 Stunden nach Waschen
                .repetition(RepetitionType.INTERVAL, 3, RepUnits.DAY).build();
            repo.write(tAufhaengen);

            TrackedItem tAbhaengen = new Builder(ItemType.TASK, "Abhängen", Priority.MODERATE)
                .maxMinutes(10).parent(gWaesche.id).created("2025-12-01")
                .delayAfter(tAufhaengen.id, 1440)  // 24 Stunden nach Aufhängen
                .repetition(RepetitionType.INTERVAL, 3, RepUnits.DAY).build();
            repo.write(tAbhaengen);

            gWaesche.children = List.of(tWaschen.id, tAufhaengen.id, tAbhaengen.id);
            repo.write(gWaesche);

            // ===== GOAL: Finanzen (DAY_OF_TIME Monatstag + budgetAccountId + cooldown) =====
            TrackedItem gFinanzen = new Builder(ItemType.GOAL, "Finanzen", Priority.HIGH)
                .maxMinutes(30).prefSlots(allDays("09:00")).created("2025-12-01")
                .description("Monatliche Finanzaufgaben")
                .goalIcon("\uD83D\uDCB0").goalColor("#FF00796B").build();
            repo.write(gFinanzen);

            // DAY_OF_TIME mit Monatstag ("jeden 1.")
            TrackedItem tMieteZahlen = new Builder(ItemType.TASK, "Miete zahlen", Priority.CRITICAL)
                .maxMinutes(10).parent(gFinanzen.id).created("2025-12-01")
                .description("Dauerauftrag prüfen")
                .repetition(RepetitionType.DAY_OF_TIME, 1, RepUnits.MONTH)  // Am 1. jeden Monats
                .build();
            repo.write(tMieteZahlen);

            // Einmalige Aufgabe mit minMinutes und cooldown
            TrackedItem tSteuern = new Builder(ItemType.TASK, "Steuererklärung", Priority.HIGH)
                .minMinutes(60).maxMinutes(180).parent(gFinanzen.id).created("2026-01-01")
                .description("Belege sammeln, ELSTER ausfüllen")
                .noRepetition().deadline("2026-05-31")
                .cooldown(7)  // 7 Tage Abstand
                .build();
            repo.write(tSteuern);

            // Fester Termin: Task mit fixedDate + fixedTime
            TrackedItem tZahnarzt = new Builder(ItemType.TASK, "Zahnarzt", Priority.CRITICAL)
                .maxMinutes(60).parent(gFinanzen.id).created("2026-01-15")
                .description("Kontrolluntersuchung")
                .noRepetition()
                .fixedAppointment("2026-02-10", "14:00")
                .build();
            repo.write(tZahnarzt);

            gFinanzen.children = List.of(tMieteZahlen.id, tSteuern.id, tZahnarzt.id);
            repo.write(gFinanzen);

            // ===== GOAL: Programmieren (MIN/MAX PROGRESS_UNITS + timePerProgressUnit) =====
            TrackedItem gProgrammieren = new Builder(ItemType.GOAL, "Programmieren", Priority.HIGH)
                .maxMinutes(120).prefSlots(allDays("19:00")).created("2025-12-01")
                .description("Abendliche Coding-Sessions")
                .goalIcon("\uD83D\uDCBB").goalColor("#FF6200EE").build();
            repo.write(gProgrammieren);

            // Task mit PROGRESS_UNITS-basierter Duration + gemessenem timePerProgressUnit
            TrackedItem tCoding = new Builder(ItemType.TASK, "Coding", Priority.HIGH)
                .minProgress(2).maxProgress(8)  // Min 2, max 8 Pomodoros pro Tag
                .parent(gProgrammieren.id).created("2025-12-01")
                .description("Pomodoro-Technik: 25min fokussiert arbeiten")
                .lastCompletion("2026-02-02").currentStreak(20).totalCompletions(20)
                .progressTarget(4).progressUnit("Pomodoros")
                .timePerProgressUnit(25).progressTimingCount(15)  // 25 min pro Pomodoro (gemessen)
                .repetition(RepetitionType.INTERVAL, 1, RepUnits.DAY).build();
            repo.write(tCoding);

            gProgrammieren.children = List.of(tCoding.id);
            repo.write(gProgrammieren);

            // ===== BUDGET-TRACKING TESTDATEN =====

            // Budget-Tabellen leeren
            db.delete("transactions", null, null);
            db.delete("budget_limits", null, null);
            db.delete("imports", null, null);
            db.delete("accounts", null, null);
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='accounts'");
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='transactions'");
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='budget_limits'");
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='imports'");

            // --- Konten (alle 4 AccountTypes + includeInTotal Demo) ---
            Account accGiro = new Account.Builder("Girokonto DKB", Account.AccountType.CHECKING)
                .initialBalance(250000)  // 2500.00 EUR
                .institution("DKB").icon("🏦").color("#FF1976D2").build();
            repo.write(accGiro);

            Account accSpar = new Account.Builder("Sparkonto", Account.AccountType.SAVINGS)
                .initialBalance(1000000)  // 10000.00 EUR
                .institution("DKB").icon("💰").color("#FF4CAF50")
                .includeInTotal(false)  // Nicht im verfügbaren Budget
                .build();
            repo.write(accSpar);

            Account accBargeld = new Account.Builder("Bargeld", Account.AccountType.CASH)
                .initialBalance(15000)  // 150.00 EUR
                .icon("💵").color("#FFFFC107").build();
            repo.write(accBargeld);

            Account accKreditkarte = new Account.Builder("Visa Kreditkarte", Account.AccountType.CREDIT)
                .initialBalance(-5000)  // -50.00 EUR (Schulden)
                .institution("DKB").icon("💳").color("#FFE91E63")
                .accountNumber("**** 4242").build();
            repo.write(accKreditkarte);

            // --- Budget-Task mit spezifischem Konto (budgetAccountId Demo) ---
            // Hinweis: Dieser Task wird dem Einkäufe-Goal hinzugefügt
            TrackedItem tOnlineShopping = new Builder(ItemType.TASK, "Online-Bestellung", Priority.LOW)
                .maxMinutes(15).parent(gEinkaeufe.id).created("2026-01-15")
                .description("Bestellung mit Kreditkarte bezahlen")
                .budgetRequirement(5000)  // 50€
                .budgetAccount(accKreditkarte.id)  // Explizit Kreditkarte
                .budgetCategory(catElektronik.id)
                .noRepetition().build();
            repo.write(tOnlineShopping);

            // Einkäufe-Goal children aktualisieren
            gEinkaeufe.children = List.of(tAirForce.id, tOnlineShopping.id);
            repo.write(gEinkaeufe);

            // --- Wiederkehrende Transaktionen (Templates) ---

            // Gehalt (recurring Income)
            Transaction txGehalt = new Transaction.Builder(
                    accGiro.id, 280000, java.time.LocalDate.of(2026, 1, 28), catGehalt.id)
                .description("Gehalt").payee("Arbeitgeber GmbH")
                .monthlyOnDay(28).nextDue(java.time.LocalDate.of(2026, 2, 28)).build();
            repo.write(txGehalt);

            // Miete (recurring Expense)
            Transaction txMiete = new Transaction.Builder(
                    accGiro.id, -85000, java.time.LocalDate.of(2026, 1, 1), catMiete.id)
                .description("Miete").payee("Vermieter")
                .monthlyOnDay(1).nextDue(java.time.LocalDate.of(2026, 2, 1)).build();
            repo.write(txMiete);

            // Netflix (recurring Expense)
            Transaction txNetflix = new Transaction.Builder(
                    accGiro.id, -1299, java.time.LocalDate.of(2026, 1, 15), catAbos.id)
                .description("Netflix").payee("Netflix")
                .monthlyOnDay(15).nextDue(java.time.LocalDate.of(2026, 2, 15)).build();
            repo.write(txNetflix);

            // --- Einmalige Transaktionen ---

            // Supermarkt
            Transaction txRewe = new Transaction.Builder(
                    accGiro.id, -4532, java.time.LocalDate.of(2026, 2, 1), catLebensmittel.id)
                .description("Wocheneinkauf").payee("REWE").build();
            repo.write(txRewe);

            // Restaurant
            Transaction txRestaurant = new Transaction.Builder(
                    accGiro.id, -2850, java.time.LocalDate.of(2026, 2, 2), catRestaurant.id)
                .description("Abendessen").payee("Pizzeria Roma").build();
            repo.write(txRestaurant);

            // ÖPNV
            Transaction txOepnv = new Transaction.Builder(
                    accGiro.id, -9900, java.time.LocalDate.of(2026, 2, 1), catOepnv.id)
                .description("Monatskarte").payee("VBB").build();
            repo.write(txOepnv);

            // --- Weitere Recurring-Typen (WEEKLY, MONTHLY_LAST, INTERVAL) ---

            // WEEKLY: Wochenmarkt jeden Samstag
            Transaction txWochenmarkt = new Transaction.Builder(
                    accBargeld.id, -2500, java.time.LocalDate.of(2026, 2, 1), catLebensmittel.id)
                .description("Wochenmarkt").payee("Marktstand")
                .weekly(java.time.DayOfWeek.SATURDAY)
                .nextDue(java.time.LocalDate.of(2026, 2, 8)).build();
            // Varianz-Tracking hinzufügen
            txWochenmarkt.amountMinCents = -2000;
            txWochenmarkt.amountMaxCents = -3500;
            txWochenmarkt.amountAvgCents = -2500;
            txWochenmarkt.occurrenceCount = 8;
            repo.write(txWochenmarkt);

            // MONTHLY_LAST: Kreditkarten-Abbuchung am Monatsende
            Transaction txKreditkartenAusgleich = new Transaction.Builder(
                    accGiro.id, -15000, java.time.LocalDate.of(2026, 1, 31), catUmbuchung.id)
                .description("Kreditkarten-Ausgleich").payee("DKB Visa")
                .linkedTransactionId(null)  // TODO: Gegenbuchung auf Kreditkarte
                .monthlyLast()
                .nextDue(java.time.LocalDate.of(2026, 2, 28)).build();
            repo.write(txKreditkartenAusgleich);

            // INTERVAL: Alle 2 Wochen Taschengeld
            Transaction txTaschengeld = new Transaction.Builder(
                    accBargeld.id, -5000, java.time.LocalDate.of(2026, 1, 20), catExpenseOther.id)
                .description("Taschengeld Kind").payee("Max")
                .interval(2, Transaction.RepUnits.WEEK)
                .nextDue(java.time.LocalDate.of(2026, 2, 3)).build();
            repo.write(txTaschengeld);

            // --- Unbestätigte Transaktion (Auto-generiert von Budget-Task) ---
            Transaction txAutoFrisoer = new Transaction.Builder(
                    accGiro.id, -3000, java.time.LocalDate.of(2026, 2, 3), catGesundheit.id)
                .description("Frisör (automatisch)").payee("Friseur")
                .isConfirmed(false)  // Vom Budget-Task generiert, noch nicht bestätigt
                .build();
            repo.write(txAutoFrisoer);

            // --- Budget Limits für Februar 2026 ---

            BudgetLimit blGroceries = new BudgetLimit.Builder(catLebensmittel.id, "2026-02", 30000)
                .notes("Lebensmittel-Budget").build();
            blGroceries.spentCents = 4532;  // REWE Einkauf
            repo.write(blGroceries);

            BudgetLimit blRestaurant = new BudgetLimit.Builder(catRestaurant.id, "2026-02", 15000)
                .notes("Essen gehen").build();
            blRestaurant.spentCents = 2850;
            repo.write(blRestaurant);

            BudgetLimit blEntertainment = new BudgetLimit.Builder(catEntertainment.id, "2026-02", 10000)
                .notes("Kino, Konzerte etc.").build();
            repo.write(blEntertainment);

            BudgetLimit blSubscriptions = new BudgetLimit.Builder(catAbos.id, "2026-02", 5000)
                .notes("Netflix, Spotify").build();
            blSubscriptions.spentCents = 1299;
            repo.write(blSubscriptions);

            // ===== MEAL-PLANNING TESTDATEN =====
            seedMealPlanningData(db);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Seeded Meal-Planning-Daten: Haushaltsmitglieder, Koch-Präferenzen, ~200 Zutaten.
     */
    private void seedMealPlanningData(SQLiteDatabase db) {
        // Tabellen leeren
        db.delete("consumption_logs", null, null);
        db.delete("pantry_items", null, null);
        db.delete("shopping_list_items", null, null);
        db.delete("meal_plans", null, null);
        db.delete("meal_schedules", null, null);
        db.delete("recipe_ratings", null, null);
        db.delete("recipes", null, null);
        db.delete("ingredients", null, null);
        db.delete("cooking_preferences", null, null);
        db.delete("household_members", null, null);
        db.delete("weekly_food_targets", null, null);

        // ID-Counter zurücksetzen
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='household_members'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='cooking_preferences'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='meal_schedules'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='ingredients'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='recipes'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='recipe_ratings'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='meal_plans'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='shopping_list_items'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='pantry_items'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='consumption_logs'");
        db.execSQL("DELETE FROM sqlite_sequence WHERE name='weekly_food_targets'");

        // === HAUSHALTSMITGLIEDER ===
        HouseholdMember m1 = new HouseholdMember.Builder("Max")
            .birthYear(1990).gender(HouseholdMember.Gender.MALE)
            .weightKg(80).heightCm(180)
            .activityLevel(HouseholdMember.ActivityLevel.MODERATE).build();
        repo.write(m1);

        HouseholdMember m2 = new HouseholdMember.Builder("Lisa")
            .birthYear(1992).gender(HouseholdMember.Gender.FEMALE)
            .weightKg(62).heightCm(168)
            .activityLevel(HouseholdMember.ActivityLevel.ACTIVE).build();
        repo.write(m2);

        // === KOCH-PRÄFERENZEN ===
        CookingPreferences prefs = new CookingPreferences.Builder()
            .maxBreakfastCooking(2)
            .maxLunchCooking(3)
            .maxDinnerCooking(4)
            .quickPrepMax(15).build();
        repo.write(prefs);

        // === MAHLZEITEN-KALENDER ===
        // Werktags (Mo-Fr): Frühstück 06:30 (30min), Mittag 12:00 (45min), Abend 18:00 (30min)
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                     DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            repo.write(new MealSchedule.Builder(day, MealType.BREAKFAST).time(6, 30).duration(30).build());
            repo.write(new MealSchedule.Builder(day, MealType.LUNCH).time(12, 0).duration(45).build());
            repo.write(new MealSchedule.Builder(day, MealType.DINNER).time(18, 0).duration(30).build());
        }
        // Wochenende (Sa-So): später aufstehen
        for (DayOfWeek day : List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            repo.write(new MealSchedule.Builder(day, MealType.BREAKFAST).time(9, 0).duration(30).build());
            repo.write(new MealSchedule.Builder(day, MealType.LUNCH).time(12, 30).duration(45).build());
            repo.write(new MealSchedule.Builder(day, MealType.DINNER).time(18, 30).duration(30).build());
        }

        // === ZUTATEN (~200 Stück) ===
        seedIngredients(db);

        // === BEISPIEL-REZEPTE ===
        Recipe r1 = new Recipe.Builder("Haferflocken mit Obst", MealType.BREAKFAST)
            .description("Schnelles gesundes Frühstück")
            .servings(2).prepTime(5).cookTime(0)
            .requiresCooking(false).prepEffort(Recipe.PrepEffort.MINIMAL)
            .tags("schnell,vegetarisch,gesund").favorite()
            .addIngredient(2L, "Haferflocken", 100, "g")
            .addIngredient(52L, "Milch", 200, "ml")
            .addIngredient(37L, "Banane", 1, "Stück")
            .addIngredient(36L, "Apfel", 1, "Stück")
            .build();
        r1.totalCalories = 450;
        r1.totalProtein = 150;
        r1.totalCarbs = 750;
        r1.totalFat = 80;
        repo.write(r1);

        Recipe r2 = new Recipe.Builder("Hähnchen mit Gemüse", MealType.DINNER)
            .description("Proteinreiches Abendessen")
            .servings(2).prepTime(20).cookTime(30)
            .requiresCooking(true).prepEffort(Recipe.PrepEffort.SIGNIFICANT)
            .servingsPerCooking(4).shelfLife(3)
            .tags("proteinreich,lowcarb")
            .addIngredient(65L, "Hähnchenbrust", 400, "g")
            .addIngredient(20L, "Brokkoli", 200, "g")
            .addIngredient(22L, "Paprika", 150, "g")
            .addIngredient(85L, "Olivenöl", 20, "ml")
            .build();
        r2.totalCalories = 680;
        r2.totalProtein = 720;
        r2.totalCarbs = 120;
        r2.totalFat = 280;
        repo.write(r2);

        Recipe r3 = new Recipe.Builder("Spaghetti Bolognese", MealType.LUNCH)
            .description("Italienischer Klassiker")
            .servings(4).prepTime(15).cookTime(45)
            .requiresCooking(true).prepEffort(Recipe.PrepEffort.SIGNIFICANT)
            .servingsPerCooking(4).shelfLife(3)
            .tags("italienisch,comfort")
            .addIngredient(5L, "Spaghetti", 400, "g")
            .addIngredient(66L, "Rinderhack", 400, "g")
            .addIngredient(17L, "Tomaten", 400, "g")
            .addIngredient(26L, "Zwiebeln", 100, "g")
            .build();
        r3.totalCalories = 2200;
        r3.totalProtein = 960;
        r3.totalCarbs = 2800;
        r3.totalFat = 680;
        repo.write(r3);

        Recipe r4 = new Recipe.Builder("Joghurt mit Nüssen", MealType.SNACK)
            .description("Schneller Snack")
            .servings(1).prepTime(2).cookTime(0)
            .requiresCooking(false).prepEffort(Recipe.PrepEffort.NONE)
            .tags("schnell,proteinreich")
            .addIngredient(53L, "Joghurt natur", 150, "g")
            .addIngredient(89L, "Mandeln", 30, "g")
            .build();
        r4.totalCalories = 280;
        r4.totalProtein = 140;
        r4.totalCarbs = 120;
        r4.totalFat = 180;
        repo.write(r4);

        Recipe r5 = new Recipe.Builder("Rührei mit Toast", MealType.BREAKFAST)
            .description("Klassisches Frühstück")
            .servings(2).prepTime(5).cookTime(5)
            .requiresCooking(true).prepEffort(Recipe.PrepEffort.MINIMAL)
            .tags("proteinreich,schnell")
            .addIngredient(82L, "Eier", 4, "Stück")
            .addIngredient(1L, "Toastbrot", 4, "Scheibe")
            .addIngredient(86L, "Butter", 20, "g")
            .build();
        r5.totalCalories = 520;
        r5.totalProtein = 280;
        r5.totalCarbs = 320;
        r5.totalFat = 300;
        repo.write(r5);
    }

    /**
     * Seeded ~200 Zutaten mit Nährwerten (pro 100g).
     * Format: name, foodGroup, unit, gramsPerUnit, calories, protein, carbs, fat, fiber
     */
    private void seedIngredients(SQLiteDatabase db) {
        // GRAIN (Getreide) - IDs 1-15
        seedIng(db, "Toastbrot", "GRAIN", "Scheibe", 30, 265, 90, 490, 35, 25);
        seedIng(db, "Haferflocken", "GRAIN", "g", 100, 372, 130, 590, 70, 100);
        seedIng(db, "Vollkornbrot", "GRAIN", "Scheibe", 50, 220, 90, 400, 20, 60);
        seedIng(db, "Reis (weiß)", "GRAIN", "g", 100, 350, 70, 780, 10, 10);
        seedIng(db, "Spaghetti", "GRAIN", "g", 100, 350, 120, 700, 20, 30);
        seedIng(db, "Penne", "GRAIN", "g", 100, 350, 120, 700, 20, 30);
        seedIng(db, "Müsli", "GRAIN", "g", 100, 380, 100, 650, 80, 80);
        seedIng(db, "Couscous", "GRAIN", "g", 100, 376, 127, 773, 6, 50);
        seedIng(db, "Quinoa", "GRAIN", "g", 100, 368, 143, 642, 61, 70);
        seedIng(db, "Bulgur", "GRAIN", "g", 100, 342, 123, 758, 13, 126);
        seedIng(db, "Cornflakes", "GRAIN", "g", 100, 378, 70, 840, 10, 30);
        seedIng(db, "Knäckebrot", "GRAIN", "Stück", 12, 334, 103, 650, 14, 145);
        seedIng(db, "Bagel", "GRAIN", "Stück", 90, 275, 106, 530, 16, 23);
        seedIng(db, "Croissant", "GRAIN", "Stück", 60, 406, 82, 458, 210, 24);
        seedIng(db, "Brötchen", "GRAIN", "Stück", 50, 280, 90, 530, 30, 30);

        // POTATO (Kartoffeln) - IDs 16
        seedIng(db, "Kartoffeln", "POTATO", "g", 100, 77, 20, 170, 1, 22);

        // VEGETABLE (Gemüse) - IDs 17-35 - alle perishable
        seedIng(db, "Tomaten", "VEGETABLE", "g", 100, 18, 9, 39, 2, 12, false, true);
        seedIng(db, "Gurke", "VEGETABLE", "g", 100, 12, 6, 18, 1, 5, false, true);
        seedIng(db, "Salat (Kopfsalat)", "VEGETABLE", "g", 100, 11, 13, 11, 2, 15, false, true);
        seedIng(db, "Brokkoli", "VEGETABLE", "g", 100, 34, 28, 70, 4, 26, false, true);
        seedIng(db, "Karotten", "VEGETABLE", "g", 100, 41, 9, 96, 2, 28, false, true);
        seedIng(db, "Paprika", "VEGETABLE", "g", 100, 26, 10, 49, 3, 17, false, true);
        seedIng(db, "Zucchini", "VEGETABLE", "g", 100, 17, 12, 31, 3, 10, false, true);
        seedIng(db, "Spinat", "VEGETABLE", "g", 100, 23, 29, 36, 4, 22, false, true);
        seedIng(db, "Blumenkohl", "VEGETABLE", "g", 100, 25, 19, 50, 3, 20, false, true);
        seedIng(db, "Zwiebeln", "VEGETABLE", "g", 100, 40, 11, 93, 1, 17, false, true);
        seedIng(db, "Knoblauch", "VEGETABLE", "g", 100, 149, 64, 331, 5, 21, false, true);
        seedIng(db, "Pilze (Champignons)", "VEGETABLE", "g", 100, 22, 31, 33, 3, 10, false, true);
        seedIng(db, "Aubergine", "VEGETABLE", "g", 100, 25, 10, 59, 2, 30, false, true);
        seedIng(db, "Bohnen (grün)", "VEGETABLE", "g", 100, 31, 18, 70, 1, 27, false, true);
        seedIng(db, "Erbsen", "VEGETABLE", "g", 100, 81, 54, 144, 4, 52, false, true);
        seedIng(db, "Mais", "VEGETABLE", "g", 100, 86, 33, 190, 12, 27, false, true);
        seedIng(db, "Sellerie", "VEGETABLE", "g", 100, 16, 7, 30, 2, 16, false, true);
        seedIng(db, "Lauch", "VEGETABLE", "g", 100, 61, 15, 143, 3, 18, false, true);
        seedIng(db, "Rotkohl", "VEGETABLE", "g", 100, 31, 14, 73, 1, 21, false, true);

        // FRUIT (Obst) - IDs 36-50 - Stück=wholeUnit, alle perishable
        seedIng(db, "Apfel", "FRUIT", "Stück", 180, 52, 3, 138, 2, 24, true, true);
        seedIng(db, "Banane", "FRUIT", "Stück", 120, 89, 11, 227, 3, 26, true, true);
        seedIng(db, "Orange", "FRUIT", "Stück", 180, 47, 9, 117, 1, 24, true, true);
        seedIng(db, "Weintrauben", "FRUIT", "g", 100, 69, 7, 181, 2, 9, false, true);
        seedIng(db, "Erdbeeren", "FRUIT", "g", 100, 32, 7, 76, 3, 20, false, true);
        seedIng(db, "Blaubeeren", "FRUIT", "g", 100, 57, 7, 144, 3, 24, false, true);
        seedIng(db, "Himbeeren", "FRUIT", "g", 100, 52, 12, 118, 7, 65, false, true);
        seedIng(db, "Birne", "FRUIT", "Stück", 180, 57, 4, 152, 1, 31, true, true);
        seedIng(db, "Pfirsich", "FRUIT", "Stück", 150, 39, 9, 96, 3, 15, true, true);
        seedIng(db, "Mango", "FRUIT", "Stück", 300, 60, 8, 150, 4, 16, true, true);
        seedIng(db, "Ananas", "FRUIT", "g", 100, 50, 5, 132, 1, 14, false, true);
        seedIng(db, "Wassermelone", "FRUIT", "g", 100, 30, 6, 76, 2, 4, false, true);
        seedIng(db, "Kiwi", "FRUIT", "Stück", 75, 61, 11, 148, 5, 30, true, true);
        seedIng(db, "Zitrone", "FRUIT", "Stück", 60, 29, 11, 93, 3, 28, true, true);
        seedIng(db, "Grapefruit", "FRUIT", "Stück", 300, 42, 8, 107, 1, 16, true, true);

        // DAIRY (Milchprodukte) - IDs 51-60 - alle perishable
        seedIng(db, "Milch (3,5%)", "DAIRY", "ml", 100, 64, 33, 48, 36, 0, false, true);
        seedIng(db, "Milch (1,5%)", "DAIRY", "ml", 100, 47, 34, 49, 15, 0, false, true);
        seedIng(db, "Joghurt natur", "DAIRY", "g", 100, 61, 35, 46, 32, 0, false, true);
        seedIng(db, "Käse (Gouda)", "DAIRY", "g", 100, 356, 247, 0, 274, 0, false, true);
        seedIng(db, "Käse (Mozzarella)", "DAIRY", "g", 100, 280, 222, 11, 212, 0, false, true);
        seedIng(db, "Quark (Magerquark)", "DAIRY", "g", 100, 68, 123, 40, 3, 0, false, true);
        seedIng(db, "Sahne", "DAIRY", "ml", 100, 292, 24, 30, 300, 0, false, true);
        seedIng(db, "Frischkäse", "DAIRY", "g", 100, 265, 57, 27, 260, 0, false, true);
        seedIng(db, "Parmesan", "DAIRY", "g", 100, 431, 381, 0, 290, 0, false, true);
        seedIng(db, "Feta", "DAIRY", "g", 100, 264, 142, 41, 213, 0, false, true);

        // Weitere DAIRY - alle perishable
        seedIng(db, "Buttermilch", "DAIRY", "ml", 100, 37, 34, 40, 6, 0, false, true);
        seedIng(db, "Skyr", "DAIRY", "g", 100, 63, 110, 40, 2, 0, false, true);
        seedIng(db, "Hüttenkäse", "DAIRY", "g", 100, 98, 113, 33, 43, 0, false, true);
        seedIng(db, "Ricotta", "DAIRY", "g", 100, 174, 113, 30, 130, 0, false, true);

        // MEAT (Fleisch) - IDs 65-80 - alle perishable
        seedIng(db, "Hähnchenbrust", "MEAT", "g", 100, 165, 310, 0, 36, 0, false, true);
        seedIng(db, "Rinderhack", "MEAT", "g", 100, 212, 196, 0, 150, 0, false, true);
        seedIng(db, "Schweineschnitzel", "MEAT", "g", 100, 171, 223, 0, 86, 0, false, true);
        seedIng(db, "Hackfleisch (gemischt)", "MEAT", "g", 100, 224, 181, 0, 170, 0, false, true);
        seedIng(db, "Rinderfilet", "MEAT", "g", 100, 188, 269, 0, 87, 0, false, true);
        seedIng(db, "Schweinefilet", "MEAT", "g", 100, 143, 223, 0, 53, 0, false, true);
        seedIng(db, "Hähnchenschenkel", "MEAT", "g", 100, 211, 180, 0, 153, 0, false, true);
        seedIng(db, "Speck", "MEAT", "g", 100, 458, 116, 14, 450, 0, false, true);
        seedIng(db, "Schinken (gekocht)", "MEAT", "g", 100, 107, 178, 13, 34, 0, false, true);
        seedIng(db, "Salami", "MEAT", "g", 100, 336, 210, 15, 270, 0, false, true);
        seedIng(db, "Bratwurst", "MEAT", "g", 100, 274, 126, 10, 240, 0, false, true);
        seedIng(db, "Wiener Würstchen", "MEAT", "g", 100, 230, 120, 10, 200, 0, false, true);
        seedIng(db, "Putenbrust", "MEAT", "g", 100, 135, 290, 0, 20, 0, false, true);
        seedIng(db, "Lammkeule", "MEAT", "g", 100, 243, 200, 0, 175, 0, false, true);
        seedIng(db, "Ente", "MEAT", "g", 100, 337, 190, 0, 285, 0, false, true);
        seedIng(db, "Leberwurst", "MEAT", "g", 100, 326, 137, 26, 288, 0, false, true);

        // EGG (Eier) - IDs 81-82 - wholeUnit + perishable
        seedIng(db, "Eier", "EGG", "Stück", 60, 155, 129, 11, 110, 0, true, true);
        seedIng(db, "Eigelb", "EGG", "Stück", 17, 322, 162, 36, 265, 0, true, true);

        // FISH (Fisch) - IDs 83-92 - alle perishable (außer Dosen)
        seedIng(db, "Lachs", "FISH", "g", 100, 208, 201, 0, 131, 0, false, true);
        seedIng(db, "Thunfisch (Dose)", "FISH", "g", 100, 116, 260, 0, 10, 0, false, false);  // Dose = nicht verderblich
        seedIng(db, "Kabeljau", "FISH", "g", 100, 82, 180, 0, 7, 0, false, true);
        seedIng(db, "Forelle", "FISH", "g", 100, 135, 196, 0, 57, 0, false, true);
        seedIng(db, "Garnelen", "FISH", "g", 100, 85, 183, 0, 10, 0, false, true);
        seedIng(db, "Hering", "FISH", "g", 100, 158, 178, 0, 90, 0, false, true);
        seedIng(db, "Makrele", "FISH", "g", 100, 205, 188, 0, 138, 0, false, true);
        seedIng(db, "Seelachs", "FISH", "g", 100, 81, 180, 0, 8, 0, false, true);
        seedIng(db, "Dorade", "FISH", "g", 100, 96, 189, 0, 20, 0, false, true);
        seedIng(db, "Sardinen", "FISH", "g", 100, 208, 248, 0, 115, 0, false, true);

        // FAT (Öle/Fette) - IDs 93-98
        seedIng(db, "Olivenöl", "FAT", "ml", 100, 884, 0, 0, 1000, 0);
        seedIng(db, "Butter", "FAT", "g", 100, 741, 7, 6, 830, 0);
        seedIng(db, "Rapsöl", "FAT", "ml", 100, 884, 0, 0, 1000, 0);
        seedIng(db, "Sonnenblumenöl", "FAT", "ml", 100, 884, 0, 0, 1000, 0);
        seedIng(db, "Kokosfett", "FAT", "g", 100, 892, 0, 0, 992, 0);
        seedIng(db, "Margarine", "FAT", "g", 100, 717, 2, 7, 800, 0);

        // LEGUME (Hülsenfrüchte) - IDs 99-106
        seedIng(db, "Linsen (rot)", "LEGUME", "g", 100, 116, 90, 200, 4, 80);
        seedIng(db, "Linsen (braun)", "LEGUME", "g", 100, 116, 90, 200, 4, 80);
        seedIng(db, "Kichererbsen", "LEGUME", "g", 100, 364, 190, 610, 60, 170);
        seedIng(db, "Bohnen (weiß)", "LEGUME", "g", 100, 333, 212, 600, 12, 150);
        seedIng(db, "Kidneybohnen", "LEGUME", "g", 100, 127, 87, 229, 5, 67);
        seedIng(db, "Edamame", "LEGUME", "g", 100, 122, 119, 98, 52, 51);
        seedIng(db, "Tofu", "LEGUME", "g", 100, 76, 82, 19, 43, 5);
        seedIng(db, "Tempeh", "LEGUME", "g", 100, 195, 184, 100, 110, 0);

        // NUT (Nüsse) - IDs 107-116
        seedIng(db, "Mandeln", "NUT", "g", 100, 576, 212, 217, 494, 122);
        seedIng(db, "Walnüsse", "NUT", "g", 100, 654, 152, 138, 654, 67);
        seedIng(db, "Cashews", "NUT", "g", 100, 553, 183, 304, 439, 33);
        seedIng(db, "Haselnüsse", "NUT", "g", 100, 628, 150, 170, 609, 97);
        seedIng(db, "Erdnüsse", "NUT", "g", 100, 567, 259, 163, 492, 85);
        seedIng(db, "Pistazien", "NUT", "g", 100, 560, 201, 276, 451, 103);
        seedIng(db, "Sonnenblumenkerne", "NUT", "g", 100, 584, 207, 200, 512, 87);
        seedIng(db, "Kürbiskerne", "NUT", "g", 100, 559, 301, 108, 491, 60);
        seedIng(db, "Chiasamen", "NUT", "g", 100, 486, 170, 421, 308, 343);
        seedIng(db, "Leinsamen", "NUT", "g", 100, 534, 183, 289, 422, 274);

        // OTHER (Sonstiges) - IDs 117+
        seedIng(db, "Salz", "OTHER", "g", 100, 0, 0, 0, 0, 0);
        seedIng(db, "Pfeffer", "OTHER", "g", 100, 251, 104, 640, 33, 253);
        seedIng(db, "Tomatensauce", "OTHER", "g", 100, 29, 15, 64, 1, 11);
        seedIng(db, "Ketchup", "OTHER", "g", 100, 112, 12, 267, 3, 4);
        seedIng(db, "Senf", "OTHER", "g", 100, 66, 46, 50, 34, 36);
        seedIng(db, "Mayonnaise", "OTHER", "g", 100, 680, 10, 10, 750, 0);
        seedIng(db, "Sojasauce", "OTHER", "ml", 100, 53, 81, 47, 0, 0);
        seedIng(db, "Honig", "OTHER", "g", 100, 304, 3, 822, 0, 2);
        seedIng(db, "Zucker", "OTHER", "g", 100, 387, 0, 1000, 0, 0);
        seedIng(db, "Mehl", "OTHER", "g", 100, 364, 100, 763, 10, 27);
        seedIng(db, "Backpulver", "OTHER", "g", 100, 53, 0, 278, 0, 0);
        seedIng(db, "Hefe", "OTHER", "g", 100, 105, 122, 185, 18, 80);
        seedIng(db, "Brühe (Gemüse)", "OTHER", "ml", 100, 5, 3, 6, 1, 0);
        seedIng(db, "Brühe (Huhn)", "OTHER", "ml", 100, 10, 10, 10, 2, 0);
        seedIng(db, "Kokosmilch", "OTHER", "ml", 100, 197, 22, 27, 210, 0);
        seedIng(db, "Passierte Tomaten", "OTHER", "g", 100, 24, 11, 47, 2, 14);
        seedIng(db, "Tomatenmark", "OTHER", "g", 100, 82, 43, 186, 5, 43);
        seedIng(db, "Sahnesoße", "OTHER", "g", 100, 147, 23, 47, 130, 0);
        seedIng(db, "Pesto", "OTHER", "g", 100, 364, 47, 50, 360, 10);
        seedIng(db, "Basilikum (frisch)", "OTHER", "g", 100, 23, 31, 27, 6, 16);
        seedIng(db, "Petersilie", "OTHER", "g", 100, 36, 30, 63, 8, 33);
        seedIng(db, "Oregano", "OTHER", "g", 100, 265, 90, 689, 43, 429);
        seedIng(db, "Thymian", "OTHER", "g", 100, 101, 57, 241, 16, 140);
        seedIng(db, "Rosmarin", "OTHER", "g", 100, 131, 33, 208, 59, 143);
        seedIng(db, "Ingwer", "OTHER", "g", 100, 80, 18, 179, 8, 20);
        seedIng(db, "Curry", "OTHER", "g", 100, 325, 141, 584, 140, 336);
        seedIng(db, "Paprikapulver", "OTHER", "g", 100, 282, 143, 540, 130, 210);
        seedIng(db, "Zimt", "OTHER", "g", 100, 247, 40, 808, 12, 533);
    }

    /** Erzeugt 7 PrefSlots (Mo-So) zur selben Uhrzeit — fuer Testdaten. */
    private static List<TrackedItem.PrefSlot> allDays(String time) {
        LocalTime t = LocalTime.parse(time);
        return Arrays.stream(DayOfWeek.values())
            .map(day -> TrackedItem.PrefSlot.weekly(day, t))
            .toList();
    }

    /**
     * Hilfsmethode zum Einfügen einer Zutat (ohne Einkaufs-Eigenschaften).
     */
    private void seedIng(SQLiteDatabase db, String name, String foodGroup, String unit, int gramsPerUnit,
                         int calories, int protein, int carbs, int fat, int fiber) {
        seedIng(db, name, foodGroup, unit, gramsPerUnit, calories, protein, carbs, fat, fiber, false, false);
    }

    /**
     * Hilfsmethode zum Einfügen einer Zutat mit Einkaufs-Eigenschaften.
     * @param isWholeUnit true = nur ganze Einheiten kaufbar (Eier, Paprika, Äpfel)
     * @param isPerishable true = verderblich (Fleisch, Milch, frisches Obst/Gemüse)
     */
    private void seedIng(SQLiteDatabase db, String name, String foodGroup, String unit, int gramsPerUnit,
                         int calories, int protein, int carbs, int fat, int fiber,
                         boolean isWholeUnit, boolean isPerishable) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("food_group", foodGroup);
        cv.put("default_unit", unit);
        cv.put("grams_per_unit", gramsPerUnit);
        cv.put("calories_per_100", calories);
        cv.put("protein_per_100", protein);
        cv.put("carbs_per_100", carbs);
        cv.put("fat_per_100", fat);
        cv.put("fiber_per_100", fiber);
        cv.put("shelf_life_days", 0);
        cv.put("requires_refrigeration", 0);
        cv.put("is_whole_unit", isWholeUnit ? 1 : 0);
        cv.put("is_perishable", isPerishable ? 1 : 0);
        db.insert("ingredients", null, cv);
    }
}
