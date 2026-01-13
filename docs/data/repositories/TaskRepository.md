# Repository: TaskRepository

## Beschreibung

Repository-Interface und -Implementation für den Datenzugriff auf Tasks.

---

## Interface (Domain Layer)

```java
// domain/repository/TaskRepository.java
public interface TaskRepository {

    // CRUD
    Task save(Task task);
    Task findById(Long id);
    List<Task> findAll();
    void delete(Long id);

    // Queries
    List<Task> findByPersonaId(Long personaId);
    List<Task> findByZielId(Long zielId);
    List<Task> findDueByDate(Date date);
    List<Task> findOverdue(Date today);

    // Bulk Operations
    List<Task> saveAll(List<Task> tasks);
}
```

---

## Implementation (Data Layer)

```java
// data/repository/TaskRepositoryImpl.java
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskDao taskDao;
    private final TaskMapper mapper;

    public TaskRepositoryImpl(TaskDao taskDao) {
        this.taskDao = taskDao;
        this.mapper = new TaskMapper();
    }

    @Override
    public Task save(Task task) {
        TaskEntity entity = mapper.toEntity(task);
        long id = taskDao.insert(entity);
        task.setId(id);
        return task;
    }

    @Override
    public Task findById(Long id) {
        TaskEntity entity = taskDao.findById(id);
        if (entity == null) return null;
        return mapper.toDomain(entity);
    }

    @Override
    public List<Task> findAll() {
        return taskDao.findAll().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        taskDao.delete(id);
    }

    @Override
    public List<Task> findDueByDate(Date date) {
        return taskDao.findByFristLessThanEqual(date).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Task> findOverdue(Date today) {
        return taskDao.findByFristLessThan(today).stream()
            .filter(e -> !e.isCompleted())
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
```

---

## Mapper

```java
// data/mapper/TaskMapper.java
public class TaskMapper {

    public TaskEntity toEntity(Task task) {
        TaskEntity entity = new TaskEntity();
        entity.id = task.getId();
        entity.beschreibung = task.getBeschreibung();
        entity.streak = task.getStreak();
        entity.wichtigkeit = task.getWichtigkeit();
        entity.letztesmalErledigt = task.getLetztesmalErledigt();
        entity.frist = task.getFrist();
        entity.bearbeitungszeit = task.getBearbeitungszeit();
        // ... weitere Felder
        entity.completionHistoryJson = serializeTimestamps(task.getCompletionHistory());
        entity.nachfolgerHistoryJson = serializeMap(task.getNachfolgerHistory());
        return entity;
    }

    public Task toDomain(TaskEntity entity) {
        Task task = new Task();
        task.setId(entity.id);
        task.setBeschreibung(entity.beschreibung);
        task.setStreak(entity.streak);
        task.setWichtigkeit(entity.wichtigkeit);
        task.setLetztesmalErledigt(entity.letztesmalErledigt);
        task.setFrist(entity.frist);
        task.setBearbeitungszeit(entity.bearbeitungszeit);
        // ... weitere Felder
        task.setCompletionHistory(deserializeTimestamps(entity.completionHistoryJson));
        task.setNachfolgerHistory(deserializeMap(entity.nachfolgerHistoryJson));
        return task;
    }
}
```

---

## Many-to-Many Beziehungen

Für Persona- und Ziel-Beziehungen separate Queries:

```java
// In TaskRepositoryImpl
@Override
public List<Task> findByPersonaId(Long personaId) {
    List<Long> taskIds = taskPersonaDao.findTaskIdsByPersonaId(personaId);
    return taskIds.stream()
        .map(this::findById)
        .collect(Collectors.toList());
}

void saveRelations(Task task) {
    // Persona-Beziehungen
    taskPersonaDao.deleteByTaskId(task.getId());
    for (Persona persona : task.getPersonas()) {
        taskPersonaDao.insert(task.getId(), persona.getId());
    }

    // Ziel-Beziehungen
    taskZielDao.deleteByTaskId(task.getId());
    for (Ziel ziel : task.getZiele()) {
        taskZielDao.insert(task.getId(), ziel.getId());
    }
}
```

---

## Siehe auch

- [Task.md](../../domain/entities/Task.md)
- [Schema.md](../database/Schema.md)
- [PersonaRepository.md](PersonaRepository.md)
