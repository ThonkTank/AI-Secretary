# Use Case: GenerateDailyTodoList

## Beschreibung

Generiert eine automatisch sortierte ToDo-Liste für den Tag, unter Berücksichtigung von Kalendertermine, Wichtigkeit, Fälligkeit und gelernten Präferenzen.

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `date` | Date | ✅ | Datum für die ToDo-Liste |
| `calendarEvents` | List<CalendarEvent> | ❌ | Kalendertermine des Tages |

---

## Output

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `todoList` | List<ScheduledTask> | Sortierte Liste mit Zeitslots |

---

## Ablauf

```
1. Alle offenen Tasks laden
   └── Tasks mit fälligkeit <= date ODER ohne fälligkeit

2. Kalender-Integration
   ├── Kalendertermine importieren
   └── 1 Stunde Puffer um jeden Termin markieren

3. Freie Zeit-Slots identifizieren
   └── Tageszeit - Termine - Puffer = verfügbare Slots

4. Tasks priorisieren
   ├── Primär: Wichtigkeit (absteigend)
   └── Sekundär: Fälligkeit (aufsteigend)

5. Gelernte Präferenzen anwenden
   ├── Zeitpunkt-Präferenz: Tasks zu "üblichen" Zeiten
   └── Task-Ketten: Häufige Abfolgen beibehalten

6. Tasks in Slots einplanen
   └── Bearbeitungszeit der Tasks berücksichtigen

7. Sortierte Liste zurückgeben
```

---

## Kalender-Integration (Detail)

```java
List<TimeSlot> calculateFreeSlots(Date date, List<CalendarEvent> events) {
    // Tagesbeginn und -ende (z.B. 8:00 - 22:00)
    Time dayStart = new Time(8, 0);
    Time dayEnd = new Time(22, 0);

    List<TimeSlot> blockedSlots = new ArrayList<>();

    for (CalendarEvent event : events) {
        // 1 Stunde Puffer vor und nach dem Termin
        Time bufferStart = event.getStart().minus(1, HOUR);
        Time bufferEnd = event.getEnd().plus(1, HOUR);

        blockedSlots.add(new TimeSlot(bufferStart, bufferEnd));
    }

    return invertSlots(dayStart, dayEnd, blockedSlots);
}
```

---

## Priorisierungs-Algorithmus

### Basis-Score

```java
float calculatePriorityScore(Task task, Date today) {
    float score = 0;

    // Wichtigkeit (0-100 Punkte)
    score += task.getWichtigkeit() * 10;

    // Dringlichkeit (0-100 Punkte)
    if (task.getFrist() != null) {
        int daysUntilDue = daysBetween(today, task.getFrist());
        if (daysUntilDue <= 0) {
            score += 100; // Überfällig
        } else if (daysUntilDue <= 3) {
            score += 80; // Bald fällig
        } else if (daysUntilDue <= 7) {
            score += 50; // Diese Woche
        }
    }

    return score;
}
```

### Präferenz-Modifier

```java
float applyPreferences(Task task, TimeSlot slot, float baseScore) {
    float modifier = 1.0f;

    // Zeitpunkt-Präferenz
    float timeMatch = calculateTimePreferenceMatch(task, slot);
    modifier += timeMatch * 0.2f; // bis zu +20%

    // Task-Ketten
    if (previousTask != null) {
        int chainCount = previousTask.getNachfolgerHistory()
            .getOrDefault(task.getId(), 0);
        modifier += Math.min(chainCount * 0.05f, 0.3f); // bis zu +30%
    }

    return baseScore * modifier;
}
```

---

## Slot-Zuweisung

```java
List<ScheduledTask> assignTasksToSlots(
    List<Task> prioritizedTasks,
    List<TimeSlot> freeSlots
) {
    List<ScheduledTask> result = new ArrayList<>();

    for (TimeSlot slot : freeSlots) {
        int remainingMinutes = slot.getDurationMinutes();

        for (Task task : prioritizedTasks) {
            if (task.isScheduled()) continue;

            int taskDuration = task.getBearbeitungszeit();
            if (taskDuration == 0) taskDuration = 30; // Default

            if (taskDuration <= remainingMinutes) {
                result.add(new ScheduledTask(task, slot.getStart()));
                task.setScheduled(true);
                remainingMinutes -= taskDuration;
                slot.advanceStart(taskDuration);
            }
        }
    }

    return result;
}
```

---

## Output-Format

```java
class ScheduledTask {
    Task task;
    Time scheduledTime;     // Geplante Startzeit
    int estimatedDuration;  // Geschätzte Dauer
    float priorityScore;    // Berechneter Score
}
```

---

## Beispiel

```java
GenerateDailyTodoListUseCase useCase = new GenerateDailyTodoListUseCase(
    taskRepository,
    calendarService,
    preferenceLearningService
);

List<CalendarEvent> events = calendarService.getEvents(today);
List<ScheduledTask> todoList = useCase.execute(today, events);

// Beispiel-Output:
// 08:00 - Meditation (30 Min) - Score: 95
// 08:30 - Emails checken (30 Min) - Score: 85
// 10:00 - Kalendertermin: Meeting
// 12:00 - Sport (60 Min) - Score: 90
// ...
```

---

## Siehe auch

- [TaskChainLearning.md](TaskChainLearning.md)
- [TimePreferenceLearning.md](TimePreferenceLearning.md)
- [Task.md](../../entities/Task.md)
