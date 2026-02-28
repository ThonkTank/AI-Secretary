# Review Backlog — meal/data/internal/mapper

## Open Issues

### [consider] No `asString` method in MapperSupport
**Files:** All `*RowMapper.java` `fromRow()` methods

All non-String types use safe conversion methods in MapperSupport. String fields use raw `(String)`
casts with no safety net. Low practical risk since the data layer always reads what it wrote,
but inconsistent with the established safe-conversion pattern. Each mapper file already carries
a comment acknowledging this.

**Canonical recommendation:** Defer — the raw cast is safe in the current architecture. If the
storage layer changes (e.g., to a database), add a `MapperSupport.asString(Object)` method.
