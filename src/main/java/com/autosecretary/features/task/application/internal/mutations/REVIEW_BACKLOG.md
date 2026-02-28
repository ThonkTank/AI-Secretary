# Review Backlog — `mutations/`

## Open Issues

---

### [drift] `RoomDatabase` direct reference in application-layer class — `TaskSlotToggleMutation.java:49,56,102,115,129`

`TaskSlotToggleMutation` holds a `RoomDatabase` instance to call `runInTransaction()`.
Room infrastructure types normally stay in the `data/` layer; the application layer
accesses data only through DAOs. No other application-layer class holds a `RoomDatabase`
reference. The cross-DAO transaction requirement (writing `task`, `slot`, and
`transitionStat` atomically) is the justification, and Room's intended API for this is
`RoomDatabase.runInTransaction()` — so the usage is correct. However, the dependency
direction is inconsistent with the rest of the codebase.

**Why it matters:** Introduces a precedent that future contributors might copy when a
simpler DAO method would suffice. Also makes testing harder (requires mocking `RoomDatabase`).

**Suggested fix (if scope allows):** Expose a `@Transaction`-annotated method on
`TaskDAO` (making it an abstract class rather than an interface) that encapsulates the
combined write. This would require:
1. Converting `TaskDAO` from interface to abstract class
2. Adding `@Transaction` default method(s) that coordinate writes with `transitionDao`
3. Updating `AppCompositionRoot.java` to not pass `db` parameter
Given that no repository layer exists and multi-DAO transactions require framework
support, evaluate whether the refactoring complexity is worth the architectural benefit.

**Note:** This issue affects `AppCompositionRoot.java` (caller) as well but the
architectural concern originates here.

---

