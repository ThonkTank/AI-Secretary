# Shared Package

Cross-feature enums and constants used throughout the AutoSecretary app.

## Overview

The `shared/` package contains definitions that are accessed by multiple features (task scheduling, budget tracking, widget configuration) and should not be tied to any single feature's domain layer.

## Contents

### Priority

Task priority levels (LOW, MEDIUM, HIGH, CRITICAL) with multiplicative scoring weights used by the task scheduler during daily planning.

**When to use:** When creating or editing a task, choose a priority level that reflects urgency and importance:
- **LOW** — Routine, non-urgent work with no time pressure
- **MEDIUM** — Normal tasks with standard importance (default)
- **HIGH** — Important or time-sensitive work
- **CRITICAL** — Urgent blockers; should appear at the top of the daily schedule

The numerical weight values (100, 200, 400, 10000) are not typically used in UI code; they're consumed by the domain scheduling logic in `TaskScorer`.

### Period

Scheduling period units (DAY, WEEK, MONTH) with fixed day counts, used for task repetition patterns.

**Key design note:** MONTH is fixed at 30 days, not adjusted for actual calendar month length. This simplifies scheduling logic.

### WidgetConfiguration

App-wide widget configuration constants shared between the task and budget widgets.

**Important:** If you change `WIDGET_UPDATE_PERIOD_MILLIS`, also update the corresponding Android widget configuration XML files:
- `src/main/res-task/xml/widget_task_info.xml` (android:updatePeriodMillis attribute)
- `src/main/res-budget/xml/widget_budget_info.xml` (android:updatePeriodMillis attribute)

The Android framework cannot reference Java constants in widget XML directly, but the Gradle task
`validateWidgetUpdatePeriods` verifies the XML values match the shared constant and fails the build
on mismatch.

## When to add new items

Only add to this package if the constant or enum is **truly used across multiple features**. Single-feature enums belong in that feature's domain layer (e.g., `features/task/domain/` or `features/budget/domain/`).
