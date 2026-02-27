# Review Backlog — `mutations/`

## Open Issues

---

### [drift] `RoomDatabase` direct reference in application-layer class — `TaskSlotToggleMutation.java:39,83,95`

`TaskSlotToggleMutation` holds a `RoomDatabase` instance to call `runInTransaction()`.
Room infrastructure types normally stay in the `data/` layer; the application layer
accesses data only through DAOs. No other application-layer class holds a `RoomDatabase`
reference. The cross-DAO transaction requirement (writing `task`, `slot`, and
`transitionStat` atomically) is the justification, and Room's intended API for this is
`RoomDatabase.runInTransaction()` — so the usage is correct. However, the dependency
direction is inconsistent with the rest of the codebase.

**Why it matters:** Introduces a precedent that future contributors might copy when a
simpler DAO method would suffice.

**Suggested fix (if scope allows):** Expose a `@Transaction`-annotated method on
`TaskDAO` (making it an abstract class rather than an interface) that encapsulates the
combined write. This would remove the `RoomDatabase` field from the application layer.
Given that no repository layer exists in this project, this change requires converting
`TaskDAO` from interface to abstract class — evaluate separately.

**Note:** This issue affects `AppCompositionRoot.java` (caller) as well but the
architectural concern originates here.

---
