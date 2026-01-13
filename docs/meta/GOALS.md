# AI Secretary - Projektziele

## Vision

**AI Secretary** ist eine persönliche All-in-One Produktivitäts-App für Android.

**Mission:** Fragmentierte Produktivitätstools in einer einzigen, intelligenten App vereinen - ohne Cloud-Zwang, ohne Account, mit voller Privatsphäre.

---

## Kernprinzipien

| Prinzip | Bedeutung |
|---------|-----------|
| **Offline-first** | Vollständig ohne Internet nutzbar |
| **Privacy-first** | Alle Daten lokal, kein Account nötig |
| **Funktional > Schön** | Features vor Design-Polish |
| **Persönlich optimiert** | Exakt auf eigene Bedürfnisse zugeschnitten |

---

## Feature-Ziele

### Phase 1: Tasks (v0.1 - v0.3)
- [ ] Basis CRUD (erstellen, bearbeiten, löschen, abhaken)
- [ ] Kategorien/Tags
- [ ] Priorisierung (Wichtigkeit/Dringlichkeit)
- [ ] Due Dates mit Notifications
- [ ] Homescreen Widget

### Phase 2: Habits (v0.4 - v0.5)
- [ ] Tägliche Gewohnheiten definieren
- [ ] Streak-Tracking
- [ ] Intervall-basierte Wiederholung (alle X Tage)
- [ ] Frequenz-basierte Wiederholung (X mal pro Woche)

### Phase 3: Routinen (v0.6 - v0.7)
- [ ] Routinen mit festen Schritten erstellen
- [ ] Schrittweise Abarbeitung (Morgenroutine etc.)
- [ ] Routinen an Tageszeiten koppeln

### Phase 4: GTD (v0.8 - v0.9)
- [ ] Inbox (schnelles Erfassen)
- [ ] Projekte (Multi-Step Tasks)
- [ ] Kontexte (@Home, @Work, @Phone)
- [ ] Next Actions

### Phase 5: Pomodoro (v1.0)
- [ ] Focus Timer (25/5 oder konfigurierbar)
- [ ] Timer an Tasks koppeln
- [ ] Fokus-Statistiken

---

## Psychologie-basierte Features

*Basierend auf: Angewandte Psychologie (Motivationspsychologie, TTM, Motivierende Gesprächsführung)*

### Persona/Quest-System (Gamification)

**Konzept:**
- User formuliert **Utopie** (Ziel-Zustand: Wer will ich sein?)
- Ziel-Person bekommt **Titel** (Sportler, Fashionista, Absolvent etc.)
- Aufgaben werden Personas zugeordnet

**RPG-Sprache:**
| App-Konzept | Videospiel-Äquivalent |
|-------------|----------------------|
| Ziel-Persona | Klasse |
| Aufgabe | Quest |
| Punkte | XP |
| Fortschritt | Level |

**Mechanik:**
- [ ] Personas/Klassen erstellen mit Titel und Utopie-Beschreibung
- [ ] Aufgaben (Quests) einer Persona zuordnen
- [ ] XP für abgeschlossene Quests
- [ ] Level-System pro Persona

### Streak & Überfälligkeits-System

**Tracking:**
- [ ] Streaks bei wiederkehrenden Aufgaben/Routinen
- [ ] Überfälligkeiten tracken

**Anzeige:**
- [ ] Streaks in der App und bei Aufgaben sichtbar
- [ ] Überfälligkeiten in der App und bei Aufgaben sichtbar

**Belohnungen:**
- [ ] Extra XP für Streak-Erhaltung (basierend auf Streak-Länge)
- [ ] Extra XP für Erledigen überfälliger Aufgaben (basierend auf Überfälligkeits-Länge)

### Wichtigkeit & Zuversicht

**Wichtigkeit (explizit):**
- [ ] Wichtigkeit beim Erstellen der Aufgabe angeben
- [ ] Beeinflusst Platzierung/Sortierung in der ToDo-Liste

**Zuversicht (implizit):**
- Wird durch Erfolgs-Feedback aufgebaut (Level-Ups, Streaks, abgeschlossene Quests)
- Kein explizites Eingabefeld

---

## Integrations-Ziele

### MVP
- [ ] Android Kalender-Sync
- [ ] System-Notifications
- [ ] Manueller Export (JSON/CSV)
- [ ] Auto-Backup lokal

### Post-MVP
- [ ] AI-Integration (Claude API)
- [ ] Multi-Device Sync (Peer-to-Peer)
- [ ] Email-Integration
- [ ] Cloud Backup (optional)

---

## Technische Ziele

| Bereich | Ziel |
|---------|------|
| **Datenhaltung** | SQLite lokal |
| **Plattform** | Android (min SDK 26) |
| **Build** | GitHub Actions (kein Gradle in Termux) |
| **Sprache** | Java 8 |
| **Architektur** | Clean Architecture (später) |

---

## Qualitätsziele

- **Stabilität:** Keine Abstürze bei normalem Gebrauch
- **Performance:** Schnelle UI-Reaktion (<100ms)
- **Datensicherheit:** Keine Datenverluste bei Updates
- **Wartbarkeit:** Sauberer, dokumentierter Code

---

## Nicht-Ziele (bewusst ausgeschlossen)

- ❌ Multi-User / Team-Features
- ❌ Web-Version
- ❌ iOS-Version
- ❌ Monetarisierung
- ❌ Analytics / Tracking
- ❌ Social Features

---

## Erfolgskriterien

**v1.0 ist erreicht wenn:**
1. Alle 5 Feature-Phasen implementiert sind
2. App täglich zuverlässig nutzbar ist
3. Daten sicher gespeichert und exportierbar sind
4. Kalender-Sync funktioniert

---

*Erstellt: 2025-01-12*
*Basierend auf: Vision Discovery Session*
