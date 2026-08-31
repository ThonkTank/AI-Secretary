# Ausführungsprotokoll: saubere Schritt- und Trainingsarchitektur

Die autoritative Zieldefinition steht in der
[Trainingsassistenten-Roadmap](training-assistant-cleanup-roadmap.md). Dieses Dokument bewahrt
Vorprüfungen, konkrete Phasenpläne, Nachweise, Audits und Korrekturrunden, ohne die ursprüngliche
Roadmap nachträglich umzuschreiben.

## Phasenstatus

| Phase | Status | Abschlussnachweis |
|---|---|---|
| 0 – Vertrag und Grundlage | abgeschlossen | PR #299, Squash `c1e72ba4` |
| 1 – Schrittverordnung | in Arbeit | – |
| 2 – Satzresultat | ausstehend | – |
| 3 – Lastentscheidung | ausstehend | – |
| 4 – Bedienung | ausstehend | – |
| 5 – Ports und Abschlussaudit | ausstehend | – |

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
