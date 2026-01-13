# Use Case: CreatePersona

## Beschreibung

Erstellt eine neue Persona (Ziel-Identität des Benutzers).

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `beschreibung` | String | ✅ | Persona-Titel und Utopie-Beschreibung |

---

## Output

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `persona` | Persona | Die erstellte Persona |

---

## Ablauf

```
1. Validierung der Eingaben
   └── beschreibung nicht leer

2. Persona-Objekt erstellen
   ├── beschreibung setzen
   ├── xp = 0
   └── level = 0

3. Persona speichern
   └── PersonaRepository.save(persona)

4. Persona zurückgeben
```

---

## Fehlerbehandlung

| Fehler | Beschreibung | Reaktion |
|--------|--------------|----------|
| `EmptyDescriptionException` | Beschreibung leer | Abbruch |

---

## Beispiel

```java
CreatePersonaUseCase useCase = new CreatePersonaUseCase(personaRepository);

CreatePersonaRequest request = new CreatePersonaRequest();
request.beschreibung = "Der Sportler - Fit, gesund und voller Energie";

Persona persona = useCase.execute(request);
// persona.xp = 0
// persona.level = 0
```

---

## Siehe auch

- [Persona.md](../entities/Persona.md)
- [CreateTask.md](CreateTask.md)
- [CreateZiel.md](CreateZiel.md)
