# Use Case: CompleteTask

## Beschreibung

Markiert eine Task als erledigt und führt alle damit verbundenen Aktionen aus (Streak-Update, XP-Vergabe, Wiederholungs-Scheduling).

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `taskId` | Long | ✅ | ID der zu erledigenden Task |
| `timestamp` | Date | ❌ | Erledigungszeitpunkt (Default: jetzt) |

---

## Output

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `result` | CompleteTaskResult | Ergebnis mit XP, Streak, nächste Fälligkeit |

---

## Ablauf

```
1. Task laden
   └── TaskRepository.findById(taskId)

2. Streak aktualisieren
   ├── Wenn innerhalb Intervall: streak++
   └── Wenn außerhalb Intervall: streak = 1 (Neustart)

3. XP berechnen
   ├── Basis-XP: 10 × wichtigkeit
   ├── Streak-Bonus: 5 × streak (max 50)
   └── Überfälligkeits-Bonus: 10 × überfälligeTage (max 100)

4. XP an Personas verteilen
   └── Für jede zugeordnete Persona: xp += totalXP / anzahlPersonas

5. Completion-History aktualisieren
   └── task.completionHistory.add(timestamp)

6. Nachfolger-History aktualisieren (falls vorherige Task bekannt)
   └── previousTask.nachfolgerHistory.put(taskId, count + 1)

7. Nächste Fälligkeit berechnen (bei Wiederholung)
   ├── INTERVALL: letztesmalErledigt + wiederholungsWert × einheit
   ├── ZEITPUNKT: nächster passender Tag
   └── FREQUENZ: Status aktualisieren (X/Y pro Zeitraum)

8. Task aktualisieren
   ├── letztesmalErledigt = timestamp
   └── TaskRepository.save(task)

9. Personas aktualisieren
   └── PersonaRepository.saveAll(personas)

10. Ergebnis zurückgeben
```

---

## XP-Berechnung (Detail)

```java
int calculateXP(Task task, Date completionTime) {
    // Basis-XP
    int baseXP = 10 * task.getWichtigkeit();

    // Streak-Bonus (max 50)
    int streakBonus = Math.min(5 * task.getStreak(), 50);

    // Überfälligkeits-Bonus (max 100)
    int overdueDays = 0;
    if (task.getFrist() != null && completionTime.after(task.getFrist())) {
        overdueDays = daysBetween(task.getFrist(), completionTime);
    }
    int overdueBonus = Math.min(10 * overdueDays, 100);

    return baseXP + streakBonus + overdueBonus;
}
```

---

## Streak-Logik (Detail)

Die Streak-Logik hängt vom WiederholungsTyp ab:

### Bei INTERVALL / ZEITPUNKT

```java
void updateStreak(Task task, Date completionTime) {
    if (task.getLetztesmalErledigt() == null) {
        // Erste Erledigung
        task.setStreak(1);
        return;
    }

    Date expectedDate = calculateNextDueDate(task);

    if (completionTime.before(expectedDate) ||
        completionTime.equals(expectedDate)) {
        // Innerhalb des Intervalls - Streak erhöhen
        task.setStreak(task.getStreak() + 1);
    } else {
        // Intervall verpasst - Streak zurücksetzen auf 1 (aktuelle Erledigung zählt)
        task.setStreak(1);
    }
}
```

### Bei FREQUENZ

```java
void updateStreakFrequenz(Task task, Date completionTime) {
    int currentCount = getCompletionsInCurrentPeriod(task);
    int target = task.getWiederholungsWert();

    if (currentCount + 1 >= target) {
        // Periodenziel erreicht - Streak erhöhen
        task.setStreak(task.getStreak() + 1);
    }
    // Bei Nicht-Erreichen am Periodenende: streak = 1
}
```

---

## Fehlerbehandlung

| Fehler | Beschreibung | Reaktion |
|--------|--------------|----------|
| `TaskNotFoundException` | Task-ID existiert nicht | Abbruch |

---

## Beispiel

```java
CompleteTaskUseCase useCase = new CompleteTaskUseCase(
    taskRepository,
    personaRepository,
    streakService,
    xpCalculationService
);

CompleteTaskResult result = useCase.execute(taskId);

// result.earnedXP = 105
// result.newStreak = 6
// result.nextDueDate = 2025-01-15
// result.levelUps = ["Sportler: Level 3 → 4"]
```

---

## Siehe auch

- [Task.md](../entities/Task.md)
- [Persona.md](../entities/Persona.md)
- [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md)
