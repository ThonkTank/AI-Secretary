# Entity: Task

## Beschreibung

Eine Task repräsentiert eine einzelne Aufgabe, die der Benutzer erledigen möchte.

---

## Felder

| Feld | Typ | Pflicht | Beschreibung |
|------|-----|---------|--------------|
| `id` | Long | ✅ | Primary Key (auto-generated) |
| `titel` | String | ✅ | Kurzer Task-Name (z.B. "Wäsche waschen") |
| `beschreibung` | String | ❌ | Optionale Details (z.B. "Mit Weichspüler, 40 Grad") |
| `streak` | Int | ✅ | Aktueller Streak (Default: 0) |
| `wichtigkeit` | Int | ✅ | Wichtigkeit für Sortierung (1-10) |
| `letztesmalErledigt` | Date | ❌ | Zeitpunkt der letzten Erledigung |
| `frist` | Date | ❌ | Optionale Deadline |
| `bearbeitungszeit` | Int | ❌ | Geschätzte Dauer in Minuten |

### Status-Logik

Eine Task ist **erledigt** wenn `letztesmalErledigt != null`. Sie wird **wieder offen** wenn die Wiederholungs-Logik sie reaktiviert. Bei `wiederholungsTyp = KEINE` (einmalig) bleibt sie dauerhaft erledigt.

### Wiederholung

**Wichtig:** Wiederholung und Completion schließen sich gegenseitig aus!
- **Wiederholung** = Task erscheint zu festen Zeiten, unabhängig von Erledigung
- **Completion** = Nach Erledigung wartet X Zeit bis Wiederholung (siehe unten)

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `wiederholungsTyp` | Enum | KEINE, INTERVALL, ZEITPUNKT, FREQUENZ |
| `wiederholungsWert` | Int | X (Anzahl) |
| `wiederholungsEinheit` | Enum | ZeitEinheit (TAG, WOCHE, MONAT) |
| `wiederholungsDetails` | String | Für Zeitpunkte (z.B. "DI", "2.DI") |

### Completion-Metriken

**Nur wenn wiederholungsTyp = KEINE!** Bestimmt wie lange nach Erledigung bis Task wieder erscheint.

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `completionTyp` | Enum | KEINE, INTERVALL, ZEIT |
| `completionWert` | Int | X (Anzahl/Minuten) |
| `completionEinheit` | Enum | ZeitEinheit (TAG, WOCHE, MONAT) |

### Beziehungen

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `personas` | List<Persona> | Zugehörige Personas (Many-to-Many) |
| `ziele` | List<Ziel> | Zugehörige Ziele (Many-to-Many) |

### Lern-Daten

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `completionHistory` | List<Timestamp> | Wann wurde Task erledigt (für Zeitpunkt-Präferenz) |
| `nachfolgerHistory` | Map<TaskId, Int> | Welche Task folgte wie oft (für Task-Ketten) |

---

## Enums

### WiederholungsTyp

```java
enum WiederholungsTyp {
    KEINE,      // Einmalige Aufgabe (kann Completion haben)
    INTERVALL,  // Alle X Tage/Wochen/Monate (egal ob erledigt)
    ZEITPUNKT,  // Jeden (zweiten) Dienstag etc. (egal ob erledigt)
    FREQUENZ    // X mal pro Zeitraum (z.B. 3x pro Woche)
}
```

### ZeitEinheit (gemeinsames Enum)

```java
enum ZeitEinheit {
    TAG,
    WOCHE,
    MONAT
}
```

### CompletionTyp

**Nur relevant wenn wiederholungsTyp = KEINE!**

```java
enum CompletionTyp {
    KEINE,      // Einmalig - bleibt nach Erledigung erledigt
    INTERVALL,  // Nach Erledigung wartet X Zeit bis Wiederholung
    ZEIT        // X Minuten Bearbeitungszeit pro Zeitraum
}
```

---

## Beispiele

### Einfache einmalige Task

```java
Task task = new Task();
task.titel = "Steuererklärung machen";
task.beschreibung = "Alle Belege zusammensuchen";
task.wichtigkeit = 8;
task.frist = Date.parse("2025-03-31");
task.wiederholungsTyp = WiederholungsTyp.KEINE;
task.completionTyp = CompletionTyp.KEINE;
// → Einmalig, bleibt nach Erledigung dauerhaft erledigt
```

### Wiederkehrende Task (Intervall)

```java
Task task = new Task();
task.titel = "Wäsche waschen";
task.wichtigkeit = 5;
task.wiederholungsTyp = WiederholungsTyp.INTERVALL;
task.wiederholungsWert = 3;
task.wiederholungsEinheit = ZeitEinheit.TAG;
// → Erscheint alle 3 Tage, egal ob erledigt oder nicht
```

### Wiederkehrende Task (Zeitpunkt)

```java
Task task = new Task();
task.titel = "Müll rausbringen";
task.wichtigkeit = 6;
task.wiederholungsTyp = WiederholungsTyp.ZEITPUNKT;
task.wiederholungsDetails = "DI,FR";
task.wiederholungsEinheit = ZeitEinheit.WOCHE;
// → Erscheint jeden Dienstag und Freitag, egal ob erledigt
```

### Wiederkehrende Task (Frequenz)

```java
Task task = new Task();
task.titel = "Sport machen";
task.wichtigkeit = 9;
task.wiederholungsTyp = WiederholungsTyp.FREQUENZ;
task.wiederholungsWert = 3;
task.wiederholungsEinheit = ZeitEinheit.WOCHE;
// → 3x pro Woche, Zeitpunkt flexibel
```

### Task mit Completion (nach Erledigung wartet X Zeit)

```java
Task task = new Task();
task.titel = "Haare schneiden";
task.wichtigkeit = 4;
task.wiederholungsTyp = WiederholungsTyp.KEINE;  // Wichtig!
task.completionTyp = CompletionTyp.INTERVALL;
task.completionWert = 6;
task.completionEinheit = ZeitEinheit.WOCHE;
// → Nach Erledigung dauert es 6 Wochen bis Task wieder erscheint
```

---

## Business Rules

### Streak-Logik

1. **Streak-Update:** Bei Erledigung innerhalb des Intervalls: `streak++`
2. **Streak-Reset:** Bei verpasstem Intervall: `streak = 1` (aktuelle Erledigung zählt)
3. **Streak bei FREQUENZ:** +1 nach Erreichen des Periodenziels (z.B. 3/3 pro Woche erreicht)

### Überfälligkeit

Eine Task gilt als überfällig:
- **INTERVALL:** Ab Tag X+1 nach letzter Erledigung (ohne neue Erledigung)
- **ZEITPUNKT:** Ab dem Tag nach dem geplanten Tag (z.B. Mittwoch wenn Dienstag verpasst)
- **FREQUENZ:** Wenn mathematisch unmöglich (z.B. Freitag bei 0/3 pro Woche)
- **Einmalig mit Frist:** Ab `heute > frist`

### Beziehungen

1. **Automatische Persona-Zuordnung:** Bei Zuordnung zu Ziel werden Personas des Ziels übernommen (via ZuordnungsUseCase)

---

## Siehe auch

- [Persona.md](Persona.md)
- [Ziel.md](Ziel.md)
- [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md)
