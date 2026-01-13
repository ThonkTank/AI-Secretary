# Use Case: CreateZiel

## Beschreibung

Erstellt ein neues Ziel (Projekt) mit optionaler Wiederholung und Completion-Metriken.

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `beschreibung` | String | ✅ | Ziel-Beschreibung |
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
   ├── beschreibung nicht leer
   └── Datum-Validierung (frist in der Zukunft)

2. Ziel-Objekt erstellen
   ├── Pflichtfelder setzen
   └── Optionale Felder setzen

3. Personas zuordnen (falls angegeben)

4. Ziel speichern
   └── ZielRepository.save(ziel)

5. Ziel zurückgeben
```

---

## Fehlerbehandlung

| Fehler | Beschreibung | Reaktion |
|--------|--------------|----------|
| `EmptyDescriptionException` | Beschreibung leer | Abbruch |
| `PersonaNotFoundException` | Persona-ID existiert nicht | Abbruch |

---

## Beispiel

```java
CreateZielUseCase useCase = new CreateZielUseCase(zielRepository);

CreateZielRequest request = new CreateZielRequest();
request.beschreibung = "Fitness-Routine etablieren";
request.completion = new CompletionConfig(
    CompletionTyp.FREQUENZ,
    4,
    WiederholungsEinheit.WOCHE
);
request.personaIds = Arrays.asList(1L); // "Sportler" Persona

Ziel ziel = useCase.execute(request);
```

---

## Automatische Zuordnung bei Persona-Zuweisung

Wenn ein Ziel bereits Tasks hat und dann einer Persona zugeordnet wird:

```java
// In CreateZielUseCase oder separatem Service
void assignPersonaToZiel(Ziel ziel, Persona persona) {
    ziel.addPersona(persona);

    // Alle existierenden Tasks des Ziels ebenfalls zuordnen
    for (Task task : ziel.getTasks()) {
        if (!task.getPersonas().contains(persona)) {
            task.addPersona(persona);
            taskRepository.save(task);
        }
    }
}
```

---

## Siehe auch

- [Ziel.md](../entities/Ziel.md)
- [CreateTask.md](CreateTask.md)
- [CreatePersona.md](CreatePersona.md)
