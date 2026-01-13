# Presentation Layer - Screens Overview

## Geplante Screens

### Phase 1: Tasks (v0.1 - v0.3)

| Screen | Beschreibung | Status |
|--------|--------------|--------|
| `TaskListActivity` | Hauptliste aller Tasks | TBD |
| `TaskDetailActivity` | Task-Details anzeigen/bearbeiten | TBD |
| `TaskCreateDialog` | Neue Task erstellen | TBD |

### Phase 2: Habits (v0.4 - v0.5)

| Screen | Beschreibung | Status |
|--------|--------------|--------|
| `HabitListActivity` | Übersicht täglicher Gewohnheiten | TBD |
| `HabitDetailActivity` | Habit-Details mit Streak-Anzeige | TBD |

### Phase 3: Routinen (v0.6 - v0.7)

| Screen | Beschreibung | Status |
|--------|--------------|--------|
| `RoutineListActivity` | Liste aller Routinen | TBD |
| `RoutineExecutionActivity` | Schrittweise Routine-Abarbeitung | TBD |

### Phase 4: GTD (v0.8 - v0.9)

| Screen | Beschreibung | Status |
|--------|--------------|--------|
| `InboxActivity` | Schnelles Erfassen von Ideen | TBD |
| `ProjectListActivity` | Übersicht aller Projekte/Ziele | TBD |
| `ContextFilterActivity` | Tasks nach Kontext filtern | TBD |

### Phase 5: Pomodoro (v1.0)

| Screen | Beschreibung | Status |
|--------|--------------|--------|
| `PomodoroTimerActivity` | Focus Timer mit Task-Verknüpfung | TBD |
| `FocusStatisticsActivity` | Fokus-Statistiken | TBD |

---

## Übergreifende Screens

| Screen | Beschreibung | Status |
|--------|--------------|--------|
| `MainActivity` | Landing Page, Navigation | TBD |
| `PersonaListActivity` | Übersicht aller Personas mit XP/Level | TBD |
| `PersonaDetailActivity` | Persona-Details, zugehörige Tasks/Ziele | TBD |
| `DailyTodoActivity` | Automatisch generierte Tagesliste | TBD |
| `SettingsActivity` | App-Einstellungen | TBD |

---

## MVVM-Struktur

Für jeden Screen:

```
presentation/
├── task/
│   ├── TaskListActivity.java      # UI, Lifecycle
│   ├── TaskListViewModel.java     # State, Use Case Calls
│   └── TaskListAdapter.java       # ListView/RecyclerView Adapter
│
├── persona/
│   ├── PersonaListActivity.java
│   └── PersonaListViewModel.java
│
└── common/
    ├── MainActivity.java
    └── BaseViewModel.java
```

---

## ViewModel-Pattern

```java
// presentation/task/TaskListViewModel.java
public class TaskListViewModel extends ViewModel {

    private final GetAllTasksUseCase getAllTasksUseCase;
    private final CompleteTaskUseCase completeTaskUseCase;

    private final MutableLiveData<List<Task>> tasks = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public TaskListViewModel(
        GetAllTasksUseCase getAllTasksUseCase,
        CompleteTaskUseCase completeTaskUseCase
    ) {
        this.getAllTasksUseCase = getAllTasksUseCase;
        this.completeTaskUseCase = completeTaskUseCase;
    }

    public LiveData<List<Task>> getTasks() { return tasks; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadTasks() {
        loading.setValue(true);
        try {
            List<Task> result = getAllTasksUseCase.execute();
            tasks.setValue(result);
        } catch (Exception e) {
            error.setValue(e.getMessage());
        } finally {
            loading.setValue(false);
        }
    }

    public void completeTask(long taskId) {
        try {
            completeTaskUseCase.execute(taskId);
            loadTasks(); // Refresh
        } catch (Exception e) {
            error.setValue(e.getMessage());
        }
    }
}
```

---

## Activity-Pattern

```java
// presentation/task/TaskListActivity.java
public class TaskListActivity extends AppCompatActivity {

    private TaskListViewModel viewModel;
    private TaskListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);

        viewModel = new ViewModelProvider(this).get(TaskListViewModel.class);
        adapter = new TaskListAdapter(this::onTaskComplete);

        setupObservers();
        viewModel.loadTasks();
    }

    private void setupObservers() {
        viewModel.getTasks().observe(this, tasks -> {
            adapter.setTasks(tasks);
        });

        viewModel.getLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        });

        viewModel.getError().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onTaskComplete(long taskId) {
        viewModel.completeTask(taskId);
    }
}
```

---

## Widgets (Phase 1)

| Widget | Beschreibung |
|--------|--------------|
| `TaskListWidget` | Homescreen Widget mit heutigen Tasks |

---

## Siehe auch

- [ARCHITECTURE.md](../../meta/ARCHITECTURE.md)
- [Task.md](../../domain/entities/Task.md)
- [CompleteTask.md](../../domain/usecases/CompleteTask.md)
