# Task application package conventions

- Put immutable application-layer display DTOs used by list rendering in `application/listmodel/` (for example: `TaskListItem`).
- Keep list-presentation-only mapping helpers in `application/listmodel/` too (for example: `TaskListItemMapper`).
- Keep non-list mappers, use-cases, and services in their current `application/` subpackages (`mapper`, etc.).
- Do not introduce `application/model` for task list display objects; use `application/listmodel` consistently.
