# AI Secretary Taskmaster - Test Plan

**Projekt:** AI Secretary - Native Android App
**Feature Suite:** Taskmaster
**Test-Datum:** 8. November 2025

---

## 🎯 Test-Ziele

1. **Funktionalität**: Alle Features funktionieren wie spezifiziert
2. **Stabilität**: Keine Crashes oder kritische Bugs
3. **Datenintegrität**: Daten werden korrekt gespeichert und geladen
4. **Performance**: App ist responsive und schnell

---

## ✅ Test-Kategorien

### 1. Build & Installation
- [ ] App kompiliert ohne Fehler
- [ ] APK wird erfolgreich erstellt
- [ ] App installiert auf Android-Gerät
- [ ] App startet ohne Crash
- [ ] Keine Permission-Fehler beim Start

### 2. Core Task Management (Phase 1-2)

#### 2.1 Task-Erstellung (CRUD)
- [ ] **Create**: Neue Task über "+" Button erstellen
  - [ ] Nur Titel (Minimum-Input)
  - [ ] Titel + Beschreibung
  - [ ] Mit Priorität (1-4 Sterne)
  - [ ] Mit Fälligkeitsdatum (Heute/Morgen/Custom)
  - [ ] Validierung: Leerer Titel wird abgelehnt
- [ ] **Read**: Tasks werden in Liste angezeigt
  - [ ] Titel korrekt
  - [ ] Beschreibung sichtbar
  - [ ] Prioritäts-Sterne korrekt (⭐⭐⭐)
  - [ ] Fälligkeitsdatum angezeigt
- [ ] **Update**: Task bearbeiten
  - [ ] Click auf Task öffnet Edit-Dialog
  - [ ] Alle Felder sind vorausgefüllt
  - [ ] Änderungen werden gespeichert
  - [ ] Toast: "Aufgabe aktualisiert!"
- [ ] **Delete**: Task löschen
  - [ ] Swipe-Left zeigt Bestätigungsdialog
  - [ ] Delete-Button funktioniert
  - [ ] Task verschwindet aus Liste
  - [ ] Toast: "Task deleted"

#### 2.2 Task-Completion & Streaks
- [ ] **Quick Complete**: Checkbox antippen
  - [ ] Completion-Dialog erscheint
  - [ ] "Überspringen" macht Quick-Complete (ohne Tracking)
  - [ ] Task verschwindet aus Today-Liste
  - [ ] Toast mit Streak-Info (bei recurring tasks)
- [ ] **Tracked Complete**: Mit Zeit + Schwierigkeit
  - [ ] Quick-Select Buttons (5/15/30 Min, 1 Std)
  - [ ] Custom-Zeit eingeben
  - [ ] 5-Sterne Difficulty-Rating
  - [ ] Speichern funktioniert
  - [ ] Durchschnittswerte werden aktualisiert
- [ ] **Uncomplete**: Task wieder uncompleten
  - [ ] Checkbox bei completed task antippen
  - [ ] Task wird wieder incomplete
  - [ ] Toast: "Task marked as incomplete"
- [ ] **Streak-Berechnung**
  - [ ] Recurring task completen erhöht Streak
  - [ ] Toast zeigt Streak (🔥 Streak: X Tage)
  - [ ] Milestone-Messages (10, 25, 50, 100 Tage)
  - [ ] longestStreak wird aktualisiert
  - [ ] Streak-Badge in Task-Liste sichtbar

#### 2.3 Swipe-Gesten
- [ ] **Swipe Right**: Complete/Uncomplete
  - [ ] Grüner Hintergrund beim Swipen
  - [ ] Completion-Dialog bei incomplete task
  - [ ] Uncomplete bei completed task
- [ ] **Swipe Left**: Delete
  - [ ] Roter Hintergrund beim Swipen
  - [ ] Bestätigungsdialog erscheint
  - [ ] "Cancel" stellt Task wieder her
  - [ ] "Delete" löscht Task

### 3. Recurrence System (Phase 2.3)

#### 3.1 Recurrence-Typen
- [ ] **Einmalig**: Task ohne Wiederholung
  - [ ] Nach Completion verschwindet Task
  - [ ] Kein Reset
- [ ] **Alle X Y**: Festes Intervall (z.B. "alle 2 Tage")
  - [ ] UI: recurrenceX + recurrenceY Input
  - [ ] Task wird nach Intervall zurückgesetzt
  - [ ] dueAt wird korrekt berechnet
- [ ] **X pro Y**: Flexible Wiederholung (z.B. "3 mal pro Woche")
  - [ ] UI: x und y Input + Unit-Spinner
  - [ ] Task bleibt bis Periode-Ende
  - [ ] Streak-Logik funktioniert
- [ ] **Geplant**: Zu bestimmten Zeitpunkten
  - [ ] UI zeigt Placeholder/Info

#### 3.2 Recurrence-Logic
- [ ] **RecurringTaskService**
  - [ ] Service startet beim App-Start
  - [ ] Automatischer Task-Reset nach Intervall
  - [ ] checkAndResetRecurringTasks() wird aufgerufen
- [ ] **Task-Reset-Verhalten**
  - [ ] completed → false
  - [ ] completedAt → 0
  - [ ] dueAt wird neu berechnet
  - [ ] Streak bleibt erhalten (bei fristgerechter Completion)

### 4. Tracking & Performance (Phase 3)

#### 4.1 Completion History
- [ ] **Datenerfassung**
  - [ ] completionTime wird gespeichert
  - [ ] difficultyRating wird gespeichert
  - [ ] timeOfDay wird automatisch erkannt
  - [ ] CompletionHistory-Tabelle wird befüllt
- [ ] **Durchschnittswerte**
  - [ ] averageCompletionTime wird berechnet
  - [ ] averageDifficulty wird berechnet
  - [ ] preferredTimeOfDay wird aktualisiert
  - [ ] Werte werden in Task-Details angezeigt

#### 4.2 Time Analysis
- [ ] **TimeAnalyzer**
  - [ ] analyzeByHour() gruppiert korrekt (0-23)
  - [ ] analyzeByTimeOfDay() erkennt 4 Phasen
  - [ ] getMostProductiveTimeOfDay() findet Peak
  - [ ] Recommendation-Text ist sinnvoll

### 5. Statistics & Motivation (Phase 4)

#### 5.1 Main Screen Stats
- [ ] **Today Stats**
  - [ ] "Today: X/Y tasks completed" korrekt
  - [ ] Prozent-Anzeige funktioniert
  - [ ] Motivational Message erscheint
- [ ] **Streak Display**
  - [ ] Höchster Streak wird angezeigt
  - [ ] "At Risk" Warning bei gefährdeten Streaks
  - [ ] Emoji korrekt (🔥)

#### 5.2 Statistics Activity
- [ ] **Öffnen**: Statistik-Button funktioniert
- [ ] **Productivity Score**
  - [ ] Score 0-100 wird berechnet
  - [ ] Level-Text korrekt (Anfänger bis Produktivitäts-Guru)
  - [ ] Emoji passt zum Level
- [ ] **Completion Stats**
  - [ ] Heute-Count korrekt
  - [ ] 7-Tage-Count korrekt
  - [ ] Durchschnitt korrekt berechnet
  - [ ] All-Time Count korrekt
- [ ] **Streak Stats**
  - [ ] Top Streak mit Task-Name
  - [ ] longestStreak und currentStreak
  - [ ] At-Risk Liste zeigt gefährdete Streaks
- [ ] **Time Analysis**
  - [ ] TimeOfDay-Verteilung korrekt
  - [ ] Recommendation-Text sinnvoll
  - [ ] Progress-Bars proportional

### 6. Intelligent Sorting (Phase 5.1)

#### 6.1 Sorting-Logik
- [ ] **getTodaysSortedTasks()**
  - [ ] Überfällige Tasks zuerst (rot)
  - [ ] Sortierung nach Dringlichkeit
  - [ ] Priority berücksichtigt
  - [ ] At-Risk Streaks priorisiert
- [ ] **Task-Reihenfolge**
  - [ ] Überfällige Tasks mit ⚠️ markiert
  - [ ] High-Priority Tasks oben
  - [ ] Streak-Tasks hervorgehoben

### 7. Daily Plan (Phase 5.2)

#### 7.1 Timeline-Generierung
- [ ] **Öffnen**: Tagesplan-Button funktioniert
- [ ] **Timeline**
  - [ ] Tasks haben Start-/Endzeiten
  - [ ] 15-Minuten-Breaks zwischen Tasks
  - [ ] preferredTimeOfDay wird berücksichtigt
  - [ ] Default 30 Min für Tasks ohne Schätzung
- [ ] **Visualisierung**
  - [ ] Vertikale Timeline mit Dots & Lines
  - [ ] Color-Coding: Green (completed), Orange (next), Grey (upcoming)
  - [ ] Zeit-Range (HH:mm - HH:mm)
  - [ ] Priority Stars, Description, Duration
- [ ] **Summary Footer**
  - [ ] "X/Y erledigt" korrekt
  - [ ] Total estimated time
  - [ ] Predicted finish time (🏁 Fertig um HH:mm)

### 8. Task Chains (Phase 6)

#### 8.1 Chain-Funktionalität
- [ ] **Chain-Erstellung** (manuell in DB)
  - [ ] chainId gesetzt
  - [ ] chainOrder korrekt
  - [ ] isCyclic Flag
- [ ] **Chain-Visualisierung**
  - [ ] Chain-Indicator in Task-Liste (🔗 Chain #X)
  - [ ] getChainDescription() zeigt "A → B → C"
  - [ ] Cyclic-Indicator (→ ↺)
- [ ] **Chain-Logic**
  - [ ] getNextTaskInChain() funktioniert
  - [ ] isPreviousTaskCompleted() prüft Abhängigkeit
  - [ ] isTaskBlocked() erkennt blockierte Tasks
  - [ ] Cyclic wrap-around funktioniert

### 9. Widgets (Phase 4.5)

#### 9.1 Large Widget (4x4)
- [ ] **Installation**
  - [ ] Widget erscheint in Widget-Liste
  - [ ] Widget kann auf Home-Screen platziert werden
- [ ] **Anzeige**
  - [ ] Header: "Taskmaster" + Stats
  - [ ] Nächste Aufgabe prominent
  - [ ] Today's Tasks Liste (bis zu 5)
  - [ ] Streak-Info
- [ ] **Interaktivität**
  - [ ] Click auf Task öffnet App
  - [ ] Refresh-Button funktioniert
  - [ ] Auto-Update nach Task-Completion

#### 9.2 Medium & Small Widgets
- [ ] **Medium Widget (4x2)**
  - [ ] Kompakte Anzeige
  - [ ] Nächste Task + 2-3 weitere
- [ ] **Small Widget (2x2)**
  - [ ] Nur nächste Task
  - [ ] Stats-Summary

### 10. Advanced Features (Phase 8)

#### 10.1 Notifications (Phase 8.1)
- [ ] **Notification Channels**
  - [ ] 3 Channels erstellt (Reminders, Daily, Streaks)
  - [ ] In System-Einstellungen sichtbar
- [ ] **Daily Summary**
  - [ ] Erscheint um 8:00 Uhr
  - [ ] Zeigt Today/Overdue/At-Risk Stats
- [ ] **Task Reminders**
  - [ ] Alle 2 Stunden Check
  - [ ] Top 3 überfällige Tasks
  - [ ] Click öffnet App
- [ ] **Streak Warnings**
  - [ ] 9:00 Uhr täglich
  - [ ] Zeigt at-risk Streaks

#### 10.2 Categories (Phase 8.2)
- [ ] **Kategorie-Auswahl**
  - [ ] 10 Kategorien im Task-Details Tab
  - [ ] Button-Grid (3 Spalten)
  - [ ] Visual-Feedback bei Auswahl
  - [ ] Emoji + Name angezeigt
- [ ] **Kategorie-Filter**
  - [ ] Spinner in MainActivity
  - [ ] "📁 Alle" zeigt alle Tasks
  - [ ] Kategorie-Auswahl filtert Liste
  - [ ] Real-time Filtering

#### 10.3 Backup & Restore (Phase 8.3)
- [ ] **Öffnen**: Backup-Button funktioniert
- [ ] **Export**
  - [ ] Export-Button erstellt JSON-Datei
  - [ ] Datei in Downloads-Ordner
  - [ ] Filename: taskmaster_backup_YYYY-MM-DD_HH-mm-ss.json
  - [ ] Success-Dialog mit Stats (X Tasks, Y History)
  - [ ] JSON enthält alle Task-Daten + History
- [ ] **Import (Add)**
  - [ ] File-Picker öffnet
  - [ ] JSON wird gelesen
  - [ ] Tasks werden hinzugefügt (nicht ersetzt)
  - [ ] Success-Dialog mit Stats
  - [ ] App reloaded nach Import
- [ ] **Import (Replace)**
  - [ ] Warnung erscheint
  - [ ] Alle existierenden Tasks werden gelöscht
  - [ ] JSON-Daten werden importiert
  - [ ] Vollständige Wiederherstellung

#### 10.4 Dark Mode (Phase 8.4)
- [ ] **Theme-Switching**
  - [ ] System Dark Mode aktivieren
  - [ ] App wechselt zu Dark Theme
  - [ ] Farben korrekt (#121212 Background)
  - [ ] Text lesbar (#FFFFFF)
  - [ ] System Light Mode → Light Theme

### 11. Datenbank & Persistence

#### 11.1 Data Integrity
- [ ] **Task CRUD**
  - [ ] Tasks überleben App-Restart
  - [ ] Alle Felder werden korrekt gespeichert
  - [ ] Keine Daten-Verluste
- [ ] **Completion History**
  - [ ] History-Einträge überleben Restart
  - [ ] Durchschnittswerte persistent
- [ ] **Database-Upgrades**
  - [ ] Alte Daten bleiben erhalten (wenn Schema-Change)

#### 11.2 Edge Cases
- [ ] **Leere Datenbank**
  - [ ] Sample-Tasks werden beim ersten Start erstellt
  - [ ] Kein Crash bei leerer DB
- [ ] **Große Datenmengen**
  - [ ] 100+ Tasks: Performance OK
  - [ ] 1000+ History-Einträge: Queries schnell
- [ ] **Invalide Daten**
  - [ ] Negative Priorität wird abgefangen
  - [ ] Invalides Datum wird behandelt
  - [ ] Null-Checks funktionieren

### 12. UI/UX

#### 12.1 Navigation
- [ ] **Hauptbildschirm**
  - [ ] Alle Buttons erreichbar
  - [ ] Quick Actions funktionieren
  - [ ] Zurück-Button schließt App
- [ ] **Dialoge**
  - [ ] AddTaskActivity schließt nach Save
  - [ ] Zurück-Button bricht ab ohne Speichern
  - [ ] Tab-Wechsel funktioniert
- [ ] **Scrolling**
  - [ ] Task-Liste scrollt flüssig
  - [ ] RecyclerView performant
  - [ ] Kein Lag bei vielen Tasks

#### 12.2 Visual Design
- [ ] **Farben**
  - [ ] Design-System konsistent
  - [ ] Priority-Colors korrekt
  - [ ] Status-Colors (Overdue-Red, etc.)
- [ ] **Typography**
  - [ ] Text lesbar
  - [ ] Hierarchie klar (Titel > Subtitle > Body)
  - [ ] Font-Sizes angemessen
- [ ] **Icons & Emojis**
  - [ ] Emojis korrekt angezeigt
  - [ ] Prioritäts-Sterne (⭐)
  - [ ] Streak-Feuer (🔥)
  - [ ] Status-Icons (✓, ⚠️)

### 13. Performance

#### 13.1 App-Performance
- [ ] **Start-Zeit**
  - [ ] App startet in < 2 Sekunden
  - [ ] Kein ANR (Application Not Responding)
- [ ] **Liste-Performance**
  - [ ] RecyclerView smooth scrolling
  - [ ] ViewHolder-Pattern effizient
- [ ] **Database-Queries**
  - [ ] Queries < 100ms
  - [ ] Kein UI-Freeze bei Datenbank-Ops

#### 13.2 Memory
- [ ] **Memory-Leaks**
  - [ ] Kein Crash bei langem Verwenden
  - [ ] Activity-Leaks vermieden
- [ ] **Resource-Management**
  - [ ] Cursor werden geschlossen
  - [ ] Streams werden geschlossen

---

## 🐛 Bug-Tracking

### Kritische Bugs (App-Crash)
- [ ] Keine kritischen Bugs gefunden

### Schwere Bugs (Feature nicht funktional)
- [ ] Keine schweren Bugs gefunden

### Mittlere Bugs (Feature teilweise funktional)
- [ ] Keine mittleren Bugs gefunden

### Leichte Bugs (Kosmetisch/UX)
- [ ] Keine leichten Bugs gefunden

---

## 📊 Test-Zusammenfassung

**Tests durchgeführt:** 0/200+
**Tests bestanden:** 0
**Tests fehlgeschlagen:** 0
**Kritische Bugs:** 0
**Schwere Bugs:** 0
**Mittlere Bugs:** 0
**Leichte Bugs:** 0

**Status:** 🔴 Testing nicht gestartet

---

## 📝 Test-Notizen

_(Hier werden während des Testens Notizen, Beobachtungen und Findings dokumentiert)_

### Build-Test
- [ ] Noch nicht durchgeführt

### Feature-Tests
- [ ] Noch nicht durchgeführt

### Performance-Tests
- [ ] Noch nicht durchgeführt

---

## ✅ Nächste Schritte

1. Build-Test durchführen (kompilieren + APK erstellen)
2. App auf Gerät installieren
3. Systematisch durch alle Test-Kategorien arbeiten
4. Bugs dokumentieren und beheben
5. Re-Test nach Bugfixes
