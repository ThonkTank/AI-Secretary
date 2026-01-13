# Service: TaskChainLearning

## Beschreibung

Lernt, welche Tasks häufig aufeinander folgen, und nutzt diese Information zur Optimierung der ToDo-Liste.

---

## Konzept

### Problem

Benutzer haben oft natürliche Abfolgen von Tasks:
- Nach dem Aufstehen → Meditation → Frühstück → Emails
- Nach Sport → Duschen → Protein-Shake
- Nach Meeting → Notizen aufschreiben → Follow-ups erstellen

### Lösung

Das System trackt, welche Task nach welcher erledigt wird, und bevorzugt diese Abfolgen bei der Planung.

---

## Datenstruktur

In der Task-Entity:

```java
// Map: Nachfolger-TaskId → Anzahl der Vorkommen
Map<Long, Integer> nachfolgerHistory;
```

---

## Algorithmus

### Tracking

Bei jeder Task-Erledigung:

```java
void trackCompletion(Task completedTask, Task previousTask) {
    if (previousTask == null) return;

    // Zeitfenster prüfen (max 2 Stunden zwischen Tasks)
    long timeDiff = completedTask.getCompletionTime() -
                    previousTask.getCompletionTime();
    if (timeDiff > 2 * 60 * 60 * 1000) return; // > 2h

    // Counter erhöhen
    Map<Long, Integer> history = previousTask.getNachfolgerHistory();
    int count = history.getOrDefault(completedTask.getId(), 0);
    history.put(completedTask.getId(), count + 1);

    taskRepository.save(previousTask);
}
```

### Anwendung bei Scheduling

```java
float getChainBonus(Task candidate, Task previousTask) {
    if (previousTask == null) return 0;

    Map<Long, Integer> history = previousTask.getNachfolgerHistory();
    int count = history.getOrDefault(candidate.getId(), 0);

    // Logarithmischer Bonus (schnell ansteigend, dann abflachend)
    // 1x → 0.05, 5x → 0.11, 10x → 0.15, 50x → 0.25
    return (float) (Math.log(count + 1) / Math.log(200)) * 0.3f;
}
```

---

## Beispiel

### Tracking über Zeit

```
Tag 1: Meditation → Frühstück
       meditation.nachfolgerHistory = {frühstück: 1}

Tag 2: Meditation → Frühstück
       meditation.nachfolgerHistory = {frühstück: 2}

Tag 10: Meditation → Frühstück (10x)
        meditation.nachfolgerHistory = {frühstück: 10}
```

### Auswirkung auf Scheduling

```
Situation: Meditation gerade erledigt
Kandidaten: Frühstück (10x), Sport (2x), Emails (0x)

Scores (ohne Chain-Bonus):
- Frühstück: 80
- Sport: 90
- Emails: 70

Chain-Bonus:
- Frühstück: +15% (10x)
- Sport: +5% (2x)
- Emails: +0%

Finale Scores:
- Frühstück: 92 ← Wird priorisiert
- Sport: 94.5
- Emails: 70
```

---

## Decay-Mechanismus (Optional)

Um veraltete Muster zu vergessen:

```java
void applyDecay(Task task) {
    Map<Long, Integer> history = task.getNachfolgerHistory();

    for (Long key : history.keySet()) {
        int count = history.get(key);
        // 10% Decay pro Woche ohne Nutzung
        int newCount = (int) (count * 0.9);
        if (newCount == 0) {
            history.remove(key);
        } else {
            history.put(key, newCount);
        }
    }
}
```

---

## Siehe auch

- [TimePreferenceLearning.md](TimePreferenceLearning.md)
- [GenerateDailyTodoList.md](GenerateDailyTodoList.md)
- [Task.md](../../entities/Task.md)
