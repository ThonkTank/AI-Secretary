# Review Backlog — meal/application/internal

## Resolved Issues (Completed in this run)

✅ **[blocker]** No README.md — Added `README.md` with detailed module overview, usage examples, data structure documentation, and integration guidance.

✅ **[blocker]** LegacyMealImportService.java:83 — sourceRows parameter undocumented. Added comprehensive javadoc to `importOnce()` explaining parameter structure, processing order, and error semantics.

✅ **[friction]** LegacyImportReport class — Added class-level and method-level javadoc explaining:
- That migratedBySource() is a success count map per entity type.
- That failures() contains partial rows with reasons, and does not prevent other rows/types from importing.
- How to interpret results and decide whether to accept partial import.

✅ **[friction]** LegacyMealImportService.java:83-101 — No documentation on import semantics. Added javadoc to importOnce() explaining:
- Idempotent behavior (can only succeed once).
- Sequential processing of entity types.
- Partial failure semantics (no rollback).
- Error handling expectations.

✅ **[friction]** importIngredients and similar methods — Added javadoc to importIngredients() and importRecipes() explaining:
- Which fields are required (upfront validation).
- Which fields are optional (with specific defaults for each type).
- Pattern applies to all import methods.

✅ **[comment]** Compatibility rules (lines 40-45) — Enhanced class-level javadoc to clarify:
- Enum values: case-insensitive matching, unknown → default.
- Required vs optional field behavior.
- Updated date format list and epoch-seconds support.

✅ **[link]** Legacy format source reference — Added reference section in README pointing to `history/migrating/entities/*` and `history/migrating/repository/parser/*`.

✅ **[docs]** Example usage — Added comprehensive usage example in README showing:
- How to construct sourceRows Map.
- How to call importOnce().
- How to inspect and interpret the returned report.

✅ **[comment]** `EntityLookupHelper.java` — German class and method Javadocs translated to English (this run).

## Backlog Status
**All issues resolved.** No open items.
