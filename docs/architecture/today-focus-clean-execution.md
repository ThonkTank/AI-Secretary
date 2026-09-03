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

Status: in Arbeit

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
erneut grün in 14 min 32 s. Die Wiederholung der vollständigen PR-Matrix steht aus.
