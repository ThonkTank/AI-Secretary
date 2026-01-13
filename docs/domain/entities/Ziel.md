# Entity: Ziel

## Beschreibung

Ein Ziel ist eine übergeordnete Einheit, die mehrere zusammengehörige Tasks bündelt. Im GTD-System entspricht ein Ziel einem "Projekt".

---

## Felder

| Feld | Typ | Pflicht | Beschreibung |
|------|-----|---------|--------------|
| `id` | Long | ✅ | Primary Key (auto-generated) |
| `beschreibung` | String | ✅ | Ziel-Beschreibung |
| `frist` | Date | ❌ | Optionale Deadline |

### Wiederholung

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `wiederholungsTyp` | Enum | KEINE, TIMER, ZEITPUNKT |
| `wiederholungsWert` | Int | X (Anzahl) |
| `wiederholungsEinheit` | Enum | TAG, WOCHE, MONAT |
| `wiederholungsDetails` | String | Für Zeitpunkte |

### Completion-Metriken

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `completionTyp` | Enum | KEINE, FREQUENZ, ZEIT, TASKS |
| `completionWert` | Int | X (Anzahl/Minuten/Tasks) |
| `completionEinheit` | Enum | TAG, WOCHE, MONAT |

### Beziehungen

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `personas` | List<Persona> | Zugehörige Personas (Many-to-Many) |
| `tasks` | List<Task> | Zugehörige Tasks (Many-to-Many) |

---

## Enums

### CompletionTyp (erweitert für Ziele)

```java
enum CompletionTyp {
    KEINE,      // Kein Tracking
    FREQUENZ,   // X mal pro Zeitraum
    ZEIT,       // X Minuten pro Zeitraum
    TASKS       // X zugehörige Tasks pro Zeitraum (NUR für Ziele!)
}
```

---

## Unterschied zu Tasks

| Aspekt | Task | Ziel |
|--------|------|------|
| Granularität | Einzelne Aktion | Sammlung von Tasks |
| Completion TASKS | ❌ | ✅ |
| Direkte Erledigung | ✅ | Über Tasks |
| XP-Vergabe | Direkt | Über zugehörige Tasks |

---

## Beispiele

### Einfaches Ziel

```java
Ziel ziel = new Ziel();
ziel.beschreibung = "Buch 'Clean Code' lesen";
ziel.frist = Date.parse("2025-06-30");
ziel.completionTyp = CompletionTyp.KEINE;
```

### Ziel mit Frequenz-Metrik

```java
Ziel ziel = new Ziel();
ziel.beschreibung = "Fitness-Routine";
ziel.completionTyp = CompletionTyp.FREQUENZ;
ziel.completionWert = 4;
ziel.completionEinheit = WiederholungsEinheit.WOCHE;
// → 4x pro Woche zugehörige Tasks erledigen
```

### Ziel mit Zeit-Metrik

```java
Ziel ziel = new Ziel();
ziel.beschreibung = "Spanisch lernen";
ziel.completionTyp = CompletionTyp.ZEIT;
ziel.completionWert = 60;
ziel.completionEinheit = WiederholungsEinheit.TAG;
// → 60 Minuten pro Tag
```

### Ziel mit Tasks-Metrik

```java
Ziel ziel = new Ziel();
ziel.beschreibung = "Hausputz";
ziel.completionTyp = CompletionTyp.TASKS;
ziel.completionWert = 5;
ziel.completionEinheit = WiederholungsEinheit.WOCHE;
// → 5 zugehörige Tasks pro Woche erledigen
```

---

## Automatische Zuordnungen

### Regel 1: Task → Ziel

Wenn eine Task einem Ziel zugeordnet wird:
→ Task wird automatisch allen Personas des Ziels zugeordnet

```java
// Ziel ist Persona "Sportler" zugeordnet
task.addZiel(ziel);
// → task wird automatisch auch "Sportler" zugeordnet
```

### Regel 2: Ziel → Persona

Wenn ein Ziel einer Persona zugeordnet wird:
→ Alle Tasks des Ziels werden der Persona zugeordnet

```java
// Ziel hat 3 Tasks
ziel.addPersona(persona);
// → Alle 3 Tasks werden auch persona zugeordnet
```

---

## Fortschritts-Berechnung

### Bei FREQUENZ/ZEIT/TASKS

```java
// Aktueller Fortschritt im Zeitraum
int current = calculateCurrentProgress(ziel);
int target = ziel.completionWert;
float progress = (float) current / target * 100;
```

### Beispiel

```
Ziel: "Fitness-Routine" (4x pro Woche)
Montag: 1 Task erledigt → 25% (1/4)
Mittwoch: 1 Task erledigt → 50% (2/4)
Freitag: 2 Tasks erledigt → 100% (4/4) ✅
```

---

## Siehe auch

- [Task.md](Task.md)
- [Persona.md](Persona.md)
- [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md)
