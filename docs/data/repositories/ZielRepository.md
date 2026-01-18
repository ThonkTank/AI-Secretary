# Repository: ZielRepository

## Beschreibung

Repository-Interface und -Implementation für den Datenzugriff auf Ziele.

---

## Interface (Domain Layer)

```java
// domain/repository/ZielRepository.java
public interface ZielRepository {

    // CRUD
    Ziel save(Ziel ziel);
    Ziel findById(Long id);
    List<Ziel> findAll();
    void delete(Long id);

    // Queries
    List<Ziel> findByPersonaId(Long personaId);
    List<Ziel> findDueByDate(Date date);

    // Beziehungen
    void addTaskToZiel(Long zielId, Long taskId);
    void removeTaskFromZiel(Long zielId, Long taskId);
    void addPersonaToZiel(Long zielId, Long personaId);
    void removePersonaFromZiel(Long zielId, Long personaId);
}
```

---

## Implementation (Data Layer)

**Hinweis:** Repositories sind "dumm" und machen nur CRUD-Operationen. Die automatischen Zuordnungen (Task→Ziel→Personas) werden im **ZuordnungsUseCase** orchestriert, nicht im Repository!

```java
// data/repository/ZielRepositoryImpl.java
public class ZielRepositoryImpl implements ZielRepository {

    private final ZielDao zielDao;
    private final ZielMapper mapper;
    private final TaskZielDao taskZielDao;
    private final ZielPersonaDao zielPersonaDao;

    @Override
    public Ziel save(Ziel ziel) {
        ZielEntity entity = mapper.toEntity(ziel);
        long id = zielDao.insert(entity);
        ziel.setId(id);
        return ziel;
    }

    @Override
    public Ziel findById(Long id) {
        ZielEntity entity = zielDao.findById(id);
        if (entity == null) return null;
        return mapper.toDomain(entity);
    }

    @Override
    public List<Ziel> findAll() {
        return zielDao.findAll().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        // Beziehungen löschen (Cascade)
        taskZielDao.deleteByZielId(id);
        zielPersonaDao.deleteByZielId(id);
        zielDao.delete(id);
    }

    @Override
    public void addPersonaToZiel(Long zielId, Long personaId) {
        // NUR Beziehung erstellen - keine automatische Zuordnung hier!
        // Automatische Zuordnungen werden im ZuordnungsUseCase orchestriert
        zielPersonaDao.insert(zielId, personaId);
    }

    @Override
    public void addTaskToZiel(Long zielId, Long taskId) {
        // NUR Beziehung erstellen - keine automatische Zuordnung hier!
        // Automatische Zuordnungen werden im ZuordnungsUseCase orchestriert
        taskZielDao.insert(taskId, zielId);
    }
}
```

---

## Mapper

```java
// data/mapper/ZielMapper.java
public class ZielMapper {

    public ZielEntity toEntity(Ziel ziel) {
        ZielEntity entity = new ZielEntity();
        entity.id = ziel.getId();
        entity.titel = ziel.getTitel();
        entity.beschreibung = ziel.getBeschreibung();
        entity.wichtigkeit = ziel.getWichtigkeit();
        entity.frist = ziel.getFrist();
        entity.wiederholungsTyp = ziel.getWiederholungsTyp();
        entity.wiederholungsWert = ziel.getWiederholungsWert();
        entity.wiederholungsEinheit = ziel.getWiederholungsEinheit();
        entity.wiederholungsDetails = ziel.getWiederholungsDetails();
        entity.completionTyp = ziel.getCompletionTyp();
        entity.completionWert = ziel.getCompletionWert();
        entity.completionEinheit = ziel.getCompletionEinheit();
        return entity;
    }

    public Ziel toDomain(ZielEntity entity) {
        Ziel ziel = new Ziel();
        ziel.setId(entity.id);
        ziel.setTitel(entity.titel);
        ziel.setBeschreibung(entity.beschreibung);
        ziel.setWichtigkeit(entity.wichtigkeit);
        ziel.setFrist(entity.frist);
        ziel.setWiederholungsTyp(entity.wiederholungsTyp);
        ziel.setWiederholungsWert(entity.wiederholungsWert);
        ziel.setWiederholungsEinheit(entity.wiederholungsEinheit);
        ziel.setWiederholungsDetails(entity.wiederholungsDetails);
        ziel.setCompletionTyp(entity.completionTyp);
        ziel.setCompletionWert(entity.completionWert);
        ziel.setCompletionEinheit(entity.completionEinheit);
        return ziel;
    }
}
```

---

## Automatische Zuordnungen

**Hinweis:** Diese Logik liegt im **ZuordnungsUseCase**, NICHT im Repository!

### Bei addTaskToZiel (via ZuordnungsUseCase)

```
Task T wird Ziel Z zugeordnet
→ ZuordnungsUseCase: Task T wird allen Personas von Ziel Z zugeordnet
```

### Bei addPersonaToZiel (via ZuordnungsUseCase)

```
Persona P wird Ziel Z zugeordnet
→ ZuordnungsUseCase: Alle Tasks von Ziel Z werden Persona P zugeordnet
```

Siehe [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md) für Details.

---

## Siehe auch

- [Ziel.md](../../domain/entities/Ziel.md)
- [Schema.md](../database/Schema.md)
- [TaskRepository.md](TaskRepository.md)
- [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md)
