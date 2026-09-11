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

### P — Datenbankübernahme und Snapshot-Speicherung vorbereitet

- Der oben gestartete lokale Gesamtlauf endete nach 24 Minuten erfolgreich: 627
  Unit-/Robolectric-Fälle, davon ein übersprungener, kein Fehler; zusätzlich 27 CI-
  und 31 Release-Vertragstests, Lint, Paketbau, Identitäts- und Größenprüfungen grün.
  Während dieses Laufs ergänzte Quellen waren nicht durchgehend Bestandteil des
  Builds. Das ist deshalb kein vollständiger Nachweis des späteren Heads.
- Exakter PR-Workflow `34581720686` für `af0e0f6a`: vollständig erfolgreich,
  einschließlich Geräte- und Animationsmatrix API 26/35/37 sowie PR-Aggregat.
  Danach ergänzter Schutz: ein inzwischen zum Folgeschritt verschobener Kandidat
  darf keine neue Kette starten. Separater gezielter Lauf: 52 Tests ohne Fehler.
- `Schema24FlowMigrationInput` liest die alten Spalten namentlich und ordnet
  Ledger-Buchungen über Lauf und Quellschritt zu. Kopierte Ausführungen verdoppeln
  Buchungen nicht; Gegenbuchungen und Reporting-Zuordnungen werden berücksichtigt.
  Ein aktiver, nicht rückgängig gemachter HEAD-Abschluss markiert geernteten
  VESSEL-Grundbetrag. Der multiplizierte HEAD-Wert wird niemals als Grundbetrag
  übernommen. Nicht zuordenbare Belohnungen führen zu einem Abbruch der Migration.
- `FlowGraphMigration25` ist die vorbereitete SQL-Umstellung von Schema 24: eigener
  `taskKind`, Schrittwartezeiten, mehrfache ausgehende Verbindungen, Laufzeitkanten,
  Schrittzustände, stabile Kapazitätsendpunkte und Ausführungszuordnung. Der alte
  Cursor und die Ressourcenpositionen werden im Ziel entfernt. Ressourcennamen,
  damalige Gesamtmengen und sämtliche Reservierungszeitpunkte bleiben erhalten.
- Die Migration ist absichtlich noch **nicht in DatabaseMigrations registriert**.
  AppDatabase und produktive Laufzeit bleiben auf Schema 24, bis Room-Modell,
  Coordinator, Editor und Projektion gemeinsam umgestellt werden. Es gibt keine
  Freigabe, die vorbereitete Migration allein auf einem Nutzergerät auszuführen.
- `FlowGraphSnapshotReader`/`Writer` lesen und speichern normalisierte neue Snapshots
  ausschließlich innerhalb der aufrufenden Transaktion. Updates dürfen nur Zustand,
  Zeiten und Tau-Fortschritt ändern, keine eingefrorenen Definitionen oder Bindungen.
  Vorhandene Ressourcenmetadaten bleiben auch nach einer Zeitänderung unverändert.
  Ledger und Occurrence-/Combo-Projektion müssen beim Produktanschluss weiterhin
  dieselbe Transaktion teilen; diese Anbindung ist noch offen.
- SQL-Prüfung auf dem exportierten echten Schema 24, jeweils API 26 und 35:
  13 Fälle pro API für Migration aller Laufzustände, Teilmengen/Carry-forward,
  geänderte Wartezeiten, Buchungsgegenläufe, unveränderte Platzierung/Kandidaten,
  parallelen Snapshot-Roundtrip, eingefrorene Nutzdaten, doppelte Startidentität
  und vollständigen Rollback bei simuliertem Zahlungsfehler.
- Anfangs hatte die neue Test-Fixture eine Last an einem Nicht-Satzschritt und wurde
  vom bestehenden Prescription-Vertrag abgewiesen. Die Fixture verwendet nun einen
  gültigen Satzschritt; kein Produktvertrag wurde gelockert. Anschließender gezielter
  Lauf: **78 Tests erfolgreich, keiner übersprungen, kein Fehler** (52 Kern/Editor
  plus 26 SQL-Fälle). Kein signierter Produktions-Upgrade- oder Room-Zielschema-
  Validierungsnachweis wird daraus abgeleitet.
- PR #356 bleibt Draft, kein Merge und keine Veröffentlichung. Alle oben benannten
  Produktanschlüsse und die vollständige UI-/Auslieferungsabnahme bleiben erforderlich.

### P — Architekturgrenze des Schreibadapters korrigiert

- Lokaler Gesamtlauf für `5128f32d`: 659 Fälle, ein Fehler und ein übersprungener
  Test. Derselbe Fehler im exakten PR-Lauf `34585651638`: Der neue Writer verwendete
  `FlowRunStepSnapshot.rehydrate` außerhalb der bestehenden zulässigen Mappergrenze.
  PR-Build und bisherige Goldens waren grün; Vertrags- und Aggregatprüfungen rot,
  Geräte-/Animationsmatrix deshalb nicht gestartet. Kein Gate wurde umgangen.
- Writer serialisiert jetzt direkt den eingefrorenen Graph-Payload in benannte
  Spalten, ohne einen alten linearen Snapshot zu erzeugen. Architekturprüfung und
  zulässige Grenzen bleiben unverändert. Zusätzlicher Roundtrip-Test umfasst alle
  vier Mengenarten, Last, RIR, Satzpause sowie vorgegebene/zuletzt verwendete/gewählte
  Wartezeit. SQL-Prüfung jetzt 14 Fälle pro API, zusammen 28.
- Gezielte Kern-, Editor-, SQL- und Architekturprüfung nach der Korrektur erfolgreich.
  Vollständiger lokaler Lauf und neuer exakter PR-Lauf müssen den korrigierten
  Commit separat prüfen. Der vorherige rote Gesamtlauf ist kein grüner Nachweis.

### P — Geprüfter Zwischenstand und transaktionaler Editoranschluss

- `987b8d592be1f9d1d4f4be8a3b94abb948cfac0d`: vollständiges lokales
  `check-all.sh` erfolgreich in 18 Minuten 4 Sekunden: 661 Testfälle, davon einer
  übersprungen, keine Fehler; zusätzlich CI-/Release-Verträge, Lint, Paketbau,
  Identitäts- und Größenprüfungen erfolgreich. Exakter PR-Workflow `34586441385`
  vollständig grün einschließlich Geräte-/Animationsmatrix API 26/35/37.
- Für einen unveränderlichen Prüflauf wurde `/tmp/autosecretary-flow-tiles-cutover`
  auf diesem Commit abgetrennt. Weitere Entwicklung auf demselben Themenbranch
  erfolgt in `/tmp/autosecretary-flow-tiles-product`; der Haupt-Checkout und fremde
  Worktrees wurden nicht geändert. Neue Änderungen sind nicht vom obigen Lauf gedeckt.
- `LoadFlowGraph`/`SaveFlowGraph` sowie SQL-Definitionsadapter ergänzen konsistentes
  Laden und einen atomaren Editor-Save. Dauerhafte Save-IDs verhindern doppelte
  Neuanlagen nach Commit ohne UI-Bestätigung. Die Belege überleben eine explizite
  Aufgabenlöschung, damit ein wiederhergestellter alter Save sie nicht rückgängig macht.
- Bestehende Schritt-/Lease-IDs werden geprüft und erhalten. Nicht im kleinen Dialog
  bearbeitete Mengen, Trainingszustände, Notizen, Aufgabenbegrenzungen und Platzierungen
  werden nicht überschrieben. Unveränderte gemeinsame Kapazitäten behalten zwischenzeitliche
  Anpassungen. Namenswechsel erfolgen atomar, einschließlich eines Namenstauschs.
- Das ViewModel speichert Entwurf, offene Eingabe, Undo und einen ggf. unbestätigten
  Save-Auftrag. Unsichere Schreibfehler erlauben nur die Wiederholung desselben Auftrags;
  transaktionale Eingabefehler führen direkt zum betroffenen Element. Der Schrittdialog
  behält ausschließlich Name, Wartezeit/Einheit und die direkte Nachfrage-Checkbox.
- Entwicklungsprüfungen: Ein während Schnittstellenänderungen gestarteter Build war
  kein konsistenter Prüflauf und scheiterte an abweichenden Methodenständen. Ein weiterer
  Lauf deckte einen falsch benannten Enum-Wert in der neuen Test-Fixture auf. Die Fixture
  verwendet jetzt den tatsächlich vorhandenen `ACCUMULATE`-Vertrag; kein Gate gelockert.
  Anschließend 132 gezielte Tests erfolgreich; nach ergänztem Namenstausch-/Löschtest
  **136 gezielte Tests erfolgreich**, keiner übersprungen, kein Fehler.
- Noch **kein Produkt-Cutover**: neuer Editor-Gateway ist nicht in App-Navigation und
  Composition angeschlossen, Schema 25 nicht registriert, produktiver Coordinator,
  Occurrence/Combo/Tau und Heute/Alles noch nicht umgestellt. Touch-Zweig/Join,
  Autoscroll, zugängliche Alternativen und finale Android-Referenzen bleiben offen.
  PR #356 bleibt Draft. Kein Merge, kein veröffentlichtes Update und kein Geräte-Upgrade.

### P — Kachelbedienung auf Android erweitert

- Ruhezustand: nur Kacheln, keine Verbindungslinien, Ports oder dauerhaften Sortierleisten.
  Lange Berührung bzw. Tastatur-Kontextmenü öffnet getrennte Griffe für Schritt/Zweig
  und ausdrückliches Zusammenführen außerhalb des Schrittdialogs. Eine zugängliche
  Zielauswahl mit Vorschau/Übernehmen verwendet dieselben Strukturvorschläge.
- Drag-Vorschläge benutzen die Geometrie vor Gestenbeginn. Die angehobenen Kacheln
  folgen dem Zeiger; Zielkonturen und andere Kacheln zeigen die vorgeschlagene Anordnung.
  Loslassen übernimmt, Gestenabbruch/Escape/Zurück verwerfen. Randscrollen ist zeitbasiert.
  Zweige enden vor einer Zusammenführung; ungültige Ziele/Zyklen werden nicht angeboten.
- Start-Rhythmus ist im angeforderten Kontext sowie über Accessibility erreichbar.
  Breitenlayout erhält Wäsche und ungleich lange Parallelzweige; zusätzlich verhindert
  eine Kollisionsprüfung überdeckte Kacheln bei verschachtelten Zusammenführungen.
- Lokaler gezielter Lauf: 146 Tests, kein Fehler/Skip (einschließlich Struktur-/Hit-Test-,
  Layout-, Editor-, SQL- und Architekturtests sowie gerenderter Android-Ansichten).
  Zusätzliche Schriftkorrektur nutzt deutsche Worttrennung; danach separat zu prüfen.
  Instrumentierungs-Testpaket erfolgreich gebaut. Sechs neue Android-Eingabetests für
  kompakten Dialog, echte Touch-/Mausgesten, Abbruch, expliziten Join und Wiederherstellung;
  auch in die Animationsmatrix aufgenommen. Deren Ausführung ist noch kein grüner Nachweis.
- Lokaler Geräteversuch ist **rot / ohne ausgeführten App-Test**: neu angelegter, isolierter
  Emulator API 36.1 stürzte mit SIGSEGV ab; Android-Eigenschaften konnten nicht geladen
  werden. Eine alternative Grafikeinstellung blieb bei Geräteauthentifizierung stehen.
  Keine Wiederholung desselben Fehlerversuchs, keine ADB-Schlüssel oder Geräterechtregeln
  geändert. Testemulator und separater Test-ADB-Dienst wurden anschließend beendet;
  der bestehende ADB-Dienst und Nutzergeräte blieben unangetastet.
- Produktnavigation, endgültiges Room-Modell/registrierte Migration, Laufzeit-/Reward-
  und Blatt-Cutover sowie vollständige Interaktions- und Auslieferungsabnahme bleiben offen.
  Keine Teilfreigabe aus diesen Tests ableiten; PR bleibt ausdrücklich Draft.
- Exakter PR-Lauf `34589489369` für Speicher-Commit `164668a938f08601c2ac1d67938fc77fc8fbb329`
  vollständig erfolgreich, einschließlich aller Geräte- und Animationsprüfungen API
  26/35/37. Dieser Lauf enthält noch nicht die oben ergänzte Android-Kachelbedienung.
  Deren letzter lokaler UI-/Editor-Lauf nach Schrift-/Innenabstandskorrektur:
  34 Tests erfolgreich, keine Fehler/Skips. Neues Testpaket zuvor erfolgreich gebaut;
  ein exakter PR-Lauf des Kachel-Commits muss die tatsächlichen Gerätegesten erst prüfen.

### P — Prüfhost und expliziter Aufgabentyp

- PR-Lauf `34591411896` für `c77e62ab8320a2b00f236e566539631a4974676c`
  scheiterte in `quality-build` an Lint `RestrictedApi`: der neue Debug-Prüfhost
  verwendete direkt `ViewModelStore.put`. Contracts und Goldens waren grün;
  Geräte-/Animationsmatrix wurde korrekt nicht gestartet. Kein Gestennachweis daraus.
- Der Prüfhost verwendet jetzt `ViewModelProvider.Factory` und
  `CreationExtras.createSavedStateHandle`. Fixture-Schlüssel und Anfangsentwurf werden
  über den Activity-Zustand erhalten; der Lebenszyklus verwaltet ViewModel und Eingaben.
  Keine Lint-Unterdrückung und keine Abschwächung der Pflichtprüfung.
- Das Domänenmodell erhält den ausdrücklichen Typ `TASK`/`FLOW`. Bestehende normale
  Aufgaben bleiben standardmäßig `TASK`; alle Kopier-/Bearbeitungswege erhalten den Typ.
  Der Graph-Ladevorgang liefert auch einen einzelnen Schritt als `FLOW` zurück.
  Die 53 gezielten JUnit-Ergebnisse (Typ, Editor-Speicherung und Architekturgrenzen)
  enthalten keine Fehler oder Skips. Room-Abbildung und produktive Navigation sind
  damit noch nicht umgestellt; sie bleiben Teil des zusammenhängenden Cutovers.

### P — Laufzeit-Identitäten und persistente Abfragen

- Lokaler Android-Lauf nach Prüfhost-Korrektur: `lintDebug` und
  `assembleInstrumentationAndroidTest` erfolgreich (6m 14s). Exakter PR-Lauf
  `34592645677` für `4b1667b7cc1fa7da2a33bdb93b61f816a1905332` hat die drei
  Quality-Lanes passiert; Geräte-/Animationsmatrix zu diesem Zeitpunkt noch offen.
- `FlowGraphRunRepository` und SQL-Adapter lesen eingefrorene Ketten samt
  Termin-/Platzierungsmetadaten sowie nach stabiler Laufzeitschritt-ID. Die nächste
  Aufweckzeit ist das Minimum aller aktiven Wartephasen; eine ausstehende Abschlussaktion
  bleibt aktiv, Kandidaten werden dabei nie zu Ketten.
- Kapazitätsabfragen zählen reservierte und aktive Einheiten unabhängig von späteren
  Definitions-/Kapazitätsänderungen. Reihungsänderungen betreffen nur Metadaten;
  Schrittzustände, Wartezeiten, Belegung, Blattposition und Rewards bleiben unverändert.
  Die bestehende Ausführungsnummer wird ohne aktuellen Positions-/Occurrence-Zeiger
  transaktional fortgeführt. Alle Repository-Einstiege verlangen dieselbe Transaktion.
- Commit `62fa7ae8`: 73 gezielte Tests erfolgreich (42 SQL-/Migrationstests auf API 26/35,
  3 Typ- und 28 Architekturtests), keine Fehler/Skips. Dazu zählen Rollback der
  Ausführungsnummer, übernommene Metadaten, unabhängige Wartezeiten und nachträglich
  verringerte/entfernte Kapazitäten. Dies ist noch keine finale Room-Upgrade-Abnahme.
- `OccurrenceStep` kann jetzt eine separate Laufzeitschritt-ID tragen. Die zentrale
  Snapshot-Factory übernimmt nur verfügbare Graph-Aktionen; Mengenfortschritt,
  Korrektur, Übertrag und Verschiebung behalten die Identität. Normale Schritte bleiben
  unverändert ohne Graph-ID. Room-Abbildung und produktive Aufrufwege bleiben offen.
- Arbeitsstand ist weiterhin **nicht vollständig implementiert und nicht auslieferbar**:
  App-Navigation/Composition, registriertes Room-Schema, Ersatz des alten Coordinators,
  Occurrence-/Combo-/Tau-Verknüpfung und Heute-/Alles-Projektion sind nicht umgestellt.
  Kein zweiter produktiver Laufzeitmechanismus wurde aktiviert. PR #356 bleibt Draft;
  kein Merge, Main-Nachweis, Release oder installiertes Update für diese Produktphase.
- Vollständiger lokaler Unit-Testlauf auf `58ac7e6c`: **717 Tests, 1 vorgesehener Skip,
  keine Fehler**, erfolgreich in 7m 22s. Dieser Nachweis ist nicht der vollständige
  Check-all-/Release-Gate und liegt vor den anschließenden Instrumentierungs-Driver-Anpassungen.
- Der erste tatsächliche Kachel-Gerätelauf `34592645677` ist rot: API 26 meldet drei
  Fehler (großer Schrittdialog, Eingabe nach Activity-Neuerstellung, Ziel der expliziten
  Zusammenführung). Touch, Maus und Gestenabbruch bestehen. API 35 meldet einen Fehler
  bei der Textabfrage nach Neuerstellung. Weitere Matrix-Lanes sind ebenfalls rot;
  keine Gerätefreigabe aus einzelnen bestandenen Fällen ableiten.
- Der Driver wartet jetzt auf ruhiges Layout, scrollt über Text **oder** Beschreibung
  und wählt den Kontext-Zielknopf über dessen eindeutige Test-ID. Große Schrift darf
  regulär scrollen; die Form enthält nach wie vor exakt vier Werte (Name, Dauer, Einheit,
  Nachfrage). Vor und nach Neuerstellung wird außerdem der echte ViewModel-Eingabewert
  geprüft. Eine Fehlerdiagnose protokolliert Formularzustand und Accessibility-Baum
  vor dem Schließen der Test-Activity. Das neue Testpaket baut lokal erfolgreich;
  ob die Anpassungen die Gerätefehler beheben, ist noch offen.
- Zusätzliche isolierte Wiederherstellungsprüfung des echten Activity-/Provider-Hosts
  auf API 26/35 erfolgreich: derselbe ViewModel-Zustand bei Activity-Neuerstellung,
  neuer ViewModel-Zustand aus parceliertem Saved State nach Zerstörung, einschließlich
  offenem Formular und ausgewählter Fixture-Generation. Zusammen mit den sechs
  Speicher-/Gateway-ViewModel-Tests: **10 Tests ohne Fehler/Skip**, 1m 1s. Dies grenzt
  den Gerätefehler ein, ersetzt aber nicht die Prüfung der realen Eingabebedienung.
