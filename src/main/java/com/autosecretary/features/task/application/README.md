# Task application package conventions

- Put immutable application-layer display DTOs used by list rendering in `application/listmodel/` (for example: `TaskListItem`).
- Keep use-cases, services, and mappers in their current `application/` subpackages (`mapper`, etc.).
- Do not introduce `application/model` for task list display objects; use `application/listmodel` consistently.
