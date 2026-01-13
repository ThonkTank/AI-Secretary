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

```java
// data/repository/ZielRepositoryImpl.java
public class ZielRepositoryImpl implements ZielRepository {

    private final ZielDao zielDao;
    private final ZielMapper mapper;
    private final TaskZielDao taskZielDao;
    private final ZielPersonaDao zielPersonaDao;
    private final TaskRepository taskRepository;

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
        // Beziehungen löschen
        taskZielDao.deleteByZielId(id);
        zielPersonaDao.deleteByZielId(id);
        zielDao.delete(id);
    }

    @Override
    public void addPersonaToZiel(Long zielId, Long personaId) {
        // 1. Beziehung Ziel→Persona erstellen
        zielPersonaDao.insert(zielId, personaId);

        // 2. Automatische Zuordnung: Alle Tasks des Ziels → Persona
        List<Long> taskIds = taskZielDao.findTaskIdsByZielId(zielId);
        for (Long taskId : taskIds) {
            taskPersonaDao.insertIfNotExists(taskId, personaId);
        }
    }

    @Override
    public void addTaskToZiel(Long zielId, Long taskId) {
        // 1. Beziehung Task→Ziel erstellen
        taskZielDao.insert(taskId, zielId);

        // 2. Automatische Zuordnung: Task → alle Personas des Ziels
        List<Long> personaIds = zielPersonaDao.findPersonaIdsByZielId(zielId);
        for (Long personaId : personaIds) {
            taskPersonaDao.insertIfNotExists(taskId, personaId);
        }
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
        entity.beschreibung = ziel.getBeschreibung();
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
        ziel.setBeschreibung(entity.beschreibung);
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

### Bei addTaskToZiel

```
Task T wird Ziel Z zugeordnet
→ Task T wird allen Personas von Ziel Z zugeordnet
```

### Bei addPersonaToZiel

```
Persona P wird Ziel Z zugeordnet
→ Alle Tasks von Ziel Z werden Persona P zugeordnet
```

---

## Siehe auch

- [Ziel.md](../../domain/entities/Ziel.md)
- [Schema.md](../database/Schema.md)
- [TaskRepository.md](TaskRepository.md)
- [BUSINESS_RULES.md](../../meta/BUSINESS_RULES.md)
