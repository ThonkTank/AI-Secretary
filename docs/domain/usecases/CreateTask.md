# Use Case: CreateTask

## Beschreibung

Erstellt eine neue Task mit allen konfigurierten Eigenschaften.

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `beschreibung` | String | ✅ | Task-Beschreibung |
| `wichtigkeit` | Int | ✅ | Wichtigkeit (1-10) |
| `frist` | Date | ❌ | Optionale Deadline |
| `bearbeitungszeit` | Int | ❌ | Geschätzte Dauer (Minuten) |
| `wiederholung` | WiederholungConfig | ❌ | Wiederholungs-Konfiguration |
| `completion` | CompletionConfig | ❌ | Completion-Metrik |
| `personaIds` | List<Long> | ❌ | Zugehörige Personas |
| `zielIds` | List<Long> | ❌ | Zugehörige Ziele |

---

## Output

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `task` | Task | Die erstellte Task |

---

## Ablauf

```
1. Validierung der Eingaben
   ├── beschreibung nicht leer
   ├── wichtigkeit zwischen 1-10
   └── Datum-Validierung (frist in der Zukunft)

2. Task-Objekt erstellen
   ├── Pflichtfelder setzen
   ├── Optionale Felder setzen
   └── Defaults für streak (0), letztesmalErledigt (null)

3. Beziehungen herstellen
   ├── Personas zuordnen (falls angegeben)
   └── Ziele zuordnen (falls angegeben)

4. Automatische Zuordnungen
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
| `EmptyDescriptionException` | Beschreibung leer | Abbruch |
| `InvalidImportanceException` | Wichtigkeit außerhalb 1-10 | Abbruch |
| `PersonaNotFoundException` | Persona-ID existiert nicht | Abbruch |
| `ZielNotFoundException` | Ziel-ID existiert nicht | Abbruch |

---

## Beispiel

```java
CreateTaskUseCase useCase = new CreateTaskUseCase(taskRepository);

CreateTaskRequest request = new CreateTaskRequest();
request.beschreibung = "30 Minuten Joggen";
request.wichtigkeit = 8;
request.wiederholung = new WiederholungConfig(
    WiederholungsTyp.TIMER,
    2,
    WiederholungsEinheit.TAG
);
request.personaIds = Arrays.asList(1L); // "Sportler" Persona

Task task = useCase.execute(request);
```

---

## Siehe auch

- [Task.md](../entities/Task.md)
- [CreatePersona.md](CreatePersona.md)
- [CreateZiel.md](CreateZiel.md)
