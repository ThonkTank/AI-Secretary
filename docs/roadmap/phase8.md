# Phase 8 - checkArchitecture v2 + Doku-Sync

## Design

Ziel dieser Phase ist, die Zielarchitektur aus der Roadmap dauerhaft im
repo-lokalen `checkArchitecture` zu erzwingen. Produktverhalten soll gleich
bleiben; die notwendigen Entkopplungen betreffen aber Laufzeitverdrahtung.
Bestehende Charakterisierungstests bleiben der Hauptanker, und fehlende
Abdeckung wird gezielt ergaenzt.

1. Import-Matrix als Allowlist
   - `build.gradle.kts` bekommt kleine Klassifizierungshelfer:
     `ArchitectureLayer`, `ArchitectureCell`,
     `architectureCellOf(source)` und `architectureProjectImportTarget(import)`.
   - Klassifizierte Produktionszellen:
     - `app/` -> `app`
     - `database/` -> `database`
     - `shared/` und `shared/ui/` -> `shared`
     - `util/` -> `util`
     - `features/<feature>/ui|application|domain|data/...`
       -> Feature + Schicht.
   - Jede Top-Level-Produktionsdatei unter `src/main/java/com/autosecretary/`
     muss klassifizierbar sein. Nicht klassifizierbare Dateien erzeugen
     `import-matrix-source-classification`.
   - Jeder projektinterne Import `com.autosecretary.*` wird gegen die Matrix
     geprueft. Bestehende spezifische Regeln bleiben als Zusatzschutz erhalten,
     aber die neue Allowlist ist die breite Fehlerklassen-Sperre.

2. Matrix-Regeln
   - Innerhalb eines Features:
     - `ui` darf `ui`, `application`, Domain-Werttypen und `shared`/`util`
       importieren; keine `data`, keine Repositories/DAOs/API-Clients, keine
       Domain-`*Service`/`*Repository`.
     - `application` darf eigene `application`, eigene `domain`, eigene `data`,
       `shared` und `util` importieren.
     - `domain` darf eigene `domain`, `shared` und `util` importieren.
     - `data` darf eigene `data`, eigene `domain`, `shared`, `util` und
       `database` nur fuer Room/DB-Integration importieren.
   - Feature-uebergreifend duerfen nur `application` und `data` fremde
     Feature-Domain importieren. Fremde `application`, `data` und `ui` bleiben
     verboten.
   - `features/*` importiert nie `app/`.
   - `shared/` und `util/` importieren keine Projektpakete ausser
     `shared/ui` -> `shared` fuer UI-Hilfen.
   - `app/` darf alle Projektzellen verdrahten.
   - `database/` darf Domain-/Data-Typen importieren, aber keine UI oder App.
   - Task-Domain-Modelle behalten die Phase-6-Ausnahme fuer Room-/Annotation-
     Imports, aber keine Projekt-`data`-Imports.
   - Bestehende `features/* -> app`-Imports werden in dieser Phase entfernt,
     nicht als Sonderfall erlaubt. Betroffene Dateien:
     `BudgetFragment`, `BudgetWidgetProvider`, `MealPlannerFragment`,
     `TaskScheduleConfigDialog`, `TaskListFragment`, `TaskEditDialog`,
     `TaskWidgetProvider`, `TaskWidgetService` und `DailyPlanningReceiver`.
   - Konkretes Ersatzmuster:
     - Jedes betroffene Feature definiert ein schmales Provider-Interface in
       seiner eigenen Schicht, z.B. `BudgetDependencies`,
       `MealPlannerDependencies`, `TaskUiDependencies`,
       `TaskWidgetDependencies`, `TaskAlarmDependencies`.
     - `AutoSecretaryApplication` implementiert diese Interfaces und delegiert
       an `AppCompositionRoot`.
     - Feature-Code castet `context.getApplicationContext()` nur auf das eigene
       Interface, nie auf `AutoSecretaryApplication` oder `AppCompositionRoot`.
     - Widget-Launch-Intents verwenden
       `PackageManager.getLaunchIntentForPackage(context.getPackageName())`
       mit denselben Extras/Aktionen statt `new Intent(context,
       MainActivity.class)`.
     - `ContentDocumentReader` wird als synchroner, executor-freier Android-
       I/O-Reader nach `shared/ContentDocumentReader` verschoben, damit Budget-
       UI keinen `app/`-Typ importiert.
   - Bestehende UI-Imports auf Domain-Services werden ebenfalls bereinigt:
     `MealPlanDialogController` darf `RecipeScalingService` nicht direkt
     importieren. Konkretes Ersatzmuster: Der Controller bekommt ueber den
     Konstruktor einen `ScalingPreviewProvider`-Callback aus dem Fragment; das
     Fragment delegiert an `MealPlannerViewModel`, und das ViewModel ruft eine
     Application-Methode auf, die `RecipeScalingService` kapselt.
   - Mechanische Domain-Werttyp-Regel fuer UI: erlaubt sind gleiche-Feature-
     Domain-Typen, deren Simple Name nicht auf `Service`, `Repository`,
     `Dao`, `ApiClient`, `Generator`, `Manager` oder `Factory` endet und deren
     Paket nicht `.domain.internal.` enthaelt. Diese Regel erlaubt Records,
     Enums, Entities/POJOs und kleine Value-Objekte, blockiert aber fachliche
     Services aus UI.

3. `unreferenced-class`
   - Der Check sammelt alle Top-Level-Produktionsklassen und ihre voll
     qualifizierten Namen.
   - Eine Klasse gilt als erreichbar, wenn ihr FQN in einer anderen
     Produktionsdatei vorkommt, wenn sie dort importiert wird, oder wenn ihr
     Simple Name in Code vorkommt, nachdem Kommentare und String-/Char-Literale
     fuer diese Pruefung entfernt wurden.
   - Javadocs, Zeilen-/Blockkommentare und reine Strings zaehlen nicht als
     Nutzung.
   - Nested Types werden nicht als Top-Level-Klassen gesammelt; sie haengen an
     der Erreichbarkeit ihrer Top-Level-Datei.
   - Manifest-registrierte Android-Komponenten gelten als erreichbar. Der
     Parser unterstuetzt voll qualifizierte Namen und Manifest-Kurzformen wie
     `.app.MainActivity`, aufgeloest gegen Package `com.autosecretary`.
   - Room-Entities, DAOs, TypeConverter und `AppDatabase` gelten als erreichbar,
     wenn sie ueber Annotationen/Entity-Liste/abstract DAO-Methoden am Room-
     Graph haengen.
   - Enums/Records/Value-DTOs ohne Konstruktion, aber mit Importnutzung, gelten
     ueber Import/Simple-Name als erreichbar.
   - Erlaubte technische Entry-Points werden in einer kleinen
     `architectureReachabilityAllowlist` dokumentiert. Die Allowlist darf nur
     Android-/Room-/Gradle-Entry-Points enthalten, keine normalen Fachklassen.
   - Der Check ist bewusst konservativ: bei Mehrdeutigkeit zaehlt nur echte
     Code-/Import-/Framework-Wiring-Nutzung, keine README- oder Roadmap-Nennung.

4. `docs-match-code`
   - `checkArchitecture` liest die DB-Version aus
     `database/AppDatabase.java` (`version = N`) und aus `CLAUDE.md`
     (`DB version N`).
   - Abweichung erzeugt `docs-match-code-db-version`.
   - Die bestehende `database/README.md`-Version wird ebenfalls geprueft, weil
     sie jetzt als lokale DB-Doku etabliert ist.

5. `Executors.new*`-Standort-Regel
   - Produktionscode wird auf `Executors.new...(` gescannt.
   - Nur `app/AppCompositionRoot.java` darf diese Aufrufe enthalten.
   - Verstoesse erzeugen `executor-owner`.

6. Application-Schicht-Konvention
   - Produktionsdateien in `features/*/application/` duerfen nicht mehr auf
     `*Presenter.java` enden.
   - Imports/Typnamen mit `Presenter` in aktuellen Application-Dateien sind
     verboten. Historische Roadmap-Doku ist davon nicht betroffen.

7. Doku-Sync
   - `CLAUDE.md` wird an die nun erzwungenen Regeln angepasst:
     - Testing-Policy benennt `ViewModel/DataService` statt der alten
       Presenter-Formulierung.
     - Architekturcheck-Beschreibung sagt, dass Matrix, Erreichbarkeit,
       DB-Version-Doku, Executor-Eigentum und Application-Konventionen
       erzwungen werden.
     - DB-Version bleibt mit `AppDatabase` synchron.
     - `ClaudeApiKeyStore`-Beschreibung wird mit der aktuellen
       SharedPreferences-Speicherung synchronisiert.
   - `AGENTS.md` wird aktualisiert, weil die vorhandene Checker-Beschreibung
     nach Phase 8 zu weich waere und die Testregel noch `Presenter` nennt.
   - Aktuelle README/Javadoc/XML-Kommentare werden auf alte
     `Presenter`-Begriffe geprueft; historische Roadmap-/Phase-Berichte duerfen
     alte Namen weiter als historische Ziel-/Baseline-Beschreibung enthalten.

8. Checker-Self-Test/Negativabdeckung
   - `build.gradle.kts` erhaelt eine kleine interne Self-Test-Funktion fuer die
     neuen Regelhelfer, ausgefuehrt durch `checkArchitecture`.
   - Die Self-Tests erzeugen in temporaeren Mini-Quellen gezielt je mindestens
     einen erwarteten Verstoss fuer:
     - unklassifizierbare Quelle/Matrix-Importverstoss,
     - unerreichbare Top-Level-Klasse,
     - DB-Version-Doku-Mismatch,
     - `Executors.new*` ausserhalb `AppCompositionRoot`,
     - Application-`*Presenter`.
   - Ein fehlender erwarteter Verstoss laesst `checkArchitecture` mit
     `architecture-self-test` fehlschlagen. Die Fixtures bleiben lokal im
     Build-Skript und erzeugen keine neuen Produktionsdateien.

9. Verhaltensberuehrte Flaechen und Beweis
   - Obwohl Phase 8 primaer Checker-/Doku-Arbeit ist, beruehrt die
     App-Import-Entkopplung Laufzeitverdrahtung. Bestehende E2E-Tests bleiben
     der Hauptanker; zusaetzlich muessen gezielt kompilierte Pfade unveraendert
     bleiben:
     - Budget-Fragment: ViewModel-Erzeugung, Import-Dateilesen und IO-Executor.
     - Meal-Fragment: ViewModel-Erzeugung und MealPlan-Skalierungsvorschau.
     - Task-List/Task-Edit/Schedule-Dialog: ViewModel-Factory-Zugriff.
     - Widgets: Budget-Summary, Task-Widget-Factory, Task-Toggle und Launch-
       Extras.
     - DailyPlanningReceiver: Schedule-Regeneration und Widget-Refresh.
   - Kein neues Test-Szenario ist erforderlich, solange die bestehenden
     Charakterisierungstests diese Feature-Vertraege weiter treiben und der
     Compile-/Architektur-Gate die neue Provider-Verdrahtung abdeckt. Wenn ein
     Pfad beim Umbau nicht durch bestehende Tests oder Compile-Zwang erfasst
     wird, wird ein enger JVM-Test ergaenzt.

## Vollstaendigkeits-Review

Erster Review: FAIL.

- Die Pflichtsektionen fuer Done-When und Invarianten waren noch leer. Sie
  wurden unten konkretisiert.
- `features/* -> app`-Imports in Widget-Entry-Points waren nicht entschieden.
  Phase 8 refaktoriert diese Stellen und erlaubt keine Matrix-Ausnahme.
- Die UI-Erlaubnis fuer Domain-Werttypen war nicht mechanisch. Sie ist jetzt
  ueber Suffixe und `domain.internal` definiert; bestehender
  `MealPlanDialogController -> RecipeScalingService` wird bereinigt.
- `unreferenced-class` war zu unscharf. Kommentare/Strings/Javadocs zaehlen
  nicht, Imports/Code/Manifest/Room-Wiring zaehlen; Nested Types werden nicht
  separat gesammelt.
- Fuer neue Checker-Regeln fehlte Negativabdeckung. Phase 8 fuegt
  Build-Skript-Self-Tests fuer jede neue Fehlerklasse hinzu.
- Doku-Ziele waren zu ungenau. `CLAUDE.md` und `AGENTS.md` werden zwingend
  synchronisiert; aktuelle README/Javadocs/XML-Kommentare werden auf alte
  Presenter-Begriffe geprueft.

Zweiter Review: FAIL.

- Die harte `features/* -> app`-Regel war nur fuer Widgets entschieden. Das
  Design listet jetzt alle betroffenen Produktionsdateien und das konkrete
  Provider-Interface-Muster.
- Die Ersatzmuster waren zu offen. Sie sind jetzt festgelegt:
  feature-eigene Provider-Interfaces, `AutoSecretaryApplication` als
  Implementierung, Package-Launch-Intent fuer Widgets,
  `shared/ContentDocumentReader` fuer Budget-I/O und
  `ScalingPreviewProvider` + ViewModel/Application fuer die Meal-
  Skalierungsvorschau.
- Die Phase behauptete keine Produktberuehrung, verlangte aber Runtime-
  Verdrahtungsrefactors. Die betroffenen Laufzeitflaechen und der erwartete
  Beweis sind jetzt explizit benannt.

## Done-When-Kriterien

- DW1: `checkArchitecture` klassifiziert jede Produktions-Java-Datei unter
  `src/main/java/com/autosecretary/` und prueft jeden projektinternen Import
  gegen die Roadmap-Import-Matrix.
- DW2: Kein Produktions-Feature importiert `com.autosecretary.app.*`; die
  bisherigen UI-/Widget-/Receiver-Kopplungen sind durch die in diesem Design
  genannten feature-eigenen Provider-Interfaces, Package-Launch-Intents und
  `shared/ContentDocumentReader` ersetzt.
- DW3: UI darf gleiche-Feature-Domain-Werttypen importieren, aber keine
  Domain-Services/-Repositories/-Factories/-Manager oder `domain.internal`-
  Typen. Der bestehende Meal-Plan-Dialog ist ueber `ScalingPreviewProvider`
  plus ViewModel/Application entkoppelt.
- DW4: `unreferenced-class` erkennt unerreichbare Top-Level-
  Produktionsklassen; Manifest- und Room-Entry-Points sind ohne Fachklassen-
  Allowlist beruecksichtigt.
- DW5: `docs-match-code-db-version` vergleicht `AppDatabase` mit `CLAUDE.md`
  und `database/README.md`.
- DW6: `executor-owner` erlaubt `Executors.new*` nur in
  `AppCompositionRoot`.
- DW7: Application-Dateien koennen nicht mehr `*Presenter.java` heissen und
  keine aktuellen Application-`Presenter`-Typen importieren/definieren.
- DW8: `checkArchitecture` enthaelt Self-Tests, die je einen erwarteten
  Verstoss fuer Matrix, Reachability, DB-Doku-Mismatch, Executor-Eigentum und
  Application-Presenter-Konvention nachweisen.
- DW9: `CLAUDE.md`, `AGENTS.md` und betroffene aktuelle README/Javadocs/XML-
  Kommentare beschreiben die neuen Regeln und enthalten keine aktuelle
  Presenter- oder alte Checker-Beschreibung mehr.
- DW10: Keine neuen Gradle-Module, kein DI-Framework, kein Event-Bus, keine
  DB-Schema-Aenderung.
- DW11: Abschluss-Gate ist gruen:
  `./gradlew checkArchitecture`, `./gradlew assembleDebug`,
  `./gradlew testDebugUnitTest`.
- DW12: Die verhaltensberuehrten Verdrahtungspfade aus Abschnitt 9 sind durch
  bestehende Charakterisierungstests, Compile-Zwang oder bei Bedarf neue enge
  JVM-Tests belegt.

## Geschuetzte Invarianten

- Der Architekturcheck blockiert neue Abhaengigkeitsrichtungen, statt nur
  bekannte Einzelfaelle zu finden.
- Framework-Entry-Points bleiben erreichbar und funktionsfaehig, ohne dass
  Feature-Code den App-Root importiert.
- Room-Schema, DB-Version und bestehende Nutzerdaten bleiben unveraendert.
- Task-, Budget- und Meal-Widget-/Dialog-Verhalten bleibt fachlich gleich;
  nur die Verdrahtungsrichtung aendert sich.
- Projekt-Doku beschreibt denselben Architekturvertrag, den
  `checkArchitecture` erzwingt.

## Umsetzung

Umgesetzt:

- `checkArchitecture` klassifiziert Produktionsquellen in App, Database,
  Shared, Util und Feature-Schichten und prueft projektinterne Imports gegen
  die Roadmap-Matrix.
- Neue Checker-Regeln:
  `unreferenced-class`, `docs-match-code-db-version`, `executor-owner`,
  `application-no-presenter` und Self-Tests fuer
  `import-matrix-source-classification`, Matrix-Importverstoss, Reachability,
  DB-Doku-Mismatch, Executor-Eigentum und Application-Presenter-Konvention.
- Feature-Code importiert kein `app/` mehr. Budget-, Meal-, Task-UI,
  Task-Widget und Task-Alarm-Entry-Points nutzen feature-eigene
  Dependencies-Interfaces, die `AutoSecretaryApplication` implementiert.
- `ContentDocumentReader` wurde nach `shared/` verschoben.
- Widget-Launch-Intents nutzen Package-Launch-Intents statt `MainActivity`
  direkt zu importieren.
- `MealPlanDialogController` nutzt `ScalingPreviewProvider`; die Berechnung
  laeuft ueber `MealPlannerViewModel`/`MealPlannerDataService`.
- TaskEdit-Default-PrefSlot-Erzeugung laeuft ueber
  `CreateDefaultTaskPrefSlotUseCase` und ein UI-lokales Callback.
- Die neue Reachability-Regel fand `ShoppingPackagingService` als echten
  Totcode; die Klasse und aktuelle Doku-Verweise wurden entfernt.
- `CLAUDE.md`, `AGENTS.md` und aktuelle README/Javadoc/XML-Kommentare wurden
  auf die neuen Checker-/Test-/Provider-Regeln synchronisiert.
- Der TaskEdit-Charakterisierungstest wurde an die neue ViewModel-Signatur
  angepasst.

## Erfolgs-Review

PASS nach drei Nachbesserungen.

- Erster Review: FAIL wegen altem `Presenter`-XML-Kommentar, stale
  `TaskWidgetService`-Javadoc und fehlendem
  `import-matrix-source-classification`-Self-Test.
- Zweiter Review: FAIL wegen veralteter "no automated tests"-Aussage im
  Alarm-README.
- Dritter Review: FAIL wegen stale Task-Widget-README-Toggle-Flow
  (`Spawn Thread` statt `TaskWidgetDependencies.getDbExecutor()`).
- Finaler Review: PASS; keine offenen DW1-DW12-Blocker.

## Abschluss-Gate

- `./gradlew checkArchitecture --console=plain` -> Exit 0.
- `./gradlew assembleDebug --console=plain` -> Exit 0.
- `./gradlew testDebugUnitTest --console=plain` -> Exit 0.
