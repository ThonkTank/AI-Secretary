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

### Korrekturdurchlauf B.1 – nachgelagerte Release-Abhängigkeit

Auslöser: PR #331 bestand Quality, alle sechs Geräte-/Animationsjobs sowie beide Sammelgates
und wurde als `1a15497f53dec88032fb3876c5438d4da3b84317` nach `main` übernommen. Der exakte
Main-Lauf `34107608930` bewies den Schnellpfad: `release_scope` und `instrumentation-gate` waren
grün, während Quality und beide Instrumentierungsmatrizen übersprungen wurden. Package baute,
signierte und validierte danach erfolgreich einen neuen Kandidaten für 0.2.156; dessen Plan
enthält `action=create_draft`, `already_published=false` und den exakten Main-SHA.

Abweichung: Upgrade und Publish wurden trotzdem übersprungen. Der Upgradejob nennt neben
`package` noch den alten direkten Bedarf `instrumentation`. GitHub überspringt einen Job mit
einem übersprungenen direkten Bedarf, bevor dessen eigene Bedingung ausgewertet wird. Diese
Abhängigkeit ist im neuen Modell redundant, weil `package` bereits ein erfolgreiches
`instrumentation-gate` voraussetzt.

Korrekturplan vor Implementierung:

1. Den direkten Bedarf von Upgrade auf `package` beschränken; die Sicherheitskette bleibt
   transitiv `instrumentation-gate -> package -> upgrade -> publish`.
2. Den Workflow-Vertrag um genau diese Abhängigkeitsregel und den weiterhin zwingenden
   Package-Gate-Bezug erweitern.
3. CI-/Release-Vertragstests, YAML-Parsing und Diff-Prüfung lokal wiederholen.
4. Die Korrektur als eigenen PR erneut durch die volle Matrix schicken und squash-mergen.
5. Den neuen exakten Main-Lauf nur dann als Phase-B-Abschluss akzeptieren, wenn der Schnellpfad,
   Package, alle drei Upgradejobs und Publish tatsächlich erfolgreich sind und 0.2.156 auf dessen
   Main-SHA veröffentlicht ist.

Lokale Validierung und Audit: 24 CI-Skript- und 24 Release-/Workflow-Vertragstests sind grün;
PyYAML bestätigt `upgrade.needs == [package]`, Package behält Quality beziehungsweise den
Wiederverwendungsbeleg und `instrumentation-gate` als Voraussetzungen, Publish behält Package
und Upgrade. `git diff --check` ist grün. Die Korrektur deckt damit die beobachtete Abweichung
vollständig ab und ändert weder Beweisregeln noch Paket-, Upgrade- oder Publish-Inhalte.
Remote-Gates und der tatsächliche Releaseabschluss stehen weiterhin aus.

### Korrekturdurchlauf B.2 – explizite Auswertung nach übersprungenen Vorfahren

Auslöser: Der Korrektur-PR #332 bestand erneut die vollständige PR-Matrix und wurde als
`ffa5e6535221874136d7064fd5bbc63469557600` nach `main` übernommen. Im exakten Main-Lauf
`34109759663` waren Schnellpfad, `instrumentation-gate` und Package erfolgreich; Upgrade und
Publish blieben dennoch übersprungen. Release 0.2.156 existiert weiterhin nicht.

Präzisierter Befund: GitHubs Standardauswertung propagiert übersprungene Vorfahren auch durch
die erfolgreiche Package-Stufe, solange der nachgelagerte Job seine Bedingung nicht ausdrücklich
mit `always()` auswertet. Nur die direkte Abhängigkeit zu entfernen war daher notwendig, aber
nicht hinreichend.

Korrekturplan vor Implementierung:

1. Upgrade mit `always()` auswerten und zusätzlich explizit `needs.package.result == 'success'`
   sowie `already_published != 'true'` verlangen.
2. Publish ebenfalls mit `always()` auswerten und explizit erfolgreiche Package- und
   Upgrade-Ergebnisse sowie `already_published != 'true'` verlangen.
3. Den Workflow-Vertrag um diese vollständigen Resultat-Guards erweitern und die strukturelle
   Kette lokal prüfen.
4. Erneut eigener PR, vollständige PR-Matrix, Squash-Merge und exakter Main-Lauf. Phase B endet
   erst mit drei erfolgreichen Upgradejobs, erfolgreichem Publish und dem auf denselben Main-SHA
   veröffentlichten Release 0.2.156.

Lokale Testkorrektur B.2.1: Der erste Vertragslauf scheiterte ausschließlich mit einem `NameError`,
weil die neue `publish`-Sektionsvariable durch einen zu breiten Patch-Anker in einer benachbarten
Testmethode angelegt worden war. Vor der Korrektur wurde kein Commit oder Remote-Lauf gestartet.
Die Variable wird in die tatsächlich erweiterte Testmethode verschoben; Produktworkflow und
Abnahmekriterien bleiben unverändert.

Lokale Validierung und Audit B.2: Nach der Testkorrektur sind erneut 24 CI-Skript- und 24
Release-/Workflow-Vertragstests grün. PyYAML bestätigt für Upgrade `always()`, ausschließlich
Package als direkten Bedarf und ein explizit erfolgreiches Package-Resultat. Für Publish
bestätigt es `always()`, Package und Upgrade als direkte Bedarfe sowie beide explizit
erfolgreichen Resultate. `already_published`, Main-Ref und Nicht-PR-Grenzen bleiben erhalten;
`git diff --check` ist grün. Damit ist die aus beiden realen Main-Läufen abgeleitete Lücke im
Plan geschlossen. Remote-Gates und der tatsächliche Releaseabschluss stehen weiterhin aus.

### Remote-Abschluss Phase B

PR #333 bestand Quality, alle sechs normalen und animationsaktiven Gerätepfade sowie
`instrumentation-gate` und `pull-request-gate`. Er wurde als
`e530f4c64cd10965332a492cd178dd17e5c7217d` per Squash nach `main` übernommen. Der exakte
Main-Lauf `34112140336` bewies danach den vollständigen Zielpfad:

- `release_scope` und `instrumentation-gate` erfolgreich;
- Quality und beide dreifachen, bereits inhaltsgleich geprüften Instrumentierungsmatrizen
  übersprungen;
- Package auf dem exakten Main-SHA erfolgreich;
- Upgrades auf API 26, 35 und 37 jeweils erfolgreich;
- Publish erfolgreich.

Release `forest-android-1015601` / 0.2.156 ist öffentlich und nicht als Vorabversion markiert.
Tag, Release-Ziel und `release-metadata.json` nennen alle
`e530f4c64cd10965332a492cd178dd17e5c7217d`; APK-Größe und SHA-256 stimmen zwischen Asset und
Metadaten überein. Phase B ist damit einschließlich der beiden protokollierten
Korrekturdurchläufe abgeschlossen.

## Phase C – redundante Satztexte entfernen

Status: in Arbeit

### Vorprüfung

- Ausgangspunkt ist `origin/main` auf `e530f4c6`, identisch mit dem abgeschlossenen
  Phase-B-Stand. Die Arbeit läuft im separaten Worktree
  `/tmp/autosecretary-flow-target-p2` auf `codex/flow-target-p2-set-dots`.
- `FocusStepRowView` erzeugt oberhalb von `SetDotsView` ein eigenes `progressHeader` mit zwei
  `TextView`s. Es berechnet dort lediglich die sichtbaren Texte `Satz x von y` und
  `x/y erledigt`; die Fachaktion, Satzdaten und Editorauswahl liegen nicht in diesem Header.
- `SetDotsView` besitzt bereits die vollständige visuelle Semantik: gefüllte bestätigte Punkte,
  hervorgehobener aktueller Punkt und Auswahlring für einen bearbeiteten früheren Satz.
- TalkBack bleibt unabhängig von den sichtbaren Texten: `SetDotsView` setzt eine vollständige
  Inhaltsbeschreibung und einen numerischen `ProgressBar`-Bereich. `SetDotsViewTest` prüft
  Fortschritt, aktuellen Satz, ausgewählten Satz und fehlende Klickaktion bereits direkt.
- Betroffene visuelle Baselines sind die satzhaltigen Fokus-Goldens und die fünf responsive
  Satzpunkt-Goldens. Der kontrollierte Updatevertrag verlangt zuerst einen fehlgeschlagenen Lauf
  mit Expected/Actual/Diff-Triplets und erst danach den expliziten Komponenten-Update-Lauf.

### Phasenplan

Ziel: Die Punkte tragen allein die sichtbare Fortschrittsinformation; es gibt keinen sichtbaren
Ersatztext. Auswahl, Eingabe, Aktionen und Accessibility bleiben unverändert.

Implementierungsfolge:

1. `progressHeader`, `progressPosition` und `progressDone` vollständig aus Konstruktion und
   Binding von `FocusStepRowView` entfernen.
2. Die dadurch unbenutzten Strings `training_set_position` und `training_sets_done` entfernen.
3. Den vorhandenen View-Test um normale Satzprogression und Bearbeitung eines früheren Satzes
   ergänzen: Beide redundanten Texte fehlen sichtbar, Punkte bleiben sichtbar und die
   Accessibility-Beschreibung beziehungsweise der Auswahlzustand bleibt erhalten.
4. Schnelle UI-, Accessibility- und Satzpunkttests ausführen. Danach die betroffenen Golden-
   Abweichungen ohne Update erzeugen und die Expected/Actual/Diff-Artefakte visuell prüfen.
5. Nur nach dieser Prüfung die Fokus-Goldens mit `UPDATE_FOCUS_TASK_GOLDENS=1` aktualisieren und
   denselben Testsatz erneut ohne Update grün ausführen.
6. Vollständiges lokales Seriengate sowie Plan-/Roadmap-Audit ausführen. Danach eigener PR,
   vollständige PR-Matrix, Squash-Merge und exakter Main-Releaseweg.

Abnahmekriterien:

- In normaler Satzprogression und bei Korrektur eines früheren Satzes existieren weder
  `Satz x von y` noch `x/y erledigt` als sichtbare Textzeilen.
- Aktueller, bestätigter und ausgewählter Satz bleiben über die Punkte eindeutig dargestellt.
- TalkBack-Inhaltsbeschreibung und numerischer Fortschrittsbereich bleiben vollständig.
- Keine Eingabe-, Menü-, Timer-, Trainingsassistent- oder Ablaufdarstellung wird fachlich
  geändert.

### Korrekturdurchlauf C.1 – vollständige Zeilenentfernung und lokale JDK-Laufzeit

Der erste fokussierte Testbefehl startete nicht. Die Vorprüfung fand noch drei späte
`grainOcclusions()`-Referenzen auf den entfernten Header; außerdem enthält der historische Pfad
`/usr/lib/jvm/java-21-openjdk` auf diesem Host keine Binärdateien mehr. Es wurde kein Test und
kein Golden ausgeführt oder verändert.

Korrekturplan vor Fortsetzung: Die drei Grain-Referenzen werden ebenfalls entfernt, weil die
nicht mehr existierenden Texte auch keine Halo-Aussparung mehr besitzen dürfen. Die Tests laufen
mit dem installierten vollständigen JDK 25 unter `/usr/lib/jvm/java-25-openjdk`; das Android-SDK
bleibt `/home/aaron/Android/Sdk`. Produktziel und Testauswahl bleiben unverändert.

### Korrekturdurchlauf C.2 – visueller Beleg für Satzkorrektur

Der erste kontrolliert rote Golden-Lauf erzeugte Expected/Actual/Diff-Triplets für vier
satzhaltige Fokusansichten und fünf responsive Satzpunktvarianten; die Ansicht ohne Satzmenge
blieb pixelgleich. Die Sichtprüfung zeigt ausschließlich die beabsichtigte Entfernung beider
Textzeilen und das geschlossene Aufrücken von Punkten und Eingabefeldern ohne Beschnitt oder
Überlagerung.

Audit-Abweichung: Der neue View-Test prüft die Korrektur eines früheren Satzes semantisch über
Auswahlbeschreibung und Fortschrittswert, die bestehende Golden-Matrix bindet jedoch immer den
normalen Eingabezustand. Der erhaltene Auswahlring wäre damit nicht pixelbasiert belegt.

Korrekturplan vor Fortsetzung: Die Satzpunkt-Golden-Matrix erhält einen eigenen Fall mit drei
Sätzen und ausgewähltem erstem, bereits gespeichertem Satz. Zuerst wird ohne Update das neue
Actual erzeugt und visuell geprüft; erst danach werden dieser neue Golden und die neun bereits
geprüften Diffs gemeinsam über den geschützten Komponenten-Updatepfad übernommen.

### Lokale Validierung Phase C

- Der neue Auswahlring-Golden lief zunächst kontrolliert rot, weil noch keine Baseline bestand.
  Das erzeugte Actual wurde bei Originalauflösung geprüft: erster gespeicherter Satz mit
  Auswahlring, zweiter gespeicherter Satz gefüllt, aktueller dritter Satz separat markiert;
  keine redundanten Texte, kein Beschnitt und keine Überlagerung.
- Nach dem expliziten Komponenten-Update sind die neun bereits geprüften Fokus-/Satzpunkt-
  Baselines und der neue Auswahlring-Golden übernommen. Der anschließende Lauf beider Golden-
  Klassen ohne Update ist grün. Die Ansicht ohne Satzmenge blieb pixelidentisch und unverändert.
- Die vollständige betroffene UI-Gruppe aus `FocusTaskViewTest`, beiden Golden-Klassen,
  `SetDotsViewTest` und der Accessibility-Layoutmatrix ist seriell grün.
- Das breite lokale Seriengate aus Domain-/Today-Kompilierung, allen
  `testInstrumentationUnitTest`-Tests, `lintDebug`, Debug-APK, Instrumentierungs-APK und
  Release-APK ist grün: 566 Tests, keine Fehler oder Fehlschläge, ein vorgesehener Skip.
- Die CI-Skript-Suite und die Release-/Workflow-Vertragssuite sind mit jeweils 24 Tests grün.
  `git diff --check` meldet keine Abweichung; die entfernten View-Felder und String-Ressourcen
  sind im Produkt- und Testquellbaum nicht mehr referenziert.

### Plan-Audit Phase C

Alle sechs geplanten Schritte sind im Diff und in den Belegen abgedeckt. Konstruktion, Binding
und Grain-Aussparungen des redundanten Headers sind entfernt; die unbenutzten Texte sind aus den
Ressourcen verschwunden. Ein View-Test deckt normalen Fortschritt und die Korrektur eines
früheren Satzes semantisch ab. Die kontrolliert aktualisierte Bildmatrix deckt helle und dunkle
Darstellung, beide Zielbreiten, erhöhte Schriftgrößen, Punktumbruch sowie den Auswahlring ab.
Eingabefelder, Satzdaten, Aktionen, Timer und Trainingsassistent wurden fachlich nicht verändert.

### Roadmap-Audit Phase C

Die Phase entspricht vollständig dem isolierten Phase-C-Schnitt: Es existiert keine sichtbare
Fortschrittszeile mehr; bestätigte, aktuelle und ausgewählte Sätze bleiben über die Punkte
unterscheidbar; TalkBack-Beschreibung und numerischer Fortschrittsbereich bleiben erhalten.
Die visuelle Abnahme und das vollständige lokale Gate sind abgeschlossen. Es besteht keine
bekannte Roadmap-Diskrepanz. Pull-Request-Matrix, Squash-Merge und der exakte Main-Releaseweg
stehen noch aus; bis dahin bleibt Phase C `in Arbeit`.

### Remote-Abschluss Phase C

PR #334 prüfte Commit `dcaf5bc5eb4362872c616494266bf23997571200`. Quality, alle sechs
normalen und animationsaktiven Gerätepfade auf API 26, 35 und 37,
`instrumentation-gate` und `pull-request-gate` sind grün. Der PR wurde als
`324205e914a73aea0a406b718509e1508afb6165` per Squash nach `main` übernommen.

Der exakte Main-Lauf `34119239363` bewies anschließend den beschleunigten Zielpfad:

- inhaltsgleicher grüner PR-Nachweis, `release_scope` und `instrumentation-gate` erfolgreich;
- Quality und beide bereits im PR ausgeführten Gerätematrizen korrekt übersprungen;
- neues Produktionspaket auf dem exakten Main-SHA erfolgreich;
- Upgrades auf API 26, 35 und 37 jeweils erfolgreich;
- Publish erfolgreich.

Release `forest-android-1015701` / 0.2.157 ist öffentlich und keine Vorabversion. Tag,
Release-Ziel und `release-metadata.json` nennen alle
`324205e914a73aea0a406b718509e1508afb6165`. Die veröffentlichte APK ist 2.827.524 Byte groß;
ihr SHA-256 `865db75b1f9305e84d4b9239bf17a112fac2e947f52ab5d98feb3d797bb63ed1`
stimmt zwischen Asset und Metadaten überein. Phase C ist damit vollständig abgeschlossen.

## Phase D – atomarer Ablauf-Cutover

Status: in Arbeit

### Vorprüfung

- Ausgangspunkt ist `origin/main` auf `324205e9`, identisch mit dem abgeschlossenen
  Phase-C-Stand und Release `forest-android-1015701` / 0.2.157. Die Arbeit läuft im separaten
  Worktree `/tmp/autosecretary-flow-target-p3` auf
  `codex/flow-target-p3-atomic-cutover`; der unabhängige Hauptcheckout bleibt unangetastet.
- Die bestehende Materialisierung erzeugt für einen lediglich fälligen Startschritt bereits
  einen vollständigen `StepFlowRun` im Zustand `PENDING_START`, einschließlich unveränderlicher
  Schritt- und Ressourcensnapshots. Damit sind Kandidat und tatsächlich gestarteter Lauf im
  Persistenzmodell nicht getrennt.
- `FlowRuntimeCoordinator` erzeugt pro Run ein als `FLOW_SHEET` benanntes `Occurrence`, hält
  dessen ID in `currentSheetOccurrenceId` und benutzt dasselbe Objekt zugleich als interne
  Ausführungs-, Reward- und sichtbare Today-Identität. Untouched-Offer-Bereinigung und
  Laufzustandskorrekturen kompensieren diese Mehrfachrolle im Laufzeitpfad.
- `LoadDashboard` rekonstruiert das vermeintlich gemeinsame Blatt nachträglich über
  `DashboardTask.flowAggregate` und `flowRunByStepId`. `FocusCardView`, Defer-Logik und Widget
  leiten daraus Sonderverhalten beziehungsweise aus mehrdeutigen String-IDs ab; eine eigene,
  stabil sortierbare Blattidentität existiert nicht.
- `LoadFlowRuns` muss `PENDING_START` ausdrücklich aus dem Alles-Tab filtern. Kapazität und
  Startbarkeit werden schon für diese vorläufigen Runs berechnet, während der eigentliche
  Startpfad Reservierung, Zustandswechsel und Abschluss des Startschritts über mehrere
  Runtime-Schritte verteilt.
- Room-Schema 22 enthält weder Kandidaten noch Blattpositionen. `step_flow_runs` besitzt
  `currentSheetOccurrenceId`, `occurrences` besitzt `kind='FLOW_SHEET'` und
  `flowSheetSequence`; der unterstützte Produktionsupgradepfad beginnt weiterhin bei Schema 8.

### Phasenplan

Ziel: Fälligkeit, nutzergestartete Ausführung und sichtbare Today-Gruppierung werden als drei
getrennte Modelle umgesetzt. Der Cutover bleibt ein einziger Produkt-PR, sodass kein
Zwischenmodell veröffentlicht wird.

Implementierungsfolge:

1. Domänen- und Persistenzmodell um `FlowCandidate`, `FlowTaskSheetPlacement` und das
   read-only `FlowTaskSheet` ergänzen. Ein Kandidat enthält ausschließlich Fälligkeit und
   Reihenfolge; das Blatt besitzt eine stabile ID und Sortierposition pro Task/Slot.
2. Materialisierung auf Kandidaten umstellen. Sie erzeugt weder Runs noch Snapshots oder
   Ressourcenbelegung und dedupliziert über Source-Key sowie Task/Startschritt/Slot.
3. `StartFlowCandidate` als atomaren Transaktionspfad implementieren: Kandidat und aktuelle
   Definition erneut laden, gesamte Startkapazität prüfen, Run-/Schritt-/Ressourcensnapshots
   anlegen, Startressourcen reservieren, Startschritt mit gewählter Dauer abschließen und erst
   nach Erfolg den Kandidaten entfernen. Kapazitätsänderung oder veraltete Definition liefern
   ein typisiertes Ergebnis und hinterlassen keinerlei Teillauf.
4. Runtime auf die fünf echten Run-Zustände reduzieren. `PENDING_START` sowie alle
   Legacy-Reconciliation- und Untouched-Candidate-Pfade entfernen; interne Run-Vorkommen in
   `FLOW_STEP`, `currentExecutionOccurrenceId` und `nextExecutionSequence` umbenennen.
5. `LoadDashboard` um ein erstes `FlowTaskSheet`-Read-Modell erweitern: angebotene aktive
   Schritte zuerst, danach aktuell startbare Kandidaten; nicht startbare Kandidaten und leere
   Blätter werden nicht projiziert. Der Alles-Tab liest ausschließlich echte Runs.
6. Today-Präsentation und Aktionen typisieren: normale Occurrence, Blatt, Kandidat und
   Run-Schritt erhalten eindeutige Targets. Kreis, Dauerabfrage, „Noch nicht fertig“ und
   „Später“ dispatchen über diese Ziele; Defer verschiebt nur die persistierte Blattposition.
   `flowAggregate`, `flowRunByStepId` und ID-Heuristiken entfallen vollständig.
7. Widget auf dasselbe Blattmodell umstellen. Ablaufzeilen öffnen die App für erforderliche
   Eingaben und können Zeitabfragen nicht per Widget-Shortcut umgehen.
8. Schema 22 nach 23 migrieren: saubere ungestartete `PENDING_START`-Runs in Kandidaten
   umwandeln, tatsächlich begonnene oder ressourcenbelegte Altdaten als echte Runs erhalten,
   alte Einzelblattpositionen deterministisch konsolidieren und die alten Spalten/Werte durch
   neu aufgebaute Tabellen entfernen. Der Pfad von Produktionsschema 8 bleibt lückenlos.
9. Domain-, Transaktions-, Room-, Migrations-, Dashboard-, Alles-, Widget-, UI-, Golden- und
   Accessibility-Tests auf das Zielmodell umstellen. Der verbindliche Wäsche-End-to-End-Fall
   prüft vier Kandidaten, eine Waschmaschine, drei Trockenplätze, Dauerwahl, Warteverlängerung,
   Defer, Neustart/Upgrade, Ressourceninvarianten und das Verschwinden des letzten Blatts.
10. Vollständiges lokales Seriengate und getrennten Plan-/Roadmap-Audit ausführen. Jede gefundene
    Abweichung wird vor der Korrektur hier protokolliert; anschließend folgen eigener Commit,
    vollständige PR-Matrix, Squash-Merge und der exakte Main-Releaseweg.

Abnahmekriterien:

- Ohne Kreisaktion existieren nur Kandidat und Blattposition, aber kein Run, Snapshot,
  Hintergrundstatus oder Ressourcenverbrauch.
- Der Start ist vollständig atomar; bei fehlender Kapazität bleibt nur der unveränderte
  Kandidat bestehen.
- Pro Task/Slot wird höchstens ein nicht leeres gemeinsames Blatt sichtbar, mit stabiler
  Defer-Reihenfolge und eindeutig zugeordneten aktiven Folgeschritten.
- Nur echte Runs erscheinen in Alles; Zeit- und Ressourcenwartezeiten blockieren Heute nicht.
- Die alten Zustände, Felder, Aggregate-Flags, Occurrence-Namen und mehrdeutigen Today-ID-Pfade
  sind nach Migration und Cutover weder im Produktmodell noch im Laufzeitpfad vorhanden.
- Migration 22→23 erhält laufende Ketten, gewählte Zeiten und Ressourcen; der unterstützte
  Upgradepfad 8→23 sowie Neustart sind getestet.
- Es gibt keine Wäsche-spezifische Produktlogik und keine Umgehung von Dauer-/Warteabfragen.

### Korrekturdurchlauf D.1 – Exception-Hierarchie im atomaren Start

Der erste frühe Kompilierlauf stoppte ausschließlich in `StartFlowCandidate`: Der neue
Stale-Candidate-Schutz fing `FlowDefinitionException` und `IllegalArgumentException` gemeinsam,
obwohl erstere bereits eine Unterklasse der zweiten ist. Es wurden noch keine Tests oder
Schemaexporte ausgeführt.

Korrekturplan: Der Catch wird auf die gemeinsame Oberklasse reduziert. Danach wird derselbe
dreiteilige Kompilierlauf erneut ausgeführt; Produktvertrag und Fehlerstatus bleiben unverändert.

### Korrekturdurchlauf D.2 – finale Blattplatzierung im Projektions-Builder

Nach D.1 kompiliert der atomare Start; der nächste Compilerstopp liegt in `LoadDashboard`.
Eine nur für fehlende Altdaten ergänzte lokale Blattplatzierung wird vor dem
`computeIfAbsent`-Lambda neu zugewiesen und ist deshalb nicht effektiv final.

Korrekturplan: Nach der Fallback-Auflösung wird eine finale lokale Referenz an den Builder
übergeben. Projektion, Reihenfolge und Persistenzvertrag ändern sich dadurch nicht; anschließend
läuft derselbe frühe Kompiliercheck erneut.

### Korrekturdurchlauf D.3 – nachgelagerte Präsentationskonsumenten

Nach D.2 sind Domain und Today-Core grün. Der App-Compiler findet sechs erwartete Restzugriffe
auf die nun entfernten Übergangsfelder `flowAggregate` und `flowRunByStepId`, ausschließlich in
Dashboard- und Widget-Mapping. Dies bestätigt, dass keine weitere Domain-Abhängigkeit besteht.

Korrekturplan: Beide Mapper werden direkt auf `Dashboard.flowTaskSheets` umgestellt. Das
Today-Mapping erhält typisierte Blatt-/Zeilenziele und explizite Aktionsfähigkeiten; das Widget
projiziert Blattzeilen nur als App-Öffnung. Danach wird erneut über alle drei Module kompiliert.

### Korrekturdurchlauf D.4 – Debug-Vorschau auf typisiertes Ziel umstellen

Nach der Mapper-Umstellung sind alle Produktionsquellen bis zur App-Kompilierung grün. Einziger
Compilerfehler ist eine Debug-Vorschau, die `TaskActionTarget` noch mit der früheren rohen
Occurrence-ID konstruiert.

Korrekturplan: Die Vorschau deklariert denselben Datensatz ausdrücklich als `OccurrenceTarget`.
Es gibt keine Laufzeit- oder UI-Verhaltensänderung; danach wird der frühe Kompiliercheck erneut
ausgeführt.

### Korrekturdurchlauf D.5 – Übergangstests auf Zielmodell umstellen

Die erste Testkompilierung nach dem produktiven Cutover fand 28 Verweise auf bewusst entfernte
APIs. Der Großteil lag in der bisherigen Ablauf-Robolectric-Suite, die weiterhin vorläufige
Runs, `PENDING_START` und aus `DashboardTask` rekonstruierte Aggregate erwartete; weitere
Treffer waren reine Fixture-Konstruktoren.

Korrektur: Die Ablauf-Suite wurde auf den verbindlichen End-to-End-Vertrag umgestellt und prüft
nun Kandidaten ohne Runtime-Daten, atomaren Start, Dauer-Snapshot, eine Waschmaschine, drei
Trockenplätze, gemeinsame Herkunftstitel, Warteverlängerung, Defer-Isolation, Widget-Grenze und
Rekomposition. Materialisierungs- und Präsentationsfixtures wurden auf die typisierten Modelle
gehoben. Die anschließende Testkompilierung reduzierte den Befund auf eine einzige, rein
sprachliche Lambda-Finalitätsstelle.

### Korrekturdurchlauf D.6 – stabile Run-ID im Wartezeit-Test

Im Wartezeit-Test wird die lokale Run-Variable nach der Lambda-Prüfung erneut zugewiesen und ist
daher nicht effektiv final. Korrekturplan: Die ID wird vor dem Streamvergleich als unveränderter
String festgehalten; Testaussage und Produktcode bleiben unverändert. Danach wird erneut die
gesamte Instrumentierungs-Testquelle kompiliert.

### Korrekturdurchlauf D.7 – historische Spaltennamen und wartende Blattprojektion

Der erste fokussierte Lauf führte 48 Tests aus. 32 Migrationsvarianten scheiterten an derselben
Ursache: Die mechanische Runtime-Umbenennung hatte versehentlich auch die historischen
16→17-DDL-Spalten umbenannt, sodass der 22→23-Schritt bei aus älteren Schemas aufgebauten
Datenbanken den alten Namen nicht mehr fand. Drei neue Ablaufassertionen waren ebenfalls zu
streng: Eine Kandidatenzeile hatte eine vom Action-Target abweichende Präfix-ID; während die
Waschmaschine bis zum Abschluss von „Aufhängen“ belegt ist, ist korrekt nur der aktive Schritt
sichtbar; und ein rein zeitwartender Run erzeugt absichtlich kein Today-Blatt.

Korrekturplan: Historische Migrationen behalten bytegenau die Schema-22-Namen und ausschließlich
22→23 benennt sie um. Kandidatenzeile und Kandidatenaktion teilen dieselbe ID. Die E2E-Aussagen
werden an den verbindlichen Kapazitäts- und Nichtblockierungsvertrag angepasst: Kandidaten
erscheinen nach Freigabe der Waschmaschine wieder, reine Wartezeit bleibt aus Today heraus.

### Korrekturdurchlauf D.8 – fehlender statischer Testimport

Der erste vollständige Suite-Start erreichte noch keine Testausführung: Der neu ergänzte
Schema-22-Fixturetest verwendet einmal `assertFalse`, während die historische Testklasse diesen
statischen Import bisher nicht benötigte.

Korrekturplan: Ausschließlich den fehlenden JUnit-Import ergänzen und danach denselben
vollständigen Testlauf neu starten.

### Korrekturdurchlauf D.9 – Upgrade-Fixture und erweiterter Bulk-Lesevertrag

Der zweite vollständige Suite-Lauf führte 560 Tests aus; 557 waren grün, einer übersprungen
und zwei schlugen fehl. Die Produktions-Upgrade-Fixture benennt noch Schema 22 als Ziel, obwohl
der zentrale Vertrag nun Schema 23 verlangt. Außerdem liegt der Dashboard-Leseaufwand konstant
bei zwölf statt zehn Abfragen: Die beiden neuen erstklassigen Tabellen für Kandidaten und stabile
Blattpositionen werden jeweils einmal zusätzlich in Bulk gelesen. Es gibt keinen datensatzabhängigen
N+1-Anstieg in diesem Fixture.

Korrekturplan: Die bestehende Produktions-Fixture wird auf Zielversion 23 gehoben und ihre
Zielerwartungen gegen den Schema-23-Export geprüft, ohne die unveränderten Quelldaten umzuschreiben.
Da die Fixture absichtlich keine Ablaufdaten sät, werden keine erfundenen Zielzeilen für die neuen
Tabellen ergänzt. Der Query-Vertrag wird explizit auf höchstens zwölf Abfragen erweitert und prüft
zusätzlich genau einen Bulk-Select für Kandidaten und Blattpositionen. Die Kandidatenprojektion
wird zugleich vollständig gebündelt: Templates, Übergänge, Leases, Kapazitäten und Verbrauch
werden pro Dashboard-Aufruf konstant geladen; auch Sortierung und Startbarkeitsprüfung dürfen
keinen Einzel-Select pro Kandidat auslösen. Danach laufen zunächst beide betroffenen Tests und
anschließend erneut die vollständige Suite.

### Korrekturdurchlauf D.10 – Geräte-Fixture auf typisiertes Today-Ziel

Das erste kombinierte Auslieferungsgate bestand Domain-/Today-Kompilierung, die vollständige
560er Testsuite, Lint und das Debug-Paket. Erst die separate AndroidTest-Kompilierung fand eine
einzelne Geräte-Fixture, die `TaskActionTarget` noch mit der entfernten rohen Occurrence-ID statt
mit `TodayItemTarget` erzeugt. Produktquellen und bereits ausgeführte Tests waren nicht betroffen.

Korrekturplan: Die Fixture deklariert denselben Datensatz ausdrücklich als
`TodayItemTarget.occurrence`. Anschließend wird das gesamte serielle Auslieferungsgate ab seinem
Anfang wiederholt, einschließlich Vollsuite, Lint, Debug-, Geräte-Test- und Release-Paket.

### Korrekturdurchlauf D.11 – direkte Abnahme der Ein-Waschmaschine-Grenze

Das grüne lokale Auslieferungsgate belegt die Implementierung der Kapazitätslogik, der
Roadmap-Abgleich findet jedoch eine Nachweislücke: Die Grenze von drei Trockenplätzen wird direkt
getestet, während die Ein-Waschmaschine-Grenze bisher nur implizit in sequenziellen Hilfsabläufen
wirkt.

Korrekturplan: Ein eigener End-to-End-Test startet einen Waschgang, prüft das sofortige
Verschwinden aller weiteren Startangebote, versucht dennoch einen zweiten Kandidaten zu starten
und verlangt `CAPACITY_CHANGED`, einen unveränderten Kandidaten sowie genau einen aktiven Run.
Danach werden fokussierter Ablauf-Test, vollständige Suite und das betroffene Paketgate erneut
ausgeführt.

### Ergebnis und lokale Validierung Phase D

- Fällige Ablaufstarts werden als persistierte `FlowCandidate`s materialisiert. Vor der
  Kreisaktion existieren weder Run noch Snapshot, interne Ausführungs-Occurrence oder
  Ressourcenverbrauch. Erst `StartFlowCandidate` prüft den aktuellen Vertrag und die Kapazität
  erneut und führt Snapshot, Reservierung, Startschritt und Kandidatenentfernung atomar aus.
- `StepFlowRunState.PENDING_START` ist aus dem Produktmodell entfernt. Interne Run-Datensätze
  heißen `FLOW_STEP` und tragen ausschließlich Ausführungsidentität; Today projiziert sie nie als
  eigenes Blatt.
- `FlowTaskSheet` und `FlowTaskSheetPlacement` bilden pro Aufgabe/Slot ein stabiles gemeinsames
  Today-Blatt. Angebotene Folgeschritte stehen vor startbaren Kandidaten, leere Blätter werden
  nicht erzeugt, und „Später“ verschiebt ausschließlich die Blattposition.
- Today-Aktionen verwenden `TodayItemTarget`; Kandidat und Run-Schritt besitzen eigene
  Aktionsarten. Fokus-, Timeline- und Widgetmodelle verwenden für gemischte Identitäten
  `itemId`. Das Wäscheblatt erlaubt weder Bulk-Abschluss noch direkte Widget-Ausführung.
- Kandidaten, Definitionen, Ressourcenverbrauch und Blattpositionen werden für das Dashboard
  gebündelt geladen. Der konstante Query-Vertrag umfasst die zwei neuen Tabellen ausdrücklich;
  Kandidatenzahl und Sortierung erzeugen keine Einzelabfragen.
- Die Schema-22→23-Migration trennt saubere ungestartete Runs in Kandidaten, erhält nachweislich
  begonnene/wartende Runs und Ressourcen und konsolidiert alte Einzelpositionen. Der exportierte
  Schema-23-Vertrag besitzt 23 Entitäten und die beiden neuen Tabellen; die Produktionsfixture
  0.2.80 zielt auf Schema 23.
- Der fokussierte Ablauf-/Migrationslauf sowie die nach jeder Korrektur betroffenen Tests sind
  grün. Die abschließende Instrumentierungs-Unit-Suite meldet 561 Tests: 560 bestanden, einer
  übersprungen, keine Fehler.
- Das serielle Android-Auslieferungsgate ist grün:
  `testInstrumentationUnitTest`, `lintDebug`, `assembleDebug`,
  `assembleInstrumentationAndroidTest` und `assembleRelease`; Domain und Today-Core wurden im
  selben Lauf kompiliert. Nach D.11 wurde die vollständige Suite erneut ausgeführt und alle
  Paket-/Lint-Ergebnisse aus demselben Arbeitsbaum bestätigt.
- Alle 24 Tests unter `scripts/ci` und alle 24 Tests unter `scripts/release` sind grün.
  `git diff --check` meldet keine Whitespace-Fehler.

### Plan-Audit Phase D

Alle Bestandteile des Phasenplans sind umgesetzt: Kandidatenmodell und Room-Persistenz,
atomarer Start, Entfernung des Vorstart-Runzustands, eigenes gemeinsames Blatt mit stabiler
Position, typisierte Today-Ziele, interne Ausführungsbezeichnungen, Today-/Alles-/Widget-Cutover
und Schema-23-Migration. Die Korrekturrunden D.1 bis D.11 dokumentieren jeden während
Kompilierung, Tests und Audit gefundenen Unterschied vor seiner Korrektur. Produktive
Negativscans finden die entfernten Zustände, Felder und Aggregate außerhalb der historischen
22→23-Migration nicht mehr; Wäschebegriffe existieren nicht in der generischen Fachlogik.

### Roadmap-Audit Phase D

Der End-to-End-Vertrag ist vollständig abgedeckt: vier fällige Wäschearten bilden genau ein
Blatt; der Kreis fordert die Waschdauer an; eine Waschmaschine und drei Trockenplätze werden
direkt geprüft; Folgeschritte behalten Herkunftstitel; „Später“ verändert nur die Today-Reihenfolge;
„Noch nicht fertig“ verschiebt den Folgeschritt bei gehaltener Ressource; Kandidaten erscheinen
nicht als Hintergrundläufe; Rekomposition und Upgrade erhalten echte Runs; nicht sichtbare oder
beendete Arbeit erzeugt kein leeres Blatt. Alte technische Begriffe bleiben ausschließlich in
ADR, Roadmap, Migration und deren historischen Fixturetests als ausdrücklich benannter
Schema-22-Quellvertrag erhalten. Lokal besteht keine offene Diskrepanz. Pull-Request-Gate,
Squash-Merge, exakter Main-Workflow, Produktionsupgrade und Veröffentlichung stehen noch aus;
bis dahin bleibt Phase D `in Arbeit`.
