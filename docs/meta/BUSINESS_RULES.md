# AI Secretary - Business Rules

## Grundprinzipien

### Wiederholung vs Completion

**Diese Konzepte schließen sich gegenseitig aus!**

| Konzept | Bedeutung | Beispiel |
|---------|-----------|----------|
| **Wiederholung** | Task erscheint zu festen Zeiten, **egal ob erledigt** | "Jeden Dienstag Müll" |
| **Completion** | Nach Erledigung wartet X Zeit bis Wiederholung | "6 Wochen nach Haarschnitt" |

---

## Automatische Zuordnungen

**Hinweis:** Diese Logik liegt im ZuordnungsUseCase, nicht im Repository!

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

| Typ | Beschreibung | Trigger | Beispiel |
|-----|--------------|---------|----------|
| **KEINE** | Einmalige Aufgabe | - | "Steuererklärung machen" |
| **INTERVALL** | Alle X Zeiteinheiten | Zeit (egal ob erledigt) | "Alle 3 Tage Wäsche" |
| **ZEITPUNKT** | An bestimmten Tagen | Kalender (egal ob erledigt) | "Jeden Dienstag Müll" |
| **FREQUENZ** | X mal pro Zeitraum | Flexibler Zeitpunkt | "3x pro Woche Sport" |

### ZeitEinheit (gemeinsames Enum)

- TAG
- WOCHE
- MONAT

### Beispiele

```
INTERVALL + 3 + TAG = "Alle 3 Tage"
INTERVALL + 2 + WOCHE = "Alle 2 Wochen"
ZEITPUNKT + "DI" = "Jeden Dienstag"
ZEITPUNKT + "2.DI" = "Jeden zweiten Dienstag"
ZEITPUNKT + "1,15" + MONAT = "Am 1. und 15. jeden Monats"
FREQUENZ + 3 + WOCHE = "3x pro Woche (Zeitpunkt flexibel)"
```

---

## Completion-Logik

### Wann Completion nutzen?

**Nur wenn wiederholungsTyp = KEINE!** Bestimmt wie lange nach Erledigung bis Task wieder erscheint.

### Completion-Typen

| Typ | Beschreibung | Beispiel |
|-----|--------------|----------|
| **KEINE** | Einmalig - bleibt erledigt | Normale einmalige Task |
| **INTERVALL** | Nach Erledigung wartet X Zeit | "6 Wochen nach Haarschnitt" |
| **ZEIT** | X Minuten pro Zeitraum | "30 Min/Tag Lesen" |

### Für Ziele zusätzlich

| Typ | Beschreibung | Beispiel |
|-----|--------------|----------|
| **FREQUENZ** | X Ziel-Tasks pro Zeitraum | "4x/Woche Fitness-Tasks" |
| **TASKS** | X Tasks pro Zeitraum | "5 Tasks/Woche für Projekt" |

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

| WiederholungsTyp | Bedingung für streak++ |
|------------------|------------------------|
| **INTERVALL** | Task innerhalb des Intervalls erledigt |
| **ZEITPUNKT** | Task am geplanten Tag erledigt |
| **FREQUENZ** | Periodenziel erreicht (z.B. 3/3 pro Woche) |

### Streak-Reset

**Bedingung:** Intervall/Zeitpunkt verpasst ODER Periodenziel nicht erreicht
**Aktion:** `streak = 1` (aktuelle Erledigung zählt)

**Hinweis:** Streak wird auf 1 gesetzt (nicht 0), da die aktuelle Erledigung den Neustart markiert.

### Streak-Freeze (TBD)

- Manuelles Pausieren des Streaks
- Kein Reset bei Nicht-Erfüllung während Freeze
- *Details bei Implementierung spezifizieren*

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
