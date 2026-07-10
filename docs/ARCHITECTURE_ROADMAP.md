# Architektur-Roadmap AutoSecretary

Status: **in Umsetzung** · Branch: `refactor/architecture-roadmap`

Diese Roadmap führt die Codebasis auf die unten definierte Zielarchitektur. Sie
entstand aus einer Vier-Wege-Architekturanalyse (task, budget, meal, Querschnitt).
Leitidee: **Fehlerklassen strukturell verhindern statt Einzelfälle ad-hoc beheben.**
Kein DI-Framework, keine neuen Gradle-Module, kein Event-Bus — es ist dieselbe
Architektur, die das Projekt zu haben *behauptet*, nur vollständig definiert und
vollständig erzwungen.

**Tests:** Es gilt die Testregel aus `CLAUDE.md` (dokumentierte, eng
End-to-End-abgesicherte Verhaltensinvarianten). Für dieses Vorhaben zusätzlich:
verhaltensberührte Bereiche bekommen neue Tests, die das heutige Verhalten *vor*
dem Umbau einfangen und danach unverändert grün bleiben (Verhaltensparität).

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

## Arbeitsweise (verbindlich)

Diese Sektion ist die Prozess-Referenz. Der Goal-Prompt bleibt statisch und
verweist nur hierauf; **der aktuelle Fortschritt lebt ausschließlich in diesem
Dokument** (Statustabelle + Änderungslog + `docs/roadmap/phaseN.md`), damit nach
einer Kontext-Kompaktierung oder in einem neuen Chat kein Fortschritt verloren geht.

**Pro Phase, in dieser Reihenfolge:**
1. **Design.** Konkretes Implementationsdesign, wo die Roadmap Fragen offen lässt →
   nach `docs/roadmap/phaseN.md` schreiben.
2. **Vollständigkeits-Review.** Einen Subagenten (general-purpose) die
   Entscheidungsvollständigkeit prüfen lassen (Folge-Effekte, übersehene
   Referenzen, Risiken). Ergebnis in `docs/roadmap/phaseN.md` festhalten.
3. **Done-When-Kriterien.** Konkret später überprüfbare Kriterien + die durch diese
   Phase geschützten Verhaltensinvarianten benennen → `docs/roadmap/phaseN.md`.
4. **Umsetzen.** Inklusive Tests gemäß Testregel (verhaltensberührende Phasen:
   Charakterisierungstests zuerst).
5. **Erfolgs-Review.** Einen Subagenten gegen die Done-When-Kriterien reviewen lassen.
6. **Abschluss-Gate (blockierend).** Erst wenn **alle** grün sind, gilt die Phase
   als erledigt:
   - `./gradlew checkArchitecture` (Exit 0)
   - `./gradlew assembleDebug` (Exit 0)
   - `./gradlew testDebugUnitTest` (Exit 0) — **ein fehlerhafter Testlauf blockiert
     den Abschluss.** (Vor Phase 2 existiert die Suite noch nicht; das Gate ist
     dann ein No-op-Pass. Ab Phase 2 ist es hart.)
7. **Fortschritt festschreiben.** Statuszelle in der Tabelle auf ✅, Änderungslog-
   Zeile ergänzen, `docs/roadmap/phaseN.md` mit Erfolgs-Review abschließen. Phase
   lokal committen (Konvention aus `CLAUDE.md`, Co-Authored-By-Trailer; **kein Push**).
8. Nächste Phase lesen, wiederholen — bis alle Phasen ✅ sind.

**Gesamtabschluss** ist ebenfalls durch das Abschluss-Gate blockiert: das Vorhaben
gilt erst als fertig, wenn alle Phasen ✅ sind *und* der vollständige Testlauf grün ist.

## Phasen

Jede Phase lässt den Build grün (`./gradlew checkArchitecture assembleDebug`) und
ab Phase 2 auch die Testsuite (`./gradlew testDebugUnitTest`) — siehe Abschluss-Gate.
Reihenfolge = Umsetzungsreihenfolge (risikoarm → strukturell).

**Prozessregel Verhaltensparität:** Verhaltensberührende Phasen (4, 5, 6, 7)
*erweitern zuerst* das Charakterisierungs-Testset um die konkret betroffenen
Verhaltensweisen (Ist-Zustand einfangen), *dann* wird umgebaut; dieselben Tests
müssen danach unverändert grün sein. Reine Struktur-/Verschiebe-Phasen (1, 3)
fügen keine neuen Tests hinzu, dürfen die bestehende Suite aber nicht brechen.

Legende Status: ⬜ offen · 🔨 in Arbeit · ✅ erledigt

| Phase | Titel | Status |
|-------|-------|--------|
| 0 | Baseline & Roadmap | ✅ |
| 1 | Toten Code entfernen | ✅ |
| 2 | Test-Infrastruktur & Verhaltens-Baseline | ✅ |
| 3 | Querschnitt-Typen nach `shared/` verschieben | ✅ |
| 4 | Schicht-Entkopplung (application↛ui, meal↔task Port) | ⬜ |
| 5 | Threading & DB-Lifecycle-Eigentum | ⬜ |
| 6 | Task-Domänenmodell nach `domain/model/` | ⬜ |
| 7 | Application-Schicht normalisieren (Presenter-Split) | ⬜ |
| 8 | `checkArchitecture` v2 + Doku-Sync | ⬜ |

### Phase 0 — Baseline & Roadmap ✅
Roadmap angelegt, Arbeitsbranch erstellt, Baseline `checkArchitecture` grün,
`assembleDebug` als Referenz gebaut.

### Phase 1 — Toten Code entfernen
> Design + Vollständigkeits-Review bereits erledigt → siehe `docs/roadmap/phase1.md`
> (erweiterter Löschsatz inkl. Folge-Totcode). Umsetzung startet direkt bei Schritt 4.

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

### Phase 2 — Test-Infrastruktur & Verhaltens-Baseline
Ziel: das Migrations-Sicherheitsnetz aufbauen, bevor Verhalten umgezogen wird.
- Test-Sourceset + Abhängigkeiten einrichten. Empfehlung (im Phasen-Design zu
  bestätigen): JVM-Tests unter `src/test` mit **Robolectric + in-memory Room**
  (`AppDatabase` via `Room.inMemoryDatabaseBuilder`), die durch die Schichten
  UI-ViewModel/Presenter → application → domain → data treiben und beobachtbare
  Ergebnisse prüfen (DB-Zustand, zurückgegebene View-States). Echte Espresso-UI-
  Tests bleiben optional/gerätegebunden und sind nicht Teil des Netzes.
- Test-Governance ist bereits gesetzt (CLAUDE.md-Testregel + Entfernen der
  `repository-no-automated-tests`-Regel, erledigt vor Phase 1). Hier nur noch das
  Test-Sourceset + Abhängigkeiten (JUnit/Robolectric) aufsetzen.
- **Charakterisierungs-Baseline** für genau die später berührten Verhaltensweisen
  schreiben, jeweils gegen den *heutigen* Code grün:
  - Budget-Übersicht laden (`LoadBudgetOverviewUseCase` → View-State) — für Phase 4.
  - Task↔Meal-Completion-Integration (`CheckOffTaskUseCase` →
    `TaskMealIntegrationService`, Meal-Consumption gebucht) — für Phase 4.
  - Two-Phase-Checkoff inkl. Streak/History/Adaptive (`TaskCompletionService` end-
    to-end über Room) — Kernverhalten, indirekt von Phase 5/6 berührt.
  - Task-Scheduling-Roundtrip: Slots generieren + über Room persistieren/lesen —
    für Phase 6 (Modell-Paketverschiebung, Room-Schema muss identisch bleiben).
  - Meal-Planner-Operationen (Home-Aggregation + eine CRUD-Kette) — für Phase 7.
- Done-When: Suite läuft via `./gradlew testDebugUnitTest` grün; jeder oben
  genannte Bereich hat mindestens einen aussagekräftigen E2E-Test; Architektur-
  regel für Tests ist entfernt; `checkArchitecture assembleDebug` weiterhin grün.

### Phase 3 — Querschnitt-Typen nach `shared/` verschieben
Ziel: Cross-Feature-Werttypen und -Verträge gehören in `shared/`, nicht in
fremde Feature-`domain/` oder in `app/`.
- `meal/domain/MealType` → `shared/` (bricht `TaskCore`→`meal.domain`-Kopplung)
- `app/WidgetRefreshNotifier` → `shared/` (bricht `features→app`-Rückabhängigkeit)
- Budget-Default-Icon/-Farbe: Konstante in Domain-Record, Entity/UI referenzieren.
- Reine Verschiebung: bestehende Suite bleibt grün, keine neuen Tests nötig.

### Phase 4 — Schicht-Entkopplung
Ziel: Abhängigkeitsrichtung UI→application→domain→data überall einhalten.
Verhaltensparität via Charakterisierungstests aus Phase 2 (Budget-Übersicht,
Task↔Meal-Completion) — vor Umbau ggf. um Randfälle erweitern, danach unverändert grün.
- `LoadBudgetOverviewUseCase` von `ui.state`/`ui.internal` lösen (Domain-Rückgabe,
  Mapping im ViewModel — oder State-Typen+Mapper nach `application`).
- meal↔task-Integration auf Consumer-Port (P2) umstellen: `TaskMealIntegrationService`
  nicht mehr direkt auf `task.data.*`.
- `MealPlannerFragment` ruft `HouseholdEnergyService` nicht mehr direkt → über
  ViewModel/Presenter.
- `BudgetViewModel`/`TaskEditViewModel`: Direkt-Repository-Zugriffe hinter
  schmale Application-Aufrufe.
- Unlock-Receiver (`AutoSecretaryApplication`) über `WidgetRefreshNotifier`.

### Phase 5 — Threading & DB-Lifecycle-Eigentum
Ziel: kein Blockieren des DB-Threads durch Netz-I/O; ein Eigentümer für DB-Lifecycle.
- `AppCompositionRoot`: `dbExecutor` + `ioExecutor`; Claude-API/`UpdateChecker`/
  Backup auf `ioExecutor`.
- DB-`getInstance()`/`closeAndReset()` nur noch in `AppCompositionRoot`;
  `SettingsDataService`/`MainActivity` nur triggern.
- Verhaltensparität: Charakterisierungstests (Two-Phase-Checkoff, Restore/Reset-
  Pfad) müssen grün bleiben; ggf. Test für Restore→Reload-Sequenz ergänzen.

### Phase 6 — Task-Domänenmodell nach `domain/model/`
Ziel: `domain` importiert nie mehr `data`; same-feature-`data`-Ausnahme im Check
kann entfallen.
- `Task`, `TaskCore`, `TaskSlot`, `TaskPrefSlot`, `TaskPrefSlotFactory`,
  `TaskPrerequisite`, `TaskRelation`, `TaskPlannedMeal` → `domain/model/` (reine
  Paketverschiebung, Room-Annotationen bleiben). DAOs/Config/Transition bleiben
  in `data/`.
- Verhaltensparität: Scheduling-Roundtrip- und Checkoff-Tests aus Phase 2 müssen
  identisch grün bleiben — sie beweisen, dass das Room-Schema unverändert ist.

### Phase 7 — Application-Schicht normalisieren
Ziel: konsistentes UseCase/DataService-Muster; „Presenter" raus.
- `MealPlannerPresenter` entlang seiner Abschnittskommentare in fokussierte
  UseCases/DataService aufteilen.
- `TaskEditPresenter` → `TaskEditFormController` (o.ä.) umbenennen.
- Verhaltensparität: Meal-Planner-Charakterisierungstests aus Phase 2 vor dem
  Split ggf. erweitern, danach unverändert grün.

### Phase 8 — `checkArchitecture` v2 + Doku-Sync
Ziel: die fünf Prinzipien werden erzwungen, damit keine Fehlerklasse zurückkehrt.
- Import-Matrix (P1) als Allowlist-Regel implementieren (Test-Sourceset von der
  Matrix ausnehmen bzw. lockerer behandeln).
- `unreferenced-class`-Regel (P5).
- `docs-match-code`-Regel (DB-Version).
- `Executors.new*`-Standort-Regel (P4).
- CLAUDE.md final abgleichen (DB-Version 24→aktuell, ApiKeyStore-Beschreibung,
  Verweis auf diese Roadmap/Matrix; Testregel wurde bereits in Phase 2 gesetzt).

---

## Änderungslog
- Phase 3 abgeschlossen: `MealType` und `WidgetRefreshNotifier` nach `shared/`
  verschoben, Budget-Default-Icon/-Farbe dem Domain-Record `BudgetCategory`
  zugeordnet, betroffene Imports und Doku aktualisiert; Erfolgs-Review ohne
  blockierende Befunde nach Gate; Abschluss-Gate gruen.
- Phase 2 abgeschlossen: `AGENTS.md` auf die neue CLAUDE/Roadmap-Testregel
  synchronisiert, JVM-Testsetup mit Robolectric + in-memory Room eingerichtet,
  fuenf Charakterisierungstests fuer Budget-Overview, Task↔Meal-Completion,
  Task-Completion-Kern, Scheduling-Roundtrip und Meal-Planner-CRUD angelegt;
  Erfolgs-Review ohne blockierende Befunde; Abschluss-Gate gruen.
- Phase 1 abgeschlossen: unerreichbare Meal-Legacy-/Generator-/Bridge-Pfade,
  zugehöriger Folge-Totcode und Dangling-Doku entfernt; Erfolgs-Review ohne
  blockierende Befunde; Abschluss-Gate grün (`checkArchitecture`,
  `assembleDebug`, `testDebugUnitTest` als NO-SOURCE-Pass vor Phase 2).
- Phase 0 abgeschlossen: Roadmap + Branch + Baseline.
- Roadmap erweitert: „keine Tests"-Regel generell durch dauerhafte Testregel
  ersetzt (dokumentierte, eng abgesicherte Verhaltensinvarianten); neue Phase 2
  (Test-Infrastruktur & Verhaltens-Baseline); Folgephasen neu nummeriert
  (alt 2–7 → neu 3–8); verbindliche Arbeitsweise-Sektion mit blockierendem
  Abschluss-Gate (roter `testDebugUnitTest`-Lauf blockiert Phasen-/Gesamtabschluss)
  und Fortschritts-Persistenz in diesem Dokument + `docs/roadmap/phaseN.md`.
- Test-Governance angewandt: `CLAUDE.md`-Testregel gesetzt und
  `repository-no-automated-tests` aus `checkArchitecture` entfernt
  (`checkArchitecture` weiterhin grün). Phase 2 muss diese Regeländerung nicht
  mehr vornehmen, nur noch das Test-Sourceset aufsetzen.
