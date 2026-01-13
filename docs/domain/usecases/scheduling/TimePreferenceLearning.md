# Service: TimePreferenceLearning

## Beschreibung

Lernt, zu welchen Uhrzeiten der Benutzer bestimmte Tasks typischerweise erledigt, und nutzt diese Information zur Optimierung der ToDo-Liste.

---

## Konzept

### Problem

Benutzer haben natürliche Rhythmen:
- Morgens: Meditation, Sport
- Vormittags: Fokus-Arbeit
- Nachmittags: Meetings, Emails
- Abends: Lesen, Entspannung

### Lösung

Das System trackt, wann Tasks erledigt werden, und plant sie bevorzugt zu diesen Zeiten ein.

---

## Datenstruktur

In der Task-Entity:

```java
// Liste aller Completion-Zeitpunkte
List<Timestamp> completionHistory;
```

---

## Algorithmus

### Tracking

Bei jeder Task-Erledigung:

```java
void trackCompletion(Task task, Date completionTime) {
    task.getCompletionHistory().add(completionTime);

    // Optional: History auf letzte 30 Einträge begrenzen
    List<Timestamp> history = task.getCompletionHistory();
    if (history.size() > 30) {
        history.remove(0);
    }

    taskRepository.save(task);
}
```

### Präferenz-Berechnung

```java
TimePreference calculateTimePreference(Task task) {
    List<Timestamp> history = task.getCompletionHistory();
    if (history.isEmpty()) return null;

    // Uhrzeiten extrahieren (nur Stunden:Minuten)
    List<Integer> minutesOfDay = history.stream()
        .map(ts -> ts.getHours() * 60 + ts.getMinutes())
        .collect(toList());

    // Durchschnitt und Standardabweichung berechnen
    double mean = calculateMean(minutesOfDay);
    double stdDev = calculateStdDev(minutesOfDay, mean);

    return new TimePreference(
        (int) mean,      // Bevorzugte Zeit (Minuten seit Mitternacht)
        (int) stdDev     // Toleranz (wie strikt die Präferenz)
    );
}
```

### Anwendung bei Scheduling

```java
float getTimePreferenceBonus(Task task, TimeSlot slot) {
    TimePreference pref = calculateTimePreference(task);
    if (pref == null) return 0;

    int slotMinutes = slot.getStart().getHours() * 60 +
                      slot.getStart().getMinutes();
    int prefMinutes = pref.getPreferredTime();

    // Differenz zur bevorzugten Zeit
    int diff = Math.abs(slotMinutes - prefMinutes);

    // Bonus basierend auf Nähe zur Präferenz
    // 0 Min Diff → +20%, 60 Min Diff → +10%, 120 Min Diff → 0%
    float bonus = Math.max(0, 0.2f - (diff / 600f));

    // Confidence basierend auf Anzahl der Datenpunkte
    float confidence = Math.min(task.getCompletionHistory().size() / 10f, 1f);

    return bonus * confidence;
}
```

---

## Beispiel

### Tracking über Zeit

```
Tag 1: Sport erledigt um 07:15
       sport.completionHistory = [07:15]

Tag 2: Sport erledigt um 06:45
       sport.completionHistory = [07:15, 06:45]

Tag 10: Sport (10 Einträge, Durchschnitt 07:00)
        sport.completionHistory = [07:15, 06:45, 07:00, ...]
        → TimePreference(420, 30) // 07:00 ± 30 Min
```

### Auswirkung auf Scheduling

```
Situation: Freie Slots um 07:00, 12:00, 18:00
Task: Sport (Präferenz: 07:00)

Bonus pro Slot:
- 07:00: +20% (perfekte Übereinstimmung)
- 12:00: +0% (5h Differenz)
- 18:00: +0% (11h Differenz)

→ Sport wird für 07:00 eingeplant
```

---

## Zeitfenster-Clustering (Erweitert)

Für Tasks, die zu verschiedenen Zeiten erledigt werden:

```java
List<TimePreference> calculateMultiplePreferences(Task task) {
    List<Timestamp> history = task.getCompletionHistory();

    // K-Means Clustering auf Uhrzeiten
    List<Cluster> clusters = kMeansClustering(history, k=3);

    return clusters.stream()
        .filter(c -> c.size() >= 3) // Mindestens 3 Datenpunkte
        .map(c -> new TimePreference(c.getMean(), c.getStdDev()))
        .collect(toList());
}
```

Beispiel:
- Sport: Cluster bei 07:00 (Werktage) und 10:00 (Wochenende)

---

## Siehe auch

- [TaskChainLearning.md](TaskChainLearning.md)
- [GenerateDailyTodoList.md](GenerateDailyTodoList.md)
- [Task.md](../../entities/Task.md)
