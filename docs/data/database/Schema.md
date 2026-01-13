# Datenbank Schema

## Übersicht

SQLite-Datenbank mit 6 Tabellen für Entities und Beziehungen.

---

## Entity-Tabellen

### tasks

| Spalte | Typ | Constraint | Beschreibung |
|--------|-----|------------|--------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Eindeutige ID |
| `beschreibung` | TEXT | NOT NULL | Task-Beschreibung |
| `streak` | INTEGER | NOT NULL DEFAULT 0 | Aktueller Streak |
| `wichtigkeit` | INTEGER | NOT NULL DEFAULT 5 | Wichtigkeit (1-10) |
| `letztes_mal_erledigt` | INTEGER | NULL | Timestamp (ms) |
| `frist` | INTEGER | NULL | Deadline Timestamp |
| `bearbeitungszeit` | INTEGER | NULL | Minuten |
| `wiederholungs_typ` | TEXT | NOT NULL DEFAULT 'KEINE' | KEINE/TIMER/ZEITPUNKT |
| `wiederholungs_wert` | INTEGER | NULL | X (Anzahl) |
| `wiederholungs_einheit` | TEXT | NULL | TAG/WOCHE/MONAT |
| `wiederholungs_details` | TEXT | NULL | z.B. "DI,FR" |
| `completion_typ` | TEXT | NOT NULL DEFAULT 'KEINE' | KEINE/FREQUENZ/ZEIT |
| `completion_wert` | INTEGER | NULL | X (Anzahl/Minuten) |
| `completion_einheit` | TEXT | NULL | TAG/WOCHE/MONAT |
| `completion_history` | TEXT | NULL | JSON Array [timestamp, ...] |
| `nachfolger_history` | TEXT | NULL | JSON Object {taskId: count, ...} |
| `created_at` | INTEGER | NOT NULL | Erstellungszeitpunkt |
| `updated_at` | INTEGER | NOT NULL | Letzte Änderung |

```sql
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    beschreibung TEXT NOT NULL,
    streak INTEGER NOT NULL DEFAULT 0,
    wichtigkeit INTEGER NOT NULL DEFAULT 5,
    letztes_mal_erledigt INTEGER,
    frist INTEGER,
    bearbeitungszeit INTEGER,
    wiederholungs_typ TEXT NOT NULL DEFAULT 'KEINE',
    wiederholungs_wert INTEGER,
    wiederholungs_einheit TEXT,
    wiederholungs_details TEXT,
    completion_typ TEXT NOT NULL DEFAULT 'KEINE',
    completion_wert INTEGER,
    completion_einheit TEXT,
    completion_history TEXT,
    nachfolger_history TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

---

### personas

| Spalte | Typ | Constraint | Beschreibung |
|--------|-----|------------|--------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Eindeutige ID |
| `beschreibung` | TEXT | NOT NULL | Persona-Beschreibung |
| `xp` | INTEGER | NOT NULL DEFAULT 0 | Experience Points |
| `level` | INTEGER | NOT NULL DEFAULT 0 | Aktuelles Level |
| `created_at` | INTEGER | NOT NULL | Erstellungszeitpunkt |
| `updated_at` | INTEGER | NOT NULL | Letzte Änderung |

```sql
CREATE TABLE personas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    beschreibung TEXT NOT NULL,
    xp INTEGER NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

---

### ziele

| Spalte | Typ | Constraint | Beschreibung |
|--------|-----|------------|--------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Eindeutige ID |
| `beschreibung` | TEXT | NOT NULL | Ziel-Beschreibung |
| `frist` | INTEGER | NULL | Deadline Timestamp |
| `wiederholungs_typ` | TEXT | NOT NULL DEFAULT 'KEINE' | KEINE/TIMER/ZEITPUNKT |
| `wiederholungs_wert` | INTEGER | NULL | X (Anzahl) |
| `wiederholungs_einheit` | TEXT | NULL | TAG/WOCHE/MONAT |
| `wiederholungs_details` | TEXT | NULL | z.B. "DI,FR" |
| `completion_typ` | TEXT | NOT NULL DEFAULT 'KEINE' | KEINE/FREQUENZ/ZEIT/TASKS |
| `completion_wert` | INTEGER | NULL | X (Anzahl/Minuten/Tasks) |
| `completion_einheit` | TEXT | NULL | TAG/WOCHE/MONAT |
| `created_at` | INTEGER | NOT NULL | Erstellungszeitpunkt |
| `updated_at` | INTEGER | NOT NULL | Letzte Änderung |

```sql
CREATE TABLE ziele (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    beschreibung TEXT NOT NULL,
    frist INTEGER,
    wiederholungs_typ TEXT NOT NULL DEFAULT 'KEINE',
    wiederholungs_wert INTEGER,
    wiederholungs_einheit TEXT,
    wiederholungs_details TEXT,
    completion_typ TEXT NOT NULL DEFAULT 'KEINE',
    completion_wert INTEGER,
    completion_einheit TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

---

## Beziehungs-Tabellen (Many-to-Many)

### task_persona

| Spalte | Typ | Constraint | Beschreibung |
|--------|-----|------------|--------------|
| `task_id` | INTEGER | FOREIGN KEY | Referenz auf tasks.id |
| `persona_id` | INTEGER | FOREIGN KEY | Referenz auf personas.id |

```sql
CREATE TABLE task_persona (
    task_id INTEGER NOT NULL,
    persona_id INTEGER NOT NULL,
    PRIMARY KEY (task_id, persona_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
);
```

---

### task_ziel

| Spalte | Typ | Constraint | Beschreibung |
|--------|-----|------------|--------------|
| `task_id` | INTEGER | FOREIGN KEY | Referenz auf tasks.id |
| `ziel_id` | INTEGER | FOREIGN KEY | Referenz auf ziele.id |

```sql
CREATE TABLE task_ziel (
    task_id INTEGER NOT NULL,
    ziel_id INTEGER NOT NULL,
    PRIMARY KEY (task_id, ziel_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (ziel_id) REFERENCES ziele(id) ON DELETE CASCADE
);
```

---

### ziel_persona

| Spalte | Typ | Constraint | Beschreibung |
|--------|-----|------------|--------------|
| `ziel_id` | INTEGER | FOREIGN KEY | Referenz auf ziele.id |
| `persona_id` | INTEGER | FOREIGN KEY | Referenz auf personas.id |

```sql
CREATE TABLE ziel_persona (
    ziel_id INTEGER NOT NULL,
    persona_id INTEGER NOT NULL,
    PRIMARY KEY (ziel_id, persona_id),
    FOREIGN KEY (ziel_id) REFERENCES ziele(id) ON DELETE CASCADE,
    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
);
```

---

## ER-Diagramm

```
┌─────────────┐     ┌───────────────┐     ┌─────────────┐
│   personas  │     │  task_persona │     │    tasks    │
├─────────────┤     ├───────────────┤     ├─────────────┤
│ id (PK)     │←────│ persona_id    │     │ id (PK)     │
│ beschreibung│     │ task_id       │────→│ beschreibung│
│ xp          │     └───────────────┘     │ streak      │
│ level       │                           │ wichtigkeit │
└─────────────┘                           │ ...         │
      ↑                                   └─────────────┘
      │                                         ↑
┌─────────────────┐                             │
│  ziel_persona   │                    ┌────────────────┐
├─────────────────┤                    │   task_ziel    │
│ ziel_id         │                    ├────────────────┤
│ persona_id      │                    │ task_id        │
└─────────────────┘                    │ ziel_id        │
      │                                └────────────────┘
      ↓                                         │
┌─────────────┐                                 │
│    ziele    │←────────────────────────────────┘
├─────────────┤
│ id (PK)     │
│ beschreibung│
│ frist       │
│ ...         │
└─────────────┘
```

---

## Indizes

```sql
-- Für häufige Queries
CREATE INDEX idx_tasks_frist ON tasks(frist);
CREATE INDEX idx_tasks_wichtigkeit ON tasks(wichtigkeit);
CREATE INDEX idx_tasks_wiederholungs_typ ON tasks(wiederholungs_typ);

-- Für Beziehungen
CREATE INDEX idx_task_persona_task ON task_persona(task_id);
CREATE INDEX idx_task_persona_persona ON task_persona(persona_id);
CREATE INDEX idx_task_ziel_task ON task_ziel(task_id);
CREATE INDEX idx_task_ziel_ziel ON task_ziel(ziel_id);
CREATE INDEX idx_ziel_persona_ziel ON ziel_persona(ziel_id);
CREATE INDEX idx_ziel_persona_persona ON ziel_persona(persona_id);
```

---

## Migrationen

### Version 1 (Initial)

- Alle 6 Tabellen erstellen

### Zukünftige Migrationen

Bei Schema-Änderungen:
1. `DATABASE_VERSION` erhöhen
2. `onUpgrade()` erweitern mit ALTER TABLE Statements
3. Datenmigration durchführen falls nötig

---

## Siehe auch

- [TaskRepository.md](../repositories/TaskRepository.md)
- [PersonaRepository.md](../repositories/PersonaRepository.md)
- [ZielRepository.md](../repositories/ZielRepository.md)
