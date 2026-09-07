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

### Remote-Abschluss

Pull Request #330 bestand den Dokumentations-Scope und den stabilen `pull-request-gate`; Quality,
Instrumentierung, Packaging, Upgrade und Publish wurden erwartungsgemäß übersprungen. Der PR
wurde als `dca52dd572dc0f74339ce910755cecc80a8a22d4` per Squash nach `main` übernommen. Der exakte
Main-Workflow `34104357543` ist vollständig grün und übersprang ebenfalls alle für reine
Dokumentation nicht anwendbaren Produktjobs. Phase A ist damit abgeschlossen.

## Phase B – Releaseweg beschleunigen

Status: in Arbeit

### Vorprüfung

- Ausgangspunkt ist `origin/main` auf `dca52dd5`, identisch mit dem abgeschlossenen
  Phase-A-Stand. Die Arbeit läuft im separaten Worktree
  `/tmp/autosecretary-flow-target-p1` auf `codex/flow-target-p1-ci-proof`.
- `.github/workflows/verify.yml` führt bei jeder produktwirksamen Änderung Quality sowie drei
  normale und drei animationsaktive Emulatorjobs zuerst auf dem PR-Head und nach dem
  Squash-Merge erneut auf `main` aus. Nur der Main-Lauf baut und signiert danach den
  Produktionskandidaten und prüft drei reale Upgrades.
- GitHubs Commit-zu-PR-API liefert für den realen Phase-A-Squash die gemergte PR-Nummer und den
  ursprünglichen Head `e811b3f0`. Die Git-Commit-API liefert für PR-Head und Squash denselben
  Tree `8f4a141f…`; damit ist Inhaltsgleichheit ohne Vertrauen in Commitnachrichten beweisbar.
- Die Actions-Run-Abfrage nach dem vollständigen PR-Head findet den erfolgreichen
  `pull_request`-Lauf. Dessen eingebettete `pull_requests`-Liste kann nach dem Merge leer sein;
  die Bindung muss deshalb vom autoritativ mit dem Squash assoziierten PR-Head ausgehen.
- Ein grüner `pull-request-gate` allein reicht für produktwirksame Änderungen nicht als
  übertragbarer Beleg: `quality` und `instrumentation-gate` müssen zusätzlich erfolgreich sein.
  Ein Dokumentations-PR hat dieselben Sammelgates bei übersprungenen Produktjobs und darf keinen
  Produkt-Schnellpfad begründen.

### Phasenplan

Ziel: Ein Main-Push übernimmt ausschließlich nachweislich identische PR-Quality- und
Instrumentierungsergebnisse. Jeder fehlende, widersprüchliche oder technisch nicht lesbare
Nachweis fällt ohne Fehlerabbruch auf die heutige vollständige Main-Matrix zurück. Packaging,
Signatur, Upgrade und Publish bleiben immer an den exakten Main-Commit gebunden.

Implementierungsfolge:

1. Einen reinen, mit JSON-Fixtures testbaren `scripts/ci`-Entscheider für Merge-PR, Tree-Gleichheit,
   erfolgreichen PR-Workflow und die drei erforderlichen Gate-Jobs einführen. Die produktive
   GitHub-Abfrage bleibt eine dünne, fail-closed CLI-Schicht.
2. Unit-Tests für identischen Squash, abweichenden Baum, direkte Pushes, fehlende/mehrdeutige PRs,
   übersprungene oder fehlgeschlagene Gates, ältere erfolgreiche Runs und API-Fehler ergänzen.
3. `release_scope` um `reuse_pr_verification` und nachvollziehbare Evidence-Outputs erweitern;
   die nötigen `actions: read`- und `pull-requests: read`-Berechtigungen bleiben auf diesen Job
   begrenzt.
4. Quality sowie beide Instrumentierungsmatrizen nur auf einem belegten Main-Schnellpfad
   überspringen. PRs, `workflow_dispatch` und unbelegte Main-Pushes behalten die vollständige
   Matrix.
5. `instrumentation-gate` und `package` so anpassen, dass sie entweder vollständige aktuelle
   Main-Ergebnisse oder den strikten Wiederverwendungsbeleg akzeptieren. PR-Gate, Production-
   Candidate, Upgrade und Publish bleiben in ihrer fachlichen Bedeutung unverändert.
6. Scope-, Workflow- und Entscheidertests, YAML-Parsing, Shellsyntax, `git diff --check` und
   getrennte Plan-/Roadmap-Audits ausführen. Nach grünem PR wird der Squash-Commit als erster
   realer Schnellpfad verifiziert; der Abschluss verlangt weiterhin grünes Package, alle drei
   Upgrades und Publish auf exakt diesem Main-SHA.

Abnahmekriterien:

- Jeder produktwirksame PR führt weiterhin Quality und alle sechs Geräte-/Animationsjobs aus.
- Wiederverwendung setzt genau einen zum Main-Squash gehörenden gemergten PR, identische Git-
  Bäume, einen erfolgreichen PR-Workflow und erfolgreiche Jobs `quality`,
  `instrumentation-gate` und `pull-request-gate` voraus.
- Direkte Pushes, Dokumentationsbelege, abweichende Trees, fehlende Jobs, API-Fehler und manuelle
  Läufe verwenden die vollständige Main-Matrix.
- Kein PR-Artefakt und kein unsigniertes APK gelangt in Packaging oder Publish.
- Produktionskandidat, Signatur-/Hashprüfung, drei Upgrades und veröffentlichte Metadaten nennen
  weiterhin den exakten Main-SHA.

### Lokale Validierung

- `python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v`: 24 Tests grün,
  einschließlich acht Entscheidertests für zulässige und fail-closed Pfade.
- `python3 -m unittest discover -s scripts/release -p 'test_*.py' -v`: 24 Tests grün,
  einschließlich des erweiterten Workflow-Vertrags.
- PyYAML liest den Workflow vollständig; strukturierte Assertions bestätigen die Guards für
  Quality, beide Instrumentierungsmatrizen, Package und Publish.
- `py_compile`, `shellcheck` für die unverändert eingebundenen Runner und `git diff --check`
  sind grün. `actionlint` ist lokal nicht installiert; die autoritative GitHub-Validierung folgt
  deshalb im Pull Request.
- Die Live-Probe gegen den veröffentlichten Produkt-Squash
  `bf189a9fc6798a506243edd9e5391ee7f362a32b` belegt den identischen Tree
  `9893cd8b…`, PR #329 und den grünen PR-Lauf `34098089702`; der Entscheider erlaubt die
  Wiederverwendung. Der Dokumentations-Squash `dca52dd5` wird wegen fehlender Produktwirkung
  erwartungsgemäß abgelehnt.
- Eine erste manuelle Produktprobe verwendete versehentlich eine nicht existierende
  ausgeschriebene SHA und fiel korrekt mit `evidence_unavailable` auf die Vollprüfung zurück.
  Erst die anschließende, gegen GitHub verifizierte vollständige SHA dient als Positivbeleg.

### Plan-Audit

Alle sechs geplanten Implementierungsschritte sind im Diff abgedeckt. Der Entscheider ist von
der GitHub-I/O-Schicht getrennt und testbar; `release_scope` exportiert Beleg und Diagnose;
Quality sowie beide Gerätematrizen werden nur bei einem positiven Beleg übersprungen;
`instrumentation-gate` akzeptiert dabei ausschließlich tatsächlich übersprungene Jobs; Package
verlangt weiterhin Produkt-Scope, Main-Ref und ein grünes Instrumentierungsgate. Packaging,
Upgrade und Publish selbst wurden fachlich nicht gelockert.

### Roadmap-Audit

Die Phase entspricht Phase B der verbindlichen Roadmap: Die PR-Seite bleibt unverändert voll,
der Main-Schnellpfad vergleicht Git-Bäume und drei erfolgreiche PR-Gates, jeder unvollständige
Nachweis fällt ohne Freigabe auf die bestehende Vollmatrix zurück, und es wird kein PR-APK für
die Produktion wiederverwendet. Lokal besteht keine bekannte Roadmap-Diskrepanz. Pull-Request-
Matrix, Squash-Merge und der erste exakte Main-Schnellpfad mit Package, drei Upgrades und Publish
stehen noch aus; bis dahin bleibt Phase B `in Arbeit`.
