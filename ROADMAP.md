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

**Phase 1: Grundlagen** 🟡 In Arbeit (40% abgeschlossen)
- [x] Task-Datenmodell (Task.java) mit allen geplanten Eigenschaften
  - Basis-Properties (id, title, description, priority, completed)
  - Tracking-Properties (createdAt, completedAt, dueAt, completionCount)
  - Recurrence-Properties (isRecurring, recurrenceType, recurrenceX/Y)
  - Performance-Properties (averageCompletionTime, averageDifficulty, streak)
- [x] MainActivity mit Basis-UI
  - Task-Liste Anzeige
  - Task-Completion Toggle
  - Basis-Statistiken (erledigte/gesamt)
  - Add-Task Button (Platzhalter)
- [x] XML-Layouts (activity_main.xml)
- [x] Resource-Dateien (strings.xml, colors.xml, styles.xml)
- [ ] **Room Datenbank-Schema** ⬅️ Nächster Schritt
- [ ] **TaskRepository für Datenzugriff**
- [ ] **ViewModel-Architektur (MVVM)**

**Gesamt-Fortschritt:** ~15% der Taskmaster Feature Suite

---

## 🗺️ Detaillierte Roadmap

### Phase 1: Grundlagen & Datenbank (Priorität: HOCH)
**Ziel:** Persistente Datenspeicherung und solide Architektur

#### 1.1 Datenbank-Integration
- [ ] Room-Dependencies einrichten
- [ ] TaskEntity erstellen (Datenbank-Modell)
- [ ] TaskDao erstellen (Datenzugriff-Interface)
  - `insert(Task)` - Task hinzufügen
  - `update(Task)` - Task aktualisieren
  - `delete(Task)` - Task löschen
  - `getAll()` - Alle Tasks laden
  - `getById(id)` - Task nach ID
  - `getByDate(date)` - Tasks für bestimmtes Datum
  - `getOverdue()` - Überfällige Tasks
- [ ] AppDatabase erstellen (Room-Datenbank)
- [ ] TaskRepository erstellen (Abstraktionsschicht)

**Geschätzte Dateien:** 4-5 neue Java-Klassen
**Komplexität:** Mittel

#### 1.2 MVVM-Architektur
- [ ] TaskViewModel erstellen
- [ ] LiveData für Task-Liste
- [ ] MainActivity auf ViewModel umstellen
- [ ] Observer-Pattern für UI-Updates

**Geschätzte Dateien:** 2 neue Java-Klassen
**Komplexität:** Mittel

#### 1.3 Verbesserte UI (gemäß DESIGN.md)
- [ ] **Design-System implementieren**
  - Farbpalette aus DESIGN.md in colors.xml
  - Typografie-Styles in styles.xml
  - Icon-Set zusammenstellen
- [ ] **Task-Item-Layout** (list_item_task.xml)
  - Titel + Beschreibung
  - Prioritäts-Sterne (⭐⭐⭐)
  - Completion-Checkbox rechts
  - Due-Date mit Icon
  - Überfälligkeits-Warnung (⚠️ rot)
  - Quick-Actions: [✓] [✎] [🗑️]
  - Streak-Badge für wiederkehrende Tasks
- [ ] RecyclerView für Task-Liste
- [ ] TaskAdapter mit ViewHolder
- [ ] **Swipe-Gesten** (Right: Complete, Left: Delete)

**Geschätzte Dateien:** 3-4 neue Dateien
**Komplexität:** Mittel
**Design-Referenz:** DESIGN.md - Main App Section

---

### Phase 2: Task-Erstellung & -Verwaltung (Priorität: HOCH)
**Ziel:** Vollständige CRUD-Funktionalität für Tasks

#### 2.1 Task-Erstellungs-Dialog (gemäß DESIGN.md)
- [ ] **AddTaskActivity mit Tab-Layout** erstellen
- [ ] **Tab 1: Basis** (add_task_basis.xml)
  - Titel-Input
  - Beschreibung-Input (mehrzeilig, optional)
  - Priorität-Auswahl (⭐ Buttons 1-4)
  - Fälligkeit (Quick-Buttons: Heute, Morgen, Datum...)
- [ ] **Tab 2: Wiederholung** (add_task_recurrence.xml)
  - Radio-Buttons: Einmalig / x pro y / alle x y / Geplant
  - Dynamische Inputs basierend auf Auswahl
  - Start-/End-Datum
- [ ] **Tab 3: Details** (add_task_details.xml)
  - Geschätzte Dauer (Quick-Select)
  - Bevorzugte Zeit (Morgen/Mittag/Abend)
  - Verkettung (Optional)
  - Kategorie (Optional)
- [ ] Validierung (Titel erforderlich)
- [ ] Smart-Defaults: Heute, Priorität 2, Einmalig
- [ ] Speichern in Datenbank über Repository

**Geschätzte Dateien:** 4-5 neue Dateien
**Komplexität:** Mittel-Hoch
**Design-Referenz:** DESIGN.md - Add/Edit Task Screen

#### 2.2 Task-Bearbeitung & -Löschung
- [ ] EditTaskActivity oder Dialog
- [ ] Task-Details-Ansicht
- [ ] Löschen-Funktionalität mit Bestätigung
- [ ] Swipe-to-Delete Geste

**Geschätzte Dateien:** 1-2 neue Dateien
**Komplexität:** Niedrig-Mittel

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

#### 3.1 Task-Completion Dialog (gemäß DESIGN.md)
- [ ] **CompletionDialog** erstellen (completion_dialog.xml)
- [ ] **Zeit-Input mit Quick-Select**
  - Buttons: 5 Min, 15 Min, 30 Min, 1 Std
  - Manuelle Eingabe: [HH:MM] Format
- [ ] **Schwierigkeits-Input**
  - 5-Sterne-Rating (Star-Icons)
  - Slider als Alternative
- [ ] **Streak-Feedback**
  - "🔥 Streak auf X Tage erhöht!"
  - Confetti-Animation bei Meilensteinen (10, 25, 50, 100 Tage)
- [ ] **Überspringen-Button** für schnelles Abhaken ohne Tracking
- [ ] Daten in Task speichern
  - Update `averageCompletionTime`
  - Update `averageDifficulty`
  - Update `streak`

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Mittel
**Design-Referenz:** DESIGN.md - Task Completion Dialog

#### 3.2 Erledigungs-Zeit Tracking
- [ ] CompletionHistory Datenbank-Tabelle
  - task_id (Foreign Key)
  - completed_at (Timestamp)
  - completion_time (Millisekunden)
  - difficulty_rating (1-5)
  - time_of_day (Uhrzeit)
- [ ] CompletionHistoryDao
- [ ] Historien-Datenerfassung bei Task-Completion
- [ ] Durchschnitts-Berechnung

**Geschätzte Dateien:** 3 neue Dateien
**Komplexität:** Mittel

#### 3.3 Zeitpunkt-Analyse
- [ ] Analyse häufigster Erledigungs-Uhrzeiten
- [ ] `getPreferredTimeOfDay(Task)` Methode
- [ ] Visualisierung (optional)

**Geschätzte Dateien:** 1-2 neue Dateien
**Komplexität:** Mittel-Hoch

---

### Phase 4: Statistiken & Motivation (Priorität: HOCH)
**Ziel:** Streak-Tracking und motivierende Statistiken

#### 4.1 Streak-Berechnung
- [ ] StreakManager Klasse
  - `calculateStreak(Task)` - Berechne aktuelle Streak
  - `updateStreak(Task, completed)` - Update Streak bei Completion
  - `resetStreak(Task)` - Reset bei verpasstem Task
- [ ] Streak-Persistierung in Datenbank
- [ ] Streak-Anzeige in UI (Feuer-Icon 🔥)

**Geschätzte Dateien:** 1-2 neue Dateien
**Komplexität:** Mittel-Hoch

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

---

## 📝 Notizen

- **Testing:** Tests werden parallel zur Entwicklung hinzugefügt
- **Performance:** Performance-Optimierung nach MVP
- **Accessibility:** A11y-Features in Phase 8
- **Internationalisierung:** I18n in Phase 8 (aktuell nur Deutsch/Englisch)
