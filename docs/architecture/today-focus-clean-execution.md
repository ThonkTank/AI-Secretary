# Ausführung: saubere Heute-Aufgabenblatt-Architektur

Kanonische Grundlage:
[Roadmap: saubere Heute-Aufgabenblatt-Architektur](today-focus-clean-roadmap.md)

Dieses append-only Protokoll hält Phasenpläne, Validierung, Audits, Korrekturrunden und Blocker
getrennt von der kanonischen Roadmap fest. Frühere Befunde werden durch spätere Runden nicht
umgeschrieben.

## Phase 0 – Zielvertrag und reproduzierbare Ausgangslage

Status: implementiert

### Plan

Ergebnis: Roadmap, Ausführungsprotokoll und ADR-031 bilden den verbindlichen Zielvertrag. Der
aktive alte Frontend-Checkout und der unveröffentlichte Sammelbranch bleiben unverändert.

Reihenfolge:

1. Frischen Worktree von `origin/main` erzeugen und Branch-/Remotezustand verifizieren.
2. Roadmap ohne inhaltliche Umdeutung persistieren, ADR-031 ergänzen und beide Dokumente im
   Architekturindex verlinken.
3. Schema-, Migrations-, Golden- und Ressourcenbaselines erfassen.
4. Dokumentationsdiff, CI-/Release-Vertragstests und anwendbaren Gradle-Gate prüfen.
5. Implementierung getrennt gegen diesen Phasenplan und die vollständige Roadmap auditieren.

Abnahme: Der Diff enthält ausschließlich die drei neuen Architekturtexte und den aktualisierten
Index. Produkt-, Schema-, Migrations-, Ressourcen- und Golden-Dateien bleiben unverändert. Der
Remote-Abschluss erfolgt anschließend über Pull Request, Squash-Merge und Main-Workflow.

### Ausgangsnachweise

- `origin/main`: `39f1e5cdb37ac048c3bd2bfa101be110de2daafa`.
- Referenzbranch: `codex/today-focus-sheet-fixes` auf `be0d7f22d3794c5674734dc43be8380e290199df`,
  ausschließlich lokal.
- Schema 22: `b4f8f0a32b84cfcbc5e70a02018cc1b6bbe120682e98061a8e7773f0f75563e2`.
- Migrationen: `bed6d1c550acbe78ffd724b3057f011366a7447ef3d89854c88841a9aa6b1d9a`.
- kombinierte Focus-/Homescreen-Goldens:
  `21d1fbc4ca705263dbc83ec65efcee89502e20a552cd30421459281fd987f7a8`.
- kombinierte Android-Ressourcen:
  `139cb0cdcc1157b75689a6e90d9c2292f41af6a45539ebb6985123b400d16d0c`.

### Validierung

- `git diff --check`: grün.
- CI-Vertragstests: 16 Tests grün.
- Release-Vertragstests: 23 Tests grün.
- Der tatsächliche Phase-0-Diff wird als reine Dokumentationsänderung erkannt; Quality-,
  Instrumentierungs- und Release-Gate sind nicht erforderlich.
- Schema-22-, Migrations-, Golden- und Ressourcenhashes entsprechen den Ausgangsnachweisen.

### Audit und Abweichungen

- Planabgleich: Roadmap, getrenntes append-only Protokoll, ADR-031, Architekturindex und
  Ausgangsbaselines sind vollständig vorhanden.
- Roadmapabgleich: Der unveröffentlichte Sammelbranch wurde weder verändert noch pauschal
  übernommen. Produktcode, Tests, Schema, Migrationen, Ressourcen und Goldens sind unverändert.
- Der aktive Frontend-Checkout blieb auf `5438e733` unangetastet.
- Es wurde keine inhaltliche Abweichung gefunden; eine Korrekturrunde war nicht erforderlich.
- Der lokale Stand erfüllt Phase 0. Pull Request, Squash-Merge und Main-Workflow stehen als
  Remote-Gate noch aus; bis dahin bleibt der Status `in Arbeit`.

### Korrekturrunde 1 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge und Main-Workflow wird ausschließlich der
Phase-0-Status auf `implementiert` gesetzt. Die früheren lokalen Nachweise und der damalige
Audittext bleiben unverändert. Produkt-, Schema-, Migrations-, Ressourcen- und Golden-Dateien
werden nicht geändert.

Ergebnis: PR 316 wurde als `c81416c802a26458f31355365c7683425da3202b` nach `main`
gesquasht. Der exakte Main-Workflow 33742146087 ist grün; Quality, Instrumentierung, Upgrade,
Packaging und Publish wurden für die reine Dokumentationsphase vertragsgemäß übersprungen.
Phase 0 ist damit implementiert und Phase 1 freigegeben.

## Phase 1 – Eine einzige Fokusprojektion

Status: implementiert

### Plan

Ergebnis: `:today-core` liefert für das aktuelle Aufgabenblatt eine einzige fertige
Zeilenprojektion. Kanonische Reihenfolge, lokale Auswahl und Reorder-Preview bleiben getrennt;
Android-Views rendern nur noch Modus und Aktion der projizierten Zeile.

Reihenfolge:

1. `FocusStepUiModel` auf abgeschlossene Identität plus aktive Ausführungsaktion reduzieren und
   `FocusStepStatus` entfernen.
2. `TodayFocusProjector`, `FocusStepListUiModel`, `FocusStepRowUiModel` und den expliziten
   Zeilenmodus einführen; Auswahl und Preview dort zu einer fertigen Darstellung projizieren.
3. `TodayFeatureState.selectedStepId` und die Auswahlaktion in Reducer und Coordinator ergänzen;
   Preview, Cancel und Fehler dürfen `TodayUiModel` nicht verändern.
4. Wiederholungseingabe, vollständigen Focus-Card-Bind-Vertrag, Titelinteraktion und
   Accessibility auf dieselbe Projektion umstellen.
5. Fokussierte Tests, vollständigen Gradle-Gate sowie getrennten Plan- und Roadmap-Audit
   durchführen.

Abnahme: Bei kanonischem `A, B, C` zeigt die Auswahl von `C` ohne Command oder Persistenzzugriff
`C, A, B`; nur `C` ist `EXPANDED`. Rebind derselben Occurrence erhält die Auswahl, während
Abschluss, Entfernung, Occurrence-Wechsel und Reorder-Start sie verwerfen. Negative Scans finden
keine Status-, Promotion-, Doppelaktions- oder View-Sortierwahrheit.

### Implementierung und Validierung

- `TodayFeatureState.selectedStepId` ist der einzige lokale Auswahlzustand. Die Android-freie
  `TodayFocusProjector` erzeugt `FocusStepListUiModel` und endgültige `FocusStepRowUiModel`s mit
  genau einem Modus und einer Aktion.
- `FocusStepStatus` wurde entfernt. `FocusStepUiModel.activeAction` beschreibt nur noch die
  Aktion bei expandierter Darstellung; kompakte und Assistant-Aktionen entstehen ausschließlich
  in der Projektion.
- Reorder-Preview, Cancel und Fehler lassen dieselbe `TodayUiModel`-Instanz unverändert. Nur eine
  bestätigte Reihenfolge wird kanonisch übernommen; dabei bleiben Backlog und Flow-Runs erhalten.
- Titelklick und die Accessibility-Aktion „Schritt nach vorn holen“ emittieren dieselbe lokale
  `SELECT_STEP`-Aktion. Der Wiederholungseingabereducer konsumiert dieselbe expandierte Zeile.
- `FocusTaskView` besitzt genau einen vollständigen Bind-Einstieg mit `FocusCardUiModel`.
- Fokussierte Reducer-, Coordinator-, Projector-, Repetition-, View-, Flow- und
  Architekturtests sind grün.
- Vollständiger Gate
  `./gradlew testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease`: grün in 19 min 8 s.
- Testresultat: 529 Tests, 0 Fehler, 0 Abbrüche, 1 bestehender Skip.
- Schema 22 bleibt
  `b4f8f0a32b84cfcbc5e70a02018cc1b6bbe120682e98061a8e7773f0f75563e2`; Migrationen bleiben
  `bed6d1c550acbe78ffd724b3057f011366a7447ef3d89854c88841a9aa6b1d9a`.

### Plan-Audit

- Schritt 1 erfüllt: Statusenum und positionsabhängige Aktivaktion sind entfernt.
- Schritt 2 erfüllt: sichtbare Reihenfolge, Modus und finale Aktion besitzen genau einen
  Projektor in `:today-core`.
- Schritt 3 erfüllt: Auswahlzustand und Auswahlaktion liegen in Reducer/Coordinator; Preview
  ersetzt das kanonische Modell nicht.
- Schritt 4 erfüllt: Eingabe, Titelinteraktion, Accessibility und Android-Bind konsumieren die
  fertige Projektion.
- Schritt 5 lokal erfüllt: fokussierte und vollständige Gates sowie negative Scans sind grün.

### Roadmap-Audit und Abweichungen

- Das Szenario `A, B, C` → Auswahl `C` ist als Reducer-, Projector- und View-Interaktionstest
  belegt. Es entsteht kein Command; die kanonische Reihenfolge bleibt `A, B, C`.
- Auswahl-Rebind, Abschluss, Entfernung, Occurrence-Wechsel und Reorder-Start sind separat
  getestet. Die Auswahl wird nicht gespeichert und löst keine Animation aus.
- Produktionsscans finden weder `FocusStepStatus`, `promotedStepId`,
  `activeExecutionAction`, `executionAction`, `promoteForDisplay` noch einen View-eigenen
  `activeStepId`-Algorithmus.
- Room-Schema, Migrationen und Snapshot-Semantik wurden nicht verändert. Die neuen Ressourcen
  sind ausschließlich die explizite Accessibility-Aktion und deren Rückmeldung.
- Der aktive alte Frontend-Checkout steht unverändert auf `5438e733`.
- Es wurde keine inhaltliche Abweichung gefunden; eine Korrekturrunde war lokal nicht
  erforderlich. PR, Squash-Merge und exakter Main-Workflow bleiben das Remote-Gate der Phase.

### Korrekturrunde 1 – API-26-Langdruckziel

Plan: Die erste PR-Ausführung 33747178136 bestand Quality sowie Instrumentierung auf API 35 und
37. Auf API 26 scheiterten in normaler und aktivierter Animation ausschließlich die drei
zeigerbasierten Today-Langdrucktests; Accessibility-Reorder und alle übrigen Tests waren grün.
Beide Diagnoseartefakte zeigen, dass der Zeiger im Titelbereich der ersten Zeile lag und keine
`BEGIN_REORDER`-Aktion entstand. Der Titel wurde für die neue lokale Auswahl zum eigenen
Tap-Ziel; auf API 26 übernimmt dieses Kind den Pointer, ohne den Langdruck an den Zeilenkörper
weiterzureichen.

Korrektur: Der Titel erhält denselben Langdrucklistener wie der Zeilenkörper. Ein kurzer Tap bleibt
ausschließlich bei kompakten Titeln die lokale Auswahl; ein Langdruck auf Titel oder Restzeile
startet weiterhin denselben Reorder-Pfad. Ein Robolectric-Test prüft beide Listener auf einer
kompakten Zeile. Danach werden fokussierte Tests, vollständiger lokaler Gradle-Gate und alle
PR-Matrixjobs erneut ausgeführt.

Ergebnis: Titel und Zeilenkörper teilen den identischen Langdrucklistener; der Test verwendet den
Titel der expandierten ersten Zeile als Langdruckziel und belegt `BEGIN_REORDER` plus
kompensierendes `CANCEL_REORDER`, wenn Robolectric keinen Plattform-Drag startet. Der fokussierte
Testblock einschließlich Instrumentierungs-APK ist grün. Der vollständige lokale Gradle-Gate ist
erneut grün in 14 min 32 s. Die vollständige Wiederholung der PR-Matrix 33749738904 ist grün:
Quality, normale Instrumentierung und Instrumentierung mit Animationen bestehen jeweils auf API
26, 35 und 37; Instrumentation- und Pull-Request-Gate sind erfolgreich.

### Korrekturrunde 2 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge und exaktem Main-Workflow wird ausschließlich der
Phase-1-Status auf `implementiert` gesetzt und der Remote-Nachweis ergänzt. Die früheren
Implementierungs-, Validierungs-, Audit- und Korrekturbefunde bleiben unverändert.

Ergebnis: PR 318 wurde als `bbe7820a3807d4bc0835f136bc369f743a6049e0` nach `main`
gesquasht. Der exakte Main-Workflow 33751400918 ist vollständig grün; Quality, normale und
animierte Instrumentierung auf API 26, 35 und 37, Packaging, Upgrade auf API 26, 35 und 37 sowie
Publish sind erfolgreich. Phase 1 ist damit implementiert und Phase 2 freigegeben.

## Phase 2 – Stabile Zeilenidentität und atomare Metadaten

Status: implementiert

### Plan

Ergebnis: Eine sichtbare Schrittzeile gehört während ihrer gesamten Lebenszeit zu genau einer
Kombination aus Occurrence- und Schritt-ID. Jeder Bind-Vorgang beschreibt alle Teilbereiche samt
leerem Zustand vollständig. Neue Occurrence- und Flow-Snapshots entstehen atomar über eine
gemeinsame Factory; der vollständige Konstruktor ist nur noch eine benannte Rehydrationsgrenze.

Reihenfolge:

1. Den positionsbasierten Zeilencache durch einen Cache für `occurrenceId + stepId` ersetzen und
   ihn bei Occurrence-Wechsel vollständig verwerfen. Layout, Reward-Anker, Grain-Anker und
   Reorder-Ziele konsumieren dieselbe sichtbare ID-Reihenfolge.
2. `FocusStepRowView.bind` in immer aufgerufene Teilbinder für Identität/Text, Aktion,
   Wiederholung, Training, Assistent, Timer, Listener und Accessibility zerlegen. Jeder Teilbinder
   setzt Text, Sichtbarkeit, Aktivierung und Listener sowohl für den belegten als auch den leeren
   Zustand; ein globales Reset wird nicht eingeführt.
3. Eine gemeinsame `StepSnapshotFactory` für Template-, Carry-forward-, Flow- und
   Flow-Angebots-Snapshots einführen. `OccurrenceStep.rehydrate` und
   `FlowRunStepSnapshot.rehydrate` bleiben die einzigen vollständigen Feldgrenzen für Persistenz
   und Tests; produktive Assembler kombinieren keine Felder mehr positionsweise.
4. Rebind-Matrix, Neun-Schritte-Szenario, eindeutige Metadaten durch Materialisierung, Move,
   Carry-forward, Flow und Dashboard sowie die Migrationspfade 8→22 und 19→20→21→22
   automatisiert belegen.
5. Negative Architektur-Scans, unveränderte Schema-/Migrationshashes, den vollständigen
   Gradle-Gate und getrennte Plan-/Roadmap-Audits ausführen; danach PR, Squash-Merge und exakten
   Main-Workflow abwarten.

Abnahme: Derselbe Zeilenschlüssel besteht die Zustandswechsel Notiz→leer, Menge→leer,
Training→Körpergewicht, Wiederholung→normal, Timer→leer und Assistent→leer ohne Resttexte,
Restlistener oder Restansichten. Nach acht Abschlüssen ist „Römische Liege“ sauber aktiv.
`sourceTemplateId`, Notiz und vollständige Prescription bleiben auf allen Snapshot-Pfaden an
derselben stabilen ID; Schema 22 und vorhandene Migrationen werden nicht verändert.

### Implementierung und Validierung

- `FocusStepListLayout` hält Zeilen in einem Cache aus Occurrence- und stabiler Schritt-ID. Ein
  Wechsel des Aufgabenblatts verwirft den Cache vollständig; sichtbare Zeilen, Reward-Anker,
  Grain-Anker und Reorder-Ziele verwenden dieselbe ID-Reihenfolge.
- `FocusStepRowView.bind` bindet Oberfläche, Text, Assistent, Wiederholung, Aktion, Training und
  Timer vollständig. Nicht vorhandene Inhalte leeren Text, Sichtbarkeit, Zustand, Animationen und
  Listener; Interaktion und Accessibility werden bei jedem Bind neu gesetzt.
- `StepSnapshotFactory` ist die einzige produktive Erzeugungsgrenze für Template-,
  Carry-forward-, Flow- und Flow-Angebots-Snapshots. Sie übernimmt Text, komplette Prescription,
  Notiz und `sourceTemplateId` gemeinsam. Vollständige Konstruktoren sind privat;
  `rehydrate` ist die benannte Persistenz- und Testgrenze.
- Die Rebind-Matrix, das reale Neun-Schritte-Trainingsszenario sowie Materialisierung, Move,
  Carry-forward, Flow, Dashboard-Mapping und die Migrationspfade 8→22 und 19→20→21→22 sind
  durch fokussierte Tests abgedeckt.
- Vollständiger Gate
  `./gradlew testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease`: grün in 20 min 4 s.
- Testresultat: 541 Tests, 0 Fehler, 0 Abbrüche, 1 bestehender Skip.
- Schema 22 bleibt
  `b4f8f0a32b84cfcbc5e70a02018cc1b6bbe120682e98061a8e7773f0f75563e2`; Migrationen bleiben
  `bed6d1c550acbe78ffd724b3057f011366a7447ef3d89854c88841a9aa6b1d9a`.

### Plan-Audit

- Schritt 1 erfüllt: Zeilen werden nach Occurrence- und Schritt-ID wiederverwendet; alle
  sichtbarkeitsabhängigen Verbraucher folgen derselben Reihenfolge.
- Schritt 2 erfüllt: Jeder Teilbereich besitzt einen immer aufgerufenen Binder mit explizitem
  Leerzustand. Ein globales Reset und alte Restlistener existieren nicht.
- Schritt 3 erfüllt: Alle produktiven Snapshot-Pfade verwenden die gemeinsame Factory;
  Persistenzmapper verwenden ausschließlich die benannten Rehydrationsgrenzen.
- Schritt 4 erfüllt: Rebind-, Neun-Schritte-, Metadaten-, Move-, Carry-forward-, Flow-,
  Dashboard- und Migrationstests sind grün.
- Schritt 5 lokal erfüllt: Architektur-Scans, unveränderte Hashes und vollständiger Gate sind
  grün. PR, Squash-Merge und exakter Main-Workflow bleiben das Remote-Gate der Phase.

### Roadmap-Audit und Abweichungen

- Die Zeileninstanz bleibt bei lokaler Auswahl und Reorder desselben Aufgabenblatts an ihrer
  stabilen ID; beim Aufgabenblattwechsel entsteht eine neue Instanz.
- Notiz→leer, Menge→leer, gewichtetes Training→Körpergewicht, Wiederholung→normal,
  Timer→leer und Assistent→leer sind einschließlich nicht mehr auslösbarer Listener belegt.
- Nach acht Abschlüssen ist „Römische Liege“ die aktive neunte Zeile ohne Notiz und ohne
  Trainingslast; die eindeutigen Metadaten der anderen Schritte bleiben an ihren IDs.
- Produktionsscans finden direkte vollständige Konstruktoraufrufe nur innerhalb der beiden
  Modellklassen. Produktive Rehydration liegt ausschließlich in der Snapshot-Factory und den
  Persistenzmappern; `resetForBind` existiert nicht.
- Room-Schema und Migrationsimplementierung sind unverändert. Es gibt keine Titel-, Positions-
  oder `kg`-Heuristik und keine Bereinigung historischer Snapshots.
- Der aktive alte Frontend-Checkout steht unverändert auf `5438e733`.

### Korrekturrunde 1 – stabile Reihenfolge ohne View-Detach

Plan: Der erste vollständige Gate war grün. Der anschließende Plan-Audit zeigte jedoch, dass
stabile Zeileninstanzen beim lokalen Umordnen mit `removeView` und `addView` kurz vom Parent
getrennt wurden. Identität und Daten blieben zwar korrekt, laufende Gesten oder
View-Animationen konnten durch den Detach aber unnötig unterbrochen werden.

Korrektur: Vorhandene Zeilen bleiben dauerhaft im selben Parent und werden mit
`bringChildToFront` in die projizierte Reihenfolge gebracht. Nur neue IDs werden hinzugefügt,
entfernte IDs gelöscht und ein Aufgabenblattwechsel verwirft den gesamten Cache. Ein fokussierter
View-Test belegt Instanzidentität und Reihenfolge; danach wurde der vollständige Gate erneut
ausgeführt.

Ergebnis: Der fokussierte View- und Architekturtestblock ist grün. Der erneute vollständige Gate
ist in 20 min 4 s grün; 541 Tests bestehen ohne Fehler oder Abbruch, bei einem bestehenden Skip.
Es wurde keine weitere lokale Abweichung gefunden. Phase 2 bleibt bis zum Remote-Gate
`in Arbeit`.

### Korrekturrunde 2 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge und exaktem Main-Workflow wird ausschließlich der
Phase-2-Status auf `implementiert` gesetzt und der Remote-Nachweis ergänzt. Implementierung,
lokale Validierung und Auditbefunde bleiben unverändert.

Ergebnis: PR 320 wurde als `66da944fda8ba44207eac0712cdaeae50e4bf902` nach `main`
gesquasht. Die vollständige PR-Matrix 33760930254 ist grün: Quality sowie normale und animierte
Instrumentierung auf API 26, 35 und 37 bestehen. Der exakte Main-Workflow 33762642029 ist
vollständig grün; Quality, dieselbe Instrumentierungsmatrix, Packaging, Upgrade auf API 26, 35
und 37 sowie Publish sind erfolgreich. Phase 2 ist damit implementiert und Phase 3 freigegeben.

## Phase 3 – Präzise Grain- und Gefäßgeometrie

Status: in Arbeit

### Plan

Ergebnis: Grain-Abschwächung folgt ausschließlich der tatsächlich sichtbaren Geometrie jeder
gerenderten Textzeile. Das XP-Gefäß besitzt eine gemeinsame mathematische Kreisgeometrie für
Füllung, Wasserlinie und Clip; rechteckige Software-Schatten entfallen.

Reihenfolge:

1. `GrainOcclusion` als explizite Geometriequelle für Textzeilen und bewusst flächige
   Nicht-Text-Elemente einführen. `GrainSpec` akzeptiert keine rohen Text-Views mehr;
   `LeafSurface` löst nur noch die von den Quellen gelieferten Rechtecke auf.
2. Textrechtecke aus dem finalen Android-`Layout` ableiten: tatsächliche linke/rechte
   Zeilenausdehnung, Compound-/Extended-Padding, vertikale Ausrichtung, Scrolloffset,
   Leerzeilen, Ellipsierung sowie Clipping an View und Vorfahren berücksichtigen.
3. Alle Header-, Aufgabenblatt-, Schritt- und Aufgabenblattlisten-Grain-Quellen auf den neuen
   Vertrag umstellen und den Titel-Schritt-Abstand von 24 auf 12 dp reduzieren.
4. `XpVesselView` auf einen gemeinsamen Innenkreis-Clip umstellen. Die Wasserlinie verwendet die
   Kreissehne an exakt derselben Füllhöhe, wird bei 0 und 100 Prozent nicht gezeichnet; Software-
   Layer und Combo-Schatten werden entfernt.
5. Geometrie-, Pixel-, Render- und Golden-Tests für kurze, gewichtete, leere, mehrzeilige und
   ellipsierte Texte, Satz-/Wiederholungszeilen, 12-dp-Abstand und XP-Füllstände 0/25/50/100
   ergänzen beziehungsweise aktualisieren.
6. Fokussierte Tests, vollständigen Gradle-Gate sowie getrennte Plan-, Roadmap- und
   phasenübergreifende Audits ausführen. Anschließend die vorgeschriebene physische Abnahme
   anhand der gemeldeten Ansichten durchführen; erst danach darf der finale PR entstehen.

Abnahme: Außerhalb sichtbarer Glyphenzeilen bleibt das Grain unverändert; Leerzeilen und der
unbeschriftete Rest einer gewichteten Zeile maskieren nichts. Satz-/Wiederholungszeilen besitzen
keine versetzten oder doppelten Ringe. Der Titelabstand beträgt 12 dp. XP-Füllung und Wasserlinie
bleiben in allen vier Referenzzuständen im Innenkreis, ohne degenerierte Linie, rechteckigen
Schatten oder überstehenden Strich.

### Korrekturrunde 1 – plattformgetreue Geometrie-Testmetriken

Plan: Der erste fokussierte Testlauf kompiliert die neue Produktionsgeometrie, meldet aber zwei
Fehler im neuen Test-Harness. Der Architekturtest sucht `dimens.xml` relativ zur Repository-Wurzel,
während Gradle den Test mit dem App-Modul als Arbeitsverzeichnis ausführt. Der Textgeometrietest
fordert außerdem pauschal mehr als 20 px vertikalen Versatz; Android bestimmt diesen Wert jedoch
aus Extended Padding, finaler `Layout`-Höhe und vertikaler Gravity.

Korrektur: Die Ressourcenauflösung erhält denselben wurzel-/modulunabhängigen Pfadvertrag wie die
Quelltextprüfungen. Die vertikale Erwartung wird aus den tatsächlichen Android-Layoutmetriken
berechnet; Padding, Gravity und Scroll bleiben weiterhin separat belegt. Danach wird der gesamte
fokussierte Block erneut ausgeführt.

Ergebnis: Ressourcenpfad und vertikale Erwartung sind plattformgetreu. Alle neun fokussierten
Geometrie-, Abstand- und Architekturtests sind grün; eine Produktionskorrektur war für diese
beiden Test-Harness-Abweichungen nicht erforderlich.

### Korrekturrunde 2 – erneute Sichtprüfung eines Golden-Zustands

Plan: Nach der Sichtprüfung hat der geschützte Golden-Update-Lauf alle vier Aufgabenblatt- und
zehn der elf geänderten Homescreen-Baselines übernommen. Der Zustand `later` wurde abgelehnt,
weil sein frisches Rendering nicht mehr bitgenau zum zuvor erzeugten Expected-/Actual-/Diff-
Triplet passte. Der Schutzmechanismus darf nicht umgangen werden.

Korrektur: `later` wird erneut ohne Update-Flag gerendert, das neue Actual-/Diff-Bild separat
geprüft und erst danach in einem zweiten Update-Lauf übernommen. Anschließend laufen alle
betroffenen Golden-Kataloge noch einmal ohne Update-Flags.

Ergebnis: Der frische `later`-Diff betrifft ausschließlich die beabsichtigte zeilenweise
Grain-Abschwächung im Kopfblatt und wurde visuell freigegeben. Der zweite geschützte Update-Lauf
ist grün. Anschließend bestehen die neuen Focus-Rendering-, bestehenden Focus-Task- und
Homescreen-Golden-Kataloge gemeinsam read-only in 1 min 21 s.

### Korrekturrunde 3 – aktualisierte lokale JDK-Laufzeit

Plan: Der erneute vollständige Gate startete nicht, weil der zuvor verwendete Pfad
`/usr/lib/jvm/java-21-openjdk` nach einer Systemaktualisierung nur noch Dokumentation, aber keine
Java-Binärdateien enthält. Dies ist eine lokale Umgebungsabweichung; Quelltext und Tests wurden
nicht ausgeführt und werden nicht verändert.

Korrektur: Der Gate wird mit dem aktuell installierten vollständigen JDK unter
`/usr/lib/jvm/java-25-openjdk` und weiterhin mit dem festgelegten Android-SDK erneut ausgeführt.
Die Validierungskommandos und der geprüfte Produktstand bleiben unverändert.

Ergebnis: Mit JDK 25 startete derselbe Gate unverändert und war in 7 min 21 s grün. Der
abgebrochene Versuch hatte zuvor weder Quelltext noch Tests ausgeführt.

### Implementierung und lokale Validierung

- `GrainSpec` konsumiert ausschließlich `GrainOcclusion`-Quellen. Textquellen lösen pro
  sichtbarer Layoutzeile ein lokales Rechteck aus tatsächlicher Zeilenbreite, Compound- und
  Extended-Padding, vertikaler Ausrichtung und Scrolloffset auf. Leerzeilen werden übersprungen;
  Ellipsierung sowie Clipping an View und Vorfahren werden berücksichtigt.
- `LeafSurface` kennt keine `TextView` mehr und verarbeitet nur die bereits aufgelösten
  Rechtecke. Die bewusst flächigen Wiederholungsbalken verwenden eine ausdrücklich benannte
  Bounds-Occlusion.
- Header, Aufgabenblätter, Fokuskarte, Schrittlisten, Schrittzeilen und Wiederholungssteuerung
  verwenden denselben Occlusion-Vertrag. Der Titel-Schritt-Abstand beträgt exakt 12 dp.
- `XpVesselView` berechnet Füllhöhe und Kreissehne aus derselben Kreisgleichung und clippt
  Füllung sowie Linie an denselben Innenkreis. Bei 0 und 100 Prozent wird keine Linie gezeichnet;
  Software-Layer und Combo-Schatten existieren nicht mehr.
- Geometrie- und Golden-Tests decken kurze, gewichtete, leere, mehrzeilige und ellipsierte Texte,
  Vorfahren-Clipping, Satz-/Wiederholungsringe, den 12-dp-Abstand und XP-Füllstände 0, 25, 50 und
  100 Prozent ab. Alle neuen und angepassten Goldens wurden vor Übernahme einzeln visuell geprüft
  und anschließend ohne Update-Flags erneut ausgeführt.
- Vollständiger Gate
  `./gradlew testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease`: grün in 7 min 21 s auf dem finalen
  Testbestand.
- Testresultat: 551 Tests, 0 Fehler, 0 Abbrüche, 1 bestehender Skip.
- Schema 22 bleibt
  `b4f8f0a32b84cfcbc5e70a02018cc1b6bbe120682e98061a8e7773f0f75563e2`; Migrationen bleiben
  `bed6d1c550acbe78ffd724b3057f011366a7447ef3d89854c88841a9aa6b1d9a`.

### Plan-Audit

- Schritt 1 erfüllt: `GrainOcclusion` trennt Textgeometrie von absichtlich flächiger Geometrie;
  `GrainSpec` akzeptiert keine View-Liste mehr.
- Schritt 2 erfüllt: Die Textrechtecke werden aus dem finalen Android-Layout samt Breite,
  Padding, Gravity, Scroll, Leerzeilen, Ellipsierung und sichtbarem Vorfahren-Clip gewonnen.
- Schritt 3 erfüllt: Alle vorgesehenen Today-Grain-Quellen sind umgestellt; der Abstandstest
  belegt 12 dp.
- Schritt 4 erfüllt: Innenkreis, Füllfläche und Kreissehne teilen eine Geometrie; die beiden
  Randzustände besitzen keine Wasserlinie und der alte Schatten-/Softwarepfad ist entfernt.
- Schritt 5 erfüllt: Geometrie-, Render- und Golden-Abnahmen einschließlich einer eigenen
  Satz-/Wiederholungsansicht sind grün.
- Schritt 6 lokal erfüllt: fokussierte Tests, finaler vollständiger Gate sowie getrennte Audits
  sind grün. Die physische Abnahme, der finale PR und das Remote-Gate bleiben ausdrücklich offen.

### Roadmap-Audit und Abweichungen

- Außerhalb der tatsächlich sichtbaren Glyphenzeilen wird keine Text-Occlusion erzeugt. Eine
  gewichtete kurze Zeile maskiert ihren unbeschrifteten Rest nicht; eine leere Layoutzeile
  erzeugt überhaupt kein Rechteck.
- Mehrzeilige und ellipsierte Texte bleiben innerhalb ihrer sichtbaren View- und
  Vorfahren-Grenzen. `LeafSurface` enthält weder `TextView` noch eine Text-Sonderbehandlung.
- Das eigene Satz-/Wiederholungs-Golden zeigt ein einziges ausgerichtetes Ringsystem über Titel,
  Menge und aktiven Bedienelementen; es gibt keine duplizierte oder versetzte Ringquelle.
- Die XP-Goldens für 0, 25, 50 und 100 Prozent zeigen keinen rechteckigen Schatten und keine über
  den Innenkreis hinausreichende Wasserlinie. Die mathematischen Randzustände sind zusätzlich
  direkt getestet.
- Negative Produktionsscans finden keine alten `fadedText`-/`grainTextViews`-Verträge, keine
  lokale Focus-Promotion, kein `resetForBind` und in `XpVesselView` weder `setLayerType`,
  `setShadowLayer` noch `clipRect`.
- Room-Schema, Migrationen und Snapshot-Semantik sind unverändert. Es wurde keine Titel-,
  Positions- oder KG-Heuristik eingeführt.
- Einziger offener Roadmap-Punkt ist die vorgeschriebene physische Abnahme: Am Host ist aktuell
  kein Android-Gerät per ADB verbunden. Deshalb werden Phase 3, finaler Cross-Phase-Audit, PR und
  Squash-Merge noch nicht als abgeschlossen markiert.

### Phasenübergreifender Audit – lokaler Stand

- ADR-031 ist im aktuellen Code durch genau eine Fokusprojektion in `:today-core`, getrennte
  kanonische/Preview-/Auswahlzustände, genau einen Zeilenmodus und eine ausführbare Aktion je
  Zeile umgesetzt.
- Zeilen werden über Occurrence- und stabile Schritt-ID wiederverwendet; vollständige Teilbinder
  verhindern Restzustände. Snapshot-Erzeugung läuft über `StepSnapshotFactory`, während die
  benannten `rehydrate`-Grenzen Persistenz und Testfixtures vorbehalten bleiben.
- Grain- und Gefäßgeometrie entsprechen nun demselben ADR-Zielvertrag. Die Entfernungslisten aller
  drei Produktphasen sind im Produktionscode leer, der vollständige lokale Gate ist grün und der
  alte aktive Frontend-Checkout bleibt unangetastet.
- Dieser Audit ist noch nicht der abschließende Freigabeaudit: Nach der Geräteabnahme werden deren
  Befunde ergänzt und der vollständige Cross-Phase-Check unmittelbar vor dem finalen PR erneut
  bestätigt.

### Korrekturrunde 4 – physisches Geräte-Gate aufgehoben

Plan: Der Owner hebt die physische Geräteabnahme ausdrücklich als Freigabegate auf. Die
kanonische Roadmap wird entsprechend präzisiert, ohne die historischen Pläne, Befunde oder den
damaligen Blocker im append-only Ausführungsprotokoll umzuschreiben. An die Stelle des Gerätes
tritt keine schwächere Einzelprüfung: Die bereits vorhandenen Geometrie-, Render- und
Golden-Abnahmen müssen gemeinsam alle gemeldeten Ansichten abdecken. Danach werden der finale
Cross-Phase-Audit, eine lokale Abschlussvalidierung, PR, Checks, Squash-Merge und der exakte
Main-Workflow in dieser Reihenfolge ausgeführt.

Ergebnis: Die kanonische Roadmap nennt nun die automatisierte visuelle Abnahme und stellt klar,
dass ein physisches Gerät kein Freigabegate ist. Die Goldens für Textgeometrie,
Satz-/Wiederholungsringe, Aufgabenblattabstand und XP-Füllstände wurden erneut direkt geprüft.
Der vollständige lokale Gate am endgültigen PR-Inhalt ist grün; da nur Roadmap und
Ausführungsprotokoll seit dem vorherigen Lauf geändert wurden, waren die Produktaufgaben
erwartungsgemäß aktuell.

### Abschließender Cross-Phase-Audit

- Phase 0 ist nachweislich abgeschlossen: Zielvertrag, ADR-031, Roadmap, getrenntes
  Ausführungsprotokoll und reproduzierbare Baselines wurden über PR 316 gemergt; der exakte
  Main-Workflow 33742146087 ist grün.
- Phase 1 ist nachweislich abgeschlossen: `:today-core` besitzt Auswahl, Reihenfolge, Zeilenmodus
  und ausführbare Aktion. Kanonische Reihenfolge, Reorder-Preview und temporäre Auswahl sind
  getrennt; entfernte Status-, Promotions- und Doppelaktionsfelder fehlen im Produktionscode.
  PR 318 und der exakte Main-Workflow 33751400918 sind grün.
- Phase 2 ist nachweislich abgeschlossen: Zeilenidentität verwendet Occurrence- und stabile
  Schritt-ID, alle Teilbinder beschreiben den leeren Zustand vollständig, und
  `StepSnapshotFactory` ist die produktive Materialisierungsgrenze. Schema 22 und Migrationen
  besitzen weiterhin die festgelegten Hashes. PR 320 und der exakte Main-Workflow 33762642029
  sind grün.
- Phase 3 erfüllt den Zielvertrag: `GrainOcclusion` liefert sichtbare Zeilenrechtecke,
  `LeafSurface` verarbeitet ausschließlich Geometrie, der Titelabstand beträgt 12 dp, und das
  XP-Gefäß verwendet einen gemeinsamen Innenkreis für Füllung und Kreissehne ohne Software-
  Schatten oder degenerierte Randlinie.
- Die automatisierte Sichtabnahme deckt kurze, gewichtete, leere, mehrzeilige und ellipsierte
  Texte, Satz-/Wiederholungsringe, den Aufgabenblattabstand sowie XP bei 0, 25, 50 und 100 Prozent
  ab. Die geprüften Bilder zeigen keine der gemeldeten Masken-, Ring-, Rechteck- oder
  Linienartefakte.
- Der vollständige Gate ist am finalen Inhalt grün: 551 Tests, 0 Fehler, 0 Abbrüche, 1 bestehender
  Skip; Lint, Debug-, Instrumentierungs- und Release-Build bestehen. `git diff --check` und alle
  negativen Architektur-Scans sind sauber.
- Der alte Frontend-Checkout und der unveröffentlichte Referenzbranch wurden nicht verändert.
  Es gibt keinen Versionssprung, keine Veröffentlichung und keine Datenbereinigung.

Audit-Ergebnis: Keine offene lokale Abweichung gegenüber Roadmap, ADR-031, Entfernungslisten,
Snapshot-Vertrag oder sichtbaren Abnahmekriterien. Phase 3 ist bereit für den finalen Pull
Request; Implementierungsstatus und Roadmap-Abschluss folgen erst nach grünen PR-Checks,
Squash-Merge und grünem exaktem Main-Workflow.
