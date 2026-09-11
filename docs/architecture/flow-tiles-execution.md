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

### Fortsetzung: transaktionale Ausführung und Geräte-Diagnose

- `GraphFlowRuntime` verbindet den Graph-Reducer mit gespeicherten Aktions-Occurrences,
  vorhandenen Schritt-Buchungen und einer idempotenten abschließenden HEAD-Buchung.
  Start, Kapazitäten, Schrittfortschritt und Tau verwenden dieselbe SQLite-Transaktion.
  Die Produkt-Composition wird erst im vollständigen Cutover umgestellt.
- Echte SQLite-Integration auf API 26/35: **12 Tests ohne Fehler/Skip**, Test-APK ebenfalls
  erfolgreich gebaut (1m 11s). Enthalten sind Wäsche 1/3, unabhängige Parallel-Wartephasen,
  Nachfrage ohne vorzeitigen Start, einmaliger Abschluss und absichtlich fehlgeschlagene
  Tau-Buchungen: sowohl Start als auch letzte Folgeaktion rollen vollständig zurück.
- Beim ersten Integrationsversuch wurde ein normaler Aufgabenfilter für offene
  Occurrences verwendet; dieser schließt FLOW_STEP absichtlich aus. Korrigiert wurde
  der Aufrufer, nicht der Filter für normale Aufgaben. Ein Aufruf des nicht vorhandenen
  Debug-Unit-Test-Tasks wurde anschließend auf den vertraglichen Instrumentation-Variant
  korrigiert; kein grüner Nachweis aus dem fehlgeschlagenen Aufruf.
- Geräte-Diagnose aus Lauf `34594147480`: Der Name-Test schrieb auf die nicht editierbare
  Beschriftung, nicht auf `flow-editor:name` (EditText); die Eingabe fehlte bereits vor
  der Neuerstellung. Der Test wählt jetzt das echte Eingabefeld. Der nicht-ziehende
  Zusammenführungs-Test nutzt die angebotene Accessibility-Klickaktion der eindeutig
  identifizierten Knöpfe; Touch und Maus werden weiterhin separat real injiziert.
  Die Bestätigung und Unverändert-vor-Bestätigung-Prüfungen bleiben bestehen.
- Die überarbeitete Geräte-Matrix ist noch nicht nachgewiesen. Editor-Navigation,
  registriertes Schema 25 und gemeinsame Blattprojektion bleiben offene Cutover-Gates;
  dieser Zwischencommit ist ausdrücklich kein veröffentlichungsfähiger Abschluss.

- Schema 25 ist im lokalen Cutover registriert und exportiert. Room bildet expliziten
  Aufgabentyp, Runtime-Schritt-ID, Graph-Kanten, Wartephasen und Speicherquittungen ab.
  Kompilierung sowie gezielte Laufzeit-/Editor-/SQL-Migrationstests erfolgreich (1m 24s).
  Alte Room-Laufzeitoperationen sind vorläufig ausdrücklich gesperrt; ihre produktiven
  Aufrufer werden im selben unveröffentlichten Cutover ersetzt. Die App-Composition
  ist damit noch kein abnahmefähiger Gesamtstand.

- Produkt-Composition verwendet jetzt ausschließlich `GraphFlowRuntime`, Graph-Planung
  und Graph-Blattprojektion. `FlowSetupActivity` ist der neue Compose-Host mit echtem
  Saved-State-ViewModel/Gateway. Der gemeinsame Erstellen-Einstieg unterscheidet Aufgabe
  und Ablauf; bestehende FLOW-Aufgaben werden vor dem alten Editor-Loader umgeleitet.
- Produktionsnaher End-to-End-Test (Speichern, Tagesplanung, Start, Warten, Einsammeln)
  bestätigt auch den Ein-Schritt-Ablauf ohne normale/leere Ersatzblätter. Parallele
  Aktionszeilen adressieren verschiedene stabile Runtime-IDs; reine Wartephasen erzeugen
  kein Heute-Blatt. Zusammen mit den Laufzeittests: **14 Tests grün**, 1m 1s.
- Die erste Projektion verwendete eine leere ID beim Abfragen aller Reservierungen und
  verletzte damit den Repository-Vertrag. Jetzt wird die Belegung aus denselben bereits
  geladenen aktiven Snapshots summiert; keine Speicherung beim Anzeigen.
- Der breite Migrationslauf ist noch rot (sechs historische Fälle auf API 26/35): Die
  alten Einzelmigrations-Fixtures enthalten absichtlich unvollständige Laufzeitzustände
  (Warten ohne Vorgänger, Freigabe außerhalb des verkürzten Snapshots, unzulässige
  Trainingswerte bei einem Dauer-Schritt). Sie müssen ihren historischen Zielstand
  separat prüfen; gültige vollständige Bestandsketten brauchen zusätzlich den echten
  Room-Upgrade-Nachweis auf 25. Keine Abschwächung der neuen Laufzeit-Invarianten.
- Heute-Aktionsdispatcher, Warteübersicht und Alles-Bedienung werden noch verbunden.
  Daher weiterhin kein veröffentlichbarer Gesamtstand und kein Main-/Release-Gate.

### P — UI-Anschluss, Ablaufgrenzen und vollständige Upgrade-Fixtures

- Heute startet/erledigt über Runtime-Schritt-IDs, sammelt separat nach einem letzten
  Warten ein und zeigt kleine, einzeln veränderbare Wartephasen. Der obere Tau-Behälter
  entfällt für Abläufe. Alles adressiert dieselben Warte-IDs; Datenänderungen laden nach.
- Abgelaufene Wartephasen sind verlängerbar, solange kein davon abhängiger Schritt
  ausgeführt wurde. Angebote verschwinden und erscheinen mit derselben Identität;
  unabhängige Zweige bleiben unberührt. Erneute Reservierungen prüfen freie Kapazität
  transaktional, bestehende Belegungen werden nicht entzogen. Neue SQL-Regressionen
  einschließlich abgelaufener Trockenzeit und inzwischen belegter Kapazität sind grün.
- Die neun bisherigen Wäsche-Laufzeitfälle prüfen jetzt die produktive Graph-Composition.
  48 von 49 gezielten Aufgaben-/Wäschetests waren grün; der verbleibende Query-Budget-Test
  deckte doppelte Bestandsabfragen auf. Inventar wird nun wiederverwendet, normale Aufgaben
  ohne Abläufe benötigen keine Graph-Abfragen. Eine vollständige Prüfung folgt separat.
- Der unbenutzte alte FlowSetup-ViewModel samt eigener Entwurfsverwaltung ist entfernt.
  Seine Wiederherstellungsabdeckung übernimmt der neue lifecycle-/parcel-geprüfte Editor.
  Normale Speichervorgänge dürfen keine Abläufe oder Kapazitäten erzeugen/überschreiben;
  die alten kombinierten Editor-Tests sind durch explizite Eigentümergrenzen ersetzt.
- Historische Einzelmigrations-Regressionen bleiben bis Schema 24 erhalten (44 Fälle
  grün). Zusätzlich validiert Room selbst das registrierte 24→25-Upgrade: 44 SQL-Fälle
  grün, einschließlich unveränderter bereits gebuchter Tau-Beträge.
- Der signierte Upgrade-Korpus zielt auf 25. Die alten synthetischen Spalten-Sentinels
  kombinierten DURATION mit Satz-Pausen/Trainingslast und Wartezustand an Position null,
  obwohl beide Schritte schon gewählte Zeiten hatten. Für vollständige Laufzeitproben
  sind das jetzt gültige Satzschritte plus unbegonnener Dauer-Folgeschritt: Position zwei
  wartet nach der zweiten Aktion. Alle Notiz-/Delay-/Load-Sentinels, Nullfälle und
  korrekten Schema-23-Spalten bleiben explizit geprüft; Ressourcen-IDs und Zeitstempel
  werden ebenfalls kontrolliert. Die ursprünglichen künstlichen Einzelspaltenfälle
  bleiben in den historischen Regressionen erhalten, nicht als produktive Laufzeit.
- Neuer Korpus-Test erzeugt jede deklarierte Quellversion, säht jede Spalte, führt die
  registrierten Room-Migrationen bis 25 aus und prüft sämtliche Zielwerte sowie den
  lauffähigen Graph-Snapshot. **9 Korpus-/Upgrade-Tests grün**, einschließlich API 26/35.
  Das ist lokaler Nachweis, noch kein signiertes Geräte-Upgrade/PR/Main/Release.

### P — vollständiger Laufzeitwechsel und Prüfung der echten Bediengrenzen

- `c00e6e66` entfernt den alten linearen Coordinator, Start-/Aktivierungspfad und die
  entsprechende Repository-API. 41 gezielte Laufzeit-/Aufgabenfälle grün. Historische
  Datenklassen dienen nur noch Migrations-/Bestandsproben, nicht einer zweiten Ausführung.
- Der lokale Gesamtversuch auf `545e2d87` bestand 743 Unit-/Robolectric-Tests ohne Fehler
  (ein vorgesehener Skip). Der anschließende Paketbau endete mit Signal 143, daher kein
  grünes Gesamtgate. Ein späterer unveränderter Wiederholungsstand wird nicht behauptet.
- Neue Editor-Goldens wurden zuerst ohne Update-Schalter gerendert, alle drei Bilder
  visuell geprüft, anschließend übernommen und ohne Update-Schalter erfolgreich verglichen:
  Kacheln auf 320 dp und minimaler Schrittdialog mit normaler/160-%-Schrift.
- PR `34602133369` auf `545e2d87`: Quality-Verträge, Goldens und Build grün, Gerätematrix
  rot. API 26 zeigte drei Ursachen: zwei Instrumentierungstests erwarteten den bewusst
  entfernten normalen Ablauf-Einstieg; der Join-Test fand seine Bestätigungsoption nicht.
  Die tatsächliche Editorreise (Erstellen, Speichern, Wiederöffnen, Recreation, Verwerfen)
  bestand auf API 26. Keine Aussage über ein installiertes Produktionspaket.
- Die alten Einstiegstests prüfen nun die Trennung der Editoren. Wiederhergestellte
  normale Editor-Navigation kann nicht auf die historische FLOW-Seite zurückspringen.
  Der nicht ziehende Join-Pfad nutzt semantisches Scrollen/Klicken in einem getrennten
  Compose-Prüfhost; physische Maus-/Touch-Prüfungen behalten ihre echte Frame-Uhr.
- Bei der Bildabnahme des Wäscheblatts fielen abgeschnittene Folgeschrittnamen auf.
  Ablaufzeilen umbrechen jetzt auch bei großer Schrift und zeigen vorhandene Mengen
  rechts. Normale Aufgaben behalten ihre bisherige Zeilendarstellung.
- Die zweite kompakte Ablaufzeile ersetzte ihren expliziten Befehl durch den normalen
  Advance-Befehl. Die Zeilenprojektion erhält jetzt Start, Zeitabfrage, Laufzeitschritt
  und Einsammeln unverändert. Ein echter Klick auf den zweiten Kreis prüft dessen
  Runtime-ID; fünf zusätzliche Aktionsvarianten sind im Projektionsvertrag erfasst.
  Ablaufzeilen bieten keine unzulässige normale Schritt-Umsortierung mehr an.
- Zwei neue Wäscheblatt- und zwei Graph-Warteübersichts-Goldens wurden nach Sichtprüfung
  übernommen. Laufende parallele Schritte zeigen keine historischen Reihenfolge-/Abbruch-
  Aktionen. Der gezielte Lauf ohne Update-Schalter mit Laufzeit- und beiden Golden-Suiten
  ist grün (20 Tests); die übrigen Anforderungen bleiben im vollständigen Gate.
- Ein einschrittiger Start ohne Wartephase zeigt bereits seine abschließende Tau-Summe.
  Auch diese Anzeige wird gegen den echten Laufzeit-/Ledgerfall auf API 26/35 geprüft.
- Neuer physischer Kantenscroll-Test hält eine Kachel in einer langen Folge am unteren
  Rand und bricht danach ab: Scrollen darf die Struktur nicht vorzeitig speichern.
  Das Testpaket baut; die erneute Geräte-/Animationsmatrix ist noch erforderlich.
- Kein physisches ADB-Gerät verbunden. Remote-main weiterhin `f42d625e`, veröffentlichte
  Version weiterhin 0.2.166. Dieser Zwischenstand ist weder Merge- noch Release-Nachweis.

### P — physische Gesten nach einer Strukturänderung

- Das vollständige lokale `check-all.sh` auf `81093df0` ist grün (23m 19s), einschließlich
  Lint, aller Paketvarianten, Identitäts- und Größengrenzen. Der Folgelauf auf `964104a3`
  besteht 746 Tests ohne Fehler; sein abschließender Paketbau läuft noch.
- Die Matrix auf `0f4830bc`/`964104a3` isoliert Kantenscrollen und Espressos auf API 37
  entfernte InputManager-API. Die semantische Compose-Suite bleibt auf den unterstützten
  APIs; eine gleichwertige native Accessibility-Reise prüft Join/Bestätigung/Undo auf
  **allen** APIs, also auch 37. Kein fachlicher Prüffall entfällt.
- Lokaler API-26-Emulator: SwiftShader endete mit SIGSEGV im Emulator, Host-Grafik startet
  dagegen und führt die isolierte Test-App aus. Kein physisches Gerät, keine Änderung
  an Produktionsdaten. Join ist dort über native Accessibility-Aktionen nachgewiesen.
- Der Kantenscroll-Test reproduziert einen echten Fehler nach Graphwechsel: Start schreibt
  den neuen Gestenzustand, Move liest noch den vorherigen. Lokale Funktionsreferenzen können
  trotz geänderter eingefangener Zustände gleich vergleichen; `rememberUpdatedState` behielt
  dadurch alte Callbacks. Der Gestenadapter aktualisiert sie jetzt nach Referenzidentität.
  Ein zusätzlicher Drag nach Undo deckt dieselbe Grenze ab. Der Viewport hat außerdem einen
  eigenen nicht scrollenden Messcontainer; die freigegebenen Goldens bleiben unverändert.
- Temporäre Diagnoselogs sind entfernt. Testpaketbau grün; lokale Bedienreise und erneute
  vollständige Geräte-/Animationsmatrix stehen für diese Korrektur noch aus.

- Auf `5780d8e7` sind alle neun lokalen Editor-Gerätefälle mit Animation 1.0 grün
  (4m 47s), zusätzlich alle sieben nativen Fälle mit reduzierter Animation 0.5 (2m 45s).
  Die vollständigen PR-Gerätejobs auf API 26 und 35 sind ebenfalls grün.
- API 37 trifft beim erfolgreichen Wegscrollen ein inzwischen ungültiges UiObject2;
  der Test fragt die Abwesenheit jetzt erneut ab. Die native Join-Reise las außerdem
  sechsmal unmittelbar denselben alten Accessibility-Baum, bevor Compose das neue Ziel
  einfügte (im Fehler-Hierarchiedump bereits sichtbar). Sie wartet nun begrenzt auf den
  aktuellen, aufgefrischten Baum sowie auf den angewendeten Graphzustand. Vor Übernehmen
  wird weiterhin nachgewiesen, dass die sichtbare Vorschau noch nichts gespeichert hat.
  Nur die Tests ändern sich; erneute Matrix erforderlich, kein vorgezogenes Merge-Gate.

- Auf `fa7e92b0` bestehen PR-Geräteprüfungen API 26/35/37 sowie Animation 26/37;
  Animation 35 läuft zu diesem Zeitpunkt noch. Ein zusätzlicher lokaler Durchlauf unter
  hoher Hostlast erwischt die erste Touch-Geste zu früh. Die native Suite wartet nun
  ausdrücklich auf Fensterfokus und einen stabilen gezeichneten Zustand, sendet Touch
  mit demselben synchronen Pointer-Helfer wie Maus/Abbruch und wartet auf die angewendete
  Struktur. Kein Ausschalten von Animationen und keine Wiederholung fehlgeschlagener Gesten.
  Alle sieben Fälle mit Animation 1.0 sind danach lokal grün (3m 5s). Diese letzte Änderung
  betrifft ausschließlich Teststeuerung; der produktive Stand bleibt `5780d8e7`.
