# Testing Infrastructure

**Created:** 2025-11-13 (Phase 4.5.1)
**Status:** Setup in progress

---

## Directory Structure

```
devkit/testing/
├── domain/           # Unit tests for Use Cases and Services
├── data/             # Integration tests for Repositories and DAOs
├── integration/      # End-to-end integration tests
├── fixtures/         # Test data and mock objects
└── README.md         # This file
```

---

## Test Coverage Goals

- **Domain Layer:** 70%+ coverage (Use Cases, Services)
- **Data Layer:** 50%+ coverage (Repositories, DAOs)
- **Presentation Layer:** 30%+ coverage (ViewModels)

---

## Testing Strategy

### Phase 4.5.1 (Current)
- Setup test directory structure
- Document baseline behavior (see `docs/REFACTORING_BASELINE.md`)
- NO tests yet (will be added in Phase 4.5.4-4.5.6)

### Phase 4.5.4 (Domain Layer Refactoring)
- Write unit tests for all Use Cases
- Write unit tests for all Services (RecurrenceService, StreakService)
- Run tests on GitHub Actions (no Android framework needed)

### Phase 4.5.5 (Presentation Layer Refactoring)
- Write tests for ViewModels
- Use Mockito to mock Use Cases

### Phase 4.5.6 (Testing & Documentation)
- Write integration tests for Repositories
- Write UI tests for critical flows (Espresso)
- Generate code coverage reports (JaCoCo)

---

## Running Tests

**Phase 4.5.1:** No tests yet

**Future (Phase 4.5.4+):**
```bash
# Run all tests on GitHub Actions
git push origin refactoring/phase-4.5-architecture

# View test results
gh run view --log
```

---

## Test Frameworks

- **JUnit 5:** Unit testing framework
- **Mockito:** Mocking framework for dependencies
- **Espresso:** UI testing (for critical flows)
- **JaCoCo:** Code coverage reports

---

## Test Naming Convention

**Use Cases:**
```
CompleteTaskUseCaseTest.java
- testExecute_NonRecurringTask_MarksCompleted()
- testExecute_IntervalTask_ResetsAndAdvancesDueDate()
- testExecute_FrequencyTask_IncrementsCounter()
```

**Services:**
```
RecurrenceServiceTest.java
- testHandleIntervalCompletion_DailyTask_Advances24Hours()
- testHandleFrequencyCompletion_PeriodBoundary_ResetsCounter()
```

**Repositories:**
```
TaskRepositoryImplTest.java
- testGetAllTasks_ReturnsAllTasks()
- testInsertTask_SavesCorrectly()
- testUpdateTask_PersistsChanges()
```

---

## Baseline Regression Tests

Before refactoring, we documented all critical user flows in `docs/REFACTORING_BASELINE.md`.

After each refactoring phase, manually verify:
- [ ] Task creation works
- [ ] Task completion works (all recurrence types)
- [ ] Task editing works
- [ ] Filtering and sorting works
- [ ] Statistics display correctly
- [ ] Streak tracking works
- [ ] Auto-update system works

---

**Last Updated:** 2025-11-13
