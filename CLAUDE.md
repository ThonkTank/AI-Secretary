# AI Secretary - Native Android App

## 🔒 Projektvision

**WICHTIG:** Sektionen, die mit dem 🔒 Emoji markiert sind, dürfen nur mit ausdrücklicher Erlaubnis des Projektinhabers editiert werden.

---

## 🔒 Gesamtkonzept

AI-Secretary ist als umfassendes Alltags-Planungstool konzipiert. Die App soll Nutzern helfen, ihre täglichen Aufgaben intelligent zu organisieren, zu tracken und zu priorisieren.

### Technische Entscheidung
- **Ursprünglicher Prototyp:** Hybrid-App (Capacitor + Web-Technologien)
- **Aktuelle Entwicklung:** Native Android-App (bessere Performance, System-Integration, kleinere APK)
- Der Hybrid-Prototyp dient als Proof of Concept

---

## 🔒 Feature Suite 1: "Taskmaster"

### Übersicht
Taskmaster ist die erste Feature-Suite der AI-Secretary App und bildet das Kernstück der Aufgabenverwaltung.

### 1. Umfassende Todo-Organisation

#### Task-Typen
- **Einzelne Tasks:** Einmalige Aufgaben
- **Wiederkehrende Tasks:**
  - `x pro y`: z.B. "3 mal pro Woche" (flexible Verteilung innerhalb des Zeitraums)
  - `alle x y`: z.B. "alle 2 Tage" (festes Intervall)
  - Zu bestimmten Zeitpunkten: z.B. "Jeden Montag 09:00 Uhr"
- **Verkettete Tasks:** Sequenzen wie A → B → C → A (zyklische Abhängigkeiten)

#### Task-Eigenschaften
- **Titel:** Kurze Beschreibung der Aufgabe
- **Beschreibung:** Ausführliche Details (optional)
- **Numerische Priorität:** Zahlenwert zur Priorisierung

### 2. Intelligentes Tracking

Die App trackt folgende Daten pro Task:
- **Erledigungsstatus:** Erledigt / Unerledigt / Überfällig
- **Erledigungs-Häufigkeit:** Wie oft wurde die Aufgabe erledigt?
- **Erledigungs-Historie:** Wann wurde sie die letzten Male erledigt?
- **Wiederholungs-Logik:** Wann wird eine wiederkehrende Aufgabe wieder auf "unerledigt" gesetzt?
- **Überfälligkeit:** Wie lange ist die Aufgabe bereits überfällig?

#### Zusätzliche Tracking-Daten beim Erledigen
- **Benötigte Zeit:** Wie lange hat die Erledigung gedauert?
- **Schwierigkeitsgrad:** Wie einfach/schwer war die Aufgabe? (User-Input)
- **Übliche Erledigungs-Uhrzeiten:** Zu welchen Tageszeiten wird die Aufgabe typischerweise erledigt?

### 3. Motivations-Features

#### Streak-Tracking
- Anzeige von "Streaks": Wie oft wurde eine Aufgabe rechtzeitig in Folge erledigt?
- Visualisierung der Streak-Länge

#### Statistiken
- **Heute:** Anzahl erledigter Aufgaben heute
- **Letzte 7 Tage:** Anzahl erledigter Aufgaben in den letzten 7 Tagen
- **Durchschnitt (7 Tage):** Durchschnittliche Anzahl erledigter Aufgaben pro Tag (letzten 7 Tage)

### 4. UI/UX-Features

#### App-Ansichten
- **Nächste Aufgabe:** Prominent angezeigte, wichtigste nächste Aufgabe
- **Aufgaben für heute:** Liste aller für heute geplanten Aufgaben
- **Statistik-Dashboard:** Streaks und Erledigungs-Statistiken

#### Home-Screen Widget
Das Widget zeigt auf dem Android-Homescreen:
- Nächste Aufgabe
- Aufgaben für heute
- Streak- und Statistik-Zusammenfassung

### 5. Intelligente Tagesplanung

Basierend auf allen gesammelten Daten soll die App einen intelligenten Tagesplan generieren:

#### Berücksichtigte Faktoren
- **Priorität:** Numerische Task-Priorität
- **Fälligkeit:** Überfällige und heute fällige Tasks
- **Geschätzte Dauer:** Basierend auf historischen Zeiterfassungen
- **Übliche Erledigungs-Zeit:** Zeitpunkt, zu dem die Aufgabe normalerweise erledigt wird
- **Schwierigkeitsgrad:** Historische Schwierigkeits-Bewertungen
- **Verkettungen:** Abhängigkeiten zwischen Tasks

#### Ziel
Automatische, intelligente Sortierung und Vorschläge für einen optimalen Tagesablauf.

---

## Entwicklungs-Roadmap

### Phase 1: Grundlagen (aktuell)
- [ ] Native Android Projekt-Setup
- [ ] Datenbank-Schema (Room)
- [ ] Basis-UI (MainActivity, Task-Liste)

### Phase 2: Core Taskmaster Features
- [ ] Task-Erstellung (alle Typen)
- [ ] Task-Tracking
- [ ] Erledigungs-Workflow (Zeit, Schwierigkeit)

### Phase 3: Intelligenz & Motivation
- [ ] Streak-Berechnung
- [ ] Statistiken
- [ ] Intelligente Sortierung

### Phase 4: Widget & Polish
- [ ] Home-Screen Widget
- [ ] UI/UX-Verbesserungen
- [ ] Testing & Optimierung

---

## Technologie-Stack

### Geplant
- **Sprache:** Kotlin (empfohlen) oder Java
- **UI-Framework:** Jetpack Compose oder XML-Layouts
- **Datenbank:** Room (SQLite)
- **Architektur:** MVVM (Model-View-ViewModel)
- **Build-System:** Gradle

### Entwicklungsumgebung
- **Primär:** Termux auf Android
- **Synchronisation:** Git (GitHub/GitLab)
- **Backup-Entwicklung:** Optional auf Laptop

---

## Notizen für Claude Code Agenten

- **Geschützte Bereiche:** Alle mit 🔒 markierten Sektionen nur mit expliziter User-Erlaubnis ändern
- **Feature-Requests:** Neue Features in "Entwicklungs-Roadmap" ergänzen
- **Design-Entscheidungen:** Bei Unklarheiten nachfragen, nicht annehmen
