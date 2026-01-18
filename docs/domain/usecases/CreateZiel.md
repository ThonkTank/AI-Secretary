# Use Case: CreateZiel

## Beschreibung

Erstellt ein neues Ziel (Projekt) mit optionaler Wiederholung und Completion-Metriken.

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `titel` | String | ✅ | Kurzer Ziel-Name |
| `beschreibung` | String | ❌ | Optionale Details |
| `wichtigkeit` | Int | ✅ | Wichtigkeit für Zeitslot-Vergabe (1-10) |
| `frist` | Date | ❌ | Optionale Deadline |
| `wiederholung` | WiederholungConfig | ❌ | Wiederholungs-Konfiguration |
| `completion` | CompletionConfig | ❌ | Completion-Metrik |
| `personaIds` | List<Long> | ❌ | Zugehörige Personas |

---

## Output

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `ziel` | Ziel | Das erstellte Ziel |

---

## Ablauf

```
1. Validierung der Eingaben
   ├── titel nicht leer
   ├── wichtigkeit zwischen 1-10
   └── Datum-Validierung (frist in der Zukunft)

2. Ziel-Objekt erstellen
   ├── Pflichtfelder setzen (titel, wichtigkeit)
   └── Optionale Felder setzen

3. Personas zuordnen (falls angegeben)
   └── Via ZuordnungsUseCase

4. Ziel speichern
   └── ZielRepository.save(ziel)

5. Ziel zurückgeben
```

---

## Fehlerbehandlung

| Fehler | Beschreibung | Reaktion |
|--------|--------------|----------|
| `EmptyTitleException` | Titel leer | Abbruch |
| `InvalidImportanceException` | Wichtigkeit außerhalb 1-10 | Abbruch |
| `PersonaNotFoundException` | Persona-ID existiert nicht | Abbruch |

---

## Beispiel

```java
CreateZielUseCase useCase = new CreateZielUseCase(zielRepository, zuordnungsUseCase);

CreateZielRequest request = new CreateZielRequest();
request.titel = "Fitness-Routine";
request.beschreibung = "Regelmäßig Sport treiben";
request.wichtigkeit = 8;
request.completion = new CompletionConfig(
    CompletionTyp.FREQUENZ,
    4,
    ZeitEinheit.WOCHE
);
request.personaIds = Arrays.asList(1L); // "Sportler" Persona

Ziel ziel = useCase.execute(request);
```

---

## Automatische Zuordnung bei Persona-Zuweisung

**Hinweis:** Diese Logik liegt im **ZuordnungsUseCase**, NICHT im CreateZielUseCase!

Wenn ein Ziel einer Persona zugeordnet wird:

```java
// Im ZuordnungsUseCase
void assignPersonaToZiel(Long zielId, Long personaId) {
    // 1. Beziehung Ziel→Persona erstellen
    zielRepository.addPersonaToZiel(zielId, personaId);

    // 2. Alle existierenden Tasks des Ziels ebenfalls zuordnen
    List<Task> tasks = taskRepository.findByZielId(zielId);
    for (Task task : tasks) {
        taskRepository.addPersonaToTask(task.getId(), personaId);
    }
}
```

Siehe [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md) für Details.

---

## Siehe auch

- [Ziel.md](../entities/Ziel.md)
- [CreateTask.md](CreateTask.md)
- [CreatePersona.md](CreatePersona.md)
