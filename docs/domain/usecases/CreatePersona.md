# Use Case: CreatePersona

## Beschreibung

Erstellt eine neue Persona (Ziel-Identität des Benutzers).

---

## Input

| Parameter | Typ | Pflicht | Beschreibung |
|-----------|-----|---------|--------------|
| `titel` | String | ✅ | Kurzer Persona-Name (z.B. "Sportler") |
| `utopie` | String | ✅ | Ziel-Beschreibung (z.B. "Fit und gesund") |

---

## Output

| Parameter | Typ | Beschreibung |
|-----------|-----|--------------|
| `persona` | Persona | Die erstellte Persona |

---

## Ablauf

```
1. Validierung der Eingaben
   ├── titel nicht leer
   └── utopie nicht leer

2. Persona-Objekt erstellen
   ├── titel setzen
   ├── utopie setzen
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
| `EmptyTitleException` | Titel leer | Abbruch |
| `EmptyUtopieException` | Utopie leer | Abbruch |

---

## Beispiel

```java
CreatePersonaUseCase useCase = new CreatePersonaUseCase(personaRepository);

CreatePersonaRequest request = new CreatePersonaRequest();
request.titel = "Sportler";
request.utopie = "Fit, gesund und voller Energie";

Persona persona = useCase.execute(request);
// persona.xp = 0
// persona.level = 0
```

---

## Siehe auch

- [Persona.md](../entities/Persona.md)
- [CreateTask.md](CreateTask.md)
- [CreateZiel.md](CreateZiel.md)
