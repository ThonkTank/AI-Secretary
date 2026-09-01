# Ausführung: minimaler Trainingsassistent

Kanonische Grundlage:
[Roadmap: minimaler Trainingsassistent ohne Übergangsarchitektur](training-assistant-minimal-roadmap.md)

Dieses kompakte Protokoll enthält ausschließlich den aktuellen Phasenstatus, den vor der
Implementierung festgehaltenen Plan, die Validierung und festgestellte Abweichungen. Historische
Nachweise bleiben im Archiv; Commit-, Pull-Request-, Workflow- und Artefaktdetails bleiben in Git
und GitHub.

## Phase 0 – Vertrag, Archiv und automatisierter Abschluss

Status: implementiert

### Plan

Ergebnis: Die Minimal-Roadmap und ADR-030 sind die einzigen aktiven normativen
Trainings-Architekturverträge. Historische Trainingsverträge und Protokolle sind unverändert
archiviert. Release- und Testverträge schließen produktwirksame Phasen ohne nachgelagerten
Folgezustand automatisiert ab.

Betroffene Grenzen:

- aktive Architekturindizes, Release-Dokumentation, Frontend-Roadmap und Teststrategie;
- bisherige Trainings-Roadmap, ihr Ausführungsprotokoll und ADR-027 bis ADR-029;
- historische Frontend-Ausführungsnachweise mit dem früheren Abschlussvertrag;
- der obsolete lokale Freigabe-Runner, dessen Vertragsprüfung und Release-Scope-Verträge.

Reihenfolge:

1. ADR-030 und die aktiven Links auf Minimal-Roadmap, ADR und dieses Protokoll anlegen.
2. Die unveränderten historischen Trainingsdokumente und historischen Gate-Protokolle unter
   `docs/archive/` verschieben; für laufende Architekturarbeit nur kompakte aktuelle Nachfolger
   behalten.
3. Aktive normative Texte auf automatisierten Abschluss umstellen und ältere aktive ADRs mit
   veralteten Gates auf ADR-030 verweisen lassen.
4. Den obsoleten Freigabe-Runner und seine Vertragsprüfung entfernen; den absichtlich
   publish-freien Dokumentationsscope der Phase 0 vertraglich absichern.
5. Diff-, CI-/Release-Vertrags-, fokussierte Gradle- und negative Repository-Prüfungen ausführen.
6. Implementierung getrennt gegen diesen Plan, die Phase-0-Entfernungslisten und die gesamte
   Roadmap auditieren. Vor jeder Korrekturrunde zuerst einen Fixplan ergänzen.

Abnahme: Alle Phase-0-Kriterien der Roadmap sind lokal belegt; Schema, Produktcode und sichtbare
Ressourcen bleiben unverändert. Der Remote-Abschluss erfolgt anschließend ausschließlich über
den vorgeschriebenen Pull-Request-, Squash-Merge- und Main-Workflow-Gate.

### Validierung

- `git diff --check`: grün.
- `python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v`: 16 Tests grün.
- `python3 -m unittest discover -s scripts/release -p 'test_*.py' -v`: 23 Tests grün.
- Tatsächlicher Phase-0-Diff über `scripts/ci/change_scope.py`: Quality und Instrumentierung
  erforderlich, Produktrelease ausdrücklich nicht erforderlich.
- `./gradlew testInstrumentationUnitTest --tests '*ArchitectureBoundaryTest' --tests
  '*CompletionArchitectureTest' --console=plain --no-daemon`: mit JDK 21 und dem vorhandenen
  Android-SDK grün; 35 Tasks ausgeführt.
- Der erste Gradle-Start in der eingeschränkten Sandbox konnte keine nutzbare Wildcard-IP
  bestimmen; der erste externe Wiederanlauf kannte den SDK-Pfad nicht. Der unveränderte Testlauf
  bestand nach expliziter JDK-21- und SDK-Auswahl. Beide Befunde waren Umgebungs-, keine
  Produktabweichungen.

### Audit und Abweichungen

- Planabgleich: ADR-030, aktive Links, kompakte Nachfolger, Vertragsumstellung,
  Runner-Entfernung und Release-Scope-Vertrag sind vollständig umgesetzt.
- Entfernungslisten: Der Negativscan findet außerhalb von Minimal-Roadmap, ADR-030 und Archiv
  keinen verbotenen Runner, keinen nachgelagerten Freigabeschritt und keinen offenen
  Folgezustand. Aktive Dokumentation referenziert keine alte Trainings-Roadmap, kein altes
  Trainingsprotokoll und keine ADR-027 bis ADR-029.
- Archiv: Die fünf historischen Trainingsdateien und das historische Frontend-Protokoll sind
  gegenüber `origin/main` jeweils byteidentisch. Historische Gate-Nachweise liegen nur dort.
- Regressionsgrenzen: Der Diff verändert weder Produktquellen noch Schema-, Migrations-, Golden-,
  Ressourcen- oder Upgradefixture-Dateien.
- Roadmapabgleich: Alle lokal belegbaren Phase-0-Kriterien sind erfüllt. Pull Request,
  Squash-Merge und Main-Workflow fehlen noch; deshalb wird Phase 0 nicht als `implementiert`
  geschlossen und Phase 1 nicht begonnen.
- Es wurde keine inhaltliche Diskrepanz gefunden; eine Fixrunde war nicht erforderlich.

### Korrekturrunde 1 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge und grünem Main-Workflow wird ausschließlich der
Phase-0-Status von der während der Implementierung zutreffenden Arbeitsmarkierung auf
`implementiert` gesetzt. Der frühere Auditbefund bleibt unverändert als damaliger Stand erhalten.
Die Korrektur verändert weder Roadmap noch Produkt-, Schema-, Ressourcen- oder Testcode und wird
erneut über einen Themenbranch, Pull Request und Main-Workflow geprüft. Validiert werden
`git diff --check`, Dokumentationsscope und der negative Gate-Scan.

Ergebnis: Der Phase-0-Status lautet `implementiert`. Der erneute Abgleich findet keine weitere
Status- oder Scopeabweichung; Phase 1 bleibt bis zum Abschluss dieses Docs-Gates gesperrt.

## Phase 1 – Eine kanonische Schritt-, Trainings- und Ergebnisstruktur

Status: veröffentlicht

### Plan

Ergebnis: `StepPrescription`, `TrainingPrescription`, `TrainingAssistantProfile` und
`RepetitionProgress.results` sind die einzigen Domainwahrheiten für Schrittplanung, Training und
Satzresultate. Historische Bundle-Schlüssel und Room-Spalten bleiben unverändert, werden aber an
ihren Grenzen direkt in diese Typen übersetzt. Sichtbares Verhalten, Schema 22 und alle
Kompatibilitätsfixtures bleiben unverändert.

Betroffene Grenzen:

- `core-domain` mit Template, Definition, Occurrence, Flow-Snapshot, Engine,
  Trainingsentscheidungen und den ausführenden Services;
- Editorzustand und `EditorStepSavedStateCodec` mit den bestehenden Saved-State-Schlüsseln;
- Room- und Flow-Mapper sowie die normalisierte Satzresultat-Persistenz;
- Today-, Widget- und Editor-Projektionen, Testfixtures und sämtliche Aufrufer der bisherigen
  Schattenfelder beziehungsweise der zweiten Wiederholungsliste;
- Domain-, Saved-State-, Trainingsregel-, Persistenz-, Migrations-, Golden- und Architekturtests.

Reihenfolge:

1. Vor dem Produktdiff Hash-Baselines für Schemaexport, Migrationen, Upgradefixture, Ressourcen
   und Editor-/Focus-Goldens erfassen.
2. Die Domain atomar umstellen: `TrainingAssistantConfig` und `legacyTrainingConfig()` löschen,
   Engine und Services direkt mit `StepPrescription` und aktiviertem
   `TrainingAssistantProfile` betreiben, `TrainingDecision` ausschließlich mit
   `nextPrescription` und `nextState` ausgeben sowie öffentliche Schritt-Schattenfelder und die
   listenbasierte `RepetitionProgress`-Compatibility-API entfernen.
3. Editorzustand und Saved-State-Codec direkt auf Prescription, nullable Policy und State
   umstellen. Die bestehenden Bundle-Namen werden unverändert gelesen und geschrieben; Room- und
   Flow-Mapper decodieren beziehungsweise encodieren ihre historischen Felder ebenfalls direkt.
4. Produktionsaufrufer, UI-Projektionen und Tests auf `prescription`, `results` oder eine
   berechnete unveränderliche `repetitions()`-Projektion migrieren. Persistenznamen bleiben nur in
   Entities, DAO-/SQL-/Migrations- und Schema-Grenzen bestehen.
5. Zuerst fokussierte Compile-, Domain-, Trainingsregel-, Saved-State- und Persistenztests, danach
   die vollständige Host-/Robolectric-, Lint- und Build-Matrix mit JDK 21 ausführen. Migration 8
   bis 22, Produktionsupgradeverträge, read-only Editor-/Focus-Goldens und Accessibility werden
   ausdrücklich mitgeprüft.
6. Implementierung getrennt gegen diesen Plan, die Phase-1-Entfernungslisten und die gesamte
   Roadmap auditieren. Negative Scans müssen Config-/Legacy-APIs, öffentliche
   Schritt-Schattenfelder und eine zweite Domain-Satzresultatliste ausschließen; Hash-Baselines
   müssen unverändert sein. Vor jeder Korrekturrunde zuerst einen Fixplan ergänzen.

Abnahme: Alle lokalen Phase-1-Kriterien sind belegt, bevor der produktwirksame Themenbranch
committed und über Pull Request, Squash-Merge, vollständigen Main-Workflow, Produktionsupgrade,
Packaging und Publish geschlossen wird. Bis dahin bleiben Phase 2 und der Status
`veröffentlicht` gesperrt.

### Korrekturrunde 1 – Negativscan der Architekturtests

Plan: Der erste separate Audit findet die entfernten Altbezeichner nicht in Produkt- oder
fachlichem Testcode, wohl aber als zusammenhängende Suchliterale im neu ergänzten
Architekturtest. Damit würde ein reiner Quellscan den Test selbst als verbotenen Aufrufer werten.
Die Literale werden ausschließlich im Architekturtest aus getrennten Fragmenten gebildet; seine
Prüfwirkung auf Dateiexistenz und verbotene Deklarationen bleibt identisch. Anschließend werden
Testcompile, fokussierter Architekturtest und der negative Quellscan erneut ausgeführt. Produkt-,
Persistenz-, Schema-, Ressourcen- und Golden-Dateien werden durch diese Korrektur nicht geändert.

Ergebnis: Der fokussierte Architekturtest ist grün. Der negative Code-Scan findet weder den
entfernten Config-Typ noch die Legacy-Konvertierung; die Korrektur hat ausschließlich Test und
Ausführungsprotokoll verändert.

### Validierung

- `git diff --check`: grün.
- `python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v`: 16 Tests grün.
- `python3 -m unittest discover -s scripts/release -p 'test_*.py' -v`: 23 Tests grün.
- Fokussierte Domain-, Trainingsregel-, Saved-State-, Transaktions- und Architektur-Suite: grün.
- Fokussierte Migration-8-bis-22-, Produktionsupgradefixture-, Neustartpersistenz-, Editor-/Focus-
  Golden- und Accessibility-Suite: grün; kein Golden-Update-Modus wurde verwendet.
- `./gradlew clean build --console=plain --no-daemon`: mit JDK 21 und vorhandenem Android-SDK
  grün; 146 Tasks ausgeführt. Der Lauf umfasst die vollständige Host-/Robolectric-Suite, Lint
  sowie Debug-, Instrumentation- und Release-Builds.
- Der tatsächliche Diff wird als Quality-, Instrumentierungs- und Produktrelease-relevant
  klassifiziert.

### Audit und Abweichungen

- Planabgleich: Config und Konvertierungen sind gelöscht; Engine und Services erhalten direkt
  Prescription und aktiviertes Profile; Entscheidungen enthalten nur atomare nächste
  Prescription und State; Editor, Saved State und Room-/Flow-Mapper verwenden die kanonischen
  Typen direkt.
- Entfernungslisten: Template, Definition, Occurrence und Flow-Snapshot veröffentlichen nur
  `prescription`. `RepetitionProgress` besitzt nur `results`; `repetitions()` ist eine bei Bedarf
  berechnete unveränderliche Projektion. Produktions- und Testcode enthalten weder Config- noch
  Legacy-Aufrufer.
- Persistenz-Allowlist: Der historische Wiederholungsname verbleibt ausschließlich an Room-,
  DAO-/Mapper-, Migrations- und deren Persistenzprüfgrenzen. Die Domain liest atomare
  `SetResult`-Werte.
- Kompatibilität: Die Menge der vorhandenen Trainings-Saved-State-Schlüssel ist unverändert.
  Schema-, Migrations-, AppDatabase-, Upgradefixture-, Ressourcen- und Golden-Baselines sind
  gegenüber dem Phasenstart byteidentisch; die kanonische Roadmap ist unverändert.
- Roadmapabgleich: Alle lokal belegbaren Phase-1-Kriterien sind erfüllt. Pull Request,
  Squash-Merge, vollständige Main-Matrix, Produktionsupgrade, Packaging und Publish fehlen noch;
  deshalb bleibt der Status `in Arbeit` und Phase 2 gesperrt.
- Außer der dokumentierten und abgeschlossenen Negativscan-Korrektur wurde keine weitere
  Abweichung gefunden.

### Korrekturrunde 2 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge, vollständiger grüner Main-Matrix, erfolgreicher
Paketierung, drei grünen Produktionsupgradeachsen und erfolgreichem Publish wird ausschließlich
der Phase-1-Status von der während der Implementierung zutreffenden Arbeitsmarkierung auf
`veröffentlicht` gesetzt. Der frühere Auditbefund bleibt unverändert als damaliger Stand erhalten.
Die Korrektur verändert weder Roadmap noch Produkt-, Schema-, Ressourcen- oder Testcode und wird
erneut über einen Themenbranch, Pull Request und Main-Workflow geprüft. Validiert werden
`git diff --check`, Dokumentationsscope und der negative Gate-Scan.

Ergebnis: Der Phase-1-Status lautet `veröffentlicht`. Das veröffentlichte Release zeigt auf den
exakten Squash-Commit und enthält ausschließlich APK und Release-Metadaten; der Remote-Themenbranch
ist gelöscht. Der erneute Abgleich findet keine weitere Status- oder Scopeabweichung; Phase 2
bleibt bis zum Abschluss dieses Docs-Gates gesperrt.

## Phase 2 – Fünf Repositories und eindeutige Composition

Status: veröffentlicht

### Plan

Ergebnis: `CatalogRepository`, `StepRepository`, `TodayRepository`, `FlowRepository` und
`TrainingRepository` sind die einzigen fachlichen Persistenzports. Room besitzt dafür genau fünf
gleichnamig fokussierte DAOs und fünf eigenständige Adapter. Portübergreifende Atomizität liegt
ausschließlich beim separaten `RoomTransactionRunner`; Schema 22, Entities, Migrationen,
Upgradefixture, Ressourcen und sichtbares Verhalten bleiben unverändert.

Betroffene Grenzen:

- `core-domain` mit den bisherigen Capability-Ports, Schedule-/Step-Services, fachlichen Use
  Cases und ihren vier bestehenden Use-Case-Bündeln;
- `data.local` mit dem bisherigen Sammel-DAO, Room-Gateway, Step-/Training-Adapter,
  Entity-Mapping und dem getrennten Transaktionsrunner;
- `AppDatabase`, `ApplicationUseCaseComposition` und `AppContainer` als einmalige Erzeugungs- und
  Verdrahtungsgrenze der fünf Adapter;
- Room-, Transaktions-, Rollback-, Migrations-, Presentation- und Cross-Slice-Tests sowie deren
  bisher breite Teststores;
- aktive Architekturübersichten und Architekturtests für Port-, DAO-, Adapter-, Import- und
  Composition-Grenzen.

Reihenfolge:

1. Vor dem Produktdiff Hash-Baselines für Schemaexport, Migrationen, Upgradefixture, Ressourcen,
   Goldens und die kanonische Roadmap erfassen; Entity- und Migrationsdiffs bleiben im gesamten
   Phasenverlauf leer.
2. Die elf überlappenden Capability-Ports atomar durch die fünf Zielports ersetzen. Use Cases,
   Schedule-/Step-Services und fachliche Hilfen erhalten jeden tatsächlich benötigten benannten
   Port genau einmal; Intersection-Typen, Capability-Probes und Portvererbung werden entfernt.
3. `TaskDao` nach `CatalogDao`, `StepDao`, `TodayDao`, `FlowDao` und `TrainingDao` aufteilen und
   seine Queries ohne SQL- oder Schemaänderung eindeutig zuordnen. Fünf einzelne Room-Adapter
   übernehmen Mapping und Writes; der Step-Adapter besitzt Templates, Snapshots und atomare
   Satzresultate, der Training-Adapter nur Profil-/Anpassungs-/Lastfrage-/Audit-/Volumengrenzen.
   `TaskStore`, `RoomTaskRepository` und `TaskDao` werden im selben Diff gelöscht.
4. `ApplicationUseCaseComposition` direkt aus `AppDatabase` verdrahten: jeden der fünf Adapter
   und den `RoomTransactionRunner` genau einmal erzeugen und ohne wiederholte positionale
   Adapterargumente an die vier Bündel weiterreichen. Slice-Tests erhalten portgenaue Fakes;
   gemeinsamer In-Memory-Zustand wird nur hinter getrennten Portadaptern beziehungsweise in
   ausdrücklich benannten Cross-Slice-Abnahmen geteilt.
5. Zuerst Compile-, Architektur-, DAO-/Room-, Transaktions-/Rollback- und Composition-Tests,
   danach Migration 8 bis 22, Produktionsupgradefixture, Neustartpersistenz, read-only Goldens,
   Accessibility und die vollständige Host-/Robolectric-, Lint- und Build-Matrix mit JDK 21
   ausführen.
6. Implementierung getrennt gegen diesen Plan, die Phase-2-Entfernungslisten und die gesamte
   Roadmap auditieren. Negative Scans müssen exakt fünf fachliche Ports/DAOs/Produktionsadapter,
   den separaten Runner, einmalige Adapterargumente, fehlende Room-Imports in Domain/Presentation
   sowie das Fehlen jedes All-Capabilities-Gateways belegen. Vor jeder Korrekturrunde zuerst
   einen Fixplan ergänzen.

Abnahme: Alle lokalen Phase-2-Kriterien sind belegt, bevor der produktwirksame Themenbranch
committed und über Pull Request, Squash-Merge, vollständigen Main-Workflow, Produktionsupgrade,
Packaging und Publish geschlossen wird. Bis dahin bleiben Phase 3 und der Status
`veröffentlicht` gesperrt.

### Implementierung

- Die elf bisherigen Capability-Ports wurden durch die fünf unabhängigen Ports
  `CatalogRepository`, `StepRepository`, `TodayRepository`, `FlowRepository` und
  `TrainingRepository` ersetzt. Kein Port erbt einen anderen Port oder den Runner.
- `CatalogDao`, `StepDao`, `TodayDao`, `FlowDao` und `TrainingDao` besitzen die unveränderten
  SQL-Zugriffe eindeutig; die fünf gleichnamigen Room-Adapter implementieren jeweils genau einen
  Port. Der Sammelstore, sein Room-Gateway und sein DAO wurden ohne Übergangsfassade entfernt.
- `ApplicationUseCaseComposition` erzeugt die fünf Adapter und `RoomTransactionRunner` direkt aus
  `AppDatabase` jeweils einmal. Use Cases, Schedule-/Step-Services und Trainingspfade erhalten nur
  ihre benannten Fachports.
- Room-Integrationstests bündeln die fünf echten Adapter in einer Fixture ohne eigenen Port.
  Cross-Slice-In-Memory-Abnahmen teilen Zustand hinter getrennten Port-Proxies; fokussierte
  Schedule-, Step- und Trainingtests verwenden ebenfalls getrennte Portobjekte und Runner.
- Die aktive Architekturübersicht, Today-Fokus-Beschreibung und die betroffenen ADR-Verweise
  beschreiben die erreichte Fünf-Port-Grenze. Die kanonische Roadmap blieb unverändert.

### Validierung vor dem unabhängigen Audit

- `:core-domain:compileJava`: grün.
- `:app:compileDebugJavaWithJavac`: grün; nur die bereits bestehenden
  Android-Deprecation-Warnungen.
- `:app:compileInstrumentationUnitTestJavaWithJavac`: grün; 12 bestehende
  Accessibility-Deprecation-Warnungen.
- Fokussierte Architektur-, Completion-, Today-Step-, Schedule- und Step-Transfer-Tests: grün.
- Vollständiges `:app:testInstrumentationUnitTest`: 515 Tests, 0 Fehler, 0 Fehlschläge,
  1 übersprungener Test.
- Vorher-/Nachher-Abgleich: Schemaexport `782fddf69323c48ec3ff09902ba36a79030c8d2e`,
  Ressourcen `0f6192766a50132038aad4825dbc0c41853009c9`, Goldens
  `96a127b245ee92c02b3534ae2912d0a06def8a85`, Upgradefixture
  `5d66ded633d944576b2d9d2d55128a411df7c45d`, Migrationen
  `0a19df76bcab6bf03f3d92a4198b88ccb42a9e72` und Roadmap
  `2e0a69aaaed6baeb23c5225781d575f09e468f31` sind unverändert; Entitydiff leer.
- Negativscans über Produktions- und Testquellen finden keinen entfernten Capability-Port, keinen
  Sammelstore, kein Sammel-DAO und kein altes Room-Gateway. Domain und Presentation importieren
  weder Room- noch `data.local`-Typen.

### Unabhängiger Audit und Korrekturrunde 1

Audit: Die Implementierung wurde erneut getrennt gegen die vollständige Roadmap, den Phase-2-Plan,
die Entfernungslisten und die unveränderlichen Dateien gelesen. Produktionsports, DAOs, Adapter,
Composition, Runner, Importgrenzen und Schema sind konform. Eine Testabweichung blieb: Der
ausdrücklich erlaubte Cross-Slice-In-Memory-Zustand implementiert selbst keinen Port, stellte aber
noch die fachlich veraltete Convenience-Methode `updateTrainingTemplate` bereit. Zwei
Rollbacktests schrieben dadurch nicht sichtbar über ihren getrennten `StepRepository`-Adapter.

Fixplan vor der Korrektur: Die beiden Tests schreiben über `repository.steps.updateTemplate`, die
Convenience-Methode wird ersatzlos entfernt. Danach laufen Negativscan, Testquellencompiler, die
beiden Set-Result-Rollbacktests und der Architekturtest erneut. Die bereits grüne vollständige
Hostmatrix wird vor dem lokalen Abschluss gemeinsam mit Lint und der vollständigen Buildmatrix
noch einmal aus sauberem Zustand ausgeführt.

Ergebnis: Beide Tests schreiben über den getrennten Step-Port; die Convenience-Methode ist
entfernt. Negativscan, Testquellencompiler, beide Set-Result-Rollbacktests und der Architekturtest
sind grün. Der erneute Roadmapabgleich findet keine weitere lokale Abweichung.

### Korrekturrunde 2 – genau eine Runnerinstanz

Audit: Der saubere Gesamtlauf war grün, der anschließende Instanziierungsscan fand jedoch neben
dem einmal in der Composition erzeugten Runner eine zweite, im `RoomStepRepository` verborgene
Runnerinstanz für die atomaren Snapshot-/Ergebniswrites. Sie überschritt keine Portgrenze, wich
aber von der wörtlichen Vorgabe ab, die fünf Adapter und den Runner jeweils einmal zu erzeugen.

Fixplan vor der Korrektur: Die Composition erzeugt `RoomTransactionRunner` vor den Adaptern und
übergibt dieselbe Instanz an `RoomStepRepository`; dessen selbst erzeugter Runner entfällt. Die
Room-Testfixture verdrahtet ihren einen Runner identisch. Der Architekturtest zählt anschließend
repo-weit im Produktionscode genau eine Runnererzeugung und genau eine Erzeugung jedes Adapters.
Danach laufen Compiler, Architektur-, Room-Step-, Transaktions-/Rollbacktests und die vollständige
Buildmatrix erneut.

Ergebnis: Produktionscode und Room-Testfixture erzeugen je Composition genau einen Runner und
reichen ihn an den Step-Adapter weiter. Architektur-, Room-Step-/Editor- und Set-Result-
Rollbacktests sind grün. Der finale `build`-Lauf enthält erneut 515 Host-/Robolectric-Tests mit
0 Fehlern, 0 Fehlschlägen und dem unverändert übersprungenen `WoodGrainBenchmarkTest`, Lint,
Check, Debug-, Instrumentation- und Release-Build, R8 sowie Packaging. Der vorherige saubere
`clean build` war ebenfalls grün. Der abschließende Negativ- und Immutable-Scan findet keine
weitere Abweichung.

### Korrekturrunde 3 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge, vollständiger grüner Main-Matrix, erfolgreicher
Paketierung, drei grünen Produktionsupgradeachsen und erfolgreichem Publish wird ausschließlich
der Phase-2-Status von `in Arbeit` auf `veröffentlicht` gesetzt. Der frühere Auditbefund bleibt
unverändert als damaliger Stand erhalten. Die Korrektur verändert weder Roadmap noch Produkt-,
Schema-, Ressourcen- oder Testcode und wird erneut über Themenbranch, Pull Request und
Main-Workflow geprüft. Validiert werden `git diff --check`, Dokumentationsscope und der negative
Gate-Scan.

Ergebnis: Der Produktstand bestand das vollständig grüne Pull-Request-Gate, wurde per Squash nach
`main` übernommen und bestand dort Quality, sechs Emulatorachsen, gemeinsames
Instrumentation-Gate, Paketierung, Produktionsupgrade auf API 26, 35 und 37 sowie Publish. Die
Veröffentlichung enthält ausschließlich APK und Release-Metadaten. Der Phase-2-Status lautet
deshalb `veröffentlicht`; Phase 3 bleibt bis zum Abschluss dieses Docs-Gates gesperrt.

## Phase 3 – UI-Ownership und Abschlussaudit

Status: in Arbeit

### Plan

Ergebnis: `TrainingAssistantEditorSection` besitzt die Editor-Assistentencontrols und arbeitet
direkt mit `StepPrescription`, nullable `TrainingAssistantPolicy` und
`TrainingAssistantState`. `TrainingAssistantPanelView` besitzt in Today Status, Lastfrage,
Antwortfeld, Verlauf und Undo. Die vier Assistentenaktionen laufen typisiert über
`TrainingAssistantUiAction` und den ausschließlich mit `TrainingUseCases` verdrahteten
`TrainingAssistantActionHandler`; `TodayViewModel` führt dessen Ergebnis nur über den vorhandenen
Command-/Feedbackkanal aus. Layout, Texte, Felder, Defaults, Reihenfolge, Test-Tags,
Accessibility und Interaktionen bleiben unverändert.

Betroffene Grenzen:

- Compose-Schritteditor und seine neue eigenständige Assistentensektion;
- Today-Fokuszeile, neues Assistentenpanel sowie die geschlossene Today-Aktionsgrenze;
- typisierte UI-Aktionen, Handler-Ergebnisse und `TodayViewModel` als reine Orchestrierung;
- UI-, Handler-, ViewModel-, Architektur-, Accessibility- und Golden-Tests;
- aktive Architekturübersichten und Abschlussaudit aller Roadmap-Entfernungslisten.

Reihenfolge:

1. Vor dem Produktdiff Hash-Baselines für Schemaexport, Migrationen, Upgradefixture, Ressourcen,
   Editor-/Focus-Goldens, Saved-State-Codec und kanonische Roadmap erfassen.
2. Die Editor-Assistentencontrols atomar nach `TrainingAssistantEditorSection` verschieben. Die
   Komponente erhält die drei kanonischen Typen direkt und liefert Änderungen ausschließlich als
   `StepPrescription` und nullable Policy zurück; ein Adaptermodell wird nicht eingeführt.
3. Status, Lastfrage, Antwortfeld, Verlauf und Undo atomar nach
   `TrainingAssistantPanelView` verschieben. `FocusStepRowView` bindet nur das Kind; Last-, RIR-
   und Safety-Controls der normalen Satzaufnahme bleiben unverändert in der Zeile.
4. `TrainingAssistantUiAction` für ApplyLoad, NoHigherLoad, Later und Undo einführen. ApplyLoad
   trägt Rohtext, aktuelle Lastart und Einheit. Der Handler übernimmt Punkt/Komma,
   Milli-Unit-Konvertierung, Validierung, Use-Case-Aufruf und typisiertes Ergebnis-Mapping.
   `TodayViewModel` verliert Lastparsing, Trainings-Use-Case-Ergebnismapping und
   Assistentenentscheidungslogik.
5. Architektur- und Interaktionstests auf die neuen Ownership-Grenzen erweitern und aktive
   Architekturtexte auf den erreichten Zielzustand aktualisieren. Danach fokussierte Compile-,
   Handler-, UI-, Recreation-, Accessibility- und read-only Golden-Suites ausführen.
6. Die vollständige Host-/Robolectric-, Lint- und Buildmatrix mit JDK 21 ausführen und die gesamte
   Roadmap getrennt gegen Entfernungslisten, Portgrenzen, Dokumentationsindizes,
   Schema-/Saved-State-Verträge sowie unveränderte UI-Goldens auditieren. Vor jeder Korrekturrunde
   zuerst einen Fixplan ergänzen.

Abnahme: Alle lokalen Phase-3- und Gesamtroadmap-Kriterien sind belegt, bevor der produktwirksame
Themenbranch committed und über Pull Request, Squash-Merge, vollständigen Main-Workflow,
Produktionsupgrade, Packaging und Publish geschlossen wird. Bis dahin bleiben der Status
`veröffentlicht` und der vollständige Roadmapabschluss gesperrt.

### Korrekturrunde 1 – identische Focus-Hierarchie und Undo-Testsequenz

Audit: Der erste read-only Focus-Golden fand ausschließlich an der nativen
`EditText`-Unterlinie eine deterministische Abweichung von 0,00001699. Die zusätzliche
`LinearLayout`-Ebene des neuen Panels veränderte dort die Alpha-Rundung einzelner Pixel, obwohl
Maße und übriger Renderstand identisch blieben. Der Handler-Test prüfte außerdem den durch
NoHigherLoad erhöhten Setstand erst nach dem unmittelbar folgenden erfolgreichen Undo und
erwartete deshalb fälschlich noch den Zwischenstand.

Fixplan vor der Korrektur: `TrainingAssistantPanelView` bleibt alleiniger Owner und Bindepunkt
aller Assistentenviews, hängt seine besessenen Summary-/Detailviews aber direkt in den bestehenden
Zeilenhost ein, sodass kein zusätzlicher Layoutknoten die unveränderte Pixelhierarchie beeinflusst.
Der Handler-Test prüft den erhöhten Setstand vor Undo, danach den wiederhergestellten Stand und
erst beim zweiten Undo das typisierte Rejected-Ergebnis. Anschließend laufen Testcompile,
Handler-, Ownership-, UI-, Focus-/Editor-Golden- und Accessibility-Suite erneut; Golden-Dateien
werden nicht aktualisiert.

Ergebnis: Das Panel besitzt und bindet weiterhin allein alle Assistentencontrols; die direkt in
den bestehenden Zeilenhost eingefügten Views stellen zugleich die byteidentische Renderhierarchie
wieder her. NoHigherLoad, der erfolgreiche Exactly-once-Undo und dessen zweite Ablehnung werden
in der fachlich richtigen Reihenfolge geprüft. Testcompile sowie 59 fokussierte Handler-,
Ownership-, UI-, Focus-/Editor-Golden- und Accessibility-Tests sind grün; kein Golden wurde
aktualisiert.

### Implementierung

- `TrainingAssistantEditorSection` besitzt alle Editor-Assistentencontrols, erhält direkt
  Prescription, nullable Policy und State und liefert nur Prescription und nullable Policy
  zurück. `TaskEditorComposeSteps` bindet die Komponente ohne Adaptermodell.
- `TrainingAssistantPanelView` besitzt in Today Summary, Lastfrage, Rohtextfeld, Antwortaktionen,
  Verlauf und Undo. `FocusStepRowView` bindet nur diesen Owner; Last-, RIR- und Safety-Eingaben
  der Satzaufnahme bleiben unverändert in der Zeile.
- Die vier Aktionen sind eigenständige `TrainingAssistantUiAction`-Typen. ApplyLoad trägt Rohtext,
  aktuelle Lastart und Einheit. `TrainingAssistantActionHandler` erhält ausschließlich
  `TrainingUseCases` und besitzt Parsing, exakte Milli-Unit-Konvertierung, Validierung,
  Use-Case-Aufruf und Completed-/Feedback-/Rejected-Mapping.
- `TodayViewModel` erzeugt den Handler einmal und ordnet sein typisiertes Ergebnis nur dem
  vorhandenen Command-/Feedbackkanal zu. Es enthält weder Lastparsing noch Trainings-Use-Case-
  Ergebnismapping oder eine Projektion der offenen Lastfrage.
- Architekturkarte, Today-Fokus-Beschreibung und Architekturindex beschreiben die erreichten
  UI-, Aktions-, Handler- und Fünf-Port-Grenzen ohne Sammelgateway.

### Validierung vor dem unabhängigen Audit

- Produkt- und Testquellencompiler für `today-core`, Debug-App und Instrumentation: grün; nur die
  bereits bestehenden Android- und Accessibility-Deprecation-Warnungen.
- Fokussierte Handler-, Ownership-, UI-, Focus-/Editor-Golden- und Accessibility-Suite:
  59 Tests grün; die Korrekturrunde ist darin enthalten.
- Vollständiges `:app:testInstrumentationUnitTest`: 521 Tests, 0 Fehler, 0 Fehlschläge,
  1 unverändert übersprungener Benchmark.
- `python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v`: 16 Tests grün.
- `python3 -m unittest discover -s scripts/release -p 'test_*.py' -v`: 23 Tests grün.
- `./gradlew clean build --console=plain --no-daemon`: mit JDK 21 und vorhandenem Android-SDK
  grün. Der Lauf umfasst Host-/Robolectric-Tests, Lint, Check, Debug-, Instrumentation- und
  Release-Build, R8 sowie Packaging.
- Vorher-/Nachher-Abgleich: Editor-/Focus-Goldens, Schemaexport, Ressourcen, Upgradefixture,
  Saved-State-Codec, Migrationen und kanonische Roadmap sind byteidentisch. Der tatsächliche
  Diff wird korrekt als Quality-, Instrumentierungs- und Produktrelease-relevant klassifiziert.

### Unabhängiger Abschlussaudit und Korrekturrunde 2

Audit: Der negative Gesamtroadmap-Scan fand trotz grüner Vorabvalidierung vier verbliebene
Vertragsabweichungen. `PresentationInvalidationSource` importiert in einer Kotlin-Datei noch den
Room-namensgebundenen Typ `data.local.RoomInvalidationSource`; der bisherige Architekturtest
prüfte diese Presentation-Grenze nur in Java. ADR-022 erklärt nachgelagerte visuelle
Folgefreigaben weiterhin zum Vertrag, und die aktive Render-Kritik hält ein späteres
hardwaregebundenes Performance-Gate offen, obwohl ADR-030 den Abschluss vollständig
automatisiert. Außerdem kopiert das Phase-2-
Ergebnis Pull-Request-, Commit-, Workflow- und Releasekennungen als nachträgliche
Ereignischronik in dieses Protokoll; mehrere Validierungsabschnitte nennen außerdem entgegen dem
kompakten Protokollvertrag konkrete Laufzeiten. `ComboPolicySource` und `TaskCatalogQuery` sind
dagegen keine
zusätzlichen Persistenzports: Ersteres liest eine Preference-Policy, Letzteres ist eine reine
Use-Case-Projektion, und kein Room-Adapter implementiert einen der beiden Typen.

Fixplan vor der Korrektur: Die Datenbank-Invalidierungsquelle wird ohne Verhaltensänderung als
`data.observable.DatabaseInvalidationSource` benannt und von Composition, Presentation sowie
ihren Tests ausschließlich über diesen neutralen Quelltyp verdrahtet; die alte Datei und ihr
Name entfallen. Der Architekturtest erhält einen Kotlin-fähigen negativen Production-Scan, der
Room- und `data.local`-Imports in allen Presentation-Quellen verbietet. ADR-022 verweist für den
visuellen Vertrag auf unveränderte Goldens und die automatisierte Matrix aus ADR-030; die
Render-Kritik ersetzt das offene hardwaregebundene Folgegate durch ein reproduzierbares
automatisiertes Performance-Budget. Das Phase-2-Ergebnis bleibt als knapper Gate-Nachweis
erhalten, entfernt aber sämtliche externen Kennungen; konkrete Laufzeiten entfallen im gesamten
Protokoll. Danach laufen Invalidierungs-,
Architektur-, UI-Ownership- und Golden-Tests, beide Python-Vertragssuiten, der vollständige
Host-/Robolectric-Testbestand sowie ein vollständiger JDK-21-Build erneut. Immutable-Hashes und
negative Gesamtroadmap-Scans werden abschließend wiederholt; Roadmap, Schema, Migrationen,
Ressourcen, Saved-State und Goldens dürfen sich nicht ändern.

Ergebnis: `DatabaseInvalidationSource` liegt als neutraler Datenbank-Quelltyp in
`data.observable`; Composition und Presentation importieren weder dessen Room-Implementierung
noch `data.local`. Der neue Java-/Kotlin-Architekturtest sichert diese Grenze über alle Domain-
und Presentation-Produktionsquellen. Die beiden aktiven Alttexte verweisen nun ausschließlich
auf unveränderte Goldens, die automatisierte Matrix und ein reproduzierbares CI-
Performancebudget. Externe Gate-Kennungen und konkrete Laufzeiten sind aus dem kompakten
Protokoll entfernt.

Die fokussierte Invalidierungs-, Ownership-, Handler-, UI-, Golden- und Accessibility-Suite ist
grün. CI-Vertragssuite mit 16 Tests und Release-Vertragssuite mit 23 Tests sind grün. Der
vollständige Host-/Robolectric-Bericht enthält 522 Tests, 0 Fehlschläge, 0 Fehler und den
unverändert einen übersprungenen Benchmark. Der anschließende saubere JDK-21-Gesamtbuild ist mit
allen 146 Tasks grün und umfasst erneut Test, Lint, Check, Debug-, Instrumentation- und
Release-Build, R8 sowie Packaging. Der finale Negativscan findet weder alte Trainings- oder
Gatewaytypen, unerlaubte Room-/`data.local`-Imports, Multi-Port-Adapter noch aktive offene
Folgegates. Schema, Migrationen, Upgradefixture, Ressourcen, Saved-State-Codec, Editor-/Focus-
Goldens und kanonische Roadmap sind gegenüber dem Phasenstart byteidentisch. Die
Scope-Klassifikation fordert korrekt Quality, Instrumentierung und Produktrelease. Damit sind
alle lokal belegbaren Phase-3- und Gesamtroadmapkriterien erfüllt; `veröffentlicht` bleibt bis
zum abgeschlossenen Remote-Gate gesperrt.
