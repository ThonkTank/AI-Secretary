# Roadmap: saubere Heute-Aufgabenblatt-Architektur

Status: verbindlich

Beschlossen: 2026-09-03

Ausgangsstand: `39f1e5cd` (`origin/main`)

Ausgangsschema: Room 22

## Zusammenfassung

Die bestehende lokale Sammeländerung `be0d7f22` wird nicht direkt veröffentlicht oder als Ganzes
übernommen. Sie bleibt zunächst als Referenz erhalten. Der Zielzustand wird in vier einzeln
prüfbaren Phasen vom jeweils frisch gemergten `origin/main` aufgebaut.

Kanonische Roadmap und append-only Ausführungsprotokoll bleiben getrennt. Jede Phase erhält Plan,
Validierung, Anforderungsaudit und gegebenenfalls dokumentierte Korrekturrunden. Erst ein grüner
Squash-Merge nach `main` schließt eine Phase.

## Dauerhafte Zielverträge

- `:today-core` ist alleiniger Owner für ausgewählten Schritt, dargestellte Reihenfolge,
  Zeilenmodus und ausführbare Aktion.
- Kanonische Schrittfolge, Reorder-Preview und lokale Auswahl bleiben getrennte Zustände.
- Jede gerenderte Zeile besitzt genau einen Modus und genau eine Aktion.
- Views verwenden stabile Schritt-IDs und vollständige Bind-Verträge; kein Zustand darf über
  Position oder unvollständige Reset-Listen weiterleben.
- Grain-Masken konsumieren sichtbare Geometrie statt ganzer View-Rechtecke.
- Materialisierung übernimmt Titel, Prescription, Notiz, `sourceTemplateId` und Provenienz atomar
  aus genau einer Quelle.
- Keine Deprecated-Aliase, Übergangskonstruktoren, parallelen Projektoren oder
  Kompatibilitätsfassaden.
- Room-Schema 22, bestehende Daten und Snapshot-Semantik bleiben unverändert.

## Phase 0 – Zielvertrag und reproduzierbare Ausgangslage

Branch: `codex/today-focus-clean-contract`

- Diese Roadmap, das separate Ausführungsprotokoll und ADR-031 für Fokusprojektion,
  Zeilenidentität und Grain-Geometrie werden angelegt.
- Der Architekturindex wird aktualisiert, ohne den noch nicht erreichten Zielzustand als
  implementiert darzustellen.
- Für alle gemeldeten Fehler werden reproduzierbare Abnahmefixtures festgelegt: Textmaskierung,
  Satzringe, Titelabstand, XP-Gefäß, temporäre Auswahl und falsche Notiz.
- Der unveröffentlichte Branch `codex/today-focus-sheet-fixes` bleibt Referenz; Commits daraus
  werden nicht pauschal übernommen.
- Baselines für Schema, Migrationen, Goldens und Ressourcen werden erfasst.

Abnahme:

- Nur Dokumentation ändert sich; kein Produktverhalten.
- Roadmap und Ausführungsprotokoll sind getrennt und im Architekturindex auffindbar.
- CI-/Release-Vertragstests und Main-Workflow sind grün.
- Squash-Merge nach `main`, bevor Phase 1 beginnt.

## Phase 1 – Eine einzige Fokusprojektion

Branch: `codex/today-focus-projection`

- `TodayFeatureState.selectedStepId` wird der einzige lokale Auswahlzustand.
- Ein Android-freier `TodayFocusProjector` erzeugt eine fertige `FocusStepListUiModel` mit
  endgültig geordneten `FocusStepRowUiModel`s.
- Jede Zeile erhält genau einen Modus (`EXPANDED`, `COMPACT`, `ASSISTANT`) und eine bereits
  gewählte `StepExecutionUiAction`.
- `FocusStepStatus.ACTIVE/AVAILABLE`, `promotedStepId`, `activeExecutionAction` sowie lokale
  Aktiv- und Sortierlogik in der View entfallen ersatzlos.
- Reorder-Preview verändert nicht mehr `TodayUiModel`; nur ein bestätigter Reorder aktualisiert
  die kanonische Reihenfolge.
- `FocusTaskView` besitzt einen vollständigen Bind-Einstieg. Neue oder bestehende
  Übergabe-Overloads werden nicht behalten.
- Wiederholungseingabe und Accessibility konsumieren dieselbe projizierte ausgewählte Zeile wie
  die sichtbare UI.

Abnahme:

- Auswahl von `C` zeigt `C, A, B`, während das kanonische Modell `A, B, C` bleibt.
- `C` ist vollständig aktiv, `A` kompakt; es entsteht weder Command noch Persistenzzugriff.
- Rebind derselben Occurrence erhält die Auswahl. Abschluss, Entfernung, Occurrence-Wechsel oder
  Reorder-Start löschen sie.
- Negative Scans finden keine entfernten Status-, Promotion- oder Doppelaktionsfelder.
- Fokussierte Reducer-, Projector-, Eingabe-, Interaction- und Accessibility-Tests sowie der
  vollständige Gradle-Gate sind grün.
- Separater Plan- und Roadmap-Audit vor PR und Squash-Merge.

## Phase 2 – Stabile Zeilenidentität und atomare Metadaten

Branch: `codex/today-step-identity`

- Der Zeilencache verwendet `occurrenceId + stepId`; bei Occurrence-Wechsel wird er verworfen.
  Eine View wird niemals an eine andere Schritt-ID gebunden.
- `FocusStepRowView` bindet Identität, Texte, Aktion, Wiederholung, Training, Assistent, Timer,
  Listener und Accessibility in vollständigen, immer aufgerufenen Teilbindern.
- Ein globaler `resetForBind()` und separate Reset-Hooks sind nicht zulässig. Jeder Teilbinder
  beschreibt sichtbaren und leeren Zustand vollständig.
- Die Snapshot-Factory wird die einzige Produktionsgrenze für Vorlage, Carry-forward und
  Flow-Snapshot. Assemblers erzeugen keine `OccurrenceStep`s mehr über lange Feldlisten.
- Der vollständige Konstruktor bleibt ausschließlich als klar benannte Rehydrationsgrenze für
  Persistenz und Testfixtures.
- Es gibt keine Reparatur nach Titel oder Position und kein nachträgliches Überschreiben offener
  Snapshots.

Abnahme:

- Eine Rebind-Matrix auf derselben ID prüft Notiz→leer, Menge→leer, Training→Körpergewicht,
  Wiederholung→normal, Timer→leer und Assistent→leer einschließlich Listenern.
- Im Neun-Schritte-Szenario zeigt „Römische Liege“ nach acht Abschlüssen keine Notiz oder
  KG-Angabe eines Geräteschritts.
- Eindeutige Notizen bleiben bei Materialisierung, Move, Carry-forward, Flow-Angebot und
  Dashboard-Mapping an derselben `sourceTemplateId`.
- Migration 8→22 und gezielt 19→20→21→22 erhalten alle Metadaten; Schemaexport und
  Datenbankversion bleiben unverändert.
- Negative Scans finden keine produktiven direkten Snapshot-Konstruktionen außerhalb der
  erlaubten Rehydrationsgrenze.
- Vollständiger Gradle-Gate, Audit, PR und Squash-Merge sind grün.

## Phase 3 – Präzise Grain- und Gefäßgeometrie

Branch: `codex/today-focus-rendering`

- `GrainSpec` verwendet `GrainOcclusion`-Quellen statt roher Text-Views. Diese liefern nach dem
  finalen Layout sichtbare lokale Rechtecke.
- Text-Occlusion berücksichtigt tatsächliche Zeilenbreite, Compound-/Extended-Padding,
  Scrolloffset, Leerzeilen, Mehrzeiligkeit, Clipping und Ellipsierung.
- `LeafSurface` verarbeitet ausschließlich aufgelöste Geometrie; eine `TextView`-Sonderbehandlung
  im Surface entfällt.
- Der Titel-Schritt-Abstand wird auf 12 dp gesetzt.
- Das XP-Gefäß verwendet eine reine Kreisfüllgeometrie für Füllhöhe und Kreissehne. Füllung und
  Linie teilen denselben Innenkreis-Clip; bei 0 und 100 Prozent wird keine Linie gezeichnet.
- Software-Layer und rechteckig abgeschnittener Combo-Schatten entfallen vollständig.

Abnahme:

- Render- und Golden-Tests für kurze, gewichtete, leere, mehrzeilige und ellipsierte Texte;
  außerhalb sichtbarer Textzeilen bleiben Jahresringe unverändert.
- Satz-/Wiederholungszeilen zeigen keine duplizierten oder verschobenen Ringe.
- Goldens prüfen 12-dp-Titelabstand sowie XP-Füllstände 0, 25, 50 und 100 Prozent ohne Rechteck
  oder überstehenden Strich.
- Vollständige Prüfung mit `./gradlew testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease`.
- Automatisierte visuelle Abnahme anhand aller gemeldeten Ansichten über Render-, Geometrie- und
  Golden-Tests vor dem finalen PR; ein physisches Gerät ist kein Freigabegate.
- Abschließender Cross-Phase-Audit gegen Roadmap, ADR-031, Entfernungslisten, Snapshot-Vertrag
  und sichtbare Abnahmekriterien.
- Erst danach PR, grüne Checks und Squash-Merge nach `main`.

## Ausführungs- und Freigaberegeln

- Jede Phase startet nach einem frischen Fetch in einem isolierten Worktree vom vorherigen
  Squash-Stand auf `origin/main`; der aktive alte Frontend-Checkout bleibt unangetastet.
- Vor jeder Implementierung wird der konkrete Phasenplan im Ausführungsprotokoll festgehalten.
  Nach den Tests folgen getrennte Plan- und Roadmap-Audits.
- Jede Abweichung erhält vor Codeänderungen eine dokumentierte Korrekturrunde.
- Externe Writes, PR-Erstellung und Merge erfolgen nur nach entsprechender Autorisierung.
- Der alte Sammelbranch bleibt bis zum erfolgreichen Abschluss als Vergleichsreferenz erhalten
  und wird nur nach ausdrücklicher Freigabe entfernt.
- Kein Versionssprung und keine manuelle Veröffentlichung gehören zur Roadmap.
