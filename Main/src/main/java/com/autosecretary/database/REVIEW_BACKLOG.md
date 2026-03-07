# REVIEW_BACKLOG — database/

## Open Issues

*(None.)*

---

## Resolved Issues

### ✅ [simplify] Converters: boilerplate in DateTime and enum converters
**File:** `Converters.java:54-203`
**Fixed:** Extracted four generic private helpers (`serialize(T)`, `deserialize(String, Function<String,T>)`, `fromEnum(E)`, `toEnum(String, Class<E>)`). All 24+ converter methods remain as concrete public static `@TypeConverter`-annotated methods (Room requires this for reflection-based discovery), but now delegate to helpers. Reduces boilerplate by ~70 LOC, clarifies pattern repeats, and makes maintenance easier without changing behavior.

### ✅ [policy] `AppDatabase.getInstance()` migration policy aligned with production usage
**File:** `AppDatabase.java`
**Fixed:** Removed `fallbackToDestructiveMigration()` and updated comments/Javadoc to reflect the current policy: user data must be preserved, schema changes require compatible Room migrations, and missing migrations should fail fast.
