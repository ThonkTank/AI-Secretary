# Entity: Persona

## Beschreibung

Eine Persona repräsentiert eine Ziel-Identität des Benutzers - wer er sein möchte. Im Gamification-System entspricht eine Persona einer "Klasse" (RPG-Terminologie).

---

## Konzept

### Psychologischer Hintergrund

Basierend auf der **EPos-Methode** (Motivationspsychologie):
1. User formuliert eine **Utopie** (Ziel-Zustand: Wer will ich sein?)
2. Diese Ziel-Person bekommt einen **Titel** (die Persona)
3. Aufgaben werden als "Quests" der Persona zugeordnet

### RPG-Analogie

| App-Konzept | RPG-Äquivalent |
|-------------|----------------|
| Persona | Klasse |
| Task | Quest |
| XP | Experience Points |
| Level | Character Level |

---

## Felder

| Feld | Typ | Pflicht | Beschreibung |
|------|-----|---------|--------------|
| `id` | Long | ✅ | Primary Key (auto-generated) |
| `beschreibung` | String | ✅ | Persona-Titel und Utopie-Beschreibung |
| `xp` | Int | ✅ | Gesammelte Experience Points (Default: 0) |
| `level` | Int | ✅ | Aktuelles Level (Default: 0) |

### Beziehungen

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `tasks` | List<Task> | Zugehörige Tasks (Many-to-Many) |
| `ziele` | List<Ziel> | Zugehörige Ziele (Many-to-Many) |

---

## Level-System

### Berechnung

```java
level = (int) Math.floor(Math.sqrt(xp / 100.0));
```

### Level-Tabelle

| XP-Bereich | Level |
|------------|-------|
| 0 - 99 | 0 |
| 100 - 399 | 1 |
| 400 - 899 | 2 |
| 900 - 1599 | 3 |
| 1600 - 2499 | 4 |
| 2500 - 3599 | 5 |
| ... | ... |

### XP für nächstes Level

```java
xpForNextLevel = (level + 1) * (level + 1) * 100;
```

---

## XP-Vergabe

### Basis-XP pro Task

```java
baseXP = 10 * task.wichtigkeit;
```

### Bonus-XP

| Bonus | Berechnung | Maximum |
|-------|------------|---------|
| Streak-Bonus | `5 * streak` | 50 |
| Überfälligkeits-Bonus | `10 * überfälligeTage` | 100 |

### Aufteilung bei mehreren Personas

Wenn eine Task mehreren Personas zugeordnet ist:

```java
xpProPersona = totalXP / anzahlPersonas;
```

---

## Beispiele

### Persona erstellen

```java
Persona persona = new Persona();
persona.beschreibung = "Der Sportler - Fit, gesund, energiegeladen";
persona.xp = 0;
persona.level = 0;
```

### XP gutschreiben

```java
// Task "30 Min Joggen" mit Wichtigkeit 8, Streak 5
int baseXP = 10 * 8;           // 80 XP
int streakBonus = 5 * 5;       // 25 XP
int totalXP = 80 + 25;         // 105 XP

persona.xp += totalXP;
persona.level = calculateLevel(persona.xp);
```

---

## Business Rules

1. **XP sind permanent:** XP können nicht verloren werden
2. **Level steigt automatisch:** Wird bei jeder XP-Änderung neu berechnet
3. **Automatische Task-Zuordnung:** Bei Ziel-Zuordnung werden alle Tasks des Ziels der Persona zugeordnet

---

## Siehe auch

- [Task.md](Task.md)
- [Ziel.md](Ziel.md)
- [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md)
