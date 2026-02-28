# Review Backlog — `mutations/`

## Open Issues

---

### [warning] 1/3 Convert TaskDAO to abstract class with @Transaction methods @skill:review-architecture

**Files:** features/task/data/TaskDAO.java

**Change:** Convert from interface to abstract class. Add `@Transaction`-annotated default methods to coordinate multi-DAO writes for task+slot+transitionStat atomicity.

**Note:** *(Needs promotion above <TARGET_DIR>)* — This file is in `features/task/data/`, outside the target scope. The full fix requires coordination across data/, application/, and app/ layers.

**Context:** Currently `TaskSlotToggleMutation` directly calls `RoomDatabase.runInTransaction()` to coordinate writes across DAO boundaries. Moving this responsibility to `TaskDAO` maintains layer boundaries (application layer uses DAOs only, not framework types).

---

### [warning] 2/3 Update TaskSlotToggleMutation to use coordinated DAO methods @skill:review-architecture

**Files:** TaskSlotToggleMutation.java:49,56,62,109,118,130,132

**Change:** Remove `RoomDatabase` dependency from this class. Replace direct `db.runInTransaction()` calls with the new `TaskDAO` transaction methods added in sub-task 1/3.

**Dependency:** Requires sub-task 1/3 (TaskDAO refactor) to be completed first.

---

### [warning] 3/3 Update AppCompositionRoot to remove db parameter @skill:review-architecture

**Files:** app/AppCompositionRoot.java

**Change:** Remove `RoomDatabase` from the composition root. Stop passing `db` parameter to `TaskSlotToggleMutation` constructor (after sub-task 2/3 removes the dependency).

**Note:** *(Needs promotion above <TARGET_DIR>)* — This file is in `app/`, outside the target scope. Coordinate with sub-tasks 1/3 and 2/3.

**Dependency:** Requires sub-task 2/3 (TaskSlotToggleMutation refactor) to be completed first.

---

