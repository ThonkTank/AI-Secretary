# Ausführungsprotokoll: saubere Schritt- und Trainingsarchitektur

Die autoritative Zieldefinition steht in der
[Trainingsassistenten-Roadmap](training-assistant-cleanup-roadmap.md). Dieses Dokument bewahrt
Vorprüfungen, konkrete Phasenpläne, Nachweise, Audits und Korrekturrunden, ohne die ursprüngliche
Roadmap nachträglich umzuschreiben.

## Phasenstatus

| Phase | Status | Abschlussnachweis |
|---|---|---|
| 0 – Vertrag und Grundlage | abgeschlossen | PR #299, Squash `c1e72ba4` |
| 1 – Schrittverordnung | abgeschlossen | PR #300, Squash `22a9ac17` |
| 2 – Satzresultat | abgeschlossen | PR #301, Squash `137c7b84` |
| 3 – Lastentscheidung | abgeschlossen | PR #302, Squash `77afd26d` |
| 4 – Bedienung | abgeschlossen | PR #303, Squash `693c94f4` |
| 5 – Ports und Abschlussaudit | abgeschlossen | PR #304, Squash `e91b8833` |

Die Tabelle bezeichnet den Implementierungsabschluss jeder Phase gemäß Pull-Request- und
Squash-Merge-Gate. Die für die sichtbare Phase 4 zusätzlich vorgeschriebene physische
Release-Abnahme ist separat offen und wird im Remote-Abschluss sowie in der abschließenden
Korrekturrunde ausgewiesen.

## Phase 0 – Vertrag und reproduzierbare Grundlage

### Vorprüfung

- Sauberer Ausgangspunkt ist `origin/main` auf `521fafcb`, Room-Schema 21. Der aktive
  Frontend-Checkout bleibt unberührt; gearbeitet wird im separaten Main-Worktree.
- CI verwendet JDK 21, lokal wurde ohne Vorgabe JDK 25 gewählt. Robolectric ist damit nachweislich
  nicht zuverlässig ausführbar.
- `DatabaseContract.VERSION` ist 21, während der unabhängige Upgrade-Probe Quellversion 8 und
  Zielversion 21 zusätzlich als Literale hält. Der Workflowvertrag fixiert dieselben Literale.
- Der Upgrade-Probe darf bewusst keine Produktklasse laden: Seine Test-APK muss sowohl gegen die
  alte als auch die neue Produkt-APK funktionieren.

### Implementationsplan

- Die freigegebene Roadmap unverändert als kanonisches Dokument ablegen, ADR-028 ergänzen und
  dieses getrennte Protokoll verlinken.
- JDK 21 über `.java-version` und Gradles Daemon-JVM-Kriterien festlegen. Der offizielle
  Foojay-Resolver erzeugt plattformbezogene Download-URLs; lokale Installationspfade werden nicht
  eingecheckt.
- Upgrade-Probe aus dem geprüften Fixture lesen lassen: Die Seed-Phase speichert die tatsächlich
  verwendete Quellversion, die Verify-Phase liest die erwartete Zielversion aus demselben Asset.
  Produkt- und Fixture-Vertrag bleiben über den bestehenden Hosttest gekoppelt.
- Workflowverträge so ändern, dass sie fehlende Literale und den Fixture-basierten Pfad verlangen.
- Veraltete Testnamen und die direkte Schema-21-Assertion auf den zentralen Vertrag umstellen.
- Release-Verträge, fokussierte Migrationstests und anschließend das vollständige lokale Gate mit
  JDK 21 ausführen.

### Planabweichung aus der Vorprüfung

Die Roadmap formulierte, der Probe solle den Produktvertrag symbolisch referenzieren. Das würde
seine nachgewiesene Unabhängigkeit von der installierten Produkt-APK verletzen. Stattdessen enthält
der Probe keine Schema-Konstante mehr und liest das durch `ProductionUpgradeFixtureContractTest`
gegen `DatabaseContract` geprüfte Fixture. Damit verschwindet die ungesicherte zweite Wahrheit,
ohne eine neue Laufzeitabhängigkeit auf Produktcode einzuführen.

### Validierung und Audit

- `git diff --check`: grün.
- `python3 -m unittest discover -s scripts/ci -p 'test_*.py'`: 17 Tests grün.
- `python3 -m unittest discover -s scripts/release -p 'test_*.py'`: 23 Tests grün.
- Erster fokussierter Gradle-Lauf: Domain und Produktcode kompilierten; AndroidTest-Kompilierung
  schlug fehl, weil `UpgradePersistenceTest` nach der neuen Fixture-basierten Signatur noch keinen
  Test-Asset-Kontext an `UpgradePersistenceProbe.verify` übergab.

### Korrekturrunde 1 – AndroidX-Probeaufruf

Plan: Den normalen Instrumentierungstest wie den unabhängigen Runner um den Context der Test-APK
ergänzen. Danach werden AndroidTest-Kompilierung und beide fokussierten Hosttests unverändert
wiederholt. Es werden weder Probe-Verhalten noch Produktcode erweitert.

Ergebnis: AndroidTest-Kompilierung und beide fokussierten Hosttests sind grün.

### Korrekturrunde 2 – ausführbarer JDK-Vertrag

Der negative Audit fand, dass die neue JDK-21-Auswahl zwar funktioniert, aber noch kein Test ihre
drei repositoryweiten Bestandteile gegen späteren Drift schützt. Der bestehende Buildgrundlagen-
Vertrag wird deshalb um `.java-version`, Daemon-JVM-Version und offiziellen Foojay-Resolver
ergänzt. Danach werden Release-/Workflowverträge erneut ausgeführt.

Ergebnis: Alle 23 Release-/Workflowverträge sind erneut grün und sichern die drei Bestandteile.

### Vollständige Validierung

- `git diff --check` und interne Markdown-Linkprüfung: grün.
- CI-Harness: 17 Tests grün; Release-/Workflowverträge: 23 Tests grün.
- Fokussierter Migrations-/Fixture-Lauf und AndroidTest-Kompilierung: grün.
- Vollständiges Quality-Gate ohne gesetztes `JAVA_HOME`:
  `./gradlew testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease --console=plain`; 157 Tasks in 21:29
  Minuten, 492 Tests, null Fehler, ein bewusster Benchmark-Skip, Lint grün und alle drei
  Paketierungen erfolgreich.
- Gradle verwendete den mit den neuen Kriterien kompatiblen JDK-21-Daemon. Die generierte Datei
  enthält nur offizielle plattformspezifische Resolver-URLs und keinen lokalen Pfad.
- Artefakte: Debug 5.571.045 Byte, Instrumentierung 1.599.043 Byte, unsigned Release 2.775.076
  Byte, Fonts 1.478.008 Byte; alle bestehenden Budgets werden eingehalten.

### Roadmap- und Phasenaudit

Der Abgleich findet keine Produkt-, Schema-, Signatur-, UI- oder Upgradepfadänderung. Roadmap,
ADR und getrenntes Protokoll sind verlinkt. JDK 21 ist ausführbar festgelegt und gegen Drift
gesichert. Der unabhängige Upgrade-Probe besitzt keine Schema-Literale oder Produktabhängigkeit;
Quelle und Ziel stammen aus dem Fixture, dessen Hostvertrag beide Werte gegen
`DatabaseContract` prüft. Der Migrationsgraph und der konkrete 20→21-Test verwenden die zentrale
Version. Die beiden dokumentierten Korrekturrunden sind geschlossen; lokal bleibt keine
Diskrepanz. Abschlussnachweis sind nun der eigene grüne Pull Request und Squash-Merge nach
`main`. Als nicht sichtbare Vertragsphase benötigt Phase 0 keine physische Geräteabnahme.

### Remote-Abschluss

- Pull Request #299 prüfte Commit `0a5f2801` gegen den unveränderten Ausgangsstand `521fafcb`.
- `quality`, normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie
  `instrumentation-gate` und `pull-request-gate` waren grün. Der längste Lauf war die
  animationsaktive API-35-Variante mit 13:35 Minuten.
- Der Pull Request wurde am 2026-08-31 per Squash als `c1e72ba4` nach `main` gemergt;
  `origin/main` und der isolierte Main-Worktree zeigten anschließend denselben Commit.

## Phase 1 – Gemeinsame Schrittverordnung

### Vorprüfung

- Ausgangspunkt ist der verifizierte Phase-0-Squash `c1e72ba4` auf `origin/main`; gearbeitet wird
  auf `feat/training-cleanup-p1-prescription` im isolierten Roadmap-Worktree.
- `StepAmount`, `RestTimerPolicy`, geplante Last und Ziel-RIR liegen aktuell als parallele Felder
  auf Definition, Vorlage, Occurrence und Flow-Snapshot. `TrainingAssistantConfig` und
  `TrainingAssistantState` bilden gemeinsam das vorlagenspezifische Profil, sind aber getrennt.
- `EditorStepState` bündelt Fachwerte, Bundle-Schlüssel und Legacy-Decoding in derselben Klasse.
  Überladene Konstruktoren halten zahlreiche ältere Aufrufformen offen.
- Die Room-Tabellen besitzen bereits alle Phase-1-Spalten. Die Phase darf deshalb weder Schema,
  Migrationen noch sichtbare Editor-Goldens verändern.

### Implementationsplan

- `TrainingPrescription` als optionale Last-/Ziel-RIR-Verordnung sowie `StepPrescription` als
  einzige Gruppierung von Amount, Pausenregel und Training einführen. Die Gruppierung validiert,
  dass Pause und Training nur bei Satzschritten zulässig sind.
- `TrainingAssistantPolicy` aus den Guardrails und Muskelzuordnungen sowie
  `TrainingAssistantProfile(policy, state)` als ausschließlich vorlagenspezifischen Werttyp
  einführen. Die bestehende Persistenzform bleibt in Phase 1 unverändert.
- Definition, Vorlage, Occurrence und Flow-Snapshot auf `StepPrescription` umstellen und alle
  Produktions- und Testaufrufer auf jeweils einen vollständigen Konstruktor beziehungsweise
  benannte Fabriken migrieren. Bereits materialisierte Occurrences und Snapshots übernehmen nur
  ihre beim Erzeugen kopierte Prescription.
- Den Editorwert in einen gruppierten `EditorStepDraft` überführen. Bundle-Encoding und tolerantes
  Legacy-Decoding kommen in einen getrennten `EditorStepSavedStateCodec`; gespeicherte Schlüssel
  bleiben kompatibel.
- Room-Mapper, Occurrence-/Flow-Materialisierung, Formatierung und UI-Projektionen auf die
  gruppierten Werte umstellen. Danach die überladenen Kompatibilitätskonstruktoren entfernen.
- Modell-, Mapper-, Materialisierungs-, Editor-Saved-State- und Golden-Tests fokussiert ausführen,
  anschließend das vollständige lokale Gate. Der negative Audit prüft zusätzlich, dass keine
  parallele fachliche Schrittverordnung und keine Schema- oder sichtbare UI-Änderung verbleibt.

### Abnahmekriterien

- Alle vier ausführbaren Schrittrepräsentationen besitzen denselben `StepPrescription`-Werttyp;
  nur die Vorlage besitzt ein optionales `TrainingAssistantProfile`.
- Änderungen an Vorlagen verändern weder bestehende Occurrences noch Flow-Snapshots.
- Alte Room-Zeilen und gespeicherte Editor-Bundles werden ohne Schemaänderung gelesen.
- Produktionscode nutzt keine überladenen Legacy-Konstruktoren; Editor-Goldens bleiben identisch.

### Implementierung und Korrekturrunden

- `StepPrescription(amount, rest, training)` und `TrainingPrescription(load, targetRir)` bilden
  nun die gemeinsame unveränderliche Ausführungsverordnung. Definition, Vorlage, Occurrence und
  Flow-Snapshot besitzen jeweils genau einen vollständigen öffentlichen Konstruktor und leiten
  bestehende Lesefelder ausschließlich aus dieser Verordnung ab.
- `TrainingAssistantPolicy` enthält nur Guardrails und Muskelzuordnung; das ausschließlich auf der
  Vorlage gespeicherte `TrainingAssistantProfile` ergänzt den Lernzustand. Die alte
  `TrainingAssistantConfig` bleibt bis Phase 3 ein abgeleiteter Engine-/Persistenzadapter, damit
  diese Phase weder Progressionsverhalten noch Room-Schema ändert.
- Occurrence- und Flow-Materialisierung kopieren die Verordnung. Room-Mapper lesen und schreiben
  weiterhin exakt die vorhandenen Spalten. Der Editor besitzt die gruppierte Verordnung und
  Policy als Wahrheit; `EditorStepSavedStateCodec` kapselt die unveränderten Bundle-Schlüssel und
  das tolerante Legacy-Decoding.

Die erste vollständige Aufrufermigration ließ überladene Konstruktoren im Editor als vermeintliche
Kompatibilitätsgrenze stehen. Der negative Audit bewertete das gegen Plan und Abnahmekriterium als
Diskrepanz.

#### Korrekturrunde 1 – kanonischer Editor-Draft

Plan: Alle zwölf Editor-Konstruktoren durch einen vollständigen Konstruktor aus Kadenz,
`StepPrescription`, optionaler Policy und Aktivierungsart ersetzen. Produktions-, Hosttest- und
Instrumentierungsaufrufer werden auf diesen Vertrag oder benannte Fabriken migriert. Die für die
bisherige UI nötige `TrainingAssistantConfig` bleibt nur eine abgeleitete, nicht unabhängig
gespeicherte Projektion.

Ergebnis: Der Editor besitzt genau einen öffentlichen Konstruktor. Die fokussierte
Testkompilierung fand zunächst noch fünf Host-/Kotlin-Fixtures und danach zwei
Instrumentierungs-Fixtures mit alter Kurzsignatur; alle wurden auf die gruppierte Verordnung
umgestellt.

#### Korrekturrunde 2 – tolerantes Nicht-Satz-Decoding

Die fokussierte Matrix fand, dass ein historisches Bundle ohne Pausenschlüssel für einen
Nicht-Satz-Schritt als `INHERIT` rekonstruiert wurde und damit die strengere Verordnung verletzte.
Plan: Nur an der Saved-State-Grenze Pause und Trainingsprofil passend zur Amount-Art
normalisieren; die gespeicherten Schlüssel und regulären Modellinvarianten bleiben unverändert.

Ergebnis: Der historische Bundle-Fall wird wieder als Nicht-Satz-Schritt mit ausgeschalteter Pause
gelesen. Die fokussierte Matrix aus 33 Modell-, Editor-, Materialisierungs- und Flow-Tests ist
anschließend grün.

### Vollständige Validierung

- `git diff --check`: grün. CI-Harness: 17 Tests grün; Release-/Workflowverträge: 23 Tests grün.
- Vollständige Host-/Robolectric-Suite: 495 Tests, null Fehler, ein bewusster Benchmark-Skip.
- Das erste vollständige Gate vor dem negativen Audit war mit Hosttests, Lint sowie Debug-,
  Instrumentierungs- und Release-Paketierung in 14:52 Minuten grün.
- Nach der Auditkorrektur liefen Hosttests erneut grün. Der anschließende Paketierungsteil fand die
  zwei genannten Instrumentierungs-Fixtures; nach deren Migration war das vollständige Gate
  `testInstrumentationUnitTest lintDebug assembleDebug assembleInstrumentationAndroidTest
  assembleRelease` in 8:07 Minuten grün (157 Tasks; unveränderte Hostresultate wurden aus dem
  unmittelbar vorangegangenen erfolgreichen Lauf wiederverwendet).
- Artefakte: Debug 5.571.045 Byte, Instrumentierung 1.646.391 Byte, unsigned Release 2.775.076
  Byte; die bestehenden Budgets werden eingehalten.

### Roadmap- und Phasenaudit

Der Abschlussabgleich findet in jeder der vier ausführbaren Schrittrepräsentationen denselben
`StepPrescription`-Typ und insgesamt genau vier vollständige öffentliche Modellkonstruktoren.
Nur `TaskStepTemplate` besitzt ein `TrainingAssistantProfile`; Occurrence und Flow-Snapshot tragen
keinen Lernzustand. Der Kopiertest belegt, dass spätere Vorlagenänderungen bestehende
Materialisierungen nicht verändern. Der Editor besitzt genau einen vollständigen Konstruktor und
einen getrennten Codec. Es gibt keine Änderung an Entity-, Migrations-, Schema-, Ressourcen- oder
Golden-Dateien.

Die noch vorhandenen Increment-Felder und Defaultwerte liegen ausschließlich im ausdrücklich bis
Phase 3 befristeten Legacy-Adapter; `TrainingAssistantPolicy` enthält keine Schrittweite. Damit
bleibt das Progressionsverhalten in Phase 1 bewusst unverändert und die Beseitigung versteckter
Lastsprünge an der dafür vorgesehenen Entscheidungsphase. Lokal bleibt keine Phase-1-Diskrepanz.
Offen sind der eigene grüne Pull Request und Squash-Merge nach `main`; als nicht sichtbare
Architekturphase benötigt Phase 1 keine physische Geräteabnahme.

### Remote-Abschluss

- Pull Request #300 prüfte Commit `8ba8f648` gegen Phase-0-Squash `c1e72ba4`.
- `quality`, normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie
  `instrumentation-gate` und `pull-request-gate` waren grün. Der längste Lauf war die
  animationsaktive API-35-Variante mit 12:24 Minuten.
- Der Pull Request wurde am 2026-08-31 per Squash als `22a9ac17` nach `main` gemergt;
  `origin/main` und der isolierte Roadmap-Worktree zeigten anschließend denselben Commit.

## Phase 2 – Ein atomisches Satzresultat

### Vorprüfung

- Ausgangspunkt ist der verifizierte Phase-1-Squash `22a9ac17` auf `origin/main`; gearbeitet wird
  auf `feat/training-cleanup-p2-set-result` im isolierten Roadmap-Worktree.
- `RepetitionProgress` hält bisher nur `List<Integer>`. Detaillierte Last-, RIR-, Herkunfts- und
  Sicherheitswerte liegen separat in `TrainingSetResult` und werden über eine zweite
  `TrainingRepository.trainingSetResults`-Abfrage geladen.
- `RecordTrainingSetResult` und `CorrectTrainingSetResult` ändern zuerst den Occurrence-Schritt
  über `StepExecutionService` und schreiben anschließend denselben Slot separat in
  `repetition_results`. Die äußere Repository-Transaktion umfasst zwar beide Pfade, das Modell
  besitzt aber weiterhin zwei Ergebniswahrheiten.
- Die bestehende Room-Tabelle `repetition_results` enthält bereits Wiederholungen, Last, RIR,
  Herkunft und Safety. Phase 2 benötigt deshalb keine Schema- oder Migrationsänderung.

### Implementationsplan

- `SetResult(repetitions, optional TrainingObservation)` als einziges Satzresultat einführen.
  `TrainingObservation` bündelt Last, optionales RIR, Safety und Herkunft. Alte Zeilen werden als
  Beobachtung mit Herkunft `LEGACY` rekonstruiert, auch wenn ihre Last nicht spezifiziert ist.
- `RepetitionProgress` auf `List<SetResult>` als Wahrheit umstellen; Wiederholungswerte bleiben
  ausschließlich eine daraus berechnete, unveränderliche Projektion. Record, Correct, Reopen und
  Completion arbeiten auf vollständigen Resultaten.
- `OccurrenceStep` trägt und mutiert vollständige Resultate. Room lädt die vorhandenen
  `repetition_results`-Zeilen einmal zusammen mit dem Schritt und schreibt bei der
  Occurrence-Aktualisierung alle Felder des geänderten Slots. Die separaten Resultatmethoden
  entfallen aus `TrainingRepository`.
- `RecordSetResult` und `CorrectSetResult` ersetzen die trainingsspezifischen Alt-Use-Cases. Eine
  gemeinsame Transaktion umfasst Ergebnis, Completion, Rewards, Flow-/Combo-Folgen und
  Adaptionsfolgen. `TrainingAdaptationService` übergibt die Resultate direkt aus dem bereits
  geladenen Occurrence-Schritt an die Engine; eine zweite Ergebnisabfrage entfällt.
- Generische Wiederholungseingaben und „mit Planwert fortfahren“ erzeugen `SetResult` ohne
  Trainingsbeobachtung. Today erzeugt für Trainingssätze eine vollständige Beobachtung, ohne das
  sichtbare Verhalten dieser Phase zu verändern.
- Modell-, Room-Kompatibilitäts-, Execution-, Reward-, Adaptions- und Rollbacktests fokussiert
  ausführen, danach vollständiges lokales Gate. Der negative Audit prüft insbesondere auf
  verbliebene `TrainingSetResult`- oder separate Resultatabfragen sowie Schemaänderungen.

### Abnahmekriterien

- `RepetitionProgress.results` ist die einzige Ergebniswahrheit; Wiederholungslisten sind nur
  Projektionen daraus.
- Aufnahme und Korrektur persistieren vollständiges Resultat, Abschluss, Reward und Adaptionsfolge
  in derselben Transaktion; ein erzwungener später Fehler rollt alles zurück.
- Die Engine erhält Resultate aus `OccurrenceStep` und fragt sie nicht separat im Repository ab.
- Bestehende detaillierte sowie reine Wiederholungszeilen bleiben ohne Room-Schemaänderung lesbar.

### Implementierung

- `SetResult` enthält Wiederholungen und optional genau eine `TrainingObservation` aus Last, RIR,
  Safety und Herkunft. `TrainingSetResult` wurde entfernt.
- `RepetitionProgress.results` ist die gespeicherte Modellwahrheit. Die bestehende
  `actualRepetitions`-API ist eine beim Erzeugen abgeleitete, unveränderliche Projektion; Record,
  Correct und Reopen kopieren vollständige Resultate.
- `OccurrenceStep` nimmt vollständige Resultate auf. `RoomTaskRepository` rekonstruiert sie beim
  Laden aus den bestehenden `repetition_results`-Spalten und schreibt bei der normalen
  Schrittaktualisierung alle Werte eines Slots. Reine Alt-Wiederholungszeilen werden als
  `SetResult` ohne künstliche Trainingsbeobachtung gelesen.
- `RecordSetResult` und `CorrectSetResult` besitzen die äußere Transaktionsgrenze. Ihre
  `StepExecutionService`-Operationen laufen innerhalb dieser Grenze ohne zweite Use-Case-
  Transaktion; Completion, Reward, Flow-/Combo-Folgen und Adaptionsänderungen gehören damit zum
  selben Commit beziehungsweise Rollback.
- `TrainingAdaptationService` übergibt `step.repetitionProgress.results` direkt an die Engine.
  `TrainingRepository.trainingSetResults` und `putTrainingSetResult` sowie der separate
  In-Memory-Ergebnisspeicher wurden entfernt.

### Validierung und Audit

- Fokussierte Matrix aus RepetitionProgress, Engine, Room-Kompatibilität, Task-Execution und
  Transaktionsrollback: grün in 59 Sekunden.
- Der Rollbacktest erzwingt einen Fehler beim letzten Adaptionsschreibvorgang und belegt, dass
  zweites Satzresultat, Step-Completion, Reward, Vorlagenänderung und Adjustment gemeinsam nicht
  bestehen bleiben.
- `git diff --check`: grün. CI-Harness: 17 Tests grün; Release-/Workflowverträge: 23 Tests grün.
- Vollständiges Gate `testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease`: grün in 12:38 Minuten, 157 Tasks.
  498 Host-/Robolectric-Tests liefen mit null Fehlern und einem bewussten Benchmark-Skip.
- Artefakte: Debug 5.571.045 Byte, Instrumentierung 1.646.391 Byte, unsigned Release 2.775.076
  Byte; die bestehenden Budgets werden eingehalten.
- Der negative Abgleich findet keinen `TrainingSetResult`, keinen alten Record-/Correct-Use-Case
  und keine separate Resultatabfrage oder -schreibmethode. Vollständige Ergebnisse werden aus dem
  Occurrence-Schritt gelesen; Wiederholungslisten sind ausschließlich Projektionen.
- Entity-, Migrations-, Schema-, Ressourcen- und Golden-Dateien sind unverändert. Detaillierte
  Zeilen roundtrippen Last, RIR, Safety und Herkunft; reine Wiederholungszeilen bleiben lesbar.
  Lokal bleibt keine Phase-2-Diskrepanz. Offen sind der eigene grüne Pull Request und Squash-Merge
  nach `main`; als nicht sichtbare Architekturphase benötigt Phase 2 keine physische
  Geräteabnahme.

### Remote-Abschluss

- Pull Request #301 prüfte Commit `11f080e9` gegen Phase-1-Squash `22a9ac17`.
- `quality`, normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie
  `instrumentation-gate` und `pull-request-gate` waren grün. Der längste Lauf war die
  animationsaktive API-35-Variante mit 11:54 Minuten.
- Der Pull Request wurde am 2026-08-31 per Squash als `137c7b84` nach `main` gemergt;
  `origin/main` und der isolierte Roadmap-Worktree zeigten anschließend denselben Commit.

## Phase 3 – Korrekte Progression und persistente Lastentscheidung

### Vorprüfung

- Ausgangspunkt ist der verifizierte Phase-2-Squash `137c7b84` auf `origin/main`; gearbeitet wird
  auf `feat/training-cleanup-p3-load-decision` im isolierten Roadmap-Worktree.
- `DatabaseContract.VERSION` und das jüngste exportierte Schema sind 21. Die in der Roadmap
  beschlossene Regel „aktuelle main-Version plus eins“ ergibt deshalb Zielversion 22; die dortige
  Klammer „Ausgangsbasis Schema 22“ wird als Bezeichnung der neuen Phase-3-Schemabasis, nicht als
  zusätzlich zu überspringende Version verstanden.
- `TrainingAssistantConfig`, `TaskStepEntity`, Saved State und Engine enthalten noch eine
  angenommene Lastschrittweite. Die Engine wendet sie nach ausgeschöpfter Wiederholungsprogression
  unmittelbar an und kann damit keine real verfügbaren Gerätegewichte berücksichtigen.
- `training_adjustments` sortiert bei gleichem Datum nach ID und trägt keine Regelversion.
  Persistente Lastfragen existieren noch nicht. Manuelle Vorlagenänderungen und Satzkorrekturen
  können deshalb keine offene Entscheidung schließen.
- Die vorhandene Editor-UI aktiviert den Assistenten mit 20 kg und kann numerische Nullwerte
  erzeugen. Phase 3 ändert die Entscheidungslogik und Persistenz; die erklärende Inline-Bedienung
  und Lastfrage folgen sichtbar in Phase 4.

### Implementationsplan

- Alle Increment-Felder aus Domainkonfiguration, Editorprojektion und `task_steps` entfernen.
  Eine aktivierte Policy validiert bei numerischen Lastarten eine tatsächlich positive
  Ausgangslast; Körpergewicht bleibt wertlos numerisch korrekt.
- `TrainingDecision` mit `HOLD`, `APPLY`, `REQUEST_NEXT_LOAD`, `PAUSE`, stabilem Grund und
  `RULE_VERSION` einführen. Die Engine erhöht zunächst Wiederholungen; am oberen Rand fordert sie
  bei verstellbarer Last eine konkrete nächste Last an. Reines Körpergewicht geht direkt zur
  zulässigen Satzprogression. Safety und Volumengrenzen bleiben erhalten.
- `TrainingLoadRequest` als persistentes, eindeutig offenes Template-Ereignis mit Richtung,
  Ausgangslast, Status, Ergebnis, Audit-Reihenfolge und Regelversion einführen. Eine erneute
  abgeschlossene Einheit darf keine zweite offene Frage erzeugen.
- Einen fokussierten Resolve-Use-Case bereitstellen: Eine konkrete Last muss Modus und Einheit
  erhalten, für unterstütztes Körpergewicht in die umgekehrte Richtung laufen und höchstens zehn
  Prozent springen, um automatisch angewendet zu werden. Größere Sprünge bleiben offen und werden
  nur als manuelle Vorlagenänderung akzeptiert. „Kein höheres Gewicht“ prüft Satz- und
  Wochenvolumenprogression; „später“ lässt die Frage unverändert offen.
- Manuelle relevante Vorlagenänderungen, Satzkorrektur und Undo schließen offene Fragen und
  starten die Kalibrierung neu. Automatisch angewendete Entscheidungen erzeugen weiterhin einen
  auditierbaren Adjustment-Eintrag.
- Schema 22 baut `task_steps` ohne Increment-Spalte neu auf, ergänzt `training_load_requests` und
  erweitert `training_adjustments` um stabile `auditOrder` und `ruleVersion`. Room-Export,
  Migrationsgraph, Schema-8-Produktupgradefixture und gezielte 21→22-Tests werden aktualisiert.
- Engine-, Richtungs-, Zehn-Prozent-, Deduplizierungs-, Persistenz-, Restart-, Korrektur- und
  Upgrade-Tests fokussiert ausführen, danach vollständiges lokales Gate. Der negative Audit prüft
  auf Increments, implizite Lasten, mehrfache offene Fragen, instabile Auditordnung und
  unversionierte Entscheidungen.

### Abnahmekriterien

- Keine Produktionsklasse oder aktuelle Schemadefinition enthält eine angenommene
  Lastschrittweite; numerische Aktivierung mit fehlender oder null Last scheitert.
- Die Engine liefert versionierte Entscheidungen und erzeugt bei ausgeschöpfter
  Wiederholungsprogression genau eine persistente Lastfrage.
- Konkrete Last, „kein höheres Gewicht“ und „später“ sind deterministisch; Richtung und
  Zehn-Prozent-Grenze gelten auch für unterstütztes Körpergewicht korrekt.
- Recreation/Repository-Neuaufbau verliert offene Fragen nicht. Manuelle Laständerung und
  Satzkorrektur schließen sie und setzen die Kalibrierung zurück.
- Schema 21 und Produktionsfixture ab Schema 8 migrieren verlustfrei auf Schema 22; Auditordnung
  und Regelversion sind persistent.

### Implementierung

- `TrainingDecision` ist der versionierte Ausgang der reinen Engine. Wiederholungs- und
  Satzänderungen werden direkt angewendet; an der Lastgrenze entsteht stattdessen
  `REQUEST_NEXT_LOAD` mit Fortschritts- oder Regressionsrichtung. Reines Körpergewicht bleibt im
  Satzpfad, unterstütztes Körpergewicht verwendet bei konkreten Lasten die umgekehrte Richtung.
- `TrainingLoadRequest` persistiert eine offene Lastfrage mit Ausgangslast, Quelle,
  `auditOrder`, `ruleVersion`, Status und Auflösung. `TrainingAdaptationService` beendet die
  Auswertung, solange bereits eine Frage offen ist, sodass ein Template höchstens eine offene
  Entscheidung erhält.
- `ResolveTrainingLoadRequest` übernimmt nur eine positive konkrete Last desselben Modus und
  derselben Einheit in der erwarteten Richtung. Der relative Sprung darf zehn Prozent nicht
  überschreiten. Größere Sprünge und falsche Richtungen lassen die Frage offen; „später“ ist eine
  reine, persistenzneutrale Entscheidung. „Kein höheres Gewicht“ löst die Frage und prüft danach
  dieselben Satz- und Wochenvolumengrenzen wie die Engine.
- Automatische Änderungen erzeugen `TrainingAdjustment` mit stabiler Reihenfolge und
  Regelversion. Manuelle relevante Vorlagenänderungen, Satzkorrektur und Undo schließen eine
  offene Frage mit eigenem Auflösungsgrund und setzen den Lernzustand auf Kalibrierung zurück.
- Alle produktiven Increment-Felder und die unbenutzten Increment-Hilfsmethoden wurden entfernt.
  Die Editoraktivierung setzt keine verborgenen 20 kg mehr. Ein verstellbarer numerischer Modus
  benötigt vor dem Speichern einen positiven Wert.
- Schema 22 baut `task_steps` ohne Increment-Spalte und unter Erhalt von Transitions,
  Ressourcenleases und Adjustment-Zeilen neu auf. Es ergänzt `training_load_requests` sowie
  `auditOrder` und `ruleVersion` auf Adjustments. Der zentrale Vertrag, der Room-Export und das
  Schema-8-Produktupgradefixture zielen gemeinsam auf 22.

### Validierung und Audit

- Die fokussierte Matrix aus Engine, Resolve-Use-Case, Exactly-once-/Korrekturtransaktion,
  Room-Neuaufbau und vollständigem Migrationsgraph war grün. Der 21→22-Datentest bewahrt
  `task_steps`, Transitions, Ressourcenleases und historische Adjustments und belegt die entfernte
  Increment-Spalte. Ein dateibasierter Room-Test schließt und öffnet die Datenbank neu und liest
  dieselbe offene Lastfrage samt Richtung, Last und Regelversion.
- `git diff --check`: grün. CI-Harness: 17 Tests grün; Release-/Workflowverträge: 23 Tests grün.
- Das erste vollständige Gate war in 15:19 Minuten grün. Nach der Auditkorrektur lief das
  endgültige Gate `testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease` in 15:37 Minuten grün: 157 Tasks,
  505 Host-/Robolectric-Tests, null Fehler, Lint und alle drei Paketierungen erfolgreich.
- Artefakte: Debug 9.786.713 Byte, Instrumentierung 1.646.490 Byte, unsigned Release
  2.775.076 Byte; alle bestehenden Budgets werden eingehalten.

#### Korrekturrunde 1 – ungültige Ausgangslast im Editor

Der negative Abgleich fand, dass das Entfernen des 20-kg-Defaults zwar die Domaininvariante
erfüllte, ein numerischer Nullwert aber erst beim Erzeugen der Definition scheitern konnte. Das
hätte einen ungültigen Editorzustand ohne vorherige Validierungsrückmeldung zugelassen.

Plan: Die vorhandene schrittspezifische Validierungsgrenze blockiert eine aktivierte verstellbare
Last ohne positiven Wert. Ein fokussierter Test erzeugt genau diesen Draft und erwartet einen
Schrittfehler. Unabhängig davon werden die nun unbenutzten generischen Increment-Hilfsmethoden aus
`ResistanceLoad` entfernt. Danach werden Fokusmatrix und vollständiges Gate wiederholt.

Ergebnis: Der fokussierte Validator-/Entscheidungslauf und das vollständige endgültige Gate sind
grün. Im aktuellen Produktionscode gibt es weder einen numerischen Lastdefault noch eine
Increment-API; der einzige verbleibende Spaltenname liegt absichtlich in der historischen
20→21-Migration und im unveränderlichen Schema-21-Export.

### Roadmap- und Phasenaudit

Der requirementweise Abschlussabgleich findet genau eine Ergebniswahrheit aus Phase 2 und genau
eine persistente offene Lastfrage pro Template. Die Engine nimmt keine Geräteschrittweite an;
konkrete Lasten validieren Positivität, Modus, Einheit, Richtung und Zehn-Prozent-Grenze.
Körpergewicht, unterstütztes Körpergewicht, Volumengrenze, Safety-Pause, „kein höheres Gewicht“
und „später“ besitzen deterministische Pfade. Recreation und Datenbank-Neuaufbau erhalten offene
Fragen. Manuelle Änderung, Korrektur und Undo schließen sie und kalibrieren neu. Schema 8 und 21
besitzen einen grünen Pfad zu Schema 22; historische Flow- und Auditdaten bleiben erhalten.

Die Auditordnung ist unabhängig vom Datum monoton und jede Entscheidung trägt ihre Regelversion.
Aktuelle Produktionsklassen und Schema 22 enthalten keine Increment-Annahme; nur der notwendige
historische 20→21-Aufbau dokumentiert die alte Spalte. Die Korrekturrunde ist geschlossen. Lokal
bleibt keine Phase-3-Diskrepanz. Offen sind nun der eigene grüne Pull Request und Squash-Merge nach
`main`; die sichtbare Erklärung und Bedienung der Lastfrage folgt bewusst in Phase 4.

### Remote-Abschluss

- Pull Request #302 prüfte den Phase-3-Commit gegen den Phase-2-Squash `137c7b84`.
- `quality`, normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie
  `instrumentation-gate` und `pull-request-gate` waren grün. Der längste Lauf war die
  animationsaktive API-35-Variante mit 12:05 Minuten.
- Der Pull Request wurde am 2026-08-31 per Squash als `77afd26d` nach `main` gemergt;
  `origin/main` und der isolierte Roadmap-Worktree zeigten vor Phase 4 exakt diesen Commit.

## Phase 4 – Erklärbare und reversible Bedienung

### Vorprüfung

- Ausgangspunkt ist der verifizierte Phase-3-Squash `77afd26d` auf `origin/main`; gearbeitet wird
  auf `feat/training-cleanup-p4-explainable-ui` im isolierten Roadmap-Worktree.
- Today erfasst Last, RIR und Sicherheitsmarker bereits gemeinsam im `RepetitionInput`, projiziert
  aber weder Assistentenzustand noch persistente Lastfrage, Begründung, Verlauf oder Undo.
- Lastfragen und Anpassungen besitzen seit Phase 3 persistente, monotone `auditOrder`-Werte und
  fokussierte Resolve-/Undo-Use-Cases. Es fehlt eine gemeinsame, auf zehn Einträge begrenzte
  Leseprojektion und die UI-Aktion darf Undo nur für das neueste noch aktuelle Ereignis anbieten.
- Der Editor besitzt Opt-in und Lastfelder. Er zeigt den vorhandenen Lernzustand nicht; außerdem
  meldet die Validierung einer fehlenden numerischen Ausgangslast bislang nur einen generischen
  Schrittmengenfehler statt einer direkt zugeordneten Lastmeldung.
- Phase 4 ändert kein Datenbankschema. Persistenz- und Neustartverhalten werden über die bereits
  versionierten Phase-3-Tabellen und neue Leseprojektion abgesichert.

### Implementationsplan

- Eine fokussierte Trainingskontext-Abfrage führt Zustand, offene Lastfrage, letzte Anpassung und
  die letzten zehn auditgeordneten Entscheidungen einschließlich Undo zusammen. `canUndo` gilt
  nur, wenn die neueste Entscheidung eine angewendete Anpassung ist und ihr Nachzustand noch der
  aktuellen Vorlage entspricht.
- Die Dashboard-/Today-Projektion ordnet diesen Kontext über die Template-ID der jeweiligen
  Übung zu. Resolve, „kein höheres Gewicht“, „später“ und Undo laufen über bestehende Use Cases
  und laden anschließend denselben persistenten Kontext neu.
- Die Today-Zeile zeigt Zustand und letzte Anpassung direkt, eine offene Frage mit expliziter
  Lastangabe und allen drei Antworten sowie einen kompakten Verlauf mit bis zu zehn lokalisierten
  Einträgen. Saved-State-/Recreation-Tests sichern den gerade eingegebenen Lastwert; Repository-
  Neuaufbau und Screenwechsel lesen die offene Frage erneut aus der Datenbank.
- Der Editor zeigt für aktivierte Schritte `Kalibriert x/3`, `Aktiv` oder `Pausiert` und ordnet
  eine fehlende positive Startlast einem eigenen Lastfeldfehler zu. Alle Zustände, Gründe,
  Entscheidungen und Fehlermeldungen werden lokalisiert.
- Nach fokussierten Domain-, ViewModel-, Recreation-, UI- und Golden-Tests folgt das vollständige
  lokale Gate, der negative Phasenaudit und der vorgeschriebene PR-/Squash-Merge-Gate. Die
  Installation des exakt gemergten Artefakts und die physische Abnahme werden separat belegt;
  fehlende Geräteverbindung ersetzt diesen Nachweis nicht.

### Implementierung

- `LoadTrainingContext` liest den Vorlagenzustand, die offene Lastfrage sowie Anpassungen und
  Lastentscheidungen in einer Transaktion. Beide Ereignisarten werden anhand der persistenten
  `auditOrder` zusammengeführt, absteigend sortiert und gemeinsam auf zehn Einträge begrenzt.
  `canUndo` ist nur wahr, wenn das neueste Ereignis eine angewendete Anpassung ist und Satz-/Last-
  Nachzustand noch exakt der Vorlage entsprechen.
- `LoadDashboard` projiziert diesen Kontext einmal je sichtbarer Template-ID. Der UI-Mapper
  lokalisiert Lernzustand, alle Entscheidungsgründe, Lastfragen, Auflösungen und Änderungen; Today
  erhält ausschließlich das begrenzte `TrainingContextUiModel`.
- Jede Assistentenübung zeigt Zustand und letzte Anpassung direkt unter ihrer Today-Zeile. Die
  aktive offene Lastfrage enthält ein frei eingebbares konkretes Gewicht, `Anwenden`, bei
  Progression `Kein höheres Gewicht` und `Später`. Eine gerade abgeschlossene Übung mit offener
  Frage bleibt dafür sichtbar; der Verlauf zeigt bis zu zehn Einträge einschließlich Undo.
- Die Aktionen verwenden ausschließlich `ResolveTrainingLoadRequest` und
  `UndoLatestTrainingAdjustment`. Richtung, Einheit, Positivität und Zehn-Prozent-Grenze werden
  nicht in der UI dupliziert. `Später` schließt die lokale Detailansicht, lässt die persistente
  Frage unverändert und zeigt sie bei einem neuen Bind wieder.
- Der Editor bewahrt den persistierten Lernzustand durch Edit- und Saved-State-Roundtrips und
  zeigt `Kalibriert x/3`, `Aktiv` oder `Pausiert`. Eine fehlende positive Ausgangslast besitzt mit
  `TRAINING_LOAD` ein eigenes Validierungsfeld und eine direkt am Lastfeld sichtbare Meldung.
- Ein neuer visueller Golden-Vertrag deckt den dichten Trainingskontext ab. Der Snapshot wurde
  vor der Aufnahme als Baseline visuell geprüft und anschließend read-only pixelgenau verglichen.
  Ein Geräteinstrumentierungstest deckt Lernzustand und dedizierten Lastfehler im Compose-Editor ab.

### Validierung und Audit

- Fokussierte Verträge belegen die gemeinsame Auditordnung und Begrenzung, eine neuere Lastfrage
  als Undo-Sperre, Exactly-once-Undo, lokalisierte Dashboard-Projektion, konkrete Inline-Eingabe,
  Editor-State-Restoration sowie Datenbank- und Repository-Neuaufbau mit weiterhin offener Frage.
- Der visuell geprüfte Golden-Snapshot `training-assistant-question.png` bestand danach einen
  read-only Vergleich. Das ergänzte Android-Instrumentierungsszenario wurde erfolgreich in das
  Instrumentierungs-APK kompiliert.
- `git diff --check`: grün. CI-Harness: 17 Tests grün; Release-/Workflowverträge: 23 Tests grün.
- Das vollständige Gate `testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease` war in 14:46 Minuten grün: 157 Tasks,
  Lint ohne Fehler und alle drei Paketierungen erfolgreich. Nach dem Audit lief die vollständige
  Host-/Robolectric-Suite mit dem ergänzten Undo-Vertrag erneut in 6:13 Minuten grün: 512 Tests,
  null Fehler, ein bewusst übersprungener Test.
- Artefakte: Debug 9.798.267 Byte, Instrumentierung 1.647.423 Byte, unsigned Release
  2.810.784 Byte; alle bestehenden Budgets werden eingehalten.
- Der negative Abgleich findet keine nicht lokalisierte `TrainingDecision.Reason`, keine zweite
  UI-Regel für zulässige Lasten und keinen Undo-Pfad ohne neuesten aktuellen Nachzustand. Die
  offene Frage wird aus Room statt aus View-State rekonstruiert und bleibt daher bei Recreation,
  Repository-Neuaufbau und Screen-Rebind erhalten.

### Roadmap- und Phasenaudit

Editor-Opt-in, erforderliche Startlast und alle drei Lernzustände sind sichtbar. Today erfasst
Last, RIR und Safety weiterhin in einem `SetResult` und ergänzt nun Status, letzte Anpassung,
persistente Lastfrage und den zehn Einträge umfassenden Verlauf direkt an der Übung. Konkrete
Last, `Kein höheres Gewicht`, `Später` und Undo besitzen geschlossene, getestete Aktionspfade.
Undo ist genau einmal und nur für die neueste noch aktuelle Anpassung verfügbar. Sämtliche
Status-, Grund- und Auflösungstexte stammen aus lokalisierten Ressourcen.

Lokal bleibt keine Phase-4-Code- oder Testdiskrepanz. Offen sind der eigene grüne Pull Request und
Squash-Merge nach `main`. Die physische Abnahme ist weiterhin separat offen: Der außerhalb der
Sandbox ausgeführte SDK-Befehl `adb devices -l` lieferte am 2026-08-31 keine verbundenen Geräte.
Das exakt gemergte Artefakt kann deshalb erst nach dem Remote-Gate und bei vorhandener
Geräteverbindung installiert und vom Owner abgenommen werden.

### Remote-Abschluss

- Pull Request #303 prüfte den Phase-4-Commit gegen den Phase-3-Squash `77afd26d`.
- `quality`, normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie
  `instrumentation-gate` und `pull-request-gate` waren grün.
- Der Pull Request wurde am 2026-08-31 per Squash als `693c94f4` nach `main` gemergt;
  `origin/main` und der isolierte Roadmap-Worktree zeigten vor Phase 5 exakt diesen Commit.

## Phase 5 – Composition Root, Ports und Abschlussaudit

### Vorprüfung

- Ausgangspunkt ist der verifizierte Phase-4-Squash `693c94f4` auf `origin/main`; gearbeitet wird
  auf `feat/training-cleanup-p5-focused-ports` im isolierten Roadmap-Worktree.
- `TaskUseCases` ist noch das gemeinsame, breite Bundle für Katalog, Today, Flows und Training.
  `AppContainer`, beide Flow-ViewModels, Editor, Today, Widget-Aktionen und All-Tasks greifen darauf
  zu. Die UI-Abhängigkeiten sind fachlich eindeutig, aber noch nicht als vier Verträge sichtbar.
- `ApplicationTaskRepository` erbt alle fokussierten Ports. Diese Ports erben ihrerseits
  `TransactionalRepository`, sodass jeder kleine Fachvertrag ungewollt auch
  Transaktionsausführung verspricht. Zahlreiche Use Cases rufen die Transaktion deshalb auf
  demselben Objekt statt über einen getrennt injizierten Runner auf.
- `RoomTaskRepository` enthält weiterhin Vorlagen-/Occurrence-Schritt- und Trainingspersistenz.
  Der vorhandene breite In-Memory-Speicher ist dadurch auch für Trainings-Slice-Tests die
  bequeme Standardabhängigkeit. Architekturkarte und ausführbare Regeln beschreiben diesen
  Zwischenstand statt des in ADR-028 beschlossenen Zielbilds.
- Phase 5 ändert weder Datenbankschema noch Ressourcen oder Goldens. Der persistierte Vertrag
  bleibt Schema 22; die Phase schneidet ausschließlich Composition-, Port-, Adapter- und
  Testgrenzen.

### Implementationsplan

- Einen eigenständigen `TransactionRunner` einführen und aus allen fokussierten Repository-Ports
  entfernen. Transaktionale Use Cases erhalten Fachport und Runner getrennt; die ersetzten
  impliziten Konstruktoren und `TransactionalRepository` werden entfernt.
- `TaskUseCases` durch vier reine, fokussierte Zusammenstellungen `CatalogUseCases`,
  `TodayUseCases`, `FlowUseCases` und `TrainingUseCases` ersetzen. `AppContainer` verdrahtet
  konkrete Use Cases aus kleinen Ports und genau einem Runner; ViewModels, Worker, Widgets und
  Presenter erhalten nur ihr fachliches Bundle beziehungsweise einzelne Use Cases.
- Trainingspersistenz und die Schrittoperationen aus `RoomTaskRepository` in
  `RoomTrainingRepository` und `RoomStepRepository` verschieben. Der konkrete Room-Gateway darf
  verbleibende Ports bündeln, ist aber weder Domainvertrag noch Quelle der extrahierten SQL-/DAO-
  Abbildung. Ein `RoomTransactionRunner` besitzt allein die Room-Transaktionsgrenze.
- Kleine Trainings- und Schritt-Testdoubles bereitstellen und die entsprechenden Slice-Tests vom
  breiten `InMemoryExecutionRepository` lösen. Breite Abnahme- und End-to-end-Tests dürfen den
  realistischen Gesamtspeicher weiterhin bewusst verwenden.
- Architekturkarte und ADR-Index auf Schema 22 sowie die fokussierte Composition aktualisieren;
  einen Abschluss-ADR für die erreichte Adaptergrenze ergänzen. Ausführbare Architekturregeln
  verbieten den alten Domainvertrag, das breite Bundle, transaktionserbende Fachports und breite
  Testdoubles in fokussierten Trainings-/Schritt-Slices.
- Zuerst Kompilation und fokussierte Domain-/Room-/Architekturtests, dann das vollständige lokale
  Gate aus Host/Robolectric, Lint, Debug-, AndroidTest- und Release-Build ausführen. Anschließend
  erfolgt ein negativer Phase-5-Audit und der requirementweise Gesamtabgleich aller sechs Phasen.
  Erst ein eigener grüner PR und Squash-Merge schließen Phase 5; danach werden Main-Release,
  Produktionsupgrade und die verfügbare Geräteabnahme separat verifiziert.

### Abnahmekriterien

- Im Domainmodul existieren weder `ApplicationTaskRepository`, `TransactionalRepository` noch
  `TaskUseCases`; produktive Consumers verwenden ausschließlich die vier fokussierten Bündel.
- Jeder transaktionale Use Case erhält einen `TransactionRunner` getrennt von seinen kleinen
  Fachports. Kein fokussierter Port erbt Transaktionsverhalten.
- Training und Schritt-Persistenz liegen in eigenen Room-Adaptern; Schema, Migrationen, Entities,
  Ressourcen und Goldens bleiben bytegenau unverändert.
- Fokussierte Slice-Tests benötigen keinen Speicher, der sämtliche App-Ports implementiert.
- Architekturregeln und Dokumentation bilden das tatsächliche Zielbild ab. Alle dauerhaften
  Abnahmeanforderungen bleiben lokal und anschließend in CI grün.

#### Korrekturrunde 1 – auch Mehrfachports explizit injizieren

Der erste negative Audit fand nach grüner Kompilation, fokussierter Matrix und vollständigem
lokalem Gate noch eine strukturelle Diskrepanz: `TransactionRunner` war zwar getrennt, mehrere
Ausführungs-Wrapper verwendeten aber weiterhin generische Intersection-Typen und verlangten damit
ein einziges Objekt, das Occurrence- und Reward-Port beziehungsweise Materialisierung und
Obligationen gemeinsam implementiert. Das hätte die breite konkrete Speicherform weiterhin in
kleine Use-Case-Konstruktoren durchsickern lassen.

Plan: Step- und Completion-Services sowie ihre Wrapper erhalten Occurrence-, Reward- und Runner-
Ports als einzelne Parameter. Combo-Decay, Materialisierung und Tagesabschluss trennen ihre
jeweiligen Ports ebenfalls. Der Flow-Koordinator erhält Occurrence-, Definitions- und Run-Port
getrennt; der daraus überflüssige `FlowExecutionRepository`-Verbund entfällt. Test- und
Produktionswiring werden explizit angepasst. Danach werden Architekturregeln, Fokusmatrix,
vollständige Hostsuite und das exakte lokale Gate erneut ausgeführt; nur der zweite Lauf gilt als
abschließender Nachweis.

Die Umsetzung entfernte zusätzlich zwei beim erweiterten negativen Scan gefundene Laufzeit-
Sondierungen: Combo-Pflichten wurden zuvor aus dem Occurrence-Port und Flow-Laufzeitdaten aus dem
Dashboard-Port per `instanceof` erraten. `StepExecutionService`,
`OccurrenceCompletionService`, `LoadDashboard`, `MaterializeDueOccurrences` und
`FlowRuntimeCoordinator` erhalten nun jeden benötigten Capability-Port explizit. Damit entfiel
auch `FlowExecutionRepository`; `TaskStore` bleibt ausschließlich das konkrete infrastrukturelle
Wiring-Detail. Die ausführbare Architekturregel verbietet künftig Repository-Capability-Probes.

### Implementierung

- `ApplicationTaskRepository`, `TransactionalRepository`, `FlowExecutionRepository` und
  `TaskUseCases` sind entfernt. `CatalogUseCases`, `TodayUseCases`, `FlowUseCases` und
  `TrainingUseCases` sind reine, persistenzfreie Feldbündel; die konkrete
  `ApplicationUseCaseComposition` verdrahtet sie aus kleinen Ports und einem separaten
  `TransactionRunner`.
- Alle transaktionalen Use Cases erhalten den Runner getrennt. Occurrence-, Reward-, Combo-,
  Materialisierungs-, Flow-Definitions-, Flow-Run-, Training-, Schedule- und Step-Ports werden
  einzeln übergeben; weder Intersection-Typen noch `instanceof`-Sondierungen verbinden sie wieder
  implizit.
- `RoomTransactionRunner` besitzt die Transaktionsgrenze. `RoomStepRepository` enthält Vorlagen-,
  Occurrence-Schritt- und Satzresultatabbildung; `RoomTrainingRepository` enthält Trainingsprofil,
  Lastfragen und Auditspur. `RoomTaskRepository` delegiert diese Fähigkeiten und enthält deren
  Entities oder Mapping nicht mehr selbst.
- Schedule- und Step-Porttests behalten ihre kleinen lokalen Doubles. Die drei fokussierten
  Trainings-Use-Case-Tests verwenden `InMemoryTrainingRepository`; der breite
  `InMemoryExecutionRepository` bleibt nur für bewusst sliceübergreifende Abnahmeszenarien.
- ADR-029, Architekturkarte, ADR-Index und ausführbare Architekturregeln bilden den erreichten
  Stand ab. Schema 22, Migrationen, Entities, Ressourcen und visuelle Goldens wurden nicht
  verändert.

### Validierung und negativer Audit

- Produktions- und Instrumentierungs-Testkompilation sowie die fokussierte Matrix aus
  Architektur-, Satztransaktions-, Training-, Flow-, Today-, Schedule- und Step-Verträgen sind
  nach der Korrekturrunde grün.
- Die vollständige Host-/Robolectric-Suite lief in 5:09 Minuten grün: 514 Tests, null Failures,
  null Errors und ein bewusst übersprungener Test. CI-Harness: 17 Tests grün;
  Release-/Workflowverträge: 23 Tests grün.
- Das exakte Gate `testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease` war in 14:37 Minuten grün: 157 Tasks,
  Lint ohne Fehler und alle Paketierungen erfolgreich. Artefakte: Debug 9.801.528 Byte,
  Instrumentierung 1.647.423 Byte, unsigned Release 2.810.784 Byte.
- `git diff --check` ist grün. Der negative Quellscan findet keinen entfernten Sammelvertrag,
  keine Repository-Intersection, keine Capability-Sondierung und keinen impliziten
  Repository-Transaktionsaufruf. Der Diff gegen den Phase-4-Squash enthält keine Schema-,
  Migrations-, Entity-, Ressourcen- oder Golden-Datei.
- Der erneute SDK-ADB-Check am 2026-08-31 meldete kein verbundenes Gerät. Die physische
  Installation und Sicht-/Update-Abnahme bleibt deshalb ausdrücklich offen und wird nicht durch
  Host-, Paket- oder spätere Emulatornachweise ersetzt.

### Requirementweiser Gesamtabgleich

- Die unveränderten Migrations- und Produktionsfixtures sichern Schema 21 und den garantierten
  Pfad ab Schema 8 bis Schema 22; vorhandene Occurrence- und Flow-Snapshots bleiben unverändert.
- `SetResult` bleibt die einzige Satzwahrheit. Aufnahme, Korrektur, Reopen, Completion, Reward und
  Adaptionsfolgen besitzen explizite Transaktionsgrenzen; die Fehlerverträge blieben in der
  vollständigen Suite grün.
- Kalibrierung nach drei Beobachtungen, Entscheidung erst nach zwei weiteren gleichartigen
  Signalen, Safety-Pause, Volumengrenze und Zehn-Prozent-Limit sind weiterhin durch die
  Phase-3-Verträge gedeckt und wurden durch Phase 5 nicht verändert.
- Pro Template existiert höchstens eine persistente offene Lastfrage. Konkrete Last,
  `Kein höheres Gewicht` und `Später`, Prozess-/Repository-Neuaufbau, stabile Auditordnung,
  zehn Einträge Verlauf sowie Exactly-once-Undo bei aktuellem Nachzustand blieben in der grünen
  Hostsuite abgedeckt.
- Lokal bleibt keine Code-, Architektur- oder Testdiskrepanz. Offen vor Roadmap-Abschluss sind
  der eigene grüne Phase-5-PR mit Squash-Merge, die anschließende Main-Veröffentlichung samt
  Produktionsupgrade und Instrumentierungsmatrix sowie mangels ADB-Ziel die physische Abnahme.

### Remote-Abschluss

- Pull Request #304 prüfte den Phase-5-Commit `9895a9d6` gegen den Phase-4-Squash `693c94f4`.
  `quality`, normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie
  `instrumentation-gate` und `pull-request-gate` waren grün. Der längste Lauf war die
  animationsaktive API-35-Variante mit 14:37 Minuten.
- Der Pull Request wurde am 2026-08-31 per Squash als `e91b8833` nach `main` gemergt;
  `origin/main`, der Phase-5-Tag und das veröffentlichte Release zeigen auf exakt diesen Commit.
- Der erste Main-Versuch scheiterte ausschließlich in beiden API-37-Jobs beim externen Download
  des Canary-Emulators: `sdkmanager --install emulator --channel=3` brach mit
  `java.io.IOException` ab, bevor Emulator oder Gradle-Test starteten. Derselbe Commit war im PR
  auf API 37 grün. Der gezielte Retry der fehlgeschlagenen Jobs war erfolgreich.
- Main-Lauf 33432201685, Versuch 2, bestand die vollständige Quality- und
  Instrumentierungsmatrix. Der einmalig gebaute und signierte Kandidat bestand anschließend den
  Produktionsupgradepfad auf API 26, 35 und 37; der Publish-Job war grün.
- Release 0.2.144 wurde als `forest-android-1014401` veröffentlicht. `AutoSecretary.apk` hat
  2.818.980 Byte und SHA-256
  `9b036f75a19f6ca55818702c742617863e11f4311b8af5483255caca4d985f88`;
  `release-metadata.json` ist vorhanden. Release, Tag und `origin/main` verwiesen beim Audit auf
  `e91b8833`.
- Damit sind Implementierung, PR-Gates, Main-Instrumentierung, Paketierung, Produktionsupgrade
  und Veröffentlichung der sechs Phasen abgeschlossen. Die in der Roadmap separat verlangte
  physische Installation und Sicht-/In-App-Update-Abnahme bleibt offen: Der letzte SDK-ADB-Check
  fand weiterhin kein verbundenes Gerät. Dieser externe Owner-Nachweis wird nicht als bestanden
  ausgegeben.

## Korrekturrunde – aktuelle Phasenstatus-Übersicht

### Festgestellte Diskrepanz

Der Remote-Abschluss dokumentiert die grünen Pull Requests und Squash-Merges der Phasen 3 bis 5,
die Statusübersicht am Dokumentanfang weist diese Phasen jedoch noch als `in Arbeit` oder
`ausstehend` aus. Damit widerspricht die aktuelle Übersicht der im selben Protokoll belegten
Implementierungs- und Mergehistorie. Die separat offene physische Abnahme von Phase 4 bleibt davon
unberührt.

### Fixplan

- Die Statusübersicht für Phase 3 bis 5 auf `abgeschlossen` setzen und die bereits belegten
  Pull-Request- und Squash-Referenzen übernehmen.
- Den Unterschied zwischen abgeschlossener Implementierungsphase und weiterhin ausstehender
  physischer Release-Abnahme direkt unter der Tabelle klarstellen.
- Markdown, Links, Diff und Dokumentations-Scope prüfen; anschließend den vorgeschriebenen eigenen
  Pull Request mit grünem Gate und Squash-Merge durchführen.

### Validierung und Audit

- GitHub weist PR #302, #303 und #304 als gemergt aus; ihre Merge-Commits stimmen exakt mit
  `77afd26d`, `693c94f4` und `e91b8833` überein. Die jeweiligen `pull-request-gate`-Checks waren
  erfolgreich.
- `git diff --check` ist grün. Der Tabellenvertrag findet für alle sechs Phasen den Status
  `abgeschlossen` samt Pull-Request- und Squash-Referenz; der Link auf die kanonische Roadmap zeigt
  weiterhin auf eine vorhandene Datei.
- Die vollständige CI-Harness-Suite lief mit 17 Tests ohne Fehler. Ihr Scope-Vertrag bestätigt,
  dass die reine Dokumentationskorrektur weder Produktbuild noch Release auslösen darf.
- Der negative Abgleich findet keine Änderung an der kanonischen Roadmap und keine Behauptung einer
  erfolgten Geräteabnahme. Die aktuelle Übersicht beschreibt ausschließlich den nachgewiesenen
  Implementierungsabschluss; der physische Owner-Nachweis bleibt ausdrücklich offen.

### Remote-Abschluss und Blockeraudit

- Die Statuskorrektur lief über den eigenen Pull Request #306. `release_scope`,
  `instrumentation-gate` und `pull-request-gate` waren grün; die für den reinen Dokumentationsdiff
  nicht erforderlichen Produkt-, Instrumentierungs-, Paket-, Upgrade- und Publish-Jobs wurden
  erwartungsgemäß übersprungen.
- PR #306 wurde am 2026-08-31 per Squash als `4f396041` nach `main` gemergt. Der anschließende
  Main-Lauf 33436739503 war für exakt diesen Commit grün; der isolierte Roadmap-Worktree und
  `origin/main` zeigten beim Abschlussaudit denselben Commit.
- USB-ADB und die lokale mDNS-Suche fanden erneut kein Gerät. Der offizielle Runner
  `run-device-acceptance.sh forest-android-1014401` validierte Release 0.2.144, dessen APK-Hash und
  die vorherige Produktionsversion 0.2.143, schrieb anschließend jedoch den Status `pending` mit
  dem Grund `Expected exactly one connected ADB device`.
- Damit ist keine weitere repository- oder remoteseitige Arbeit offen. Die Roadmap bleibt allein
  an der ausdrücklich verlangten physischen In-App-Update- und Sichtabnahme blockiert. Dafür muss
  genau ein entsperrtes und autorisiertes Gerät mit installierter Produktionsversion 0.2.143
  verbunden werden; ohne diesen externen Zustand dürfen Installationsversion, Datenerhalt und die
  manuellen UI-Prüfungen nicht als bestanden markiert werden.
