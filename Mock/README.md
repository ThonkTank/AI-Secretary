# Mock Task Feature – Zielarchitektur (timeslot-basiertes Experiment)

Dieser Ordner beschreibt die geplante Architektur für ein Bucket-basiertes Scheduling-Experiment.
Alle Dateien sind **Implementations-Platzhalter**: Die Datei, die später realen Code enthält,
existiert hier schon als Vertrag + Integrationsdokumentation.

## Ziel

- Tagesplanung läuft **1× täglich nachts** (ähnlich Main).
- Planung ist **kalender- und zeitbewusst** auf Bucket-Ebene.
- Scoring nutzt Main-ähnliche Kriterien, aber **ohne Uhrzeit-Fit**.
- Bucket-Füllung erfolgt greedy (höchster Score zuerst).
- Tasks dürfen in mehrere Buckets passen (Unsicherheit erlaubt).
- Wird eine mehrfach geplante Task früh erledigt, wird sie aus späteren Buckets entfernt und Lücken werden nachgefüllt.

## Warum diese Dokumentation so detailliert ist

Jede Datei dokumentiert:
1. **Wer** sie aufruft (Caller),
2. **Wie** sie aufgerufen wird (Call-Sequenz + Datenfluss),
3. **Warum** genau diese Parameter benötigt werden,
4. **Wo/Wie/Warum** sie selbst fremde APIs benötigt.

So können Implementierer Signaturen bauen, die direkt verwertbare Daten liefern,
statt „zufällige“ Rohdaten weiterzureichen, aus denen Caller erst wieder Nutzdaten ableiten müssen.

## Haupt-Callchains

### A) Nightly Daily Planning
1. `PlanningTriggerGateway.onNightlyTrigger()` startet den Lauf.
2. `TaskReadGateway.readPlanningCandidates(day)` lädt Task-Kandidaten (expliziter **Task-Read-Pfad**).
3. `BucketWindowConfigGateway.readBucketWindowsForDay(day)` lädt effektive Bucket-Zeitfenster.
4. `CalendarAvailabilityGateway.readBlockedIntervals(day)` lädt Kalenderevents.
5. `BucketCapacityGateway.computeAvailableMinutes(...)` berechnet freie Minuten je Bucket.
6. `CompletionTrackingGateway.readCompletionHistory(...)` lädt Historie für Slot-Inferenz/Signale.
7. `TaskSlotAssignmentApi.assignEligibleSlots(tasks)` erzeugt Multi-Bucket-Eignung.
8. `TaskScoringApi.computeDailyScores(tasks, day)` erzeugt Scores.
9. `TaskPlanningApi.createBucketPlan(...)` erstellt den Tagesplan.
10. `PlanWriteGateway.saveDraftPlan(day, plan)` persistiert den Entwurf (**Plan-Write-Pfad**).
11. Optional: `TaskWriteGateway.upsertPlannedForDay(taskId, day)` persistiert Plan-Markierungen (**Task-Write-Pfad**).

### B) Completion-driven Cleanup + Refill
1. `CompletionEventIngestGateway.onTaskCompleted(...)` meldet Abschluss.
2. `TaskWriteGateway.markCompleted(taskId, finishedAt)` persistiert Task-Status (**Task-Write-Pfad**).
3. `CompletionTrackingGateway.appendCompletion(...)` persistiert Event.
4. `PlanReadGateway.readPlanForDay(day)` lädt aktuellen Tagesplan (**Plan-Read-Pfad**).
5. `TaskPlanningApi.replanAfterCompletion(plan, taskId)` entfernt späte Duplikate und füllt Lücken.
6. `PlanWriteGateway.overwritePlan(day, updatedPlan)` persistiert Update (**Plan-Write-Pfad**).
