# AI Secretary - Business Rules

## Automatische Zuordnungen

### Regel 1: Task → Ziel → Personas

**Wenn:** Task wird einem Ziel zugeordnet
**Dann:** Task wird automatisch allen Personas des Ziels zugeordnet

```
Task "Kapitel 1 lesen" → Ziel "Buch fertig lesen" → Persona "Absolvent"
                                                         ↓
Task "Kapitel 1 lesen" ────────────────────────→ Persona "Absolvent"
```

### Regel 2: Ziel → Persona → Tasks

**Wenn:** Ziel wird einer Persona zugeordnet
**Dann:** Alle Tasks des Ziels werden automatisch der Persona zugeordnet

```
Ziel "Buch fertig lesen" (enthält 3 Tasks) → Persona "Absolvent"
                                                    ↓
Alle 3 Tasks ───────────────────────────→ Persona "Absolvent"
```

---

## Wiederholungs-Logik

### Wiederholungs-Typen

| Typ | Beschreibung | Beispiel |
|-----|--------------|----------|
| **KEINE** | Einmalige Aufgabe | "Steuererklärung machen" |
| **TIMER** | Alle X Zeiteinheiten | "Alle 3 Tage Wäsche waschen" |
| **ZEITPUNKT** | An bestimmten Tagen | "Jeden Dienstag Müll rausbringen" |

### Wiederholungs-Einheiten

- TAG
- WOCHE
- MONAT

### Beispiele

```
TIMER + 3 + TAG = "Alle 3 Tage"
TIMER + 2 + WOCHE = "Alle 2 Wochen"
ZEITPUNKT + "DI" = "Jeden Dienstag"
ZEITPUNKT + "2.DI" = "Jeden zweiten Dienstag"
ZEITPUNKT + "1,15" + MONAT = "Am 1. und 15. jeden Monats"
```

---

## Completion-Metriken

### Completion-Typen

| Typ | Beschreibung | Beispiel |
|-----|--------------|----------|
| **KEINE** | Einfaches Abhaken | Normale Task |
| **FREQUENZ** | X mal pro Zeitraum | "3x pro Woche Sport" |
| **ZEIT** | X Minuten pro Zeitraum | "30 Min/Tag Lesen" |
| **TASKS** | X Tasks pro Zeitraum (nur Ziele) | "5 Tasks/Woche für Projekt" |

### Anwendbarkeit

| Metrik | Task | Ziel |
|--------|------|------|
| FREQUENZ | ✅ | ✅ |
| ZEIT | ✅ | ✅ |
| TASKS | ❌ | ✅ |

---

## XP & Level System

### XP-Vergabe

| Aktion | Basis-XP | Modifikator |
|--------|----------|-------------|
| Task abschließen | 10 | × Wichtigkeit |
| Streak halten | +5 | × Streak-Länge (max 50) |
| Überfällige Task erledigen | +10 | × Überfälligkeits-Tage (max 100) |

### Level-Berechnung

```
Level = floor(sqrt(XP / 100))
```

| XP | Level |
|----|-------|
| 0-99 | 0 |
| 100-399 | 1 |
| 400-899 | 2 |
| 900-1599 | 3 |
| 1600-2499 | 4 |
| ... | ... |

### XP-Zuordnung

- XP werden der **Persona** gutgeschrieben, der die Task zugeordnet ist
- Bei mehreren Personas: XP werden aufgeteilt

---

## Streak-Regeln

### Streak-Erhöhung

**Bedingung:** Task innerhalb des Wiederholungs-Intervalls erledigt
**Aktion:** `streak++`

### Streak-Reset

**Bedingung:** Wiederholungs-Intervall verpasst (ohne Erledigung)
**Aktion:** `streak = 0`

### Streak-Freeze (geplant)

- Manuelles Pausieren des Streaks
- Kein Reset bei Nicht-Erfüllung während Freeze

---

## Scheduling-Algorithmus

### Basis-Sortierung

1. **Wichtigkeit** (absteigend)
2. **Fälligkeit** (aufsteigend - dringendste zuerst)

### Lernende Präferenzen

#### Task-Ketten

- System trackt: Welche Task folgt oft auf welche?
- `nachfolgerHistory: Map<TaskId, Int>`
- Je öfter Task B auf Task A folgt, desto wahrscheinlicher wird B nach A vorgeschlagen

#### Zeitpunkt-Präferenz

- System trackt: Wann wird Task typischerweise erledigt?
- `completionHistory: List<Timestamp>`
- Tasks werden bevorzugt zu ihren "üblichen" Zeiten eingeplant

### Kalender-Integration

1. Kalendertermine importieren
2. **1 Stunde Puffer** um jeden Termin
3. Freie Slots mit Tasks füllen
4. Bearbeitungszeit der Tasks berücksichtigen

---

## Überfälligkeits-Regeln

### Überfällig-Status

**Bedingung:** `aktuellesDatum > frist`
**Anzeige:** Visuell hervorgehoben in der App

### Überfälligkeits-Bonus

- Je länger überfällig, desto mehr Bonus-XP bei Erledigung
- Motiviert zum Aufholen statt Ignorieren

---

*Erstellt: 2025-01-12*
*Basierend auf: Business Rules Discovery Session*
