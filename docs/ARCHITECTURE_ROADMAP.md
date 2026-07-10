# Architektur-Roadmap AutoSecretary

Status: **in Umsetzung** · Branch: `refactor/architecture-roadmap`

Diese Roadmap führt die Codebasis auf die unten definierte Zielarchitektur. Sie
entstand aus einer Vier-Wege-Architekturanalyse (task, budget, meal, Querschnitt).
Leitidee: **Fehlerklassen strukturell verhindern statt Einzelfälle ad-hoc beheben.**
Kein DI-Framework, keine neuen Gradle-Module, kein Event-Bus — es ist dieselbe
Architektur, die das Projekt zu haben *behauptet*, nur vollständig definiert und
vollständig erzwungen.

---

## Zielarchitektur — fünf Prinzipien

### P1 — Vollständige Import-Matrix (Allowlist statt Blocklist)
Jede Produktionsdatei wird über ihren Pfad einer Zelle `(Feature, Schicht)`
zugeordnet. Jeder projektinterne Import muss von der Matrix erlaubt sein; eine
nicht klassifizierbare Datei ist selbst ein Verstoß.

Innerhalb eines Features:

| von \ nach      | `ui` | `application` | `domain`     | `data` | `shared`/`util` |
|-----------------|------|---------------|--------------|--------|------------------|
| `ui`            | ✅   | ✅            | ✅ nur Typen¹| ❌     | ✅               |
| `application`   | ❌   | ✅            | ✅           | ✅     | ✅               |
| `domain`        | ❌   | ❌            | ✅           | ❌     | ✅               |
| `data`          | ❌   | ❌            | ✅           | ✅     | ✅               |

¹ UI darf Domain-*Werttypen* binden, aber keine `*Service`/`*Repository`-Typen.

Feature-übergreifend: nur `application` und `data`-Adapter dürfen fremde Pakete
importieren, und ausschließlich `features.<X>.domain.*` — nie fremdes
`application`, `data` oder `ui`. Global: `features/*` importiert nie `app/`;
`shared/`+`util/` importieren nichts Projektinternes; `app/` darf alles.

### P2 — Ein Muster für Feature-Kopplung: Consumer-Port + Adapter
1. Der Konsument definiert ein schmales Interface in seiner eigenen Domain
   (Vorlage: `TaskBudgetEligibilityService`).
2. Ein Adapter in `<konsument>/application/internal/<provider>/` implementiert es
   gegen das *Domain*-Interface des Providers.
3. Gemeinsame Werttypen (Enums) leben in `shared/`.
4. Richtung folgt dem Bedarf, nie beidseitig konkret.

### P3 — Das Domänenmodell gehört der Domain
`data/` enthält nur Persistenz-*Mechanik* (DAOs, `*RoomRepository`, Projektionen,
Entities bei Zwei-Modell-Features). `domain/` besitzt das fachliche Modell.
- **task** (Ein-Modell): `Task`/`TaskCore`/`TaskSlot`/… wandern nach
  `domain/model/` (reine Paketverschiebung; Room-Annotationen ziehen mit;
  Tabellennamen explizit → keine Schema-Änderung, keine Migration).
- **budget/meal** (Zwei-Modell): bleiben Entity + Mapper. Defaults/Konstanten
  wohnen im Domain-Record; Entities referenzieren sie.

### P4 — Benannte Eigentümer für Querschnittsressourcen
- **Threading:** `dbExecutor` (single-thread, Room-Serialisierung) und
  `ioExecutor` (Netz + Dateisystem). I/O-Klassen sind synchron/executor-frei.
  `Executors.new*` nur in `AppCompositionRoot`.
- **DB-Lifecycle:** nur `AppCompositionRoot` ruft `AppDatabase.getInstance()`/
  `closeAndReset()`.
- **Cross-Feature-Verträge** wohnen in `shared/`, nicht in `app/`.

### P5 — Erreichbarkeit & Doku als geprüfte Invarianten
- `unreferenced-class`: jede Top-Level-Produktionsklasse muss referenziert sein
  (Allowlist: Manifest-registrierte Klassen).
- `docs-match-code`: DB-Version in CLAUDE.md == `version = N` in `AppDatabase`.
- Application-Schicht-Konvention: fokussierte `*UseCase` + `*DataService`.
  „Presenter" verschwindet aus der Application-Schicht.

---

## Phasen

Jede Phase lässt den Build grün (`./gradlew checkArchitecture assembleDebug`).
Reihenfolge = Umsetzungsreihenfolge (risikoarm → strukturell).

Legende Status: ⬜ offen · 🔨 in Arbeit · ✅ erledigt

| Phase | Titel | Status |
|-------|-------|--------|
| 0 | Baseline & Roadmap | ✅ |
| 1 | Toten Code entfernen | ⬜ |
| 2 | Querschnitt-Typen nach `shared/` verschieben | ⬜ |
| 3 | Schicht-Entkopplung (application↛ui, meal↔task Port) | ⬜ |
| 4 | Threading & DB-Lifecycle-Eigentum | ⬜ |
| 5 | Task-Domänenmodell nach `domain/model/` | ⬜ |
| 6 | Application-Schicht normalisieren (Presenter-Split) | ⬜ |
| 7 | `checkArchitecture` v2 + Doku-Sync | ⬜ |

### Phase 0 — Baseline & Roadmap ✅
Roadmap angelegt, Arbeitsbranch erstellt, Baseline `checkArchitecture` grün,
`assembleDebug` als Referenz gebaut.

### Phase 1 — Toten Code entfernen
Ziel: ~1.200 Zeilen nicht erreichbarer Code raus, damit keine still divergierende
Logik (v.a. duplizierte Completion-Logik) unbemerkt weiterlebt.
- `meal/application/MealTaskBridgeService.java` (nicht verdrahtet, dupliziert
  Completion-Logik ohne Streak/History/Adaptive)
- `meal/domain/MealPlanGenerator.java` (nie aufgerufen)
- `meal/application/internal/LegacyMealImportService.java` (+ README, einmaliges
  Migrationstool, nicht verdrahtet)
- tote Methoden auf `BudgetImportRepository` (`existsTransactionByImportHash`,
  `isKnownCategory`) + Implementierungen
- Javadoc-Referenzen auf die gelöschten Klassen bereinigen.

### Phase 2 — Querschnitt-Typen nach `shared/` verschieben
Ziel: Cross-Feature-Werttypen und -Verträge gehören in `shared/`, nicht in
fremde Feature-`domain/` oder in `app/`.
- `meal/domain/MealType` → `shared/` (bricht `TaskCore`→`meal.domain`-Kopplung)
- `app/WidgetRefreshNotifier` → `shared/` (bricht `features→app`-Rückabhängigkeit)
- Budget-Default-Icon/-Farbe: Konstante in Domain-Record, Entity/UI referenzieren.

### Phase 3 — Schicht-Entkopplung
Ziel: Abhängigkeitsrichtung UI→application→domain→data überall einhalten.
- `LoadBudgetOverviewUseCase` von `ui.state`/`ui.internal` lösen (Domain-Rückgabe,
  Mapping im ViewModel — oder State-Typen+Mapper nach `application`).
- meal↔task-Integration auf Consumer-Port (P2) umstellen: `TaskMealIntegrationService`
  nicht mehr direkt auf `task.data.*`.
- `MealPlannerFragment` ruft `HouseholdEnergyService` nicht mehr direkt → über
  ViewModel/Presenter.
- `BudgetViewModel`/`TaskEditViewModel`: Direkt-Repository-Zugriffe hinter
  schmale Application-Aufrufe.
- Unlock-Receiver (`AutoSecretaryApplication`) über `WidgetRefreshNotifier`.

### Phase 4 — Threading & DB-Lifecycle-Eigentum
Ziel: kein Blockieren des DB-Threads durch Netz-I/O; ein Eigentümer für DB-Lifecycle.
- `AppCompositionRoot`: `dbExecutor` + `ioExecutor`; Claude-API/`UpdateChecker`/
  Backup auf `ioExecutor`.
- DB-`getInstance()`/`closeAndReset()` nur noch in `AppCompositionRoot`;
  `SettingsDataService`/`MainActivity` nur triggern.

### Phase 5 — Task-Domänenmodell nach `domain/model/`
Ziel: `domain` importiert nie mehr `data`; same-feature-`data`-Ausnahme im Check
kann entfallen.
- `Task`, `TaskCore`, `TaskSlot`, `TaskPrefSlot`, `TaskPrefSlotFactory`,
  `TaskPrerequisite`, `TaskRelation`, `TaskPlannedMeal` → `domain/model/` (reine
  Paketverschiebung, Room-Annotationen bleiben). DAOs/Config/Transition bleiben
  in `data/`.

### Phase 6 — Application-Schicht normalisieren
Ziel: konsistentes UseCase/DataService-Muster; „Presenter" raus.
- `MealPlannerPresenter` entlang seiner Abschnittskommentare in fokussierte
  UseCases/DataService aufteilen.
- `TaskEditPresenter` → `TaskEditFormController` (o.ä.) umbenennen.

### Phase 7 — `checkArchitecture` v2 + Doku-Sync
Ziel: die fünf Prinzipien werden erzwungen, damit keine Fehlerklasse zurückkehrt.
- Import-Matrix (P1) als Allowlist-Regel implementieren.
- `unreferenced-class`-Regel (P5).
- `docs-match-code`-Regel (DB-Version).
- `Executors.new*`-Standort-Regel (P4).
- CLAUDE.md aktualisieren (DB-Version 24→aktuell, ApiKeyStore-Beschreibung,
  Verweis auf diese Roadmap/Matrix).

---

## Änderungslog
- Phase 0 abgeschlossen: Roadmap + Branch + Baseline.
