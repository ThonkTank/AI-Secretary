# AI Secretary - Architektur

## Architektur-Entscheidung

**Gewählt:** Clean Architecture (vollständig)

---

## Warum Clean Architecture?

### Vorteile für dieses Projekt

| Vorteil | Bedeutung für AI Secretary |
|---------|---------------------------|
| **Testbarkeit** | Domain-Logik (Streaks, XP, Scheduling) isoliert testbar |
| **Austauschbarkeit** | SQLite später durch Room ersetzbar ohne Domain-Änderungen |
| **Skalierbarkeit** | Neue Features (AI-Integration) sauber integrierbar |
| **Wartbarkeit** | Klare Verantwortlichkeiten pro Schicht |

### Nachteile (akzeptiert)

- Mehr Boilerplate-Code initial
- Overhead bei sehr einfachen Features
- Lernkurve bei strikter Einhaltung

---

## Schichten-Übersicht

```
┌─────────────────────────────────────────────┐
│           PRESENTATION LAYER                │
│  (Activities, ViewModels, Adapters, UI)     │
├─────────────────────────────────────────────┤
│              DOMAIN LAYER                   │
│  (Entities, Use Cases, Repository-Interfaces)│
├─────────────────────────────────────────────┤
│               DATA LAYER                    │
│  (Repository-Impl, DAOs, Database, Mapper)  │
└─────────────────────────────────────────────┘
```

### Dependency Rule

**Abhängigkeiten zeigen NUR nach innen:**
- Presentation → Domain ✅
- Data → Domain ✅
- Domain → nichts (keine Abhängigkeiten) ✅

---

## Layer-Details

### 1. Domain Layer (Kern)

**Enthält:**
- **Entities:** Task, Persona, Ziel
- **Use Cases:** Geschäftslogik-Operationen
- **Repository Interfaces:** Abstrakte Datenzugriffs-Definitionen
- **Services:** Komplexe Berechnungen (Scheduling, XP, Streaks)

**Regeln:**
- Keine Android-Framework-Abhängigkeiten
- Keine Datenbank-Abhängigkeiten
- Pure Java/Kotlin

### 2. Data Layer

**Enthält:**
- **Repository Implementations:** Konkrete Datenzugriffs-Logik
- **DAOs:** Database Access Objects
- **Database:** SQLite/Room Setup
- **Mapper:** Entity ↔ Domain Model Konvertierung

**Regeln:**
- Implementiert Domain-Interfaces
- Kapselt alle Datenbank-Details
- Entities nur intern, Domain Models nach außen

### 3. Presentation Layer

**Enthält:**
- **Activities:** UI-Lifecycle, User Interaction
- **ViewModels:** UI-State, Use Case Orchestrierung
- **Adapters:** Listen-Darstellung
- **Dialogs:** Benutzer-Eingaben

**Regeln:**
- Ruft nur Use Cases auf
- Kein direkter Datenbankzugriff
- Beobachtet ViewModel-State (LiveData/StateFlow)

---

## Projektstruktur

```
src/com/secretary/
├── domain/
│   ├── entity/
│   │   ├── Task.java
│   │   ├── Persona.java
│   │   └── Ziel.java
│   │
│   ├── repository/
│   │   ├── TaskRepository.java (Interface)
│   │   ├── PersonaRepository.java (Interface)
│   │   └── ZielRepository.java (Interface)
│   │
│   ├── usecase/
│   │   ├── CreateTaskUseCase.java
│   │   ├── CreatePersonaUseCase.java
│   │   ├── CreateZielUseCase.java
│   │   ├── CompleteTaskUseCase.java
│   │   └── scheduling/
│   │       ├── GenerateDailyTodoListUseCase.java
│   │       ├── TaskChainLearningService.java
│   │       └── TimePreferenceLearningService.java
│   │
│   └── service/
│       ├── XPCalculationService.java
│       ├── StreakService.java
│       └── RecurrenceService.java
│
├── data/
│   ├── repository/
│   │   ├── TaskRepositoryImpl.java
│   │   ├── PersonaRepositoryImpl.java
│   │   └── ZielRepositoryImpl.java
│   │
│   ├── database/
│   │   ├── AppDatabase.java
│   │   ├── TaskDao.java
│   │   ├── PersonaDao.java
│   │   └── ZielDao.java
│   │
│   └── entity/
│       ├── TaskEntity.java
│       ├── PersonaEntity.java
│       └── ZielEntity.java
│
└── presentation/
    ├── task/
    │   ├── TaskActivity.java
    │   ├── TaskViewModel.java
    │   └── TaskAdapter.java
    │
    ├── persona/
    │   ├── PersonaActivity.java
    │   └── PersonaViewModel.java
    │
    └── common/
        ├── MainActivity.java
        └── BaseViewModel.java
```

---

## Datenfluss-Beispiel

**Task als erledigt markieren:**

```
1. User tippt auf "Erledigt" Button
   ↓
2. TaskActivity.onCompleteClick(taskId)
   ↓
3. TaskViewModel.completeTask(taskId)
   ↓
4. CompleteTaskUseCase.execute(taskId)
   ├── StreakService.updateStreak(task)
   ├── XPCalculationService.calculateXP(task)
   ├── RecurrenceService.scheduleNext(task)
   └── TaskRepository.update(task)
   ↓
5. TaskRepositoryImpl.update(task)
   ├── TaskMapper.toEntity(task)
   └── TaskDao.update(entity)
   ↓
6. ViewModel aktualisiert UI-State
   ↓
7. Activity beobachtet Änderung, UI aktualisiert
```

---

## Technische Details

| Aspekt | Technologie |
|--------|-------------|
| **Sprache** | Java 8 |
| **Datenbank** | SQLite (später Room) |
| **Build** | GitHub Actions |
| **Min SDK** | 26 (Android 8.0) |
| **Architecture Components** | ViewModel, LiveData |

---

*Erstellt: 2025-01-12*
*Basierend auf: Architecture Discovery Session*
