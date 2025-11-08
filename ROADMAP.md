# AI Secretary - Development Roadmap

**Projekt:** AI Secretary - Native Android App
**Feature Suite 1:** Taskmaster
**Letzte Aktualisierung:** 8. November 2025

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

#### 1.3 Verbesserte UI
- [ ] Task-Item-Layout (list_item_task.xml)
  - Titel, Beschreibung
  - Prioritäts-Indikator (Farbe)
  - Completion-Checkbox
  - Due-Date Anzeige
  - Überfälligkeits-Warnung
- [ ] RecyclerView für Task-Liste (besser als LinearLayout)
- [ ] TaskAdapter erstellen

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Niedrig-Mittel

---

### Phase 2: Task-Erstellung & -Verwaltung (Priorität: HOCH)
**Ziel:** Vollständige CRUD-Funktionalität für Tasks

#### 2.1 Task-Erstellungs-Dialog
- [ ] AddTaskActivity oder Dialog erstellen
- [ ] Formular-UI (add_task_layout.xml)
  - Titel-Input (EditText)
  - Beschreibung-Input (EditText, mehrzeilig)
  - Priorität-Auswahl (Spinner oder Slider)
  - Due-Date Picker
  - Task-Typ Auswahl (Einmalig/Wiederkehrend)
- [ ] Validierung (Titel erforderlich)
- [ ] Speichern in Datenbank über Repository

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Mittel

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

#### 3.1 Task-Completion Dialog
- [ ] Complete-Task-Dialog
- [ ] Zeit-Input: "Wie lange hat es gedauert?"
  - Schnellauswahl (5 Min, 15 Min, 30 Min, 1 Std)
  - Manuelle Eingabe (Stunden/Minuten)
- [ ] Schwierigkeits-Input: "Wie schwer war es?"
  - Rating 1-5 (Sterne oder Slider)
- [ ] Daten in Task speichern
  - Update `averageCompletionTime`
  - Update `averageDifficulty`

**Geschätzte Dateien:** 2 neue Dateien
**Komplexität:** Mittel

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

#### 4.2 Statistik-Dashboard
- [ ] StatsManager Klasse
  - `getTasksCompletedToday()` - Heutige erledigte Tasks
  - `getTasksCompletedLast7Days()` - Letzte 7 Tage
  - `getAverageTasksPerDay()` - Durchschnitt/Tag
  - `getLongestStreak()` - Längste Streak
- [ ] Erweiterte Stats-Anzeige in MainActivity
  - Karten-Layout für Statistiken
  - Grafische Darstellung (Balkendiagramm, optional)
- [ ] Historische Daten-Visualisierung

**Geschätzte Dateien:** 2-3 neue Dateien
**Komplexität:** Mittel

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

### Phase 7: Home-Screen Widget (Priorität: MITTEL)
**Ziel:** Schneller Zugriff vom Home-Screen

#### 7.1 Basis-Widget
- [ ] TaskWidgetProvider Klasse (bereits in Manifest)
- [ ] Widget-Layout (widget_task.xml)
  - Nächste Aufgabe
  - Anzahl Tasks heute
  - Streak-Anzeige
- [ ] Widget-Update Service
- [ ] Click-Handler (öffne App bei Click)

**Geschätzte Dateien:** 3 neue Dateien
**Komplexität:** Mittel

#### 7.2 Interaktives Widget
- [ ] "Task erledigt" Button im Widget
- [ ] Widget-Konfiguration (Größe, Anzeigeoptionen)
- [ ] Aktualisierung bei Task-Änderungen

**Geschätzte Dateien:** 1-2 neue Dateien
**Komplexität:** Mittel-Hoch

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
- **Java-Klassen:** ~40-50 neue Dateien
- **XML-Layouts:** ~15-20 neue Dateien
- **Gesamt-LOC:** ~5.000-7.000 Zeilen Code (ohne Tests)

### Entwicklungszeit-Schätzung (grob)
- **Phase 1:** 2-3 Tage
- **Phase 2:** 3-4 Tage
- **Phase 3:** 3-4 Tage
- **Phase 4:** 2-3 Tage
- **Phase 5:** 4-5 Tage (komplex)
- **Phase 6:** 3-4 Tage
- **Phase 7:** 2-3 Tage
- **Phase 8:** 5-7 Tage (optional)

**Gesamt-Schätzung für MVP (Phasen 1-5):** 14-19 Tage intensive Entwicklung

---

## 🎯 Nächste konkrete Schritte

### Sofort (heute/morgen)
1. **Room-Datenbank einrichten**
   - build.gradle Dependencies hinzufügen (oder manuelles Setup)
   - TaskEntity, TaskDao, AppDatabase erstellen
2. **TaskRepository implementieren**
3. **MainActivity auf Repository umstellen**

### Diese Woche
4. Task-Item-Layout verbessern
5. RecyclerView implementieren
6. Add-Task Dialog erstellen

### Nächste Woche
7. Task-Bearbeitung & -Löschung
8. Basis-Statistiken verbessern
9. Streak-Berechnung starten

---

## 🔄 Roadmap-Aktualisierung

Diese Roadmap wird regelmäßig aktualisiert bei:
- Abschluss von Phasen oder Features
- Änderungen in Prioritäten
- Neuen Feature-Anforderungen
- Technischen Erkenntnissen während der Entwicklung

**Änderungshistorie:**
- 2025-11-08: Initiale Roadmap erstellt basierend auf CLAUDE.md und aktuellem Fortschritt

---

## 📝 Notizen

- **Testing:** Tests werden parallel zur Entwicklung hinzugefügt
- **Performance:** Performance-Optimierung nach MVP
- **Accessibility:** A11y-Features in Phase 8
- **Internationalisierung:** I18n in Phase 8 (aktuell nur Deutsch/Englisch)
