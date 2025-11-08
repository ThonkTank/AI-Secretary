# AI Secretary - Development Roadmap

**Projekt:** AI Secretary - Native Android App
**Feature Suite 1:** Taskmaster
**Letzte Aktualisierung:** 8. November 2025
**Design Dokument:** [DESIGN.md](./DESIGN.md) - Widget-First, Streak-Focused UX

---

## 🎯 Vision

Ein umfassendes Alltags-Planungstool mit intelligenter Aufgabenverwaltung, Tracking und automatischer Tagesplanung. Siehe [CLAUDE.md](./CLAUDE.md) für vollständige Feature-Spezifikation.

---

## ✅ Aktueller Status

### Erreichte Meilensteine

**Phase 0: Projekt-Setup** ✅ Abgeschlossen
- [x] Native Android Projekt-Struktur erstellt
- [x] Git-Repository initialisiert
- [x] CLAUDE.md mit vollständiger Taskmaster-Spezifikation
- [x] README.md und Projekt-Dokumentation
- [x] Build-Infrastruktur (build.sh)
- [x] Entwicklungsumgebung in Termux eingerichtet
- [x] DESIGN.md mit umfassender UX/UI-Vision erstellt

**Phase 1: Grundlagen & Datenbank** ✅ Abgeschlossen
- [x] Task-Datenmodell (TaskEntity.java) mit ALLEN Taskmaster-Features
  - Basis-Properties (id, title, description, priority, completed)
  - Tracking-Properties (createdAt, completedAt, dueAt, completionCount, lastCompletedAt)
  - Recurrence-Properties (isRecurring, recurrenceType, recurrenceX/Y, startDate, endDate)
  - Performance-Properties (averageCompletionTime, averageDifficulty)
  - Streak-Properties (currentStreak, longestStreak, streakLastUpdated)
  - Scheduling-Properties (preferredTimeOfDay, preferredHour)
  - Chain-Properties (chainId, chainOrder)
  - Category support
- [x] **Design System v1.0** vollständig implementiert
  - colors.xml: Komplette Farbpalette aus DESIGN.md
  - styles.xml: Typografie, Buttons, Cards, Spacing
  - dimens.xml: Spacing-System (8dp/16dp/24dp)
- [x] **TaskDao** (SQLite Datenbank)
  - Vollständiges CRUD
  - Queries: getAll, getTodayTasks, getOverdueTasks, getTasksWithStreaks
  - Completion counts für Statistiken
- [x] **TaskRepository** implementiert
  - Singleton-Pattern
  - Business-Logic für Task-Completion
  - Automatische Streak-Berechnung
  - Stats-Methoden (heute, 7 Tage, Durchschnitt)
  - Recurring-Task Reset-Logic
- [x] MainActivity aktualisiert
  - Verwendet jetzt TaskRepository (echte Datenbank)
  - Sample-Tasks mit Streaks beim ersten Start
  - Task-Anzeige mit Design-System-Farben
  - Prioritäts-Sterne (⭐⭐⭐)
  - Streak-Badges (🔥 Streak: X)
  - Überfälligkeits-Warnungen (⚠️ OVERDUE)
  - Stats: Heute-Counter + Höchste Streak
- [x] **RecyclerView + TaskAdapter**
  - RecyclerView mit LinearLayoutManager
  - TaskAdapter extends RecyclerView.Adapter
  - ViewHolder-Pattern für Performance
- [x] **Swipe-Gesten** (Right: Complete, Left: Delete)
  - SwipeHelper mit ItemTouchHelper
  - Visuelle Feedback (Grün/Rot)
  - Bestätigungsdialog beim Löschen
- [x] **Verbessertes Task-Item-Layout** (list_item_task.xml)
  - Prioritäts-Sterne, Beschreibung, Due-Date
  - Streak-Badge, Überdue-Warnung
  - Quick-Actions (Edit/Delete) on long press

**Phase 2: Task-Erstellung & -Verwaltung** 🟢 67% abgeschlossen
- [x] **AddTaskActivity mit Tab-Layout** (Phase 2.1)
  - Tab 1: Basis (Titel, Beschreibung, Priorität, Fälligkeit)
  - Tab 2: Wiederholung (x pro y, alle x y, geplant)
  - Tab 3: Details (Dauer, Zeit, Kategorie)
  - Validierung (Titel erforderlich)
  - Smart-Defaults (Priorität 2, Heute, Einmalig)
  - TaskPagerAdapter für ViewPager2
  - 3 Fragments (TaskBasisFragment, TaskRecurrenceFragment, TaskDetailsFragment)
- [x] **Task-Bearbeitung** (Phase 2.2)
  - AddTaskActivity erweitert um Edit-Modus (EXTRA_TASK_ID)
  - Fragment Setter-Methoden für Daten-Vorausfüllung
  - populateFragments() auto-befüllt alle Tabs
  - saveTask() unterscheidet Create vs Update
  - MainActivity: onTaskClick + onTaskEditClick → Edit-Dialog
  - Dynamischer Header + Button-Text
- [x] **Task-Löschung** bereits in Phase 1.3 implementiert
  - Swipe-Left → Delete mit Bestätigung
  - Quick-Actions → Delete-Button
- [ ] Wiederkehrende Tasks - Erweitert (Phase 2.3) ⬅️ Nächster Schritt

**Phase 3: Tracking & Performance-Daten** 🟢 67% abgeschlossen
- [x] **Task-Completion Dialog** (Phase 3.1)
  - CompletionDialog mit Zeit/Schwierigkeits-Input
  - Quick-Select Buttons (5/15/30 Min, 1 Std)
  - 5-Sterne-Rating mit Labels
  - Streak-Feedback mit Meilenstein-Erkennung
  - Überspringen-Button für Quick-Complete
  - MainActivity-Integration (Checkbox + Swipe)
- [x] **Erledigungs-Zeit Tracking** (Phase 3.2)
  - CompletionHistory Datenbank-Tabelle
  - CompletionHistoryDao mit Analytics-Methoden
  - Automatische History-Erfassung bei jeder Completion
  - Durchschnitts-Berechnung aus gesamter Historie
  - PreferredTimeOfDay Auto-Detection
- [ ] Zeitpunkt-Analyse (Phase 3.3)

**Phase 4: Statistiken & Motivation** 🟢 25% abgeschlossen
- [x] **Streak-Berechnung** (Phase 4.1)
  - StreakManager Utility-Klasse
  - Grace Period Support für flexible Habits
  - Streak-at-Risk Detection & Warnings
  - 6-Level Streak-System (Anfänger bis Meister)
  - Emoji-Visualisierung (🔥 bis 🔥🔥🔥🔥🔥)
  - Milestone-Tracking (10, 25, 50, 100, 250, 500, 1000)
  - MainActivity: Enhanced Streak Display mit Warnings
- [ ] Statistik-Dashboard (Phase 4.2) ⬅️ Nächster Schritt

**Gesamt-Fortschritt:** ~60% der Taskmaster Feature Suite

---

## 🗺️ Detaillierte Roadmap

### Phase 1: Grundlagen & Datenbank (Priorität: HOCH)
**Ziel:** Persistente Datenspeicherung und solide Architektur

#### 1.1 Datenbank-Integration ✅ ABGESCHLOSSEN
- [x] SQLite-basierte Implementierung (statt Room wegen Termux)
- [x] TaskEntity erstellt (Vollständiges Datenbank-Modell)
- [x] TaskDao erstellt (SQLiteOpenHelper mit allen Queries)
  - `insert(Task)` - Task hinzufügen ✅
  - `update(Task)` - Task aktualisieren ✅
  - `delete(Task)` - Task löschen ✅
  - `getAll()` - Alle Tasks laden ✅
  - `getById(id)` - Task nach ID ✅
  - `getTasksForToday()` - Heutige + überfällige Tasks ✅
  - `getOverdueTasks()` - Überfällige Tasks ✅
  - `getTasksWithStreaks()` - Tasks mit Streaks ✅
  - `getCompletedCount()` - Erledigte Tasks für Zeitraum ✅
- [x] TaskRepository erstellt (Repository-Pattern)
  - Singleton-Pattern ✅
  - Complete/Uncomplete mit Streak-Update ✅
  - Stats-Methoden (heute, 7 Tage, Durchschnitt) ✅
  - Recurring-Task Logic ✅

**Dateien erstellt:** 3 neue Java-Klassen (TaskEntity, TaskDao, TaskRepository)
**Komplexität:** Mittel
**Status:** ✅ Vollständig implementiert

#### 1.2 Design-System Implementation ✅ ABGESCHLOSSEN
- [x] colors.xml mit vollständiger Farbpalette aus DESIGN.md
  - Primary/Accent Colors ✅
  - Status Colors (Overdue, Completed, Due Today) ✅
  - Priority Colors (1-4) ✅
  - Streak Colors ✅
  - Widget/Card/Interactive Colors ✅
- [x] styles.xml mit Typografie und Komponenten-Styles
  - Text Appearances (Title, Subtitle, Body, Caption) ✅
  - Task-Specific Styles ✅
  - Streak Number Style ✅
  - Button/Card Styles ✅
- [x] dimens.xml für Spacing-System
  - Spacing (8dp/16dp/24dp) ✅
  - Padding (Cards, Widgets, Buttons) ✅
  - Elevation (2dp/4dp/8dp) ✅
  - Corner Radius ✅

**Dateien aktualisiert/erstellt:** 3 Resource-Dateien
**Status:** ✅ Design System v1.0 komplett

#### 1.3 Verbesserte UI (gemäß DESIGN.md) ✅ ABGESCHLOSSEN
- [x] **Design-System implementieren**
  - Farbpalette aus DESIGN.md in colors.xml ✅
  - Typografie-Styles in styles.xml ✅
  - Icon-Set zusammenstellen ✅
- [x] **Task-Item-Layout** (list_item_task.xml)
  - Titel + Beschreibung ✅
  - Prioritäts-Sterne (⭐⭐⭐) ✅
  - Completion-Checkbox rechts ✅
  - Due-Date mit Icon ✅
  - Überfälligkeits-Warnung (⚠️ rot) ✅
  - Quick-Actions: [✓] [✎] [🗑️] ✅
  - Streak-Badge für wiederkehrende Tasks ✅
- [x] RecyclerView für Task-Liste ✅
- [x] TaskAdapter mit ViewHolder ✅
- [x] **Swipe-Gesten** (Right: Complete, Left: Delete) ✅

**Dateien erstellt:** 4 neue Dateien (list_item_task.xml, TaskAdapter.java, SwipeHelper.java, activity_main.xml aktualisiert)
**Komplexität:** Mittel
**Design-Referenz:** DESIGN.md - Main App Section
**Status:** ✅ Vollständig implementiert

---

### Phase 2: Task-Erstellung & -Verwaltung (Priorität: HOCH)
**Ziel:** Vollständige CRUD-Funktionalität für Tasks

#### 2.1 Task-Erstellungs-Dialog (gemäß DESIGN.md) ✅ ABGESCHLOSSEN
- [x] **AddTaskActivity mit Tab-Layout** erstellen ✅
- [x] **Tab 1: Basis** (fragment_task_basis.xml)
  - Titel-Input ✅
  - Beschreibung-Input (mehrzeilig, optional) ✅
  - Priorität-Auswahl (⭐ Buttons 1-4) ✅
  - Fälligkeit (Quick-Buttons: Heute, Morgen, Datum...) ✅
  - DatePickerDialog für Custom-Datum ✅
- [x] **Tab 2: Wiederholung** (fragment_task_recurrence.xml)
  - Radio-Buttons: Einmalig / x pro y / alle x y / Geplant ✅
  - Dynamische Inputs basierend auf Auswahl ✅
  - x pro y Container mit Number-Input + Spinner ✅
  - Alle x y Container mit Interval-Input + Spinner ✅
  - Info-Text zeigt aktuelle Konfiguration ✅
- [x] **Tab 3: Details** (fragment_task_details.xml)
  - Geschätzte Dauer (Quick-Select: 5/15/30 Min, 1/2 Std) ✅
  - Bevorzugte Zeit (🌅 Morgen, ☀️ Mittag, 🌙 Abend) ✅
  - Kategorie (Optional) ✅
  - Verkettungs-Hinweis (Phase 6) ✅
- [x] Validierung (Titel erforderlich) ✅
- [x] Smart-Defaults: Heute, Priorität 2, Einmalig ✅
- [x] Speichern in Datenbank über Repository ✅
- [x] TaskPagerAdapter für ViewPager2 ✅
- [x] 3 Fragment-Klassen (TaskBasisFragment, TaskRecurrenceFragment, TaskDetailsFragment) ✅
- [x] MainActivity-Integration (AddTaskButton öffnet Dialog) ✅
- [x] AndroidManifest-Registrierung ✅

**Dateien erstellt:** 11 neue Dateien (4 Layouts, 4 Fragments, 1 Adapter, 1 Activity, 1 Manifest-Update)
**Komplexität:** Mittel-Hoch
**Design-Referenz:** DESIGN.md - Add/Edit Task Screen
**Status:** ✅ Vollständig implementiert

#### 2.2 Task-Bearbeitung & -Löschung ✅ ABGESCHLOSSEN
- [x] Edit-Modus in AddTaskActivity implementiert ✅
  - EXTRA_TASK_ID Intent-Parameter ✅
  - isEditMode, editingTaskId, editingTask Variablen ✅
  - Dynamischer Header ("Neue Aufgabe" vs "Aufgabe bearbeiten") ✅
  - Dynamischer Save-Button ("Speichern" vs "Aktualisieren") ✅
- [x] Fragment Setter-Methoden für Daten-Vorausfüllung ✅
  - TaskBasisFragment: setTitle, setDescription, setPriority, setDueDate ✅
  - TaskRecurrenceFragment: setRecurrenceType, setRecurrenceX, setRecurrenceY ✅
  - TaskDetailsFragment: setEstimatedDuration, setPreferredTimeOfDay, setCategory ✅
- [x] populateFragments() Methode ✅
  - Automatisches Befüllen aller Tabs mit vorhandenen Daten ✅
  - Timing via viewPager.post() ✅
- [x] saveTask() erweitert ✅
  - Unterscheidung zwischen Create und Update ✅
  - Toast: "Aufgabe erstellt!" vs "Aufgabe aktualisiert!" ✅
- [x] MainActivity-Integration ✅
  - onTaskClick() → öffnet Edit-Dialog ✅
  - onTaskEditClick() → öffnet Edit-Dialog ✅
  - openEditDialog(TaskEntity) Methode ✅
- [x] Löschen-Funktionalität ✅ (bereits in Phase 1.3)
  - Swipe-Left → Delete mit Bestätigung ✅
  - Quick-Actions Delete-Button ✅
  - AlertDialog mit Cancel-Handling ✅

**Dateien aktualisiert:** 6 Dateien (+228 Zeilen Code)
**Komplexität:** Mittel
**Status:** ✅ Vollständig implementiert

#### 2.3 Wiederkehrende Tasks - Basis
- [ ] UI für Recurrence-Konfiguration
  - Task-Typ Auswahl: "Einmalig", "x pro y", "alle x y", "Geplant"
  - x/y Input-Felder (z.B. "3 mal pro Woche")
  - Zeiteinheit-Auswahl (Tag, Woche, Monat)
- [ ] RecurrenceManager Klasse
  - `calculateNextDueDate(Task)` - Berechne nächstes Fälligkeitsdatum
  - `shouldResetTask(Task)` - Prüfe ob Task zurückgesetzt werden soll
  - `resetTask(Task)` - Setze Task auf "unerledigt" zurück
- [ ] Hintergrund-Service für automatisches Zurücksetzen

**Geschätzte Dateien:** 3-4 neue Dateien
**Komplexität:** Hoch

---

### Phase 3: Tracking & Performance-Daten (Priorität: MITTEL)
**Ziel:** Datenerfassung für intelligente Features

#### 3.1 Task-Completion Dialog (gemäß DESIGN.md) ✅ ABGESCHLOSSEN
- [x] **CompletionDialog** erstellen (dialog_completion.xml) ✅
  - Header mit Task-Titel ✅
  - Dynamischer Titel: "Aufgabe abgeschlossen!" / "🎉 Meilenstein erreicht!" ✅
- [x] **Zeit-Input mit Quick-Select** ✅
  - Buttons: 5 Min, 15 Min, 30 Min, 1 Std ✅
  - Visual Feedback (Button-Highlighting) ✅
  - Selected Time Display ✅
- [x] **Schwierigkeits-Input** ✅
  - 5-Sterne-Rating (☆/★ Icons) ✅
  - Labels: "Sehr einfach" bis "Sehr schwierig" ✅
  - Click-Handler für jede Sterne ✅
- [x] **Streak-Feedback** ✅
  - "🔥 Streak: X Tage!" Anzeige ✅
  - Meilenstein-Detection (10, 25, 50, 100) ✅
  - Spezielle Texte: "🔥🎉 25 Tage Streak! 🎉🔥" ✅
  - Toast-Nachrichten mit Meilenstein-Info ✅
- [x] **Überspringen-Button** für Quick-Complete ✅
  - onCompleteWithoutTracking() Callback ✅
  - Kein Tracking, direkt completeTask() ✅
- [x] Daten in Task speichern ✅
  - repository.completeTask(id, duration, difficulty) ✅
  - Update `averageCompletionTime` ✅
  - Update `averageDifficulty` ✅
  - Update `streak` ✅
- [x] MainActivity-Integration ✅
  - showCompletionDialog(TaskEntity) Methode ✅
  - CompletionListener Implementation ✅
  - onTaskCheckboxClick → Dialog ✅
  - onSwipeRight → Dialog ✅

**Dateien erstellt:** 3 Dateien (2 neu, 1 aktualisiert, +469 Zeilen)
**Komplexität:** Mittel
**Design-Referenz:** DESIGN.md - Task Completion Dialog
**Status:** ✅ Vollständig implementiert

#### 3.2 Erledigungs-Zeit Tracking ✅ ABGESCHLOSSEN
- [x] CompletionHistory Datenbank-Tabelle ✅
  - task_id (Foreign Key) ✅
  - completed_at (Timestamp) ✅
  - completion_time (Millisekunden) ✅
  - difficulty_rating (1-5) ✅
  - time_of_day (Uhrzeit - auto-extracted hour 0-23) ✅
- [x] CompletionHistoryDao ✅
  - insert(), getByTaskId(), getRecentByTaskId(), getByDateRange() ✅
  - getAverageCompletionTime(), getAverageDifficulty() ✅
  - getMostCommonTimeOfDay() für intelligente Zeitplanung ✅
- [x] Historien-Datenerfassung bei Task-Completion ✅
  - Bei completeTask(with tracking): volle Daten gespeichert ✅
  - Bei completeTask(simple): auch gespeichert mit Defaults ✅
- [x] Durchschnitts-Berechnung ✅
  - Averages berechnet aus gesamter History (nicht nur 2-Werte-Avg) ✅
  - preferredTimeOfDay auto-detected aus häufigster Uhrzeiten ✅
- [x] TaskDao & TaskRepository Integration ✅
  - Database Version 2 mit onUpgrade-Migration ✅
  - Repository-Methoden für History-Zugriff ✅

**Dateien erstellt:** 4 Dateien (2 neu, 2 aktualisiert, +494 Zeilen)
**Komplexität:** Mittel
**Status:** ✅ Vollständig implementiert

#### 3.3 Zeitpunkt-Analyse
- [ ] Analyse häufigster Erledigungs-Uhrzeiten
- [ ] `getPreferredTimeOfDay(Task)` Methode
- [ ] Visualisierung (optional)

**Geschätzte Dateien:** 1-2 neue Dateien
**Komplexität:** Mittel-Hoch

---

### Phase 4: Statistiken & Motivation (Priorität: HOCH)
**Ziel:** Streak-Tracking und motivierende Statistiken

#### 4.1 Streak-Berechnung ✅ ABGESCHLOSSEN
- [x] StreakManager Klasse ✅
  - `calculateStreak(Task)` - Berechne aktuelle Streak ✅
  - `updateStreak(Task)` - Update Streak bei Completion (mit Grace Period) ✅
  - `resetStreak(Task)` - Reset bei verpasstem Task (behält longestStreak) ✅
  - `isStreakAtRisk(Task)` - Erkenne gefährdete Streaks ✅
  - `getDaysUntilStreakExpires(Task)` - Zeit bis Streak-Verlust ✅
  - `getStreakLevel(streak)` - 6 Level-System (0-5) ✅
  - `getStreakEmoji(streak)` - Emoji-Visualisierung (🔥 bis 🔥🔥🔥🔥🔥) ✅
  - `isMilestoneReached()` - Milestone-Detection (10, 25, 50, 100, 250, 500, 1000) ✅
- [x] Streak-Persistierung in Datenbank ✅ (bereits in Phase 1)
- [x] Streak-Anzeige in UI (Feuer-Icon 🔥) ✅
  - MainActivity: Emoji-Level-Anzeige ✅
  - "At Risk" Warnung: "🔥🔥 25 (⚠️ 2 at risk)" ✅
- [x] TaskRepository Integration ✅
  - isStreakAtRisk(), getDaysUntilStreakExpires(), getTasksWithStreaksAtRisk() ✅
  - Refactored updateStreak() to use StreakManager ✅

**Dateien erstellt:** 3 Dateien (1 neu, 2 aktualisiert, +313 Zeilen)
**Komplexität:** Mittel-Hoch
**Status:** ✅ Vollständig implementiert

#### 4.2 Statistik-Dashboard (gemäß DESIGN.md)
- [ ] StatsManager Klasse
  - `getTasksCompletedToday()` - Heutige erledigte Tasks
  - `getTasksCompletedLast7Days()` - Letzte 7 Tage
  - `getAverageTasksPerDay()` - Durchschnitt/Tag
  - `getLongestStreak()` - Längste Streak
- [ ] **Erweiterte Stats-Anzeige in MainActivity**
  - Streak-Karten horizontal scrollbar (oberhalb der Task-Liste)
  - Jede Karte: Task-Name, Feuer-Emojis (🔥🔥🔥), Streak-Zahl
  - Progress-Bar für Tagesfortschritt (3/8 Tasks = 37%)
  - Karten-Layout für Statistiken
- [ ] **Statistics-Screen** (stats_activity.xml)
  - "Deine Leistung" Übersicht (Heute/Woche/Durchschnitt)
  - Liste aktiver Streaks mit Visualisierung
  - "Streaks in Gefahr"-Warnung (⚠️)
  - Historische Best-Streak
- [ ] Mini-Wochengraph für Aktivität

**Geschätzte Dateien:** 3-4 neue Dateien
**Komplexität:** Mittel
**Design-Referenz:** DESIGN.md - Main App & Statistics View

---

### Phase 4.5: Home-Screen Widget - Widget-First Interface (Priorität: SEHR HOCH)
**Ziel:** Widget als primäre Schnittstelle implementieren

**WICHTIG:** Widget-Entwicklung wurde vorgezogen, da Widget-First die Kern-Design-Philosophie ist (siehe DESIGN.md)

#### 4.5.1 Large Widget (4x4) - Haupt-Widget
- [ ] **TaskWidgetProvider** Klasse (bereits in Manifest)
- [ ] **Large Widget Layout** (widget_large.xml)
  - Header: Streak-Anzeige (🔥 X Tage) + Heute-Counter (X/Y)
  - "NÄCHSTE AUFGABE"-Bereich (prominent)
    - Task-Titel mit Prioritäts-Sternen
    - Zeit + Wiederholungs-Info
    - Streak-Badge wenn vorhanden
    - Checkbox zum Abhaken
  - "HEUTE"-Bereich (scrollbar)
    - Top 3-5 Tasks für heute
    - Jeweils: Titel, Zeit, Checkbox
    - Farbcodierung (Überfällig rot, etc.)
  - Footer: [➕ Neue Aufgabe] [📱 App öffnen]
- [ ] **Widget-Update Service**
  - Auto-Update alle 15 Minuten
  - Update bei Task-Änderungen
  - Update bei Mitternacht
- [ ] **Click-Handler**
  - Checkbox → CompletionDialog über Widget
  - Task-Body → Task-Details öffnen
  - ➕ Button → AddTask-Dialog
  - 📱 Button → App öffnen
- [ ] **Long-Press Menu** (Quick-Actions)
  - Bearbeiten, Löschen, Verschieben

**Geschätzte Dateien:** 4-5 neue Dateien
**Komplexität:** Hoch
**Design-Referenz:** DESIGN.md - Home Screen Widget

#### 4.5.2 Medium & Small Widgets
- [ ] **Medium Widget** (4x2) Layout (widget_medium.xml)
  - Kompakte Version: Streak + Counter + Top 3 Tasks
- [ ] **Small Widget** (2x2) Layout (widget_small.xml)
  - Mini-Version: Streak + Counter + Nächste Aufgabe
- [ ] Widget-Size-Handling
- [ ] Responsive Layouts für verschiedene Displaygrößen

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Mittel

#### 4.5.3 Widget-Interaktivität
- [ ] **Completion-Dialog aus Widget** (Overlay)
  - Dialog öffnet sich über Home-Screen
  - Zeit/Schwierigkeits-Input
  - Streak-Feedback
- [ ] **Quick-Add aus Widget**
  - Minimal-Form-Dialog
  - Nur Titel + Priorität + Fälligkeit
  - "Erweitert"-Link zur vollen Form
- [ ] **Widget-Configuration Activity** (optional)
  - Widget-Größe anpassen
  - Anzahl angezeigter Tasks
  - Theme-Optionen

**Geschätzte Dateien:** 3-4 neue Dateien
**Komplexität:** Hoch

---

### Phase 5: Intelligente Sortierung & Tagesplan (Priorität: HOCH)
**Ziel:** Automatische, intelligente Task-Sortierung

#### 5.1 Sortier-Algorithmus
- [ ] TaskScheduler Klasse
  - Eingabe: Liste aller offenen Tasks
  - Ausgabe: Sortierte Liste für optimalen Tagesplan
- [ ] Gewichtungs-Faktoren implementieren:
  - **Priorität:** Numerischer Wert (höher = wichtiger)
  - **Fälligkeit:** Überfällig > Heute fällig > Bald fällig
  - **Geschätzte Dauer:** Basierend auf `averageCompletionTime`
  - **Übliche Zeit:** Präferierte Tageszeit für Task
  - **Schwierigkeit:** Schwere Tasks früh am Tag (optional)
  - **Verkettungen:** Abhängigkeiten berücksichtigen
- [ ] Scoring-System: Berechne Score für jeden Task
- [ ] Sortierung nach Score

**Geschätzte Dateien:** 1-2 neue Dateien
**Komplexität:** Hoch

#### 5.2 Tagesplan-Generierung
- [ ] `generateDailyPlan()` Methode
  - Rufe TaskScheduler auf
  - Berücksichtige verfügbare Zeit
  - Generiere Zeitslots für Tasks
- [ ] Tagesplan-Ansicht in UI
  - Timeline-Layout
  - Vorgeschlagene Reihenfolge
  - Geschätzte Startzeiten
- [ ] "Nächste Aufgabe" Highlight

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Hoch

---

### Phase 6: Verkettete Tasks (Priorität: MITTEL)
**Ziel:** Sequenzen und Abhängigkeiten

#### 6.1 Task-Verkettungen Datenmodell
- [ ] TaskChain Datenbank-Tabelle
  - chain_id
  - task_order (Reihenfolge)
  - is_cyclic (A → B → C → A wieder)
- [ ] TaskChainDao
- [ ] Verkettungs-Logik
  - `getNextTaskInChain(Task)` - Nächster Task in Kette
  - `isPreviousTaskCompleted(Task)` - Prüfe Vorbedingung
  - `resetChain(Chain)` - Zyklische Ketten zurücksetzen

**Geschätzte Dateien:** 3-4 neue Dateien
**Komplexität:** Hoch

#### 6.2 Verkettungs-UI
- [ ] Chain-Editor Dialog
- [ ] Drag-and-Drop für Task-Reihenfolge
- [ ] Visualisierung von Abhängigkeiten
- [ ] Blockierung von Tasks (wenn Vorgänger nicht erledigt)

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Hoch

---

### Phase 7: Visual Polish & Animationen (Priorität: NIEDRIG)
**Ziel:** UI-Verfeinerungen und Animationen

**HINWEIS:** Diese Phase wurde von "Home-Screen Widget" zu "Visual Polish" geändert, da Widget-Entwicklung in Phase 4.5 vorgezogen wurde.

#### 7.1 Mikro-Animationen (gemäß DESIGN.md)
- [ ] **Checkbox-Animation**
  - Bounce-Effekt beim Abhaken
  - Smooth transition
- [ ] **Task-Completion-Animation**
  - Fade-Out mit Slide
  - Nächster Task rückt nach
- [ ] **Streak-Meilenstein-Animation**
  - Confetti bei 10, 25, 50, 100 Tagen
  - Feuerwerk-Effekt
- [ ] **Task-Add/Delete Animationen**
  - Fade-In von oben (Add)
  - Slide-Out zur Seite (Delete)
- [ ] **Swipe-Reveal Animationen**
  - Icon erscheint während Swipe
  - Smooth Follow-Finger

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Mittel

#### 7.2 Haptisches Feedback
- [ ] Vibration bei Task-Completion
- [ ] Doppel-Vibration bei Streak-Meilenstein
- [ ] Warnung-Vibration bei Löschen
- [ ] Fehler-Vibration bei Validierung

**Geschätzte Dateien:** 1 Datei (HapticManager)
**Komplexität:** Niedrig

#### 7.3 Loading & Empty States
- [ ] Skeleton Screens für Task-Liste
- [ ] Shimmer-Effekt beim Laden
- [ ] Pull-to-Refresh Support
- [ ] Empty-State Illustrationen
  - "Keine Tasks für heute 🎉"
  - "Erstelle deine erste Aufgabe"

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Niedrig-Mittel

---

### Phase 8: Erweiterte Features (Priorität: NIEDRIG)
**Ziel:** Zusätzliche Komfort-Features

#### 8.1 Benachrichtigungen
- [ ] Notification-Service
- [ ] Erinnerungen für fällige Tasks
- [ ] Tägliche Zusammenfassung
- [ ] Streak-Gefahr Warnung

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Mittel

#### 8.2 Kategorien/Tags
- [ ] Task-Kategorisierung
- [ ] Filter nach Kategorie
- [ ] Statistiken pro Kategorie

**Geschätzte Dateien:** 3-4 neue Dateien
**Komplexität:** Mittel

#### 8.3 Backup & Sync
- [ ] Datenbank-Export (JSON/CSV)
- [ ] Datenbank-Import
- [ ] Cloud-Sync (optional, später)

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Hoch

#### 8.4 Dark Mode
- [ ] Dark Theme (colors_night.xml)
- [ ] Theme-Umschaltung
- [ ] Automatisch nach System-Einstellung

**Geschätzte Dateien:** 2 neue Dateien
**Komplexität:** Niedrig

---

## 📊 Geschätzter Gesamtumfang

### Dateien-Schätzung
- **Java-Klassen:** ~50-60 neue Dateien
- **XML-Layouts:** ~20-25 neue Dateien (inkl. Widget-Layouts)
- **Gesamt-LOC:** ~6.000-8.000 Zeilen Code (ohne Tests)

### Entwicklungszeit-Schätzung (mit UX/UI-Design)
- **Phase 1:** 3-4 Tage (Datenbank + verbessertes UI)
- **Phase 2:** 4-5 Tage (Tab-basierte Forms)
- **Phase 3:** 3-4 Tage (Completion-Dialog mit Feedback)
- **Phase 4:** 3-4 Tage (Streaks + Stats Dashboard)
- **Phase 4.5:** 5-6 Tage (Widget-Entwicklung - WICHTIG!)
- **Phase 5:** 4-5 Tage (Intelligente Sortierung)
- **Phase 6:** 3-4 Tage (Verkettungen)
- **Phase 7:** 2-3 Tage (Animationen - optional)
- **Phase 8:** 5-7 Tage (Erweiterte Features - optional)

**Gesamt-Schätzung für Widget-First MVP (Phasen 1-4.5):** 18-23 Tage intensive Entwicklung

**WICHTIG:** Phase 4.5 (Widget) ist kritisch für die Widget-First-Philosophie und sollte nicht übersprungen werden!

---

## 🎯 Nächste konkrete Schritte (mit Design-Focus)

### Sofort (heute/morgen) - Phase 1 Start
1. **Design-System implementieren**
   - Farbpalette aus DESIGN.md in colors.xml übertragen
   - Typografie-Styles definieren
   - Icon-Set vorbereiten
2. **Room-Datenbank einrichten**
   - TaskEntity, TaskDao, AppDatabase erstellen
   - TaskRepository implementieren
3. **MainActivity auf Repository umstellen**

### Diese Woche - Phase 1 Abschluss
4. **Verbessertes Task-Item-Layout**
   - Prioritäts-Sterne (⭐⭐⭐)
   - Quick-Actions ([✓] [✎] [🗑️])
   - Farbcodierung (Überfällig rot, etc.)
   - Streak-Badge
5. **RecyclerView + Swipe-Gesten**
   - Swipe Right: Complete
   - Swipe Left: Delete
6. **Tab-basierter Add-Task Dialog** (Phase 2 Start)

### Nächste Woche - Phase 2-3
7. **Completion-Dialog mit Streak-Feedback**
   - Zeit/Schwierigkeits-Input
   - Confetti bei Meilensteinen
8. **Streak-Berechnung & Visualisierung**
9. **Streak-Karten in MainActivity**

### Danach - Phase 4-4.5 (KRITISCH!)
10. **Statistics-Screen** mit aktiven Streaks
11. **Large Widget (4x4)** - Hauptschnittstelle!
12. **Widget-Interaktivität** (Checkbox, Quick-Add)

**Design-First-Ansatz:** Jedes Feature wird gemäß DESIGN.md implementiert, nicht als Platzhalter!

---

## 🔄 Roadmap-Aktualisierung

Diese Roadmap wird regelmäßig aktualisiert bei:
- Abschluss von Phasen oder Features
- Änderungen in Prioritäten
- Neuen Feature-Anforderungen
- Technischen Erkenntnissen während der Entwicklung

**Änderungshistorie:**
- 2025-11-08 (v1.0): Initiale Roadmap erstellt basierend auf CLAUDE.md und aktuellem Fortschritt
- 2025-11-08 (v2.0): Umfassende Design-Integration
  - DESIGN.md erstellt mit Widget-First, Streak-Focused UX-Vision
  - Alle Phasen mit Design-Referenzen ergänzt
  - Phase 4.5 hinzugefügt: Home-Screen Widget (SEHR HOCH Priorität)
  - Phase 7 geändert: Von "Widget" zu "Visual Polish & Animationen"
  - Detaillierte UI-Spezifikationen für alle Features
  - Entwicklungszeit neu geschätzt: 18-23 Tage für Widget-First MVP
  - Nächste Schritte um Design-Focus erweitert
- 2025-11-08 (v2.1): Phase 1.1 & 1.2 Implementierung abgeschlossen
  - ✅ Design System v1.0 vollständig implementiert (colors, styles, dimens)
  - ✅ Datenbank-Layer komplett (TaskEntity, TaskDao, TaskRepository)
  - ✅ MainActivity auf Datenbank umgestellt mit Sample-Tasks
  - Fortschritt: 25% der Taskmaster Feature Suite
  - Nächstes: RecyclerView + Swipe-Gesten
- 2025-11-08 (v2.2): Phase 1.3 abgeschlossen - RecyclerView + Improved UI
  - ✅ list_item_task.xml: Vollständiges Task-Item-Layout mit allen Design-Elementen
  - ✅ TaskAdapter: RecyclerView.Adapter Implementation mit ViewHolder-Pattern
  - ✅ SwipeHelper: ItemTouchHelper für Swipe-Gesten (Right: Complete, Left: Delete)
  - ✅ MainActivity: RecyclerView-Integration mit LinearLayoutManager
  - ✅ Visuelle Feedback beim Swipen (Grün/Rot Hintergründe)
  - ✅ Bestätigungsdialoge mit Cancel-Handling
  - **Phase 1 vollständig abgeschlossen! 🎉**
  - Fortschritt: 30% der Taskmaster Feature Suite
  - Nächstes: Phase 2.1 - Task-Erstellungs-Dialog
- 2025-11-08 (v2.3): Phase 2.1 abgeschlossen - Task-Erstellungs-Dialog
  - ✅ AddTaskActivity: Tab-basierter Dialog mit TabLayout + ViewPager2
  - ✅ Tab 1 (Basis): Titel, Beschreibung, Priorität (⭐ Buttons), Fälligkeit (Quick-Select + DatePicker)
  - ✅ Tab 2 (Wiederholung): Radio-Buttons für Recurrence-Typen, dynamische UI, x pro y / alle x y
  - ✅ Tab 3 (Details): Geschätzte Dauer, Bevorzugte Tageszeit, Kategorie
  - ✅ TaskBasisFragment, TaskRecurrenceFragment, TaskDetailsFragment
  - ✅ TaskPagerAdapter für Fragment-Management
  - ✅ Validierung: Titel erforderlich, Tab-Navigation bei Fehler
  - ✅ Smart-Defaults: Priorität 2, Heute, Einmalig
  - ✅ Repository-Integration für Speicherung
  - ✅ Success-Message mit Recurrence-Info
  - 11 neue Dateien (4 Layouts, 7 Java-Klassen)
  - **Phase 2.1 vollständig abgeschlossen! 🎉**
  - Fortschritt: 40% der Taskmaster Feature Suite
  - Nächstes: Phase 2.2 - Task-Bearbeitung & -Löschung
- 2025-11-08 (v2.4): Phase 2.2 abgeschlossen - Task-Bearbeitung
  - ✅ AddTaskActivity: Edit-Modus Support via EXTRA_TASK_ID
  - ✅ isEditMode, editingTaskId, editingTask Variablen
  - ✅ Dynamischer Header + Button-Text (Neue/Bearbeiten, Speichern/Aktualisieren)
  - ✅ populateFragments() Methode zum Auto-Befüllen aller Tabs
  - ✅ saveTask() unterscheidet Create vs Update
  - ✅ Fragment Setter-Methoden:
    - TaskBasisFragment: setTitle, setDescription, setPriority, setDueDate (mit Auto-Erkennung)
    - TaskRecurrenceFragment: setRecurrenceType, setRecurrenceX, setRecurrenceY
    - TaskDetailsFragment: setEstimatedDuration, setPreferredTimeOfDay, setCategory
  - ✅ MainActivity: onTaskClick + onTaskEditClick → openEditDialog()
  - ✅ Intent-basierte Navigation mit Task-ID
  - 6 Dateien aktualisiert (+228 Zeilen)
  - **Phase 2.2 vollständig abgeschlossen! ✅**
  - Fortschritt: 45% der Taskmaster Feature Suite
  - Nächstes: Phase 3 - Tracking & Performance-Daten (Completion Dialog)
- 2025-11-08 (v2.5): Phase 3.1 abgeschlossen - Task-Completion Dialog
  - ✅ dialog_completion.xml: Vollständiges Dialog-Layout mit allen UI-Elementen
  - ✅ Zeit-Input mit Quick-Select Buttons (5 Min, 15 Min, 30 Min, 1 Std)
  - ✅ Schwierigkeits-Rating: 5-Sterne System mit visueller Feedback
  - ✅ Difficulty Labels: "Sehr einfach" bis "Sehr schwierig"
  - ✅ Streak-Feedback: Dynamische Anzeige des neuen Streak-Werts
  - ✅ Meilenstein-Erkennung: Special UI bei 10, 25, 50, 100 Tagen
  - ✅ Überspringen-Button: Quick Complete ohne Tracking
  - ✅ Speichern-Button: Complete mit vollständigem Tracking
  - ✅ CompletionDialog.java: Custom Dialog Klasse mit CompletionListener Interface
  - ✅ MainActivity Integration: showCompletionDialog() in Checkbox- und Swipe-Aktionen
  - ✅ Default-Werte: Duration 0 (optional), Difficulty 3.0 (Medium) wenn nicht ausgewählt
  - 3 neue/aktualisierte Dateien (dialog_completion.xml, CompletionDialog.java, MainActivity.java)
  - **Phase 3.1 vollständig abgeschlossen! 🎉**
  - Fortschritt: 50% der Taskmaster Feature Suite
  - Nächstes: Phase 3.2 - Erledigungs-Zeit Tracking (History)
- 2025-11-08 (v2.6): Phase 3.2 abgeschlossen - Erledigungs-Zeit Tracking (Completion History)
  - ✅ CompletionHistoryEntity: Neue Entity für individuelle Completion-Erfassung
    - Fields: id, taskId, completedAt, completionTime, difficultyRating, timeOfDay
    - Auto-Extraktion der Stunde (0-23) aus Timestamp für Zeitanalyse
  - ✅ CompletionHistoryDao: Vollständiger DAO mit CRUD und Analytics
    - insert(), getByTaskId(), getRecentByTaskId(), getByDateRange()
    - getAverageCompletionTime(), getAverageDifficulty()
    - getMostCommonTimeOfDay() für intelligente Zeitplanung
  - ✅ TaskDao: Update auf Database Version 2
    - onCreate: Erstellt beide Tabellen (tasks + completion_history)
    - onUpgrade: Migriert von v1 zu v2 (fügt completion_history hinzu)
  - ✅ TaskRepository: Integrierte History-Tracking
    - completeTask(with tracking): Speichert volle Daten in History
    - completeTask(simple): Speichert auch History-Eintrag mit Defaults
    - Averages jetzt aus gesamter History berechnet (nicht nur 2-Werte-Durchschnitt)
    - Auto-Detection von preferredTimeOfDay aus häufigsten Completion-Zeiten
  - ✅ Repository History-Methoden: getTaskHistory(), getAverageCompletionTimeFromHistory(), etc.
  - 4 Dateien (2 neu, 2 aktualisiert, +494 Zeilen)
  - **Phase 3.2 vollständig abgeschlossen! ✅**
  - Fortschritt: 55% der Taskmaster Feature Suite
  - Vorteile: Vollständige Audit-Trail, präzise Averages, Zeitanalyse, Foundation für Phase 5
  - Nächstes: Phase 3.3 - Zeitpunkt-Analyse (Visualisierung) oder Phase 4 - Statistiken
- 2025-11-08 (v2.7): Phase 4.1 abgeschlossen - Streak-Berechnung (Advanced Streak Management)
  - ✅ StreakManager Utility-Klasse: Zentralisierte Streak-Logik
    - updateStreak() mit Grace Period Support (x_per_y erlaubt 1 Tag Spielraum)
    - resetStreak() - Reset mit Erhaltung von longestStreak
    - isStreakAtRisk() - Erkenne gefährdete Streaks bei überfälligen Tasks
    - getDaysUntilStreakExpires() - Berechne verbleibende Zeit
    - getStreakLevel() - 6-Level-System (0=none, 1=beginner, 2=intermediate, 3=advanced, 4=expert, 5=master)
    - getStreakEmoji() - Visuelle Darstellung (🔥 bis 🔥🔥🔥🔥🔥)
    - getStreakDescription() - Level-Namen ("Anfänger", "Meister", etc.)
    - isMilestoneReached() & getMilestone() - Milestone-Detection (10, 25, 50, 100, 250, 500, 1000)
  - ✅ TaskRepository: Refactored & erweitert
    - updateStreak() delegiert zu StreakManager
    - Neue Methoden: isStreakAtRisk(), getDaysUntilStreakExpires(), getTasksWithStreaksAtRisk(), resetStreak()
  - ✅ MainActivity: Enhanced Streak Display
    - Emoji-Level-Anzeige basierend auf Streak-Höhe
    - "At Risk" Warnung: "🔥🔥 25 (⚠️ 2 at risk)"
    - Visuelle Feedback für Streak-Achievements
  - 3 Dateien (1 neu, 2 aktualisiert, +313 Zeilen)
  - **Phase 4.1 vollständig abgeschlossen! ✅**
  - Fortschritt: 60% der Taskmaster Feature Suite
  - Vorteile: DRY-Prinzip, bessere UX, proaktive Warnungen, Grace Period, Gamification
  - Nächstes: Phase 4.2 - Statistik-Dashboard

---

## 📝 Notizen

- **Testing:** Tests werden parallel zur Entwicklung hinzugefügt
- **Performance:** Performance-Optimierung nach MVP
- **Accessibility:** A11y-Features in Phase 8
- **Internationalisierung:** I18n in Phase 8 (aktuell nur Deutsch/Englisch)
