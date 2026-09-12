# Preserving schema-24 orphan flow snapshots

Release 0.2.168 failed before the 24→25 graph migration on a Pixel 8 (API 36).
The matching R8 map locates the exception at the initial foreign-key check;
`flow_run_steps.runId` referenced an absent `step_flow_runs.id`. The origin and
number of those rows were not available from the production crash log.

The upgrade now archives snapshots with missing run headers in both
`flow_run_steps` and `flow_run_resources`. All rows from both tables are copied
before any original is removed. The enclosing upgrade transaction rolls back
both copies and removals on any failure. The existing global foreign-key checks
remain unchanged, so unrelated corruption still fails explicitly. No run,
execution, reward, or progress is invented.

`migration_recovery` has no foreign keys and survives deletion of live tasks.
Its composite identity is `(sourceSchema, sourceTable, sourceId)`. Schema 24 means
the schema boundary where the row was captured, not necessarily the schema at
which the user began upgrading. Inserts abort on duplicate archive identities.
Schema 26→27 creates the same table for healthy existing installations.

Payload format 1 stores an ordered `columns` array of `[name, SQLite type, value]`
tuples. Values retain NULL, text, signed 64-bit integers (decimal strings),
doubles (hexadecimal strings), and blobs (base64). No positional table copy is
used. The archive is a forensic source for later restoration; it is not an
executable queue and does not infer the missing parent.

After the upgrade has committed and Room has validated it, the database-open
callback logs a `MigrationRecovery` report with source schema, table and archived
row count. It includes no task names or payloads. Counts can also be read with:

```sql
SELECT sourceSchema, sourceTable, COUNT(*)
FROM migration_recovery GROUP BY sourceSchema, sourceTable;
```

Regression coverage includes mixed healthy runs with earned/paid history,
full typed payload comparison, archive-write failure rollback, unrelated FK
failure rollback, healthy 25/26 upgrades, and a native instrumentation migration
fixture. Existing historical signed-upgrade fixtures now target schema 27.

## Retained-device follow-up

The signed 0.2.169 update reached a second pre-cutover violation on the retained
Pixel database: `occurrence_steps.occurrenceId` referenced an absent occurrence.
The entire upgrade rolled back, including the initial snapshot archive. Thus
0.2.169 was published and installed, but did not restore this device's startup.

Recovery now captures the occurrence dependency closure: orphan occurrence
steps, their repetitions and timers, bookings/assignments and obligations tied
to missing occurrences, and descendants that a cascade would otherwise remove.
All identities are frozen in a temporary row set, every payload is archived,
and children are explicitly removed before parents, with foreign keys either
on or off. Unrelated violations still fail the transaction. Repetition-result
archive identities use a JSON tuple `[stepId, slotIndex]`; their original key
columns also remain in the typed payload. Valid ledgers and stored balances
are not rewritten. No additional schema change is needed beyond schema 27.

The migration logs all pre-existing FK table counts and reports all remaining
violations together instead of exposing one table per failed device upgrade.
The committed recovery report remains separate from this pre-upgrade diagnosis.

The supported read-only entry is now the dedicated `DiagnosticProbeInstrumentation`,
activated before Application/provider creation through bootstrap contract 1. Use
`scripts/release/diagnostic_probe.py` with the matching signed helper. APKs without
that contract, including releases through 0.2.173, are explicitly unsupported;
`upgradePhase=diagnose` on the historical runner is no longer supported. The protected
scope is business schema/data, while Android/WorkManager bookkeeping still initializes.
See [ADR-038](../architecture/adr-038-frueher-diagnose-bootstrap.md) for the exact lifecycle,
compatibility and validation boundary. Never use `seed` on a retained user database.

## Runtime allocation follow-up

On the retained Pixel, 0.2.170 committed schema 27 with no FK violations and
archived 8 flow steps, 4 flow resources and 2 occurrence steps. Startup no longer
crashed, but loading the dashboard failed while creating a flow occurrence step.
The parent occurrence insert used IGNORE on its unique source key; a lagging
execution counter reused an existing key, so the new parent ID was absent.
A full Room runtime regression reproduces the same FK exception before the fix.

Sequence allocation now stays ahead of both the stored counter and retained
occurrence sequences and checks the actual unique source key. It advances only
the counter in the same transaction, preserving historical occurrences, steps
and rewards. This works for already-migrated schema-27 databases without another
schema migration.

Historical device observation: the old diagnostic phase succeeded when invoked
directly, while an explicit prior force-stop could expose a pending boot broadcast
against the suppressed application initialization. Component state changes were
denied by Android and no override was made. That observation did not prove provider
or receiver safety; the new early contract and native event tests address that gap.
