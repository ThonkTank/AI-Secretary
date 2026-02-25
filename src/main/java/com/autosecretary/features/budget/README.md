# Budget feature overview

## Start here

The budget feature is organized by responsibility and then by sub-feature:

- `ui/`: fragment/view-model and budget screen presentation.
- `application/`: use-cases coordinating workflows.
  - `application/importing/`: end-to-end import flow (parse, deduplicate, persist, recurring suggestions).
- `domain/`: business logic and recurring detection algorithms.
- `data/`: Room entities and DAOs for canonical budget persistence.
  - `data/importing/`: import metadata entities.
  - `data/legacy/`: transitional pre-v8 classes kept for migration reference only.

## Placement rule for new code

- Add new workflow orchestration to `application/`.
- Add import-related workflows and adapters to `application/importing/`.
- Add persisted canonical models to `data/`.
- Only place files in `data/legacy/` when they are intentionally deprecated/compatibility-only.

## Rollover-Regeln für Budgetlimits

Rollover wird pro `BudgetLimit` für das Zielmonat konfiguriert.

- **Delta-Formel:** `delta = limit(vormonat) - ausgaben(vormonat)`.
- **Effektives Limit:** `effectiveLimit(zielmonat) = baseLimit(zielmonat) + delta + rolloverCarryover`.
- **Optional Caps:** Das Delta kann über `rolloverCapPositiveCents` und `rolloverCapNegativeCents` begrenzt werden.
- **Kein Vormonatslimit:** Delta wird als `0` behandelt.
- **Archivierte Kategorien:** Werden in der Übersicht nicht gerendert (`archived = 0`), bleiben aber historisch auswertbar.
- **Negativer Carryover > Basislimit:** Effektives Limit wird auf `0` gedeckelt.
