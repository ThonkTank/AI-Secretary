# Review Backlog — features/ (cross-feature)

Issues that span multiple features and belong at the lowest common ancestor.

## Open Issues

### [inconsistent] DAO naming convention differs between task and budget features
**Files:** `task/data/TaskDao.java`, `task/data/TaskScheduleConfigDao.java`, `task/data/TaskTransitionStatDao.java` vs all 5 budget DAOs in `budget/data/dao/`

**What makes it hard to navigate today:**
Task DAOs use `read*/write*/delete*` (custom project convention where `write*` = upsert via REPLACE). Budget DAOs use JPA-style `find*/get*/insert*/update*/delete*`. A contributor working across features faces two incompatible conventions for the same concept (`readAll` vs `findAll`, `write(entity)` vs `insert(entity)`). The task convention's `write*` naming signals an upsert semantic that is invisible in budget DAOs despite many also using `onConflict = REPLACE`.

**Proposed change:** Align budget DAOs to the task convention (`read*/write*/delete*`). This is the documented project convention in `task/data/TaskDao.java` Javadoc. Budget DAOs using `insert(onConflict = REPLACE)` are semantically upserts and should be named `write*`. Those with `OnConflictStrategy.IGNORE` should keep a distinct name.

**Why it reduces mental load:** One naming convention across the project eliminates the "which pattern do I follow?" question for new contributors.

**Tradeoffs / risks:** All 5 budget DAOs + all callers in `BudgetRoomRepository`, `BudgetImportRoomRepository`, `BudgetViewModel`. Large coordinated rename — deferred.

*(Promoted from `budget/data/REVIEW_BACKLOG.md`)*

---

### ✅ [inconsistent] BudgetViewModel vs TaskViewModel — different thread-posting mechanisms
**Files:** `budget/ui/BudgetViewModel.java`, `task/ui/list/TaskViewModel.java`

**Resolved:** `BudgetViewModel` no longer injects `Consumer<Runnable> postToMain`. All LiveData updates
use `liveData.postValue(x)` directly, consistent with `TaskViewModel`. The constructor was trimmed to
remove the now-unnecessary parameter. Both ViewModels follow the same threading convention.

*(Promoted from `budget/ui/REVIEW_BACKLOG.md`)*

---

### [consider] `task/data/` flat structure (13 files) vs `budget/data/` sub-packaged structure

**Paths involved:** `task/data/` (13 Java files: entities, DAOs, factory mixed together), `budget/data/` (sub-packages: `dao/`, `entity/`, `repository/`, `api/`, `keystore/`)

**What makes it hard to navigate today:** A reader moving from budget to task encounters two different organizational principles for the same layer. Budget separates entities from DAOs from repositories; task puts everything in one flat folder. When scanning `task/data/`, a reader must mentally classify each of the 13 files (entity? DAO? factory?) — the folder gives no structural cues.

**Proposed change:** Create `task/data/entity/` and `task/data/dao/` sub-packages, matching budget's structure. Move `Task.java`, `TaskCore.java`, `TaskSlot.java`, `TaskPrefSlot.java`, `TaskRelation.java`, `TaskPrerequisite.java`, `TaskPlannedMeal.java`, `TaskScheduleConfig.java`, `TaskTransitionStat.java`, `TaskPrefSlotFactory.java` to `entity/`; move `TaskDao.java`, `TaskScheduleConfigDao.java`, `TaskTransitionStatDao.java` to `dao/`.

**Why it reduces mental load:** Consistent cross-feature data-layer organization — a reader learns the pattern once.

**Tradeoffs / risks:** 13 files moving → massive import churn across the entire codebase (every file that imports `task.data.*` needs updating). For a feature-complete project, the churn cost likely outweighs the navigation benefit. The flat structure is also defensible at 13 files — it's not unmanageable. Deferred unless a broader refactor is planned.

## Acknowledged Good Patterns

### [keep] Consistent `application/data/domain/ui` layer split across all three features
Each feature (`budget/`, `task/`, `meal/`) follows the same four-layer structure. A reader moving between features finds an identical layout, making the entire project immediately navigable after learning one feature.

### [keep] `internal/` convention applied consistently
Both `task/domain/internal/`, `budget/domain/recurring/internal/`, and `meal/data/internal/` use `internal/` sub-packages to signal "implementation detail, not public API." This structural cue is the strongest navigation aid in the project — a reader knows `internal/` folders can be safely skipped during initial exploration.

### [keep] `shared/` package contains only genuinely cross-feature types
`Period.java`, `Priority.java`, and `WidgetConfiguration.java` are each imported by multiple features. No feature-specific types have leaked into `shared/`.
