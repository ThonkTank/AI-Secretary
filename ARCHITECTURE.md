# AI Secretary - Architecture Documentation

**Last Updated:** 2025-11-27
**Version:** v0.3.72 (Build 372) - Dream Analytics Dashboard Complete
**Architecture Style:** Clean Architecture + MVVM

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Principles](#architecture-principles)
3. [Layer Structure](#layer-structure)
4. [Component Diagram](#component-diagram)
5. [Data Flow](#data-flow)
6. [Key Design Patterns](#key-design-patterns)
7. [Dream Feature (Phase 5.1)](#dream-feature-phase-51)
8. [Dependencies](#dependencies)
9. [Testing Strategy](#testing-strategy)

---

## Overview

AI Secretary follows **Clean Architecture** principles combined with the **MVVM (Model-View-ViewModel)** pattern for the presentation layer. This architecture ensures:

- **Separation of Concerns**: Each layer has a single, well-defined responsibility
- **Testability**: Business logic is independent of Android framework
- **Maintainability**: Changes in one layer don't ripple through others
- **Scalability**: Easy to add new features without modifying existing code

### Architecture Completion Status

✅ **Phase 4.5 Complete** (100% of 7 subphases):
- ✅ Phase 4.5.1: Data Layer (Room ORM + Repository Pattern)
- ✅ Phase 4.5.2: Domain Layer (Use Cases + Services)
- ✅ Phase 4.5.3: Kotlin Migration (100% Kotlin codebase)
- ✅ Phase 4.5.4: Package Renaming (`com.secretary`)
- ✅ Phase 4.5.5: MVVM Integration (ViewModels + LiveData)
- ✅ Phase 4.5.6: Dialog Extraction (DialogFragments with MVVM)
- ✅ Phase 4.5.7: Testing & Documentation (95+ unit tests, 95% pass rate)

✅ **Phase 5.1 Complete** - Dream-to-Task Feature:
- ✅ Full Clean Architecture implementation with Dreams + Milestones
- ✅ XP system with level progression
- ✅ Task-to-Milestone linking (many-to-many)
- ✅ Bottom Navigation UI (Tasks, Dreams, Settings)

---

## Architecture Principles

### 1. Dependency Rule

Dependencies point **inward** - outer layers depend on inner layers, never the reverse.

```
Presentation → Domain → Data
     ↓           ↓        ↓
  Android    Pure     Room/
  Framework  Kotlin   Android
```

### 2. Single Responsibility Principle (SRP)

Each class has one reason to change:
- **Activities/Fragments**: UI rendering and user interaction
- **ViewModels**: Presentation logic and state management
- **Use Cases**: Single business operation orchestration
- **Services**: Domain logic without dependencies
- **Repositories**: Data access abstraction
- **DAOs**: Database query execution

### 3. Dependency Inversion Principle (DIP)

High-level modules don't depend on low-level modules. Both depend on abstractions:
- Domain layer defines `TaskRepository` interface
- Data layer provides `TaskRepositoryImpl` implementation
- Presentation layer depends on interface, not implementation

---

## Layer Structure

### 📂 Project Structure

```
app/src/main/java/com/secretary/
├── app/                          # Application Layer
│   └── MainActivity.kt           # App entry point with BottomNavigationView
│
├── features/                     # Feature Modules
│   ├── tasks/                    # Task Management Feature
│   │   ├── presentation/         # 🎨 Presentation Layer
│   │   │   ├── activity/
│   │   │   │   └── TasksActivity.kt
│   │   │   ├── fragment/
│   │   │   │   └── TasksFragment.kt  # Tab fragment for navigation
│   │   │   ├── viewmodel/        # ViewModels + Factory
│   │   │   │   ├── TaskListViewModel.kt
│   │   │   │   ├── TaskDetailViewModel.kt
│   │   │   │   └── TaskViewModelFactory.kt
│   │   │   └── dialog/           # DialogFragments
│   │   │       ├── AddTaskDialog.kt
│   │   │       ├── EditTaskDialog.kt
│   │   │       └── CompletionDialog.kt
│   │   │
│   │   ├── domain/               # 💼 Domain Layer
│   │   │   ├── repository/       # Repository Interfaces
│   │   │   │   └── TaskRepository.kt
│   │   │   ├── usecase/          # Use Cases
│   │   │   │   ├── CreateTaskUseCase.kt
│   │   │   │   ├── GetTasksUseCase.kt
│   │   │   │   ├── UpdateTaskUseCase.kt
│   │   │   │   ├── DeleteTaskUseCase.kt
│   │   │   │   └── CompleteTaskUseCase.kt
│   │   │   └── service/          # Domain Services
│   │   │       ├── StreakService.kt
│   │   │       └── RecurrenceService.kt
│   │   │
│   │   └── data/                 # 💾 Data Layer
│   │       ├── repository/       # Repository Implementations
│   │       │   └── TaskRepositoryImpl.kt
│   │       ├── entity/
│   │       │   └── TaskEntity.kt     # Room Entity
│   │       └── dao/
│   │           └── TaskDao.kt        # Room DAO
│   │
│   ├── dreams/                   # Dream-to-Task Feature (Phase 5.1)
│   │   ├── presentation/         # 🎨 Presentation Layer
│   │   │   ├── activity/
│   │   │   │   └── CreateDreamActivity.kt
│   │   │   ├── fragment/
│   │   │   │   ├── DreamsFragment.kt        # Tab fragment for navigation
│   │   │   │   └── DreamDetailFragment.kt
│   │   │   ├── viewmodel/
│   │   │   │   ├── DreamsViewModel.kt
│   │   │   │   ├── DreamsViewModelFactory.kt
│   │   │   │   ├── DreamDetailViewModel.kt
│   │   │   │   └── DreamDetailViewModelFactory.kt
│   │   │   ├── adapter/
│   │   │   │   ├── DreamListAdapter.kt
│   │   │   │   └── MilestoneListAdapter.kt
│   │   │   ├── dialog/
│   │   │   │   ├── AddMilestoneDialog.kt
│   │   │   │   ├── EditMilestoneDialog.kt
│   │   │   │   ├── MilestoneCompletionDialog.kt
│   │   │   │   ├── LinkTasksDialog.kt
│   │   │   │   └── LevelUpDialog.kt
│   │   │   └── components/
│   │   │       └── XpGainView.kt
│   │   │
│   │   ├── domain/               # 💼 Domain Layer
│   │   │   ├── model/
│   │   │   │   ├── Dream.kt
│   │   │   │   ├── Milestone.kt
│   │   │   │   ├── TaskMilestoneLink.kt
│   │   │   │   └── MilestoneInterdependency.kt
│   │   │   ├── repository/
│   │   │   │   ├── DreamRepository.kt
│   │   │   │   ├── MilestoneRepository.kt
│   │   │   │   ├── TaskMilestoneLinkRepository.kt
│   │   │   │   └── InterdependencyRepository.kt
│   │   │   ├── service/
│   │   │   │   ├── XPCalculationService.kt
│   │   │   │   ├── LevelProgressionService.kt
│   │   │   │   ├── DreamProgressService.kt
│   │   │   │   ├── InterdependencyService.kt
│   │   │   │   └── SelfRegulationService.kt
│   │   │   └── usecase/
│   │   │       ├── CreateDreamUseCase.kt
│   │   │       ├── UpdateDreamUseCase.kt
│   │   │       ├── GetDreamWithMilestonesUseCase.kt
│   │   │       ├── CreateMilestoneUseCase.kt
│   │   │       ├── CompleteMilestoneUseCase.kt
│   │   │       ├── LinkTaskToMilestonesUseCase.kt
│   │   │       ├── GetTaskMilestoneLinksUseCase.kt
│   │   │       ├── UpdateInterdependencyUseCase.kt
│   │   │       ├── GetDreamProgressUseCase.kt
│   │   │       └── CompleteTaskWithXPUseCase.kt
│   │   │
│   │   └── data/                 # 💾 Data Layer
│   │       ├── repository/
│   │       │   ├── DreamRepositoryImpl.kt
│   │       │   ├── MilestoneRepositoryImpl.kt
│   │       │   ├── TaskMilestoneLinkRepositoryImpl.kt
│   │       │   └── InterdependencyRepositoryImpl.kt
│   │       ├── entity/
│   │       │   ├── DreamEntity.kt
│   │       │   ├── MilestoneEntity.kt
│   │       │   ├── TaskMilestoneJunction.kt
│   │       │   ├── MilestoneInterdependencyEntity.kt
│   │       │   ├── DreamXpHistoryEntity.kt         # XP transaction history (v7)
│   │       │   └── DreamDailySnapshotEntity.kt     # Daily aggregates (v7)
│   │       └── dao/
│   │           ├── DreamDao.kt
│   │           ├── MilestoneDao.kt
│   │           ├── TaskMilestoneJunctionDao.kt
│   │           ├── MilestoneInterdependencyDao.kt
│   │           └── DreamAnalyticsDao.kt            # Analytics queries (v7)
│   │
│   └── motivation/               # Motivation Feature
│       ├── domain/
│       │   └── MotivationalMessageService.kt  # Motivational messages based on statistics
│       └── presentation/
│           └── StreakColorUtil.kt              # Color utilities for streak visualization
│
├── shared/                       # Shared Components
│   └── database/
│       ├── TaskDatabase.kt       # Room Database (v7 with Dreams + Analytics schema)
│       └── DatabaseConstants.kt  # Schema Constants
│
└── core/                         # Core Infrastructure
    ├── config/                   # Configuration
    │   └── AppPreferences.kt     # Application preferences and settings
    ├── logging/                  # Logging System
    │   ├── AppLogger.kt
    │   └── HttpLogServer.kt
    └── network/                  # Update System
        ├── UpdateChecker.kt
        └── UpdateInstaller.kt
```

---

## Component Diagram

### Layer Interaction Flow

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Activity   │  │  ViewModel   │  │DialogFragment│  │
│  │              │←→│              │←→│              │  │
│  │ TaskActivity │  │TaskListVM    │  │AddTaskDialog │  │
│  └──────────────┘  └──────┬───────┘  └──────────────┘  │
└────────────────────────────┼────────────────────────────┘
                             │
                     ┌───────▼───────┐
                     │   Use Cases   │
┌────────────────────┼───────────────┼────────────────────┐
│                    │  Domain Layer │                    │
│  ┌─────────────────▼──────┐  ┌────▼──────────────────┐ │
│  │  Use Cases             │  │  Domain Services      │ │
│  │  - CreateTaskUseCase   │  │  - StreakService      │ │
│  │  - CompleteTaskUseCase │  │  - RecurrenceService  │ │
│  │  - GetTasksUseCase     │  │                       │ │
│  └───────────┬────────────┘  └───────────────────────┘ │
└──────────────┼─────────────────────────────────────────┘
               │
       ┌───────▼────────┐
       │   Repository   │
┌──────┼────────────────┼─────────────────────────────────┐
│      │   Data Layer   │                                 │
│  ┌───▼────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ TaskRepository │  │  Room DAO    │  │   Entity   │  │
│  │  Impl          │→│  TaskDao     │→│  Task      │  │
│  └────────────────┘  └──────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## Data Flow

### Example: Completing a Task

**User Action Flow:**

1. **User taps checkbox** in TaskActivity
   ```kotlin
   // TaskActivity.kt:467
   override fun onTaskCheckChanged(task: Task, isChecked: Boolean) {
       if (isChecked) showCompletionDialog(task)
   }
   ```

2. **CompletionDialog shown** with time tracking UI
   ```kotlin
   // CompletionDialog.kt:104
   fun completeTaskWithTracking(timeSpent, difficulty, notes) {
       viewModel.completeTask(taskId)
   }
   ```

3. **ViewModel calls Use Case**
   ```kotlin
   // TaskDetailViewModel.kt:89
   fun completeTask(taskId: Long) {
       viewModelScope.launch {
           completeTaskUseCase(taskId).fold(
               onSuccess = { /* notify success */ },
               onFailure = { /* handle error */ }
           )
       }
   }
   ```

4. **Use Case orchestrates business logic**
   ```kotlin
   // CompleteTaskUseCase.kt:30
   suspend operator fun invoke(taskId: Long): Result<Unit> {
       val task = taskRepository.getTaskById(taskId)
       var updatedTask = streakService.updateStreak(task)
       updatedTask = recurrenceService.handleRecurringCompletion(updatedTask)
       taskRepository.updateTask(updatedTask)
       return Result.success(Unit)
   }
   ```

5. **Repository persists to database**
   ```kotlin
   // TaskRepositoryImpl.kt:43
   override suspend fun updateTask(task: Task) {
       taskDao.update(task.toEntity())
   }
   ```

6. **ViewModel updates UI via LiveData**
   ```kotlin
   // TaskListViewModel.kt:35
   fun loadTasks() {
       viewModelScope.launch {
           val tasks = getTasksUseCase().getOrNull() ?: emptyList()
           _tasks.value = tasks // LiveData triggers UI update
       }
   }
   ```

---

## Key Design Patterns

### 1. Repository Pattern

**Purpose:** Abstract data sources from business logic

**Implementation:**
```kotlin
// Domain Layer - Interface
interface TaskRepository {
    suspend fun getAllTasks(): List<Task>
    suspend fun insertTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(taskId: Long)
}

// Data Layer - Implementation
class TaskRepositoryImpl(private val taskDao: TaskDao) : TaskRepository {
    override suspend fun getAllTasks() = taskDao.getAllTasks().map { it.toDomain() }
    override suspend fun insertTask(task: Task) = taskDao.insert(task.toEntity())
    // ...
}
```

**Benefits:**
- Domain layer doesn't know about Room
- Easy to swap data sources (e.g., add remote API)
- Testable with mock repositories

### 2. Use Case Pattern

**Purpose:** Encapsulate single business operations

**Implementation:**
```kotlin
class CompleteTaskUseCase(
    private val taskRepository: TaskRepository,
    private val streakService: StreakService,
    private val recurrenceService: RecurrenceService
) {
    suspend operator fun invoke(taskId: Long): Result<Unit> {
        val task = taskRepository.getTaskById(taskId) ?: return Result.failure(...)
        var updated = streakService.updateStreak(task)
        updated = recurrenceService.handleRecurringCompletion(updated)
        taskRepository.updateTask(updated)
        return Result.success(Unit)
    }
}
```

**Benefits:**
- Single Responsibility: One use case = one business operation
- Testable: No Android dependencies
- Reusable: Can be called from multiple ViewModels

### 3. MVVM Pattern

**Purpose:** Separate UI logic from business logic

**Components:**
- **Model**: Domain entities (Task, CompletionEntity)
- **View**: Activities, Fragments, Dialogs
- **ViewModel**: Presentation logic + LiveData

**Implementation:**
```kotlin
class TaskListViewModel(
    private val getTasksUseCase: GetTasksUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase
) : ViewModel() {
    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    fun loadTasks() {
        viewModelScope.launch {
            getTasksUseCase().fold(
                onSuccess = { _tasks.value = it },
                onFailure = { /* error handling */ }
            )
        }
    }
}
```

### 4. Factory Pattern

**Purpose:** Create ViewModels with dependencies

**Implementation:**
```kotlin
class TaskViewModelFactory(
    private val repository: TaskRepository,
    private val streakService: StreakService,
    private val recurrenceService: RecurrenceService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(TaskListViewModel::class.java) -> {
                TaskListViewModel(GetTasksUseCase(repository), ...) as T
            }
            // ... other ViewModels
        }
    }
}
```

### 5. Observer Pattern

**Purpose:** React to data changes

**Implementation:** LiveData + ViewModelScope
```kotlin
// ViewModel produces data
_tasks.value = updatedTasks

// Activity observes changes
viewModel.tasks.observe(this) { tasks ->
    adapter.submitList(tasks)
}
```

---

## Dream Feature (Phase 5.1)

The Dream-to-Task system provides motivational goal tracking based on the EPos psychological model, enabling users to link daily tasks to long-term dreams through milestones with XP-based progression.

### Architecture Overview

Dreams represent long-term goals, which contain Milestones (intermediate targets), which link to Tasks (daily actions). XP flows from task completion through milestones to dreams.

```
Dream ("Sportler")          Level 5 - 2,340 XP
    ├── Milestone: "5K laufen"     [500/500 XP] ✓
    ├── Milestone: "30-Day Challenge" [720/1000 XP]
    │       ├── Task: Joggen (10 XP)
    │       ├── Task: Stretching (10 XP)
    │       └── Task: Krafttraining (25 XP)
    └── Milestone: "Team beitreten" [0/750 XP]
```

### XP Flow System

The XP system creates a motivational feedback loop:

**1. Task Completion → XP to Milestone(s)**
- XP based on task priority:
  - Low: 10 XP
  - Medium: 25 XP
  - High: 50 XP
  - Urgent: 100 XP
- Full XP awarded to **each** linked milestone (no splitting)
- Tasks can link to multiple milestones across different dreams
- XP awarded instantly on task completion

**2. Milestone Completion → XP to Dream**
- User manually completes milestone via `MilestoneCompletionDialog`
- All accumulated XP transfers to parent dream
- Milestone marked as completed with timestamp
- User controls when to "claim" progress

**3. Dream Level Up → Celebration**
- Level progression formula: `XP_required = 500 × (level - 1)²`
- Level thresholds:
  - Level 2: 500 XP
  - Level 3: 2,000 XP
  - Level 5: 8,000 XP
  - Level 10: 40,500 XP
- `LevelUpDialog` shown on level increase
- Levels provide visual progress and motivation

### Database Schema (v7)

**dreams table:**
| Column | Type | Description |
|--------|------|-------------|
| dream_id | INTEGER PRIMARY KEY | Unique identifier |
| title | TEXT NOT NULL | Dream name |
| description | TEXT | Optional description |
| icon_name | TEXT | Icon identifier for UI |
| color | INTEGER | Color value for theming |
| total_xp | INTEGER DEFAULT 0 | Total accumulated XP |
| current_level | INTEGER DEFAULT 1 | Current level |
| created_at | INTEGER NOT NULL | Creation timestamp |
| is_archived | INTEGER DEFAULT 0 | Archive flag (0=active, 1=archived) |

**milestones table:**
| Column | Type | Description |
|--------|------|-------------|
| milestone_id | INTEGER PRIMARY KEY | Unique identifier |
| dream_id | INTEGER NOT NULL | Foreign key to dreams |
| title | TEXT NOT NULL | Milestone name |
| description | TEXT | Optional description |
| target_xp | INTEGER NOT NULL | XP goal for completion |
| current_xp | INTEGER DEFAULT 0 | Current progress |
| status | INTEGER DEFAULT 0 | 0=Active, 1=Completed |
| created_at | INTEGER NOT NULL | Creation timestamp |
| completed_at | INTEGER | Completion timestamp |
| order_index | INTEGER NOT NULL | Display order within dream |

**task_milestone_junction table:**
| Column | Type | Description |
|--------|------|-------------|
| task_id | INTEGER NOT NULL | Foreign key to tasks |
| milestone_id | INTEGER NOT NULL | Foreign key to milestones |
| created_at | INTEGER NOT NULL | Link timestamp |
| PRIMARY KEY (task_id, milestone_id) | | Composite key |

**milestone_interdependency table:**
| Column | Type | Description |
|--------|------|-------------|
| milestone_id | INTEGER NOT NULL | Dependent milestone |
| prerequisite_id | INTEGER NOT NULL | Required prerequisite |
| created_at | INTEGER NOT NULL | Link timestamp |
| PRIMARY KEY (milestone_id, prerequisite_id) | | Composite key |

**dream_xp_history table (v7):**
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PRIMARY KEY | Unique identifier |
| dream_id | INTEGER NOT NULL | Foreign key to dreams |
| xp_change | INTEGER NOT NULL | XP amount (positive or negative) |
| source_type | TEXT NOT NULL | Transaction source: "TASK", "MILESTONE_COMPLETION", "BONUS" |
| source_id | INTEGER | Optional reference to source task/milestone |
| created_at | INTEGER NOT NULL | Transaction timestamp |

**dream_daily_snapshots table (v7):**
| Column | Type | Description |
|--------|------|-------------|
| dream_id | INTEGER NOT NULL | Foreign key to dreams |
| date_day | INTEGER NOT NULL | Day timestamp (truncated to midnight) |
| total_xp | INTEGER NOT NULL | Total XP at end of day |
| milestones_completed | INTEGER NOT NULL | Cumulative milestone count |
| PRIMARY KEY (dream_id, date_day) | | Composite key |

### Dream Analytics (Phase 5.2)

The analytics tables enable historical tracking and visualization of progress:

**XP History Tracking:**
- Records every XP transaction with source attribution
- Supports three source types:
  - `TASK`: XP from task completion
  - `MILESTONE_COMPLETION`: Bonus XP for milestone completion
  - `BONUS`: Manual adjustments or special events
- Enables filtering by time range (e.g., "last 7 days", "this month")
- Foundation for detailed activity reports

**Daily Snapshots:**
- Aggregates progress at day granularity for performance
- Composite primary key (dream_id, date_day) prevents duplicates
- Stores cumulative values (total XP, milestones completed)
- Optimized for chart rendering (line graphs, progress curves)
- Uses `REPLACE` conflict strategy for idempotent updates

**Use Cases:**
- Progress charts showing XP growth over time
- Activity heatmaps showing productive periods
- Milestone completion rate analysis
- Comparative analytics across multiple dreams

**Analytics Dashboard:**
The Dream Analytics Dashboard provides visual insights into progress:
- **XP History Chart**: Interactive line chart using MPAndroidChart library
- **Time Range Filtering**: Toggle between 7 days, 30 days, or all time
- **Gap Analysis**: SOLL-IST comparison with SelfRegulationCard integration
- **Stats Cards**: Quick metrics (milestones completed, current streak)
- **Streak Insights**: Motivational messages based on activity patterns
- **Strategy Recommendations**: Actionable suggestions based on progress

**Implementation:**
```kotlin
// DreamAnalyticsDao.kt
interface DreamAnalyticsDao {
    // XP History
    suspend fun insertXpHistory(entry: DreamXpHistoryEntity): Long
    suspend fun getXpHistoryForDream(dreamId: Long): List<DreamXpHistoryEntity>
    suspend fun getXpHistorySince(dreamId: Long, sinceTimestamp: Long): List<DreamXpHistoryEntity>

    // Daily Snapshots
    suspend fun insertOrUpdateSnapshot(snapshot: DreamDailySnapshotEntity)
    suspend fun getSnapshotsForDream(dreamId: Long): List<DreamDailySnapshotEntity>
    suspend fun getRecentSnapshots(dreamId: Long, limit: Int): List<DreamDailySnapshotEntity>
}

// DreamAnalyticsViewModel.kt
class DreamAnalyticsViewModel(
    private val dreamId: Long,
    private val dreamDao: DreamDao,
    private val milestoneDao: MilestoneDao,
    private val analyticsDao: DreamAnalyticsDao
) : ViewModel() {
    val chartData: LiveData<LineData?>
    val hasData: LiveData<Boolean>
    val milestonesCompleted: LiveData<Int>
    val streakDays: LiveData<Int>
    val strategyRecommendations: LiveData<String>

    fun setTimeRange(timeRange: TimeRange)
}
```

**Location:**
- Entities: `/features/dreams/data/entity/DreamXpHistoryEntity.kt`, `DreamDailySnapshotEntity.kt`
- DAO: `/features/dreams/data/dao/DreamAnalyticsDao.kt`
- ViewModel: `/features/dreams/presentation/viewmodel/DreamAnalyticsViewModel.kt`
- Fragment: `/features/dreams/presentation/fragment/DreamAnalyticsFragment.kt`
- Layout: `/res/layout/fragment_dream_analytics.xml`
- Strings: `/res/values/strings_analytics.xml` (German UI)
- Database: `/shared/database/TaskDatabase.kt` (v7 migration)
- External Dependency: MPAndroidChart v3.1.0 (via JitPack)

**Navigation:**
- Access via "Analytics" button in DreamDetailFragment
- Opens in same fragment container with back stack support
- Seamless integration with existing Dream navigation flow

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **XP Distribution** | Full XP per milestone | Rewards synergies, motivates multi-linking tasks that support multiple goals |
| **Level Formula** | 500 × (lvl-1)² | Balanced progression curve, matches typical milestone XP ranges (500-1000) |
| **Cross-Dream Links** | Allowed | Tasks can realistically support multiple dreams (e.g., "Exercise" → Health + Athletic dreams) |
| **Milestone Completion** | Manual via dialog | User controls when to "claim" accumulated XP, adds intentional reflection moment |
| **Database Version** | v7 | Extended TaskDatabase with 6 tables total (4 in v6 + 2 analytics tables in v7), maintains backward compatibility |

### Navigation Structure

The app uses `BottomNavigationView` with three main tabs:

```kotlin
// MainActivity.kt - Navigation setup
bottomNavigationView.setOnItemSelectedListener { item ->
    when (item.itemId) {
        R.id.nav_tasks -> {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TasksFragment())
                .commit()
            true
        }
        R.id.nav_dreams -> {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DreamsFragment())
                .commit()
            true
        }
        R.id.nav_settings -> {
            showSettingsDialog()
            true
        }
        else -> false
    }
}
```

**Navigation Tabs:**
- **Tasks** (`TasksFragment`) - Existing task management with completion tracking
- **Dreams** (`DreamsFragment`) - Dream list with levels and progress visualization
- **Settings** - App configuration dialog

### Domain Layer Architecture

**Services** (Pure business logic):
```kotlin
// XPCalculationService.kt
class XPCalculationService {
    fun calculateTaskXP(priority: Priority): Int {
        return when (priority) {
            Priority.LOW -> 10
            Priority.MEDIUM -> 25
            Priority.HIGH -> 50
            Priority.URGENT -> 100
        }
    }
}

// LevelProgressionService.kt
class LevelProgressionService {
    fun calculateXpForLevel(level: Int): Int {
        return 500 * (level - 1) * (level - 1)
    }

    fun calculateLevel(totalXp: Int): Int {
        var level = 1
        while (calculateXpForLevel(level + 1) <= totalXp) {
            level++
        }
        return level
    }
}
```

**Use Cases** (Orchestrate operations):
```kotlin
// CompleteTaskWithXPUseCase.kt
class CompleteTaskWithXPUseCase(
    private val taskRepository: TaskRepository,
    private val milestoneRepository: MilestoneRepository,
    private val linkRepository: TaskMilestoneLinkRepository,
    private val xpService: XPCalculationService
) {
    suspend operator fun invoke(taskId: Long): Result<Int> {
        val task = taskRepository.getTaskById(taskId) ?: return Result.failure(...)
        val xp = xpService.calculateTaskXP(task.priority)

        // Award XP to all linked milestones
        val links = linkRepository.getLinksForTask(taskId)
        links.forEach { link ->
            milestoneRepository.addXP(link.milestoneId, xp)
        }

        return Result.success(xp)
    }
}

// CompleteMilestoneUseCase.kt
class CompleteMilestoneUseCase(
    private val milestoneRepository: MilestoneRepository,
    private val dreamRepository: DreamRepository,
    private val levelService: LevelProgressionService
) {
    suspend operator fun invoke(milestoneId: Long): Result<Boolean> {
        val milestone = milestoneRepository.getById(milestoneId) ?: return Result.failure(...)

        // Transfer XP to dream
        val dream = dreamRepository.getById(milestone.dreamId) ?: return Result.failure(...)
        val newTotalXP = dream.totalXp + milestone.currentXp
        val oldLevel = dream.currentLevel
        val newLevel = levelService.calculateLevel(newTotalXP)

        dreamRepository.updateXpAndLevel(dream.dreamId, newTotalXP, newLevel)
        milestoneRepository.markAsCompleted(milestoneId)

        val leveledUp = newLevel > oldLevel
        return Result.success(leveledUp)
    }
}
```

### Presentation Layer Patterns

**ViewModel with Factory:**
```kotlin
// DreamsViewModel.kt
class DreamsViewModel(
    private val getDreamsUseCase: GetDreamWithMilestonesUseCase,
    private val createDreamUseCase: CreateDreamUseCase
) : ViewModel() {
    private val _dreams = MutableLiveData<List<Dream>>()
    val dreams: LiveData<List<Dream>> = _dreams

    fun loadDreams() {
        viewModelScope.launch {
            getDreamsUseCase().fold(
                onSuccess = { _dreams.value = it },
                onFailure = { /* error handling */ }
            )
        }
    }
}

// DreamsViewModelFactory.kt
class DreamsViewModelFactory(
    private val dreamRepository: DreamRepository,
    private val milestoneRepository: MilestoneRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DreamsViewModel::class.java)) {
            return DreamsViewModel(
                GetDreamWithMilestonesUseCase(dreamRepository, milestoneRepository),
                CreateDreamUseCase(dreamRepository)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
```

**Dialog with MVVM:**
```kotlin
// MilestoneCompletionDialog.kt
class MilestoneCompletionDialog : DialogFragment() {
    private lateinit var viewModel: DreamDetailViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnComplete.setOnClickListener {
            viewModel.completeMilestone(milestoneId)
        }

        viewModel.levelUpEvent.observe(viewLifecycleOwner) { leveledUp ->
            if (leveledUp) {
                LevelUpDialog.show(requireActivity())
            }
            dismiss()
        }
    }
}
```

### Integration with Existing Task System

**Task Completion Flow with XP:**
```kotlin
// TasksFragment.kt - Task completion
override fun onTaskCheckChanged(task: Task, isChecked: Boolean) {
    if (isChecked) {
        // 1. Show existing CompletionDialog with time tracking
        CompletionDialog.show(task) { timeSpent, difficulty, notes ->
            viewModel.completeTask(task.id)

            // 2. Calculate and award XP to linked milestones
            viewModel.awardXP(task.id)

            // 3. Show XP gain feedback if task is linked
            viewModel.xpGained.observe(this) { xp ->
                if (xp > 0) {
                    showXpGainFeedback(xp)
                }
            }
        }
    }
}
```

**Task Detail with Milestone Links:**
```kotlin
// LinkTasksDialog.kt - Link tasks to milestones
class LinkTasksDialog : DialogFragment() {
    fun onMilestoneChecked(milestone: Milestone, isChecked: Boolean) {
        if (isChecked) {
            viewModel.linkTaskToMilestone(taskId, milestone.milestoneId)
        } else {
            viewModel.unlinkTaskFromMilestone(taskId, milestone.milestoneId)
        }
    }
}
```

### Testing Strategy

**Domain Layer Tests** (Priority):
```kotlin
// XPCalculationServiceTest.kt
@Test
fun calculateTaskXP_lowPriority_returns10() {
    val service = XPCalculationService()
    val xp = service.calculateTaskXP(Priority.LOW)
    assertEquals(10, xp)
}

// LevelProgressionServiceTest.kt
@Test
fun calculateLevel_withDifferentXP_returnsCorrectLevel() {
    val service = LevelProgressionService()
    assertEquals(1, service.calculateLevel(0))
    assertEquals(2, service.calculateLevel(500))
    assertEquals(5, service.calculateLevel(8000))
}

// CompleteMilestoneUseCaseTest.kt
@Test
fun invoke_transfersXPToDream_andReturnsLevelUp() = runBlocking {
    // Mock repositories
    val milestone = Milestone(currentXp = 500)
    val dream = Dream(totalXp = 1500, currentLevel = 2)

    val result = useCase.invoke(milestoneId)

    assertTrue(result.isSuccess)
    verify(dreamRepository).updateXpAndLevel(dreamId, 2000, 3)
}
```

### Performance Considerations

**Database Queries Optimization:**
```kotlin
// DreamDao.kt - Efficient queries with relations
@Transaction
@Query("SELECT * FROM dreams WHERE is_archived = 0 ORDER BY created_at DESC")
suspend fun getDreamsWithMilestones(): List<DreamWithMilestones>

// Use @Relation for automatic join
data class DreamWithMilestones(
    @Embedded val dream: DreamEntity,
    @Relation(
        parentColumn = "dream_id",
        entityColumn = "dream_id"
    )
    val milestones: List<MilestoneEntity>
)
```

**Lazy Loading:**
- Dreams list: Load basic info only
- Detail view: Load full milestone list on demand
- Task links: Load only when LinkTasksDialog is opened

### Milestone Dependencies (Phase 5.2)

The milestone dependencies system provides visual indicators and soft warnings for prerequisite relationships, helping users understand milestone order without blocking progress.

**Implementation:**
- **Visual Indicators**: Each milestone shows a badge (🔗) with prerequisite count
- **Color Coding**: Green if prerequisites met, Orange if unmet
- **Soft Warning**: Dialog appears when completing milestones with unmet prerequisites
- **User Choice**: Users can proceed anyway or go back to complete prerequisites first

**Architecture:**
```kotlin
// InterdependencyService - Domain Layer (Pure Kotlin)
class InterdependencyService {
    fun getPrerequisites(milestoneId: Long, allMilestones: List<Milestone>,
                        interdependencies: List<MilestoneInterdependency>): List<Milestone>

    fun getUnmetPrerequisites(milestoneId: Long, allMilestones: List<Milestone>,
                             interdependencies: List<MilestoneInterdependency>): List<Milestone>

    fun hasUnmetPrerequisites(milestoneId: Long, allMilestones: List<Milestone>,
                             interdependencies: List<MilestoneInterdependency>): Boolean
}

// PrerequisiteWarningDialog - Presentation Layer
class PrerequisiteWarningDialog : DialogFragment(), Callback {
    interface Callback {
        fun onContinueAnyway(milestoneId: Long)
        fun onGoBack()
    }
}
```

**Data Flow:**
1. DreamDetailFragment loads milestones and interdependencies
2. For each milestone, calculate prerequisite info (total and unmet count)
3. Pass info to MilestoneListAdapter via `setPrerequisites()`
4. Adapter displays badge with appropriate color
5. On milestone completion attempt, check for unmet prerequisites
6. If found, show PrerequisiteWarningDialog with list
7. User chooses: Go Back or Continue Anyway
8. If Continue, proceed with normal completion flow

**Key Files:**
- Service: `/features/dreams/domain/service/InterdependencyService.kt`
- Dialog: `/features/dreams/presentation/dialog/PrerequisiteWarningDialog.kt`
- Layout: `/res/layout/dialog_prerequisite_warning.xml`
- Strings: `/res/values/strings_dependencies.xml` (German)
- Integration: `/features/dreams/presentation/fragment/DreamDetailFragment.kt`
- Adapter: `/features/dreams/presentation/adapter/MilestoneListAdapter.kt`

### Future Enhancements

**Phase 5.2+ Planned Features:**
1. ✅ **Milestone Dependencies** - Visual indicators and soft warnings implemented (v0.3.64)
2. **Dream Analytics** - Progress charts, completion trends
3. **Self-Regulation Tracking** - Monitor planning vs execution patterns
4. **Dream Templates** - Pre-defined dreams with milestone suggestions
5. **Achievement System** - Badges for dream milestones
6. **Dream Sharing** - Export/import dream structures

---

## Dependencies

### Dependency Injection (Manual)

Currently using **manual dependency injection**:

```kotlin
// TaskActivity.kt:78
val database = TaskDatabase.getDatabase(this)
val taskDao = database.taskDao()
val repository = TaskRepositoryImpl(taskDao)
val streakService = StreakService()
val recurrenceService = RecurrenceService()

val viewModelFactory = TaskViewModelFactory(
    repository,
    streakService,
    recurrenceService
)
viewModel = ViewModelProvider(this, viewModelFactory)
    .get(TaskListViewModel::class.java)
```

**Future:** Migrate to Hilt or Koin for automatic DI (Phase 5+)

---

## Testing Strategy

### Test Coverage by Layer

| Layer | Coverage | Test Type | Tools |
|-------|----------|-----------|-------|
| **Domain Services** | 95%+ | Unit Tests | JUnit, Mockito |
| **Use Cases** | 95%+ | Unit Tests | JUnit, Mockito, Coroutines-Test |
| **Repositories** | N/A (Interface) | Integration Tests | Room Testing |
| **ViewModels** | 0% (Future) | Unit Tests | LiveData Testing |
| **UI** | 0% (Future) | UI Tests | Espresso |

### Testing Principles

1. **Domain Layer (70%+ Coverage Target)**
   - **Services**: Pure Kotlin, no mocks needed
   - **Use Cases**: Mock repository, use real services
   - **Focus**: Business logic correctness

2. **Integration Tests (Future)**
   - Test Repository + DAO + Room
   - Use in-memory database
   - Verify persistence correctness

3. **UI Tests (Future)**
   - Test complete user flows
   - Use Espresso for UI automation

### Test Files

```
app/src/test/java/com/secretary/features/tasks/domain/
├── service/
│   ├── StreakServiceTest.kt         # 27 tests
│   └── RecurrenceServiceTest.kt     # 45 tests
└── usecase/
    ├── CompleteTaskUseCaseTest.kt   # 18 tests
    ├── CreateTaskUseCaseTest.kt     # 7 tests
    ├── GetTasksUseCaseTest.kt       # 9 tests
    ├── UpdateTaskUseCaseTest.kt     # 6 tests
    └── DeleteTaskUseCaseTest.kt     # 5 tests

Total: 117 tests, 95% pass rate
```

---

## Key Architecture Achievements

### ✅ What We've Accomplished

1. **Complete Layer Separation**
   - Domain layer has zero Android dependencies
   - All business logic testable without emulator

2. **MVVM Implementation**
   - ViewModels handle all presentation logic
   - Activities only render UI and handle user input
   - LiveData ensures reactive UI updates

3. **Use Case Pattern**
   - Each business operation isolated
   - Single Responsibility Principle enforced
   - Easy to add new features

4. **Repository Pattern**
   - Data source abstraction
   - Room implementation hidden from domain
   - Ready for remote API integration

5. **Testability**
   - 117 unit tests covering domain layer
   - Mock-friendly architecture
   - Fast test execution (no Android framework)

### 🚧 Future Improvements

1. **Dependency Injection Framework** (Hilt or Koin)
   - Reduce boilerplate in Activities
   - Improve testability with automatic mocking

2. **ViewModel Testing** (Phase 5)
   - Add LiveData testing utilities
   - Test presentation logic thoroughly

3. **UI Testing** (Phase 5)
   - Espresso integration tests
   - Test complete user journeys

4. **Remote Data Source** (Phase 6+)
   - Add RemoteDataSource implementation
   - Sync tasks to cloud backend

---

## Related Documentation

- **[ROADMAP.md](ROADMAP.md)** - Development phases and future plans
- **[DEBUGGING.md](DEBUGGING.md)** - Debugging guide and common issues
- **[CLAUDE.md](CLAUDE.md)** - Developer environment setup
- **[README.md](README.md)** - Project overview and features

---

**Architecture Status:** ✅ **Complete and Production-Ready**
**Last Major Refactor:** Phase 4.5 (6 months of work)
**Latest Feature:** Phase 5.1 - Dream-to-Task System (Complete)
**Next Phase:** Phase 5.2+ - Advanced Features (Dream Analytics, Dependencies, Self-Regulation)
