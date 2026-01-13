# Entity: Task

## Beschreibung

Eine Task repräsentiert eine einzelne Aufgabe, die der Benutzer erledigen möchte.

---

## Felder

| Feld | Typ | Pflicht | Beschreibung |
|------|-----|---------|--------------|
| `id` | Long | ✅ | Primary Key (auto-generated) |
| `beschreibung` | String | ✅ | Aufgabenbeschreibung |
| `streak` | Int | ✅ | Aktueller Streak (Default: 0) |
| `wichtigkeit` | Int | ✅ | Wichtigkeit für Sortierung (1-10) |
| `letztesmalErledigt` | Date | ❌ | Zeitpunkt der letzten Erledigung |
| `frist` | Date | ❌ | Optionale Deadline |
| `bearbeitungszeit` | Int | ❌ | Geschätzte Dauer in Minuten |

### Wiederholung

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `wiederholungsTyp` | Enum | KEINE, TIMER, ZEITPUNKT |
| `wiederholungsWert` | Int | X (Anzahl) |
| `wiederholungsEinheit` | Enum | TAG, WOCHE, MONAT |
| `wiederholungsDetails` | String | Für Zeitpunkte (z.B. "DI", "2.DI") |

### Completion-Metriken

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `completionTyp` | Enum | KEINE, FREQUENZ, ZEIT |
| `completionWert` | Int | X (Anzahl/Minuten) |
| `completionEinheit` | Enum | TAG, WOCHE, MONAT |

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
    KEINE,      // Einmalige Aufgabe
    TIMER,      // Alle X Tage/Wochen/Monate
    ZEITPUNKT   // Jeden (zweiten) Dienstag etc.
}
```

### WiederholungsEinheit

```java
enum WiederholungsEinheit {
    TAG,
    WOCHE,
    MONAT
}
```

### CompletionTyp

```java
enum CompletionTyp {
    KEINE,      // Einfaches Abhaken
    FREQUENZ,   // X mal pro Zeitraum
    ZEIT        // X Minuten pro Zeitraum
}
```

---

## Beispiele

### Einfache Task

```java
Task task = new Task();
task.beschreibung = "Steuererklärung machen";
task.wichtigkeit = 8;
task.frist = Date.parse("2025-03-31");
task.wiederholungsTyp = WiederholungsTyp.KEINE;
```

### Wiederkehrende Task (Timer)

```java
Task task = new Task();
task.beschreibung = "Wäsche waschen";
task.wichtigkeit = 5;
task.wiederholungsTyp = WiederholungsTyp.TIMER;
task.wiederholungsWert = 3;
task.wiederholungsEinheit = WiederholungsEinheit.TAG;
// → Alle 3 Tage
```

### Wiederkehrende Task (Zeitpunkt)

```java
Task task = new Task();
task.beschreibung = "Müll rausbringen";
task.wichtigkeit = 6;
task.wiederholungsTyp = WiederholungsTyp.ZEITPUNKT;
task.wiederholungsDetails = "DI,FR";
task.wiederholungsEinheit = WiederholungsEinheit.WOCHE;
// → Jeden Dienstag und Freitag
```

### Habit mit Completion-Metrik

```java
Task task = new Task();
task.beschreibung = "Sport machen";
task.wichtigkeit = 9;
task.completionTyp = CompletionTyp.FREQUENZ;
task.completionWert = 3;
task.completionEinheit = WiederholungsEinheit.WOCHE;
// → 3x pro Woche
```

---

## Business Rules

1. **Streak-Update:** Bei Erledigung innerhalb des Intervalls: `streak++`
2. **Streak-Reset:** Bei verpasstem Intervall: `streak = 0`
3. **Automatische Persona-Zuordnung:** Bei Zuordnung zu Ziel werden Personas des Ziels übernommen

---

## Siehe auch

- [Persona.md](Persona.md)
- [Ziel.md](Ziel.md)
- [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md)
