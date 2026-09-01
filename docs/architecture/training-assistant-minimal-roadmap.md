# Roadmap: minimaler Trainingsassistent ohne Übergangsarchitektur

Status: verbindlich

Beschlossen: 2026-09-01

Ausgangsstand bei Beschluss: `28182d06` (`origin/main`)

Ausgangsschema: Room 22

## Ziel und unveränderliche Grenzen

Der vorhandene Trainingsassistent wird in vier einzeln mergebaren Phasen auf eine einzige
Schritt-, Trainings-, Ergebnis- und Persistenzsprache reduziert. Das sichtbare und fachliche
Verhalten bleibt dabei unverändert:

- Opt-in, Widerstandsart, Ausgangslast, Einheit, Ziel-RIR, Wochenlimit sowie primäre und
  sekundäre Muskelgruppen bleiben im normalen Editor sichtbar;
- Kalibrierung, Ready-/Hard-Streaks, Safety-Pause, Volumengrenze, Lastfragen,
  Progressionsreihenfolge, Zehn-Prozent-Grenze, Verlauf und Undo bleiben unverändert;
- bestehende Aufgaben, Occurrences, Flow-Snapshots, Satzresultate, Lastfragen und Auditdaten
  bleiben verlustfrei erhalten;
- Room-Schema 22, Datenbankversion, Schemaexport, Saved-State-Schlüssel und garantierter
  Upgradepfad ab Schema 8 bleiben unverändert;
- Editor und Today werden strukturell, nicht visuell verändert.

Die Bereinigung führt keine Deprecated-Aliase, Übergangskonstruktoren, Sammelfassaden oder
parallelen Read-/Write-Pfade ein. Bestehende Room-Spalten und Saved-State-Schlüssel sind
ausschließlich stabile Persistenzformen und werden direkt in die kanonischen Typen übersetzt.
Eine Phase darf während der lokalen Bearbeitung vorübergehend nicht kompilieren; ihr Merge-Stand
darf jedoch keine Übergangsarchitektur enthalten.

Physische Geräteabnahme ist repo-weit kein normatives Gate mehr. Automatisierte Prüfung,
Produktionsupgrade und Veröffentlichung bilden für produktwirksame Phasen den vollständigen
Abschluss. Der In-App-Updater und seine Hash-, Signatur-, Paket-, Versions- und Trust-Prüfungen
bleiben unverändert.

## Verbindlicher Phasenablauf

Jede Phase beginnt nach einem frischen Fetch vom aktuellen `origin/main` in einem isolierten
Worktree auf dem angegebenen Themenbranch. Der aktive Frontend-Checkout bleibt unberührt.

Vor der Implementierung werden diese Roadmap, das kompakte Ausführungsprotokoll, der aktuelle
Projektstand und die betroffenen Tests erneut gelesen. Nach Implementierung und fokussierter
Validierung folgt ein separater negativer Abgleich gegen Phasenplan, Entfernungslisten und
Gesamtroadmap. Abweichungen erhalten vor ihrer Korrektur einen dokumentierten Fixplan.

Jede Phase wird committed, in einem eigenen Pull Request gegen `main` geprüft und erst nach
grünem `pull-request-gate` per Squash-Merge übernommen. Die nächste Phase beginnt erst vom
gemergten Stand auf `origin/main`.

Eine nicht produktwirksame Phase ist mit grünem Main-Workflow und vertraglich korrekt
übersprungenem Publish abgeschlossen. Eine produktwirksame Phase ist erst abgeschlossen, wenn der
exakte Squash-Stand auf `main` die vollständige Instrumentierungs-, Upgrade-, Packaging- und
Publish-Kette bestanden hat. Es gibt keinen nachgelagerten Status „Geräteabnahme ausstehend“.

## Zielarchitektur und Schnittstellen

### Eine Schritt-, Trainings- und Ergebnissprache

- `StepPrescription(amount, rest, training)` ist die einzige Ausführungsverordnung für Template,
  Definition, Occurrence und Flow-Snapshot.
- `TrainingPrescription(load, targetRir)` enthält die aktuelle Trainingsvorgabe.
- `TrainingAssistantProfile(policy, state)` ist das einzige optionale Assistentenprofil. `null`
  bedeutet Opt-out.
- Die Engine erhält direkt `StepPrescription`, ein aktiviertes `TrainingAssistantProfile`, die
  atomaren `SetResult`-Ergebnisse und das effektive Wochenvolumen.
- `TrainingDecision` enthält ausschließlich `action`, `reason`, `ruleVersion`,
  `nextPrescription`, `nextState` und bei `REQUEST_NEXT_LOAD` zusätzlich `loadDirection`.
- Template, Definition, Occurrence und Flow-Snapshot veröffentlichen nur `prescription`.
  Schattenfelder wie `amount`, `restTimerPolicy`, `plannedLoad` und `targetRir` entfallen.
- `RepetitionProgress.results` bleibt die einzige Ergebniswahrheit. Eine Wiederholungsliste darf
  nur als berechnete, unveränderliche Methode wie `repetitions()` existieren.
- `TrainingAssistantConfig`, `legacyTrainingConfig()`, Config-Konvertierungen und der
  listenbasierte Compatibility-Reader von `RepetitionProgress` entfallen ersatzlos.

Persistenznamen wie `RepetitionResultEntity.actualRepetitions` und die historische Room-Spalte
`occurrence_steps.actualRepetitions` bleiben wegen des unveränderten Schemas erlaubt. Verboten ist
eine gleichnamige zweite Domain- oder Anwendungswahrheit.

### Fünf kohärente Persistenzgrenzen

| Port | Verantwortlichkeit |
|---|---|
| `CatalogRepository` | Aufgaben, Katalogabfragen, Zeitpläne und wiederverwendbare Definitionen |
| `StepRepository` | Templates, Occurrence-Schritte, Reihenfolge und atomare Satzresultate |
| `TodayRepository` | Occurrences, Materialisierung, Rewards, Combos und Verpflichtungen |
| `FlowRepository` | Flow-Definitionen, Läufe, Übergänge und Ressourcen |
| `TrainingRepository` | Profile, Anpassungen, Lastfragen, Auditordnung und effektives Volumen |

Kein Port erweitert einen anderen Port oder `TransactionRunner`. Room besitzt je Port genau einen
DAO und einen Adapter. Kein Produktionsadapter implementiert mehrere der fünf Ports.
Portübergreifende Atomizität läuft ausschließlich über den separaten `RoomTransactionRunner`.
Training darf das effektive Volumen über bestehende Tabellen ermitteln, ohne dafür einen zweiten
Port zu implementieren.

`ApplicationUseCaseComposition` erzeugt die fünf Room-Adapter und den Runner jeweils einmal direkt
aus `AppDatabase`. Die vier fachlichen Bündel `CatalogUseCases`, `TodayUseCases`, `FlowUseCases`
und `TrainingUseCases` bleiben bestehen. Use Cases erhalten nur die benötigten benannten Ports.
`TaskStore`, `RoomTaskRepository`, `TaskDao`, Intersection-Typen, Capability-Probes und mehrfach
positionell übergebene Store-Objekte entfallen vollständig.

Fokussierte Tests verwenden portgenaue Fakes. Ein umfassender In-Memory-Speicher bleibt nur in
explizit benannten Cross-Slice-Abnahmetests zulässig.

### Klare UI-Ownership bei identischem Layout

- `TrainingAssistantEditorSection` erhält direkt `StepPrescription`, eine nullable
  `TrainingAssistantPolicy` und `TrainingAssistantState` und liefert Änderungen wieder in diesen
  Typen zurück. Ein Editor-Adaptermodell für dieselben Werte ist nicht zulässig.
- `TrainingAssistantPanelView` besitzt in Today Status, Lastfrage, Antwortfeld, Verlauf und Undo.
  `FocusStepRowView` bindet nur dieses Kind und kennt dessen Controls nicht. Last-, RIR- und
  Safety-Eingaben für die Satzaufnahme bleiben normale Ausführungscontrols der Zeile.
- `TrainingAssistantUiAction` bildet `ApplyLoad`, `NoHigherLoad`, `Later` und `Undo` typisiert ab.
  `ApplyLoad` trägt den Rohtext sowie die aktuelle Lastart und Einheit.
- `TrainingAssistantActionHandler` übernimmt Dezimalpunkt/-komma, Milli-Unit-Konvertierung,
  Validierung, Use-Case-Aufruf und Ergebnis-Mapping.
- Das Handler-Ergebnis unterscheidet `Completed`, `Feedback(message)` und `Rejected(message)`.
  `TodayViewModel` führt es nur über den vorhandenen Command-/Feedbackkanal aus und enthält weder
  Lastparsing noch Trainings-Use-Case-Ergebnismapping.

Alle heutigen Texte, Felder, Defaults, Reihenfolgen, Test-Tags, Accessibility-Eigenschaften und
Interaktionen bleiben unverändert. Editor- und Focus-Goldens werden nicht aktualisiert.

## Roadmap

### Phase 0 – Vertrag, Archiv und automatisierter Abschluss

Branch: `refactor/training-minimal-p0-contract`

- Diese Roadmap, ein kompaktes Ausführungsprotokoll und ADR-030 für Domain-, Repository- und
  Abschlussgrenzen werden als einzige aktive Trainings-Architekturführung angelegt.
- Die bisherige Trainings-Roadmap, ihr 815-zeiliges Protokoll sowie ADR-027 bis ADR-029 werden
  unverändert nach `docs/archive/training-assistant-cleanup-2026-08/` verschoben.
- Historische Protokolle mit dem früheren Gerätegate werden vollständig archiviert statt
  nachträglich umgeschrieben. Laufende Protokolle erhalten einen kompakten aktuellen Nachfolger.
- Normative Release-, Frontend-, Teststrategie- und Architekturtexte werden auf automatisierten
  Abschluss umgestellt. Ältere aktive ADRs mit einzelnen veralteten Gate-Sätzen verweisen nur
  noch auf ADR-030.
- `scripts/ci/run-device-acceptance.sh`, `scripts/ci/test_device_acceptance.py` und alle
  zugehörigen Vertragsprüfungen werden gelöscht.
- Phasenstatus kennt nur `implementiert` und bei produktwirksamen Phasen `veröffentlicht`.

Abnahme:

- Kein aktiver normativer Vertrag referenziert Runner, manuelle Gerätefreigabe oder einen offenen
  Geräte-Abnahmestatus.
- Historische Gate-Nachweise liegen ausschließlich unter `docs/archive/`.
- Release-Scope-Tests bestätigen für Phase 0 den absichtlich übersprungenen Produkt-Publish.
- Pull Request, Squash-Merge und Main-Workflow sind grün.

### Phase 1 – Eine kanonische Schritt-, Trainings- und Ergebnisstruktur

Branch: `refactor/training-minimal-p1-domain`

- `TrainingAssistantConfig`, sämtliche Konvertierungen und `legacyTrainingConfig()` werden
  ersatzlos entfernt.
- Editorzustand, Saved-State-Codec, Room-Mapper, Engine, Services und Tests werden direkt auf
  Prescription, Policy, Profile und State umgestellt.
- `TrainingDecision` wird atomar auf `nextPrescription` und `nextState` umgestellt; getrennte
  Satz-/Last-Ergebnisfelder entfallen.
- Alle öffentlichen Schattenfelder werden aus Template, Definition, Occurrence und Flow-Snapshot
  entfernt und sämtliche Aufrufer auf `prescription` migriert.
- `actualRepetitions` wird aus `RepetitionProgress` entfernt. Produktions- und Testcode liest
  `results` oder die berechnete Projektion.
- Bestehende Bundle-Schlüssel und Room-Felder werden ohne Zwischenmodell direkt decodiert und über
  denselben Persistenzpfad geschrieben.
- Schema 22, Datenbankversion, Migrationen und Exportfixture bleiben byteidentisch.

Abnahme:

- Negative Architekturtests finden keine entfernten Typen, Legacy-Helfer, Domain-Schattenfelder
  oder zweite Ergebnisliste.
- Persistence-Entities, DAO-Spalten, Migrationstexte und Schemaexporte sind die einzige Allowlist
  für historische Feldnamen.
- Trainingsregeln, Saved-State-Recreation, Migration 8 nach 22 und Persistenz-Neustarts sind grün.
- Golden-, Schema-, Migrations- und Ressourcenfiles sind unverändert.
- Pull Request, Squash-Merge, Main-Matrix, Produktionsupgrade und Publish sind grün.

### Phase 2 – Fünf Repositories und eindeutige Composition

Branch: `refactor/training-minimal-p2-storage`

- Die bestehenden Capability-Ports werden atomar durch die fünf festgelegten Repository-Ports
  ersetzt.
- `CatalogDao`, `StepDao`, `TodayDao`, `FlowDao` und `TrainingDao` werden eingeführt und alle
  Queries ohne Schemaänderung eindeutig zugeordnet.
- Fünf eigenständige Room-Adapter werden erstellt. `TaskStore`, `RoomTaskRepository` und `TaskDao`
  werden im selben Pull Request gelöscht; eine delegierende Übergangsfassade ist nicht zulässig.
- `ApplicationUseCaseComposition` wird aus `AppDatabase`, den fünf einmal erzeugten Adaptern und
  einem Runner verdrahtet.
- Use Cases erhalten nur die fachlich benötigten Ports. Intersection-Typen, Capability-Probes und
  wiederholte positionale Übergabe desselben Stores sind verboten.
- Slice-Tests verwenden portgenaue Fakes; der breite In-Memory-Speicher bleibt ausschließlich für
  ausdrücklich benannte Cross-Slice-Abnahmen.

Abnahme:

- Domain und Presentation importieren keine Room-Typen.
- Kein Produktionsadapter implementiert mehrere der fünf Ports.
- Transaktions- und Rollbacktests beweisen atomare Satzaufnahme, Korrektur, Completion, Rewards und
  Adaptation über mehrere Ports.
- Schema 22, Migrationen, Entities und vorhandene Daten bleiben unverändert.
- Pull Request, Squash-Merge, Main-Matrix, Produktionsupgrade und Publish sind grün.

### Phase 3 – UI-Ownership und Abschlussaudit

Branch: `refactor/training-minimal-p3-ui`

- Die Editor-Assistentensektion wird in `TrainingAssistantEditorSection` verschoben und direkt an
  die kanonischen Typen gebunden.
- Status, Lastfrage, Verlauf und Undo werden vollständig in `TrainingAssistantPanelView`
  verschoben.
- Die vier Assistentenaktionen werden über `TrainingAssistantUiAction`,
  `TrainingAssistantActionHandler` und typisierte Ergebnisse geführt.
- `TaskEditorComposeSteps`, `FocusStepRowView` und `TodayViewModel` werden von
  Assistentencontrols, Lastparsing und Use-Case-Ergebnismapping bereinigt.
- Abschließend werden alle Entfernungslisten, Portgrenzen, Dokumentationsindizes,
  Schema-/Saved-State-Verträge und UI-Goldens negativ auditiert.

Abnahme:

- Editor- und Focus-Goldens bleiben byteidentisch; ihre Aktualisierungsmodi werden nicht verwendet.
- Accessibility-Matrix und sämtliche Assistenteninteraktionen sind unverändert grün.
- Aktive Dokumentation beschreibt ausschließlich den erreichten Zielzustand. Das kompakte
  Protokoll enthält nur Status, Plan, Validierung und Abweichungen.
- Nach Squash-Merge schließen grüner Main-Workflow, Produktionsupgrade, Packaging und Publish die
  Roadmap vollständig ab.

## Dauerhafte Test- und Freigabematrix

Pro Phase laufen mindestens:

- `git diff --check`, Architektur-Negativtests sowie CI- und Release-Vertragstests;
- die fokussierte Domain-, Saved-State-, Persistenz-, Transaktions- oder UI-Suite des
  Phasenscopes;
- ein negativer Scan, dass keine unerlaubte Übergangs-API im Merge-Diff verbleibt.

Vor jedem produktwirksamen Merge laufen zusätzlich:

- vollständige Host-/Robolectric-Suite, Lint sowie Debug-, Release- und AndroidTest-Builds;
- Domainverträge für Kalibrierung, Ready-/Hard-Streaks, RIR, Safety-Pause, Volumengrenze,
  Lastfragen, Körpergewicht, unterstütztes Körpergewicht und Zehn-Prozent-Limit;
- Transaktionstests für Satzaufnahme, Korrektur, Reopen, Completion, Rewards, Adaptation und
  Fehlerrollback;
- Persistenztests für offene Lastfragen, stabile Auditordnung, Verlauf, Exactly-once-Undo und
  Datenbankneustart;
- vollständiger Migrationsgraph ab Schema 8 und Nachweis des byteidentischen Schema-22-Exports;
- Editor-/Today-Recreation, read-only Goldens und Accessibility-Matrix.

Im Pull Request und auf `main` laufen normale und animationsaktive Instrumentierung auf API 26,
35 und 37. Für Phasen 1 bis 3 folgen Produktionsupgrade, Paketprüfung und Veröffentlichung
desselben geprüften Artefakts. Es gibt keine physische Geräteprüfung und keinen später offenen
Acceptance-Status.

## Festgelegte Annahmen

- Trainingsforschung, Progressionsreihenfolge, Produktdefaults und Sicherheitsgrenzen ändern sich
  nicht.
- Alle heutigen erweiterten Assistenteneinstellungen bleiben ohne Expertenmodus sichtbar.
- Interne Java-/Kotlin-APIs dürfen brechen; Datenbank-, Saved-State- und Releasekompatibilität
  dürfen nicht brechen.
- Jede ersetzte interne API wird innerhalb derselben Phase vollständig entfernt.
- Historische Roadmaps und Protokolle bleiben nur im separaten Archiv, nicht in der aktiven
  Architekturführung.
- Ein grüner lokaler Lauf ersetzt weder Pull Request, Squash-Merge noch den erforderlichen grünen
  Main-Workflow.
