# Use Case: CreateTask

## Beschreibung

Erstellt eine neue Task mit allen konfigurierten Eigenschaften.

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `titel` | String | ✅ | Kurzer Task-Name |
| `beschreibung` | String | ❌ | Optionale Details |
| `wichtigkeit` | Int | ✅ | Wichtigkeit (1-10) |
| `frist` | Date | ❌ | Optionale Deadline |
| `bearbeitungszeit` | Int | ❌ | Geschätzte Dauer (Minuten) |
| `wiederholung` | WiederholungConfig | ❌ | Wiederholungs-Konfiguration |
| `completion` | CompletionConfig | ❌ | Completion-Metrik (nur bei wiederholungsTyp=KEINE!) |
| `personaIds` | List<Long> | ❌ | Zugehörige Personas |
| `zielIds` | List<Long> | ❌ | Zugehörige Ziele |

**Hinweis:** Wiederholung und Completion schließen sich gegenseitig aus!

---

## Output

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `task` | Task | Die erstellte Task |

---

## Ablauf

```
1. Validierung der Eingaben
   ├── titel nicht leer
   ├── wichtigkeit zwischen 1-10
   ├── Datum-Validierung (frist in der Zukunft)
   └── Wiederholung XOR Completion (nicht beide!)

2. Task-Objekt erstellen
   ├── Pflichtfelder setzen (titel, wichtigkeit)
   ├── Optionale Felder setzen
   └── Defaults für streak (0), letztesmalErledigt (null)

3. Beziehungen herstellen
   ├── Personas zuordnen (falls angegeben)
   └── Ziele zuordnen (falls angegeben)

4. Automatische Zuordnungen (via ZuordnungsUseCase)
   └── Bei Ziel-Zuordnung: Task zu Personas des Ziels hinzufügen

5. Task speichern
   └── TaskRepository.save(task)

6. Task zurückgeben
```

---

## Automatische Zuordnung (Detail)

Wenn Task einem Ziel zugeordnet wird:

```java
for (Ziel ziel : task.getZiele()) {
    for (Persona persona : ziel.getPersonas()) {
        if (!task.getPersonas().contains(persona)) {
            task.addPersona(persona);
        }
    }
}
```

---

## Fehlerbehandlung

| Fehler | Beschreibung | Reaktion |
|--------|--------------|----------|
| `EmptyTitleException` | Titel leer | Abbruch |
| `InvalidImportanceException` | Wichtigkeit außerhalb 1-10 | Abbruch |
| `InvalidConfigException` | Wiederholung UND Completion gesetzt | Abbruch |
| `PersonaNotFoundException` | Persona-ID existiert nicht | Abbruch |
| `ZielNotFoundException` | Ziel-ID existiert nicht | Abbruch |

---

## Beispiele

### Einmalige Task

```java
CreateTaskUseCase useCase = new CreateTaskUseCase(taskRepository, zuordnungsUseCase);

CreateTaskRequest request = new CreateTaskRequest();
request.titel = "Steuererklärung machen";
request.beschreibung = "Alle Belege zusammensuchen";
request.wichtigkeit = 8;
request.frist = Date.parse("2025-03-31");
// Keine Wiederholung, keine Completion → einmalig, bleibt erledigt

Task task = useCase.execute(request);
```

### Wiederkehrende Task (INTERVALL)

```java
CreateTaskRequest request = new CreateTaskRequest();
request.titel = "Wäsche waschen";
request.wichtigkeit = 5;
request.wiederholung = new WiederholungConfig(
    WiederholungsTyp.INTERVALL,
    3,
    ZeitEinheit.TAG
);
// → Erscheint alle 3 Tage, egal ob erledigt

Task task = useCase.execute(request);
```

### Wiederkehrende Task (FREQUENZ)

```java
CreateTaskRequest request = new CreateTaskRequest();
request.titel = "Sport machen";
request.wichtigkeit = 9;
request.wiederholung = new WiederholungConfig(
    WiederholungsTyp.FREQUENZ,
    3,
    ZeitEinheit.WOCHE
);
request.personaIds = Arrays.asList(1L); // "Sportler" Persona
// → 3x pro Woche, Zeitpunkt flexibel

Task task = useCase.execute(request);
```

### Task mit Completion

```java
CreateTaskRequest request = new CreateTaskRequest();
request.titel = "Haare schneiden";
request.wichtigkeit = 4;
// KEINE Wiederholung!
request.completion = new CompletionConfig(
    CompletionTyp.INTERVALL,
    6,
    ZeitEinheit.WOCHE
);
// → Nach Erledigung dauert es 6 Wochen bis Task wieder erscheint

Task task = useCase.execute(request);
```

---

## Siehe auch

- [Task.md](../entities/Task.md)
- [CreatePersona.md](CreatePersona.md)
- [CreateZiel.md](CreateZiel.md)
