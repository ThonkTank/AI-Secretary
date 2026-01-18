# Repository: PersonaRepository

## Beschreibung

Repository-Interface und -Implementation für den Datenzugriff auf Personas.

---

## Interface (Domain Layer)

```java
// domain/repository/PersonaRepository.java
public interface PersonaRepository {

    // CRUD
    Persona save(Persona persona);
    Persona findById(Long id);
    List<Persona> findAll();
    void delete(Long id);

    // XP & Level
    void addXP(Long personaId, int xp);

    // Bulk Operations
    List<Persona> saveAll(List<Persona> personas);
}
```

---

## Implementation (Data Layer)

```java
// data/repository/PersonaRepositoryImpl.java
public class PersonaRepositoryImpl implements PersonaRepository {

    private final PersonaDao personaDao;
    private final PersonaMapper mapper;

    public PersonaRepositoryImpl(PersonaDao personaDao) {
        this.personaDao = personaDao;
        this.mapper = new PersonaMapper();
    }

    @Override
    public Persona save(Persona persona) {
        PersonaEntity entity = mapper.toEntity(persona);
        long id = personaDao.insert(entity);
        persona.setId(id);
        return persona;
    }

    @Override
    public Persona findById(Long id) {
        PersonaEntity entity = personaDao.findById(id);
        if (entity == null) return null;
        return mapper.toDomain(entity);
    }

    @Override
    public List<Persona> findAll() {
        return personaDao.findAll().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        // Auch Beziehungen löschen
        taskPersonaDao.deleteByPersonaId(id);
        zielPersonaDao.deleteByPersonaId(id);
        personaDao.delete(id);
    }

    @Override
    public void addXP(Long personaId, int xp) {
        Persona persona = findById(personaId);
        if (persona == null) return;

        persona.setXp(persona.getXp() + xp);
        persona.setLevel(calculateLevel(persona.getXp()));

        PersonaEntity entity = mapper.toEntity(persona);
        personaDao.update(entity);
    }

    private int calculateLevel(int xp) {
        return (int) Math.floor(Math.sqrt(xp / 100.0));
    }
}
```

---

## Mapper

```java
// data/mapper/PersonaMapper.java
public class PersonaMapper {

    public PersonaEntity toEntity(Persona persona) {
        PersonaEntity entity = new PersonaEntity();
        entity.id = persona.getId();
        entity.titel = persona.getTitel();
        entity.utopie = persona.getUtopie();
        entity.xp = persona.getXp();
        entity.level = persona.getLevel();
        return entity;
    }

    public Persona toDomain(PersonaEntity entity) {
        Persona persona = new Persona();
        persona.setId(entity.id);
        persona.setTitel(entity.titel);
        persona.setUtopie(entity.utopie);
        persona.setXp(entity.xp);
        persona.setLevel(entity.level);
        return persona;
    }
}
```

---

## Siehe auch

- [Persona.md](../../domain/entities/Persona.md)
- [Schema.md](../database/Schema.md)
- [TaskRepository.md](TaskRepository.md)
