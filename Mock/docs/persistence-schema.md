# Persistenz- und Schema-Dokumentation (Mock Task Feature)

Dieses Dokument ergänzt die Architekturverträge in `Mock/src/...` um eine konkrete,
implementierungsnahe Persistenzspezifikation.

## 1) Physisches Persistenzschema

Die folgende Modellierung ist der Referenzzustand für relationale Stores (SQLite/Postgres).
Bei NoSQL-Backends müssen dieselben Felder, Constraints und Semantiken abgebildet werden.

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
