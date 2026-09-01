# Roadmap: saubere Schritt- und Trainingsarchitektur

Status: verbindlich

Beschlossen: 2026-08-29

Ausgangsstand: `521fafcb` (`origin/main`)

Ausgangsschema: Room 21

## Ziel und unveränderliche Grenzen

Der optionale Trainingsassistent wird in sechs einzeln mergebaren Phasen auf eine eindeutige
Schritt-, Satz- und Entscheidungsarchitektur gestellt. Die Bereinigung bleibt auf den gemeinsamen
Schritt-/Trainings-Slice begrenzt; sie ist keine Umschreibung der gesamten Task-Domain und kein
visuelles Redesign.

Der Assistent bleibt lokal, optional, deterministisch, erklärbar, reversibel und
sicherheitsbegrenzt. Er diagnostiziert nichts und trifft keine medizinischen Aussagen. Bereits
materialisierte Occurrences und Flow-Snapshots bleiben unveränderliche Planungsstände. Der
garantierte Produktionsupgradepfad beginnt weiterhin bei Schema 8.

Automatische Laständerungen dürfen niemals aus einer angenommenen Geräteschrittweite entstehen.
Wenn die Wiederholungsprogression ausgeschöpft ist, fragt die App nach der konkret nächsten
verfügbaren Last. Ein automatisch angewendeter Lastsprung bleibt auf zehn Prozent begrenzt.

## Verbindlicher Phasenablauf

Jede Phase beginnt vom aktuellen `origin/main` auf einem eigenen Branch
`feat/training-cleanup-p<Nummer>-<Thema>`. Vor Produktänderungen werden diese Roadmap, das getrennte
Ausführungsprotokoll, der aktuelle Projektstand und die betroffenen Tests erneut gelesen. Der
konkrete Plan wird vor seiner Umsetzung im Protokoll festgehalten.

Nach Implementation und Validierung folgt ein separater negativer Abgleich mit Phasenplan und
Gesamtroadmap. Gefundene Diskrepanzen erhalten vor ihrer Korrektur einen dokumentierten Fixplan.
Eine Phase ist erst nach grünem Pull Request und Squash-Merge nach `main` implementiert. Sichtbare
UI-Arbeit ist erst nach Installation des exakten Merge-Artefakts und Abnahme auf einem physischen
Gerät vollständig abgeschlossen; ohne Gerät bleibt dieser Status ausdrücklich offen.

## Zielarchitektur und Schnittstellen

- `StepPrescription` bündelt `StepAmount`, Pausenregel und eine optionale
  `TrainingPrescription(load, targetRir)`. Vorlage, Occurrence und Flow-Snapshot verwenden
  denselben Werttyp.
- Ein optionales `TrainingAssistantProfile(policy, state)` existiert ausschließlich auf der
  Vorlage. `TrainingAssistantPolicy` enthält Satz-/Wiederholungskorridor, Volumengrenze und
  Muskelzuordnung, aber keine angenommene Gewichtsschrittweite.
- `RepetitionProgress` besitzt `List<SetResult>` als einzige Ergebniswahrheit. `SetResult`
  enthält Wiederholungen und optional `TrainingObservation(load, rir, safety, origin)`.
- `TrainingDecision` liefert `HOLD`, `APPLY`, `REQUEST_NEXT_LOAD` oder `PAUSE` samt stabilem Grund
  und Regelversion. Ein persistentes `TrainingLoadRequest` hält offene Lastfragen.
- `TrainingHistoryEntry` projiziert Anpassungen für die UI. Undo bleibt auf die neueste noch
  aktuelle Anpassung begrenzt.
- Fokussierte Use-Case-Bündel, Capability-Ports und ein separater `TransactionRunner` ersetzen
  die breite Übergabe von `TaskUseCases` und `ApplicationTaskRepository` an Verbraucher.

## Roadmap

### Phase 0 – Vertrag und reproduzierbare Grundlage

Diese Roadmap, ADR-028 und ein getrenntes Ausführungsprotokoll werden angelegt. JDK 21 wird
repositoryweit pfadunabhängig als Gradle-Daemon-JVM festgelegt. `DatabaseContract` bleibt die
Produktwahrheit; Upgrade-Probe, Fixture- und Workflowverträge dürfen keine ungesicherte zweite
Schemawahrheit enthalten. Der Migrationsgraph wird bis zur zentralen Version geprüft. Produkt- und
Schemaverhalten bleiben unverändert.

### Phase 1 – Gemeinsame Schrittverordnung

`StepPrescription`, `TrainingPrescription` und `TrainingAssistantProfile` werden eingeführt.
Template, Definition, Occurrence und Flow-Snapshot werden darauf migriert. Der Editor erhält einen
gruppierten Step-Draft und einen getrennten Saved-State-Codec. Nach Migration aller Aufrufer werden
die überladenen Kompatibilitätskonstruktoren entfernt. Das Persistenzschema und die sichtbaren
Editor-Goldens bleiben unverändert.

### Phase 2 – Ein atomisches Satzresultat

`TrainingSetResult` geht in `SetResult`/`TrainingObservation` auf. `RepetitionProgress` hält
vollständige Resultate; Wiederholungslisten sind nur noch Projektionen. `RecordSetResult` und
`CorrectSetResult` schreiben Resultat, Abschluss, Rewards und Adaptionsfolgen in einer Transaktion.
Die Engine liest aus dem Occurrence-Schritt; eine zweite Ergebnisabfrage entfällt. Bestehende
`repetition_results`-Zeilen bleiben ohne Schemaänderung kompatibel.

### Phase 3 – Korrekte Progression und persistente Lastentscheidung

Versteckte Standardlasten und Load-Increments entfallen. Eine numerische Ausgangslast muss beim
Aktivieren tatsächlich angegeben werden. Nach ausgeschöpfter Wiederholungsprogression erzeugt die
Engine `REQUEST_NEXT_LOAD`:

- eine konkrete nächste Last wird bei korrekter Richtung und höchstens zehn Prozent automatisch
  angewendet;
- ein größerer Sprung wird erklärt und nur als bewusste manuelle Vorlagenänderung zugelassen;
- „kein höheres Gewicht“ prüft die Satzprogression;
- „später“ hält die persistente Frage offen.

Körpergewicht geht direkt zur zulässigen Satzprogression; unterstütztes Körpergewicht validiert
die umgekehrte Lastrichtung. Manuelle Laständerung und Satzkorrektur schließen offene Entscheidungen
und starten die Kalibrierung neu. Die erste schemaändernde Phase verwendet die aktuelle
`main`-Version plus eins (auf der Ausgangsbasis Schema 22), ergänzt Lastanfragen, stabile
Auditreihenfolge und Regelversion und entfernt die obsolete Increment-Spalte.

### Phase 4 – Erklärbare und reversible Bedienung

Der Editor zeigt Opt-in, erforderliche Ausgangslast und den Zustand `Kalibriert x/3`, `Aktiv` oder
`Pausiert`. Today bindet Last, RIR und Sicherheitsmarker an das einheitliche Resultat. Eine offene
Lastfrage erscheint direkt im Trainingskontext und überlebt Recreation, Prozessneustart und
Screenwechsel. Status und letzte Anpassung werden pro Übung inline gezeigt; ein kompakter Verlauf
zeigt die letzten zehn Entscheidungen einschließlich rückgängig gemachter Änderungen. Undo ist
nur bei der neuesten angewendeten und noch aktuellen Änderung aktiv. Alle Gründe werden lokalisiert.

### Phase 5 – Composition Root, Ports und Abschlussaudit

Use Cases werden in fokussierte Bündel für Katalog, Today-Ausführung, Flows und Training geteilt.
`ApplicationTaskRepository` entfällt aus dem Domainvertrag; `TransactionRunner` und kleine Ports
werden getrennt injiziert. Room-Schritt-/Trainingspersistenz wird aus `RoomTaskRepository` in
fokussierte Adapter extrahiert. Kleine Testdoubles ersetzen den breiten Abnahmespeicher in
Slice-Tests. Architekturkarte, ADRs und ausführbare Architekturregeln werden auf den erreichten
Stand gebracht; danach folgt ein requirementweiser Gesamtabgleich.

## Dauerhafte Abnahmematrix

- Schema-21- und Schema-8-Daten bleiben vollständig lesbar und ausführbar.
- Templateänderungen verändern keine bestehende Occurrence und keinen Flow-Snapshot.
- Satzaufnahme, Korrektur, Reopen, Completion und Persistenzfehler besitzen eine Wahrheit und eine
  Transaktionsgrenze.
- Drei Beobachtungen kalibrieren; erst zwei weitere gleichartige Signale entscheiden. Safety-Pause,
  Volumengrenze und Zehn-Prozent-Grenze bleiben wirksam.
- Die Lastfrage entsteht genau einmal, überlebt Prozessverlust und verarbeitet konkrete Last,
  „kein höheres Gewicht“ und „später“ deterministisch.
- Verlauf und Undo zeigen Gründe und Reihenfolge stabil; Undo funktioniert genau einmal und nur
  bei unverändertem Nachzustand.
- Vor Abschluss laufen vollständige Host-/Robolectric-Suite, Lint, Debug-/Release-/AndroidTest-
  Builds, Produktionsupgrade sowie normale und animationsaktive Instrumentierung auf API 26, 35
  und 37.
