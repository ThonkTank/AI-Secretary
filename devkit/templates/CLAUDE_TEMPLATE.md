# CLAUDE.md Template for Directories

**Purpose:** Template for per-directory CLAUDE.md files

Use this template when creating CLAUDE.md files for new directories in the project.

---

## Template Structure

```markdown
# [Directory Name] - [Brief Description]

**Purpose:** [What this directory does in 1-2 sentences]

**Key Principle:** [Main architectural principle, e.g., "NO Android dependencies"]

---

## Directory Structure

```
directory_name/
├── subdirectory1/
│   ├── File1.java              # Brief comment on purpose
│   └── File2.java              # Brief comment on purpose
├── subdirectory2/
│   └── File3.java              # Brief comment on purpose
└── CLAUDE.md                   # This file
```

---

## Purpose

[Detailed explanation of what this directory contains and why it exists]

Key goals:
- [Goal 1]
- [Goal 2]
- [Goal 3]

---

## Key Workflows

### [Workflow Name 1]

**Entry Point:** `FileName.java:methodName()`

**Flow:**
1. [Step 1]
2. [Step 2]
3. [Step 3]

**Code Reference:** See `FileName.java:25-45` for implementation.

**Detailed Docs:** [docs/FEATURE_NAME.md](../../docs/FEATURE_NAME.md)

### [Workflow Name 2]

**Entry Point:** `OtherFile.java:method()`

**Key Points:**
- [Point 1]
- [Point 2]

**Code Reference:** See `OtherFile.java:15-30`.

---

## Important Conventions

- **Convention 1:** [Description]
- **Convention 2:** [Description]
- **Convention 3:** [Description]

---

## Testing

Unit tests located in: `devkit/testing/[layer_name]/`

Run tests:
```bash
# Run on GitHub Actions
git push origin [branch]
gh run view --log
```

**Coverage Target:** [e.g., 70%+ for domain layer]

---

## Related Documentation

- [Main Architecture](../../docs/ARCHITECTURE.md)
- [Feature Documentation](../../docs/FEATURE_NAME.md)
- [Testing Strategy](../../devkit/testing/README.md)

---

**Last Updated:** YYYY-MM-DD
**Maintainer:** AI Secretary Development Team
```

---

## Full Example: features/tasks/domain/CLAUDE.md

```markdown
# features/tasks/domain - Task Business Logic

**Purpose:** Contains all business logic for task management (Use Cases, Services, domain models).

**Key Principle:** NO Android dependencies. Pure Java/Kotlin. Fully testable without Android framework.

---

## Directory Structure

```
domain/
├── model/
│   ├── Task.java                  # Pure task entity (id, title, recurrence, etc.)
│   └── RecurrenceRule.java        # Encapsulates INTERVAL/FREQUENCY logic
├── repository/
│   └── TaskRepository.java        # Interface for data access (impl in data/)
├── usecase/
│   ├── CompleteTaskUseCase.java   # Orchestrates task completion
│   ├── CreateTaskUseCase.java     # Validates and creates new tasks
│   └── UpdateTaskUseCase.java     # Updates existing tasks
├── service/
│   ├── RecurrenceService.java     # Complex recurrence calculations
│   └── StreakService.java         # Streak tracking logic
└── CLAUDE.md                      # This file
```

---

## Purpose

This directory contains the **core business logic** of the task management system. All domain logic is isolated here to:
- Enable 70%+ unit test coverage (no Android framework needed)
- Enforce Clean Architecture (Presentation → Domain → Data)
- Make business rules explicit and maintainable

---

## Key Workflows

### Completing a Task

**Entry Point:** `CompleteTaskUseCase.java:execute(taskId)`

**Flow:**
1. Fetch task from Repository
2. Update streak (StreakService)
3. Handle recurrence if applicable (RecurrenceService)
4. Save updated task via Repository

**Code Reference:** See `CompleteTaskUseCase.java:25-45` for implementation.

**Detailed Docs:** [docs/TASK_COMPLETION_FLOW.md](../../../docs/TASK_COMPLETION_FLOW.md)

### Creating a Task

**Entry Point:** `CreateTaskUseCase.java:execute(task)`

**Validation Rules:**
- Title must not be empty
- Recurrence must be valid (if present)
- Due date must be in future (if present)

**Code Reference:** See `CreateTaskUseCase.java:15-30`.

---

## Important Conventions

- **All Use Cases:** Single public method `execute()` or `invoke()`
- **Services:** Stateless, reusable logic components
- **Models:** Immutable where possible (use builders)
- **NO Android Imports:** Domain layer is framework-agnostic

---

## Testing

Unit tests located in: `devkit/testing/domain/`

Run tests:
```bash
# Run on GitHub Actions
git push origin [branch]
gh run view --log
```

**Coverage Target:** 70%+ for all Use Cases and Services

---

## Related Documentation

- [Architecture Overview](../../../docs/ARCHITECTURE.md)
- [Task Completion Flow](../../../docs/TASK_COMPLETION_FLOW.md)
- [Recurrence System](../../../docs/RECURRENCE_SYSTEM.md)
- [Testing Strategy](../../../devkit/testing/README.md)

---

**Last Updated:** 2025-11-13
**Maintainer:** AI Secretary Development Team
```

---

**Usage:** Copy the template section above and fill in the placeholders for your directory.
