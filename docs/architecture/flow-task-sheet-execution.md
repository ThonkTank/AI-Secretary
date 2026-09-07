# Ausführungsprotokoll der Ablaufkandidaten-/Today-Blatt-Roadmap

Die autoritative Zieldefinition bleibt die
[Roadmap: echte Ablaufkandidaten und gemeinsame Today-Blätter](flow-task-sheet-roadmap.md).
Dieses append-only Protokoll hält Vorprüfungen, Phasenpläne, Nachweise, Audits,
Korrekturrunden und Blocker getrennt von der kanonischen Roadmap fest.

## Phase A – Architekturvertrag

Status: in Arbeit

### Vorprüfung

- Verifizierter Ausgangspunkt ist `origin/main` auf `bf189a9f`, identisch mit dem
  veröffentlichten Release `forest-android-1015501` / Auto Secretary 0.2.155.
- Der aktive Hauptcheckout liegt auf dem unabhängigen Branch
  `codex/frontend-p6b-alltasks-compose-cutover` und bleibt unangetastet. Phase A läuft im
  separaten Worktree `/tmp/autosecretary-flow-target-p0` auf
  `codex/flow-target-p0-contract`.
- Der Persistenzstand ist Room-Schema 22. `StepFlowRunState.PENDING_START`, pro Run erzeugte
  `OccurrenceKind.FLOW_SHEET`, `currentSheetOccurrenceId`, `DashboardTask.flowAggregate` und
  `flowRunByStepId` bilden den aktuellen Übergangszustand.
- `DeferTask` erkennt ein gemeinsames Blatt über `OccurrenceKind.FLOW_SHEET` und verschiebt alle
  passenden offenen Run-Occurrences über Task-ID und Slot. Das sichtbare Blatt besitzt damit
  noch keine eigene Identität oder Sortierposition.
- Die aktuelle CI führt Qualitäts-, normale Instrumentierungs- und Animationstests sowohl auf
  dem PR-Head als auch erneut auf dem Squash-Commit aus; Packaging, Upgrade und Publish laufen
  ausschließlich auf `main`.
- Die Roadmap wurde im Gespräch vollständig festgelegt, war aber noch nicht im Repository
  gespeichert. Die vorhandene Konvention legt kanonische Roadmaps und append-only
  Ausführungsprotokolle unter `docs/architecture` ab und verlinkt sie im dortigen Index.

### Phasenplan

Ziel: Die im Gespräch festgelegte Roadmap wird unverändert als kanonische Referenz persistiert,
durch ADR-033 fachlich verbindlich gemacht und aus den aktiven Architekturübersichten
auffindbar. Produktcode, Room-Schema und Golden-Baselines bleiben unverändert.

Betroffene Artefakte:

- `flow-task-sheet-roadmap.md` als kanonische Roadmap;
- dieses `flow-task-sheet-execution.md` als getrenntes, append-only Protokoll;
- ADR-033 für Kandidaten-, Run-, Blatt-, Aktions-, Migrations- und Abschlussverträge;
- `README.md`, `architecture-map.md` und `phase-7-teststrategie.md` für aktive Links und den
  neuen verbindlichen Zielvertrag.

Implementierungsfolge:

1. Kanonische Roadmap und dieses Protokoll anlegen.
2. ADR-033 ohne Übergangsarchitektur oder Wäsche-Sonderlogik formulieren.
3. Aktive Architekturkarte und Teststrategie auf Ausgangszustand, Zielgrenzen,
   Migrationsnachweis und Phasengates aktualisieren.
4. Architekturindex auf Roadmap, ADR und Protokoll ergänzen.
5. Markdown-Links, Negativscans, Python-Vertragssuites und Diff-Hygiene prüfen.
6. Ergebnis getrennt gegen diesen Phasenplan und die vollständige Roadmap auditieren;
   Diskrepanzen erhalten vor Änderungen eine eigene Korrekturrunde.

Abnahmekriterien:

- Roadmap und Ausführungszustand sind getrennt und im aktiven Index auffindbar.
- ADR-033 nennt ausdrücklich `FlowCandidate`, nur nutzergestartete `StepFlowRun`s, genau ein
  `FlowTaskSheet` pro Task/Slot, typisierte Today-Ziele und Schema-23-Migration.
- Die Dokumentation hält den unveränderten sichtbaren Vertrag sowie die Entfernungsliste fest.
- Phase B bis E und deren Gates bleiben deckungsgleich mit der kanonischen Roadmap.
- Produktquellen, Ressourcen, Room-Schema und Golden-Baselines sind gegenüber `bf189a9f`
  byteidentisch.

### Korrekturrunde A.1 – stabile Dokumentanker

Der erste kombinierte ADR-/Index-Patch wurde vollständig abgelehnt, weil der erwartete Anker in
`architecture-map.md` nicht mit dem tatsächlichen Zeilenumbruch übereinstimmte. Roadmap und
Ausführungsprotokoll waren bereits separat angelegt; ADR und Indexänderungen wurden nicht
teilweise geschrieben.

Fixplan: Die tatsächlichen Anfangs- und Endbereiche der Architekturkarte und Teststrategie werden
als Anker verwendet. ADR-033 wird unverändert ergänzt, danach werden README, Architekturkarte
und Teststrategie in kleinen, getrennt prüfbaren Patches verlinkt. Anschließend belegen
`git diff --check`, eine relative Markdown-Linkprüfung und Negativscans den vollständigen
Phase-A-Vertrag.

### Ergebnis und lokale Validierung

- Die im Gespräch festgelegte Roadmap liegt ohne Scope-Kürzung als
  `flow-task-sheet-roadmap.md` vor; dieses Dokument bleibt davon getrennt und append-only.
- ADR-033 entscheidet Kandidaten-/Run-Trennung, atomaren Start, eigenes `FlowTaskSheet`,
  typisierte Today-Ziele, Schema-23-Migration, sichtbaren Vertrag und den nachweisgebundenen
  Main-Schnellpfad.
- `README.md`, `architecture-map.md` und `phase-7-teststrategie.md` verlinken und begrenzen den
  neuen Zielvertrag gegenüber dem noch produktiven Schema-22-Stand.
- `git diff --check` ist sauber. Eine relative Markdown-Linkprüfung über alle sechs betroffenen
  Dokumente meldet `checked=6 missing=0`.
- Alle 16 Tests unter `scripts/ci` und alle 23 Tests unter `scripts/release` sind grün.
- Der Negativabgleich der geänderten Pfade findet ausschließlich Dateien unter
  `docs/architecture`; Produktquellen, Ressourcen, Room-Schemas, Workflows und Goldens bleiben
  gegenüber `bf189a9f` unverändert.

### Plan- und Roadmap-Audit

Planabgleich: Roadmap, getrenntes Protokoll, ADR-033 und alle drei vorgesehenen aktiven Links
sind vorhanden. Die tatsächliche Schema-22-Zwischenarchitektur und CI-Dopplung sind als
Ausgangslage benannt; die festgelegten Zieltypen, Invarianten, Migration, Abnahmeszenarien und
Phasen A bis E sind vollständig erfasst. Die Korrekturrunde A.1 hat nur Patchanker angepasst und
keinen fachlichen Inhalt verändert.

Roadmapabgleich: Phase A ändert weder Produktcode noch Schema oder visuelle Baselines. ADR-033
deckt Kandidat, Run, Blatt, Today-Aktionsmatrix, Migration und Test-/Releasegrenzen ab. Der
Architekturindex macht Roadmap, ADR und Protokoll auffindbar. Lokal besteht keine offene
Diskrepanz. Pull-Request-Gate und Squash-Merge nach `main` stehen noch aus; bis dahin bleibt
Phase A `in Arbeit`.
