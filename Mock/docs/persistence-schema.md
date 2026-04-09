# Persistenz- und Schema-Dokumentation (Mock Task Feature)

Dieses Dokument ergänzt die Architekturverträge in `Mock/src/...` um eine konkrete,
implementierungsnahe Persistenzspezifikation.

## 1) Physisches Persistenzschema

Die folgende Modellierung ist der Referenzzustand für relationale Stores (SQLite/Postgres).
Bei NoSQL-Backends müssen dieselben Felder, Constraints und Semantiken abgebildet werden.

### Tabelle `task_definition`

- `task_id` (VARCHAR, NOT NULL)
- `name` (VARCHAR, NOT NULL)
- `parent_task_id` (VARCHAR, NULL) – optionaler Parent in der Task-Hierarchie
- `is_active` (BOOLEAN, NOT NULL)
- `created_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)
- `updated_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)

**Constraints/Indizes**
- Primary Key: `(task_id)`
- Check: `trim(name) <> ''` (Name ist Pflichtfeld)
- Foreign Key: `parent_task_id -> task_definition.task_id` (self-reference)
- Index: `(parent_task_id)` für Hierarchie-Auflösungen

### Tabelle `task_recurrence_rule`

- `task_id` (VARCHAR, NOT NULL)
- `is_repeating` (BOOLEAN, NOT NULL)
- `every_n_units` (INT, NULL) – z. B. alle 2 Tage
- `recurrence_unit` (VARCHAR, NULL) – `DAY`, `WEEK`, `MONTH`
- `window_start_day` (DATE, NULL) – Start des aktiven Wiederholungszeitraums
- `window_end_day` (DATE, NULL) – optionales Ende des aktiven Wiederholungszeitraums
- `updated_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)

**Constraints/Indizes**
- Primary Key: `(task_id)`
- Foreign Key: `task_id -> task_definition.task_id`
- Check: bei `is_repeating = true` müssen `every_n_units >= 1` und `recurrence_unit` gesetzt sein
- Check: `window_end_day >= window_start_day` falls beide gesetzt

### Tabelle `task_bucket_plan`

- `day` (DATE, NOT NULL)
- `revision` (BIGINT, NOT NULL)
- `payload_json` (TEXT/JSONB, NOT NULL) – serialisiertes `BucketPlan`
- `created_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)
- `updated_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)
- `writer_idempotency_key` (VARCHAR, NOT NULL)

**Constraints/Indizes**
- Primary Key: `(day)`
- Optimistic-Locking-Feld: `revision` (muss bei Overwrite mit `expectedRevision` matchen)
- Unique: `(day, writer_idempotency_key)` zur Deduplikation wiederholter Writes
- Index: `(updated_at_utc DESC)` für Operability/Diagnose

### Tabelle `task_completion_event`

- `event_id` (VARCHAR, NOT NULL)
- `task_id` (VARCHAR, NOT NULL)
- `finished_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)
- `source` (VARCHAR, NOT NULL) – z. B. `USER_CHECKOFF`, `IMPORT`, `SYNC`
- `idempotency_key` (VARCHAR, NOT NULL)
- `ingested_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)

**Constraints/Indizes**
- Primary Key: `(event_id)`
- Unique: `(idempotency_key)` zur Event-Deduplikation
- Index: `(task_id, finished_at_utc DESC)` für History-Lookups
- Index: `(finished_at_utc DESC)` für Zeitfenster-Abfragen

### Tabelle `task_planned_marker`

- `task_id` (VARCHAR, NOT NULL)
- `day` (DATE, NOT NULL)
- `planned_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)

**Constraints/Indizes**
- Primary Key: `(task_id, day)`
- Index: `(day)`

### Tabelle `task_completion_streak_snapshot`

- `task_id` (VARCHAR, NOT NULL)
- `current_streak_days` (INT, NOT NULL)
- `longest_streak_days` (INT, NOT NULL)
- `last_completed_at_utc` (TIMESTAMP WITH TIME ZONE, NULL)
- `completion_count_7d` (INT, NOT NULL)
- `completion_count_30d` (INT, NOT NULL)
- `completion_count_total` (BIGINT, NOT NULL)
- `updated_at_utc` (TIMESTAMP WITH TIME ZONE, NOT NULL)

**Constraints/Indizes**
- Primary Key: `(task_id)`
- Foreign Key: `task_id -> task_definition.task_id`
- Check: alle Counts/Streaks `>= 0`
- Index: `(updated_at_utc DESC)`

## 2) Serialisierungs-/Payload-Schema

`task_bucket_plan.payload_json` speichert ein versioniertes JSON-Dokument:

```json
{
  "schemaVersion": 1,
  "day": "2026-04-09",
  "entriesBySlot": {
    "MORGEN": [
      {
        "taskId": "task-123",
        "score": 0.92,
        "plannedMinutes": 30,
        "source": "NIGHTLY_SEED"
      }
    ],
    "VORMITTAG": [],
    "MITTAG": [],
    "NACHMITTAG": [],
    "ABEND": []
  },
  "remainingMinutesBySlot": {
    "MORGEN": 20,
    "VORMITTAG": 45,
    "MITTAG": 60,
    "NACHMITTAG": 30,
    "ABEND": 25
  }
}
```

**Vertragsregeln**
- `schemaVersion` ist Pflichtfeld und startet bei `1`.
- Alle `TimeSlot`-Keys sind verpflichtend vorhanden.
- `plannedMinutes >= 0`, `remainingMinutesBySlot[*] >= 0`.
- Unbekannte Felder dürfen gelesen, aber beim Schreiben nicht gelöscht werden (forward compatibility).

## 3) Zeitformat und Zeitzonenregel

Alle technischen Zeitstempel werden als UTC-Instants behandelt:

- Java-Vertragstyp: `java.time.Instant`
- Serialisierung: RFC 3339 / ISO-8601 mit `Z`-Suffix  
  Beispiel: `2026-04-09T21:15:33Z`
- Keine lokalen Offsets in Persistenz-Events.
- Ableitung auf `LocalDate` für Tageslogik erfolgt ausschließlich über die App-Zeitzone im Application-Layer.

## 4) Completion-History als typisiertes Event

Completion-History wird nicht mehr als `List<String>`, sondern als Liste strukturierter
Events modelliert (siehe `CompletionEvent`):

- `eventId`
- `taskId`
- `finishedAtUtc`
- `source`
- `idempotencyKey`
- `ingestedAtUtc`

Damit sind Auditing, Replays und eindeutige Deduplikation stabil spezifiziert.

## 4.1) Abgeleitete Completion-Statistik für Scoring

Neben vollständigen Events wird pro Task ein Snapshot persistiert (`task_completion_streak_snapshot`),
damit Scoring ohne Vollscan großer Historien auf häufig benötigte Merkmale zugreifen kann:

- aktuelle Streak-Länge (`current_streak_days`)
- längste historische Streak (`longest_streak_days`)
- letzter Abschlusszeitpunkt (`last_completed_at_utc`)
- Aktivität in 7/30 Tagen (`completion_count_7d`, `completion_count_30d`)
- Gesamtanzahl (`completion_count_total`)

## 5) Idempotenz- und Konsistenzregeln

### Plan-Writes (`saveDraftPlan`, `overwritePlan`)

1. **saveDraftPlan**
   - Muss idempotent sein über `(day, idempotencyKey)`.
   - Bei gleichem Key darf kein zweiter Datenzustand erzeugt werden.

2. **overwritePlan**
   - Muss mit Optimistic Locking arbeiten (`expectedRevision`).
   - Update nur erlaubt, wenn `currentRevision == expectedRevision`.
   - Bei Mismatch: `OptimisticLocking`-Fehler an Caller zurückgeben.
   - Erfolgreiches Update inkrementiert `revision` genau um `+1`.

### Completion-Events

- Doppelte Ingests desselben Nutzer-Events müssen über `idempotencyKey` dedupliziert werden.
- Reihenfolge ist über `finishedAtUtc` fachlich, über `ingestedAtUtc` technisch nachvollziehbar.
- Nach erfolgreichem Event-Ingest wird `task_completion_streak_snapshot` atomar mit aktualisiert
  (oder im selben Outbox-Flow deterministisch nachgezogen), damit Streak-/Scoringdaten konsistent bleiben.
