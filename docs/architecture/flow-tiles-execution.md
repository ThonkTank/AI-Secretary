# Ausführungsprotokoll: Kachel-Editor und parallele Abläufe

Dieses Protokoll wird append-only ergänzt. Verbindlich ist die
[Roadmap](flow-tiles-roadmap.md), fachlich [ADR-036](adr-036-kachel-editor-und-parallele-ablaeufe.md).

## 2026-09-10 — D-Freigabe und C-Beginn

- Nutzerfreigabe: Kacheln bestimmen Reihenfolge; Dialog enthält Name, Wartezeit danach
  und direkt „Beim Start nachfragen“. Kein Zeitoptionen-Dropdown, keine Ablaufbestätigung.
  Kapazitäten folgen auf dem zweiten Blatt. Kein erneutes Design-Freigabegate erforderlich.
- Ausgangspunkt `80476639`, Schema 24, isolierter Worktree
  `/tmp/autosecretary-flow-tiles-contract`, Branch `codex/flow-tiles-contract`.
- Vor Vertragsarbeit Fast-forward auf geprüftes Remote-main
  `de6fc4037a974dc6482e91e841befca01c964c64`: PR #353/#354 bleiben erhalten.
- Vertragsphase enthält keine Android-Produktänderung und veröffentlicht kein Paket.
- Mockup-Prüfungen werden separat von Android-Nachweisen geführt. Android-Referenzzustände,
  komplette Laufzeit-/Migrationsprüfung und Geräteabnahme sind noch offen.

### C — lokale Vertragsprüfung

- Mockup: Name/Zeit/Einheiten, direkt sichtbare Checkbox, kein Dropdown/Bestätigungsfeld,
  Cancel/Undo, Anlegen und Kapazitätszuordnung bestanden. Kachelgeometrie, Drag/Undo,
  320/360/736 px und große Schrift separat bestanden.
- CI-Vertragstests: 27 bestanden; Release-Vertragstests: 31 bestanden.
- `git diff --check` bestanden. Android-Gesamtsuite ist nicht als ausgeführt behauptet:
  diese Phase ändert ausschließlich Dokumentation und Referenz, keinen Produktcode.
- Mockup-SHA-256 steht in der Referenz. PR-/main-Nachweise folgen nach Remoteprüfung.

## 2026-09-10 — C abgeschlossen, P begonnen

- PR #355, Head `e29b0339d3ddb3005eb859eb701b4a82396655ff`, PR-Workflow
  `34481441711` erfolgreich; erforderliche Gates grün, Android-Lanes wegen Docs-only
  bestimmungsgemäß nicht ausgeführt.
- Squash `f42d625e31f1b6af703dc61c32405fd2715e51df`; exakter main-Workflow
  `34481533074` erfolgreich. Paket/Upgrade/Veröffentlichung für Docs-only übersprungen.
- Produktphase auf `codex/flow-tiles-cutover`, isolierter Worktree
  `/tmp/autosecretary-flow-tiles-cutover`. Noch kein Produkt-PR zur Freigabe oder Release.
- Zuerst gemeinsame unveränderliche Topologie für Drag-/Accessibility-Operationen und
  erreichbare Run-Snapshots. Runtime-/Persistenz-Cutover bleibt bis zur vollständigen
  Integration unveröffentlicht; der bisherige App-Pfad wird nicht vorzeitig umgeschaltet.

### P — geprüfter Editor-Teilstand, keine Auslieferungsfreigabe

- `FlowTileGraph`: stabile Identitäten, erreichbarer Teilgraph, getrennte alternative
  Wurzeln, Einfügen/Verschieben, explizite Zusammenführung, Zweiggrenze und Zyklenschutz.
- `FlowEditorDraft`, `FlowEditorCapacities`, `FlowEditorViewModel`: kompletter ungespeicherter
  Entwurf, direktes Namens-/Warteformular, Einheiten/Zeitabfrage, getrennte Kapazitätsseite,
  Zuordnungen inklusive Freigabe am selben Schritt nach Wartezeit, Undo und Parcel-Restore.
- `FlowTileEditorScreen`: eigene Compose-Fläche und revidierter Dialog. Wiederverwendung
  der mobilen Typografie/Farben/Blattflächen, nicht des alten Aufgaben-Wizards.
- 16 gezielte Struktur-/Entwurfs-/ViewModel-/Layouttests sowie ein Android-Render-Test mit
  drei Zuständen bestanden. Review-PNGs in `app/build/reports/flow-tiles`; keine neu
  freigegebenen Goldens behauptet. Breiten-/Formdarstellung nach Sichtprüfung nachgebessert.
- Aufgedeckte Fehler: Ein zu früh laufender Build sah nachträglich hinzugefügte Quellen
  nicht; vollständiger Folgelauf behoben. Alter `TaskFlowDraft.addLease` filterte neue
  Zuordnungen anhand linearer Verbindungen weg; separate Kapazitätsoperationen beheben dies
  im neuen Editor. Fehlender Compose-Fokus-Owner im neuen Screen wurde ergänzt und gerendert.
- Der bestehende TaskFlowDraft sowie die installierte App bleiben unverändert.

### P — ausdrücklich noch offen

Der Teilstand ist **nicht** der Produkt-Cutover und darf nicht gemergt/veröffentlicht werden.

1. Eigener Ablauf-Typ, Navigation/Activity-Anbindung, Laden und atomarer Save. Es gibt noch
   keinen produktiv erreichbaren neuen Editor; `onSave` ist nur dessen Aufrufervertrag.
2. Touch-Zweigauswahl, ausdrückliches Join-Dropziel, Autoscroll, geometrisch ungültige
   Vorschauen, vollständige zugängliche Bedienung und Start-/Rhythmusdarstellung.
3. Texte in Ressourcen, vollständige Referenz-/Golden-/Accessibility-Abnahme und
   Kapazitäts-Validierung auch nach Strukturänderungen.
4. `StepFlowDefinition`/`CreateFlowRunSnapshot`/`FlowRuntimeCoordinator` sind weiterhin
   linear; `step_transitions` hat weiterhin nur `sourceStepId` als Primärschlüssel.
   Diese Annahmen gemeinsam mit per-Schritt-/Wartephasen-Zielen ersetzen, nicht einzeln aktivieren.
5. Schema 24 ist unverändert. Schritte, Run-Verbindungen, Zustände und Ressourcenbindungen
   auf stabile IDs migrieren; alte Cursor entfernen; Einzelschritt-Wartezeiten erhalten.
6. `LoadFlowRuns`/`LoadDashboard`, Today-Aktionen und Tau-Abschluss pro Run integrieren.
   `StepExecutionService` bucht derzeit pro Schritt; bestehende Ledger-Einträge nicht
   durch neue Summenbuchungen verdoppeln.
7. Vollständige lokale Suite, PR-Geräte-/Animationsmatrix 26/35/37, signierte Upgrades,
   requirementweiser Abschluss, exaktes main, Paket und installierte Version getrennt prüfen.
   Bei der bisherigen Geräteabfrage war kein ADB-Gerät verbunden.

### P — Prüfstand und noch offener Gesamtnachweis

- Teilstand `6fba98bd201e1d2a85ee21026f48266e512b0c93` als **Draft-PR #356** gesichert.
  Kein Merge und kein Produktupdate. PR-Workflow `34483933471`: Quality-Verträge,
  Goldens, Build sowie Gerätematrix 26/35/37 erfolgreich; Animationsmatrix beim letzten
  Snapshot auf 26/37 erfolgreich und auf 35 noch laufend. Das ist keine Abnahme des
  noch fehlenden Cutovers und gilt nur für den genannten Commit.
- Erster lokaler `check-all.sh`-Lauf: 27 CI- und 31 Release-Vertragstests grün;
  anschließend 595 Unit-/Robolectric-Tests, ein Fehler und ein übersprungener Test.
  Fehler: `TaskEditorComposeGoldenRobolectricTest.allTenComposeWizardStatesStayWithinTheApprovedVisualContract`,
  fehlende Bestätigungsdialoge in Review-Bildern 09/10 (Referenzen unverändert).
- Isolierte unveränderte Baseline `f42d625e` im neuen Diagnose-Worktree
  `/tmp/autosecretary-flow-tiles-baseline`: derselbe einzelne Test grün.
  Neuer Render-Test zusammen mit dem betroffenen einzelnen Test ebenfalls grün.
  Eine Regression der produktiven normalen Editorlogik ist damit nicht nachgewiesen;
  die Ursache des Fehlers im Gesamtlauf bleibt offen.
- Test-Isolation verbessert: neue Render-Komposition explizit freigeben, ursprüngliche
  Schriftkonfiguration/Metriken zurücksetzen und Looper nach Activity-Ende leeren.
  Danach neuer Render-Test plus alle drei bestehenden Compose-Editor-Goldentests grün.
- Erneuter vollständiger Lauf nach dieser Änderung endete mit Exit 143 ohne finalen
  Test-/Buildnachweis. Ursache des Abbruchsignals nicht festgestellt. Deshalb weiterhin
  **kein grünes lokales Gesamtgate**, keine vollständige Lint-/APK-Auslieferung behauptet.
- Der letzte Test-Isolationscommit benötigt eigene PR-Prüfungen. Keine bestandenen
  Prüfungen älterer Heads als Beleg für den neuen Head verwenden.

### P — Fortsetzung 2026-09-11: Graph-Ausführung und Editorvalidierung

- Erneut geprüft: Remote-main bleibt `f42d625e`; Produktworktree sauber auf
  `30463778`. PR #356 weiterhin Draft. Dessen exakter Workflow `34485641102` ist
  inzwischen vollständig grün, einschließlich Geräte-/Animationsmatrix 26/35/37 und
  `pull-request-gate`. Keine Produktveröffentlichung und kein Merge.
- `FlowGraphDefinition` und `FlowGraphRun` bilden den nächsten Laufzeitkern ab:
  stabile Laufzeitschritt-IDs, eingefrorene erreichbare Teilgraphen, eigene Zustände,
  Wartephasen-IDs, Kapazitätsbindungen und einmaliger Abschluss pro Run.
- `FlowGraphExecution` enthält reine Übergänge. `FlowGraphCommands` stellt die
  Transaktionsgrenze für Start, Aktion, Zeitänderung, Aktivierung und Einsammeln bereit.
  Der Speichervertrag ist noch **nicht** an Room angeschlossen. Der alte produktive
  Coordinator bleibt bis zum zusammenhängenden Cutover unverändert; kein produktiver
  Parallelbetrieb zweier Ausführungen wird eingeführt.
- Tests prüfen alternative Starts, echte/ungleich lange Parallelzweige und Joins,
  Zeitabfragen auch am letzten Schritt, separate Verlängerung, unabhängiges
  Kapazitätswarten, Freigabe nach Aktion/Wartezeit, Verringerung von Gesamtmengen,
  doppelte Aktionen/Einsammeln und Rollback bei Ledgerfehlern. Konkurrierende Starts
  sind gegen einen serialisierbaren Test-Speicher geprüft, **nicht gegen Room**.
- `MigrateLinearFlowExecution` übersetzt Positionsbezüge einmalig in Schrittzustände
  und stabile Bindungen. Die eigentliche SQL-Migration und der Produktions-Upgrade
  bleiben offen. Alte Header, Occurrences, Blattpositionen und Ledger müssen durch
  den späteren Adapter unverändert bleiben. Schema 24 hatte keine Aktionszeit pro
  Schritt; der Konverter verwendet dafür eine konservative Untergrenze, behauptet
  keinen rekonstruierten exakten Zeitpunkt. Absolute Wartezeit und gewählte Dauer
  werden getrennt bewahrt. Historisch abgebrochene Runs bleiben inaktiv.
- Editor: `graphDefinition` übernimmt Kacheln, Wartezeiten und Zuordnungen in denselben
  Graphvertrag. `Fertig` validiert erneut; nach dem Verschieben ungültige Bindungen
  werden nicht gelöscht, sondern zur Korrektur geöffnet. Doppelte Kapazitätsnamen
  und zusammen zu große Zuordnungen eines Schritts werden abgewiesen. Reines
  Umbenennen erhält die zuletzt verwendete Wartezeit.
- Gezielter Lauf: 46 Tests erfolgreich (15 Ausführung, 7 Transaktionsbefehle,
  7 Konvertierung, 8 ViewModel, 3 Entwurf, 6 Kachelgraph). Vollständiges lokales Gate
  gestartet; das Ergebnis wird gesondert nachgetragen.
- Weiterhin **nicht auslieferbar**: Room-/SQL-Anschluss, eigener Ablauf-Typ und
  Navigation, atomarer persistenter Editor-Save, Ablösung des alten Coordinators,
  Occurrence-/Reward-Anbindung, Heute-/Alles-Projektion sowie vollständige
  Drag-/Accessibility-/Golden-Abnahme fehlen. Ein grüner Test des neuen Kerns
  ist kein Nachweis dieser noch fehlenden Produktwege.

- Beim Abgleich mit `StepExecutionService.adjustQuantitativeReward` zusätzlich erkannt:
  Auch unvollständige Mengen-/Satzschritte können bereits Tau enthalten. Die Konvertierung
  muss diese Teilbeträge erhalten; Aktivierung darf sie nicht auf null zurücksetzen und
  Schrittabschluss übernimmt den Endbetrag statt ihn ein zweites Mal zu addieren.
  Separater Regressionstest ergänzt. Für bereits eingesammelte Beträge zählt der
  geerntete VESSEL-Grundbetrag, nicht der durch Aufgabe/Combo multiplizierte HEAD-Betrag.
  Der zuvor gestartete Gesamtlauf bezieht sich noch auf den Stand vor dieser Ergänzung.
