# REVIEW_BACKLOG — database/

## Open Issues

*(None.)*

---

## Resolved Issues

### ✅ [simplify] Converters: boilerplate in DateTime and enum converters
**File:** `Converters.java:54-203`
**Fixed:** Extracted four generic private helpers (`serialize(T)`, `deserialize(String, Function<String,T>)`, `fromEnum(E)`, `toEnum(String, Class<E>)`). All 24+ converter methods remain as concrete public static `@TypeConverter`-annotated methods (Room requires this for reflection-based discovery), but now delegate to helpers. Reduces boilerplate by ~70 LOC, clarifies pattern repeats, and makes maintenance easier without changing behavior.

### ✅ [stale] `AppDatabase.getInstance()` — misleading "not for production" comment
**File:** `AppDatabase.java:117-118`
**Fixed:** Replaced the standard Android boilerplate ("Never use in production with user data") with a project-specific note explaining the deliberate trade-off: fallbackToDestructiveMigration is intentional; manual Migration subclasses are forbidden; see CLAUDE.md.
