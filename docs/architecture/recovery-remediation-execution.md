# Ausführungsprotokoll: Recovery-Remediation

Kanonische Roadmap: [recovery-remediation-roadmap.md](recovery-remediation-roadmap.md)

## Aktueller Arbeitsstand (P4 integriert, P4b in Arbeit)

P0-Belegbestand, P1, P2 und P3 sind auf Remote-Main integriert. P3 ist mit0.2.174 veröffentlicht.
P4 hat den Erzeuger der drei Waisenkategorien reproduziert und ist nach vollständiger PR-Prüfung
und exaktem Main-Nachweis integriert. P4b enthält den lokalen Ursachenfix und die nativen Erhaltungs-/Rollbackbelege.
Der ergänzte signierte Fixture-Vertrag wird geprüft; PR, Main und Release sind noch offen. Pixel-Diagnose und sichtbare Bedienabnahme bleiben bei P5 offen.
Die folgenden ursprünglichen Planungsstände und Fehlversuche bleiben historische Aufzeichnungen.

## Planungsstand 2026-09-12

- Status: Roadmap entworfen; Umsetzung nicht begonnen.
- Referenz: Remote-Main `6ad6b6c6019a69eb75529479644df793074c4618`.
- Planungsbranch: `codex/remediation-roadmap`.
- Ausgangsprodukt laut vorhandenen Belegen: 0.2.172, Schema 27. Bei Umsetzung frisch prüfen.
- Keine Produktdatei, CI-Regel oder Gerätedatei wurde für diese Planung geändert.
- Die Roadmap schlägt in P2 eine Änderung des bestehenden Freigabevertrags vor. Dieser ist
  durch die Erstellung des Entwurfs noch nicht geändert.

| Phase | Status | Abschlussbelege |
|---|---|---|
| P0 – Ausgangsstand | Geplant | Keine neuen Abnahmebelege |
| P1 – Today-Bedienweg | Geplant | — |
| P2 – Freigabeprofile | Geplant | — |
| P3 – Diagnosezustand | Geplant | — |
| P4 – Ursachenuntersuchung | Geplant | — |
| P4b – Ursachenfix | Bedingt; kein Erzeuger nachgewiesen | Nicht freigegebener Implementierungsumfang |
| P5 – Abschluss | Geplant | Sichtbare Pixel-Prüfung zuletzt offen |

## Pro Phase zu ergänzen

- Frischer Ausgangsstand und betroffene Verträge.
- Konkreter Plan vor Implementierung, Umfang und erwarteter Releasebedarf.
- Reproduktion, Implementierungs-/Testbelege und gesonderter Roadmap-Abgleich.
- Abweichungen mit vorab notiertem Korrekturplan.
- Branch, PR, geprüfter Head, Squash-Main und exakter Main-Lauf.
- Bei Release: Kandidat, anwendbare Upgradefälle, veröffentlichte Bytes und Gerätebelege.
- Getrennte offene Punkte und Voraussetzungen der nächsten Phase.

Prüflaufzeiten mit Parallelität und abgedecktem Risiko dokumentieren. Zwischenmeldungen
benennen bevorzugt neue Ergebnisse oder die konkrete verbleibende Abhängigkeit.

## P0 – Beauftragte Umsetzung und Phasenplan

Die ausdrückliche Beauftragung „Setze die roadmap per roadmap executor skill um“ nimmt die
Roadmap einschließlich des in P2 vorgeschlagenen Prüfvertrags an. Bis zum geprüften P2-Merge
bleiben die bisherigen Gates gültig. Der ursprüngliche Roadmaptext bleibt als Referenz unverändert.

Plan: beide Planungsdokumente versionieren; Remote-Main und PR-/Releasebelege frisch prüfen;
die vorhandenen Gerätedatenbelege zuordnen und die aktuell fehlende Geräteverbindung als
separaten Abnahmenachweis offen halten. Diese Dokumentationsphase erfordert keinen Produktrelease.

### P0 – Validierung und Abgleich

- Remote-Main unverändert `6ad6b6c6019a69eb75529479644df793074c4618`; PR 362 ist gemergt.
- PR-Lauf 34691047203 und exakter Main-Lauf 34692015363 erneut erfolgreich bestätigt;
  Release `forest-android-1017201` ist öffentlich und kein Prerelease.
- Gesicherte frühere Gerätebelege: 0.2.172 / 1017201 in-place installiert; ursprüngliche
  Installation erhalten; Kaltstart ohne App-/Datenbankfehler; Archiv 8 Ablauf-Schritte,
  4 Ressourcen, 2 Aufgabenschritte. Dies sind historische Belege, keine neue Geräteprüfung.
- Frisches `adb devices -l`: keine Geräte. Anschließen/Entsperren wurde angefragt. Sichtbare
  Aufgaben-/Ablauf-, Titel-/Später- und Persistenzprüfung bleibt offen; P1–P4 sind nicht blockiert.
- Lokaler Baselinecheck: 777 Tests, 0 Fehler, 1 optionaler Benchmark übersprungen, 22m44s
  für den vollständigen Check. Host-Klickregression prüft API 26/35, noch keine gesamte Speicherung.
- PR-Baseline: Contracts 2m37s, Goldens 2m03s, Build 6m03s; normale Instrumentierung auf
  API 26/35/37 4m11s/5m20s/5m38s; Animationen 5m43s/13m52s/5m51s.
  Der lange API-35-Animationslauf absolvierte 35 Tests erfolgreich; kein Stillstand oder Retry.
- PR-Gesamtdauer 20m22s, Main-/Release-Gesamtdauer 7m11s. Lokaler Check und PR liefen parallel;
  die Zeiten sind keine addierbare Wartezeit. Historische Upgrade-Lanes prüfen Datenherkunft
  und signierten In-place-Übergang, nicht den Titel-Bedienweg.
- Reproduktionsbelege liegen lokal in `/tmp/title-repro.xml`, `/tmp/title-tap-crash.txt` und
  `/tmp/recovery-device-proof/`; keine persönlichen Daten werden ins Repository kopiert.
- Dokumentverweise, Phasen P0–P5 und Format wurden geprüft. Abgleich: P0-Belegbestand erfüllt;
  Pixel-Abnahme ausdrücklich offen. Integration per PR steht noch aus.

### P0 – Integration

PR 363 (`6f54d0cc`) bestand den Dokumentations-Gate im Lauf 34697046556 und wurde per
Squash als `da6e9a41f844878762870e8a3c2d8cd265faec89` integriert. Exakter Main-Lauf
34697081276 erfolgreich; kein Produktrelease. P0-Belegbestand abgeschlossen, sichtbare
Pixel-Abnahme weiterhin separat offen.

## P1 – Phasenplan vor Implementierung

Ausgangsstand: `da6e9a41f844878762870e8a3c2d8cd265faec89`.
Branch: `codex/today-action-integration`.

- Eine eigene Integrationstestklasse verwendet bestehenden Room-/UseCase-/ViewModel-Aufbau,
  den echten DashboardRenderer und seine produktiven Klickbindungen. Datenbankdatei nach dem
  Titel-Klick schließen/neu öffnen; kein Befehlsrecorder hinter dem Klick.
- Fokus-/Abschnittsübergänge von „Später“ einschließlich Einzelaufgabe und einen normalen/Flow-
  Mischfall prüfen. Historie, XP, Ablaufkandidaten und Ressourcen vor/nach den Aktionen vergleichen.
- Nativer repräsentativer Test in der isolierten Instrumentierungs-App durch den tatsächlichen
  MainActivity-Bedienweg; kein Säen auf dem Pixel-Produktionspaket.
- Coordinator-Befehlsaktionen mit gültigen Factory-Eingaben ausführen und genau einen typisierten
  Befehl sowie normale Rückkehr prüfen. Die vorhandene Quelltextprüfung bleibt höchstens als
  Architekturgrenze, nicht als Ersatz für diese Verhaltensprüfung.
- Gezielte Gegenproben für historischen break, falsches Ziel und unterbundene Speicherung
  lokal/isoliert durchführen; Änderungen danach zurücknehmen und Ergebnisse protokollieren.
- Zuerst gezielte Tests, dann vollständiger bisheriger lokaler Check und PR-Matrix. Test-/Harness-
  Änderungen erfordern Instrumentierung, aber bei unverändertem Produkt-/Upgrade-Probe-Code
  keinen Produktrelease. Ein abweichender Scope wird vor Auslieferung ausdrücklich geprüft.

### P1 – Korrekturrunde 1: Testaufbau

Der erste gezielte Lauf bestand die normalen Titel-/Später-Fälle und alle Coordinator-Fälle.
Der Mischfall scheiterte vor dem Bedienweg: `FlowGraphEdit` prüft `containsValue(null)`, was die
verwendete Java-`Map.of` bereits mit einer NPE abweist. Plan: wie der bestehende Flow-Testaufbau
`LinkedHashMap` verwenden; den zusätzlichen Abschnittsgrenzfall anschließend mitprüfen.
Kein Produktfix oder abgeschwächter Assertion-Umfang ist dafür erforderlich.

Die korrigierten Hosttests sind grün (zehn Integrationsfälle auf API 26/35 und fünf
Coordinator-Tests). Der parallel angeforderte native Build sah beim laufenden Umbenennen der
neuen Hilfsklasse einen inkonsistenten Quellstand und scheiterte an deren Auflösung. Korrekturplan:
Quellstand einfrieren und den nativen Build erneut ausführen. Das neue Szenario wird vom bereits
in allen normalen und Animations-Lanes ausgewählten `TodayInteractionInstrumentationTest`
aufgerufen; die CI-Auswahl muss dafür nicht verändert werden.

### P1 – Gezielte Validierung und Abgleich vor PR

- Hosttestlauf: zehn Integrationsfälle (fünf Szenarien jeweils API 26/35) und fünf Coordinator-
  Tests bestanden. Nativer APK-Build nach eingefrorenem Quellstand ebenfalls erfolgreich.
- Gegenproben, jeweils tatsächlich ausgeführt auf API 26 und 35: historischer `break` erzeugt
  `Unhandled Today action BRING_FIRST` über `DashboardRenderer` → ViewModel → Coordinator;
  falsches Dispatcher-Ziel und unterbundene Room-Speicherung ergeben jeweils unveränderte
  Reihenfolge `[A,B,C]` statt `[C,A,B]`. Alle sechs Negativfälle scheitern am erwarteten Verhalten.
  Die drei XML-Ergebnisse haben getrennte aktuelle Laufzeitstempel; keine Compilerfehler als
  Testversagen gezählt. Lokale Rohbelege: `/tmp/p1-mutation-*/result.xml` und `gradle.log`.
- Alle Gegenproben vollständig zurückgenommen; Produktdateien entsprechen unverändert dem
  Ausgangsstand. Der anschließende vollständige lokale Check prüft den wiederhergestellten Stand.
- Abgleich gegen Phasenplan: produktiver Renderer-/ViewModel-/Room-Bedienweg, dateibasierte
  Neuöffnung, Abschnittsende/-wechsel/Einzelfall, Mischfall mit nichtleerem Verlauf, Ressourcen,
  Kandidaten und Belohnungen abgedeckt. Coordinator prüft alle Befehlsarten; stateful Reorder
  behält seinen eigenen Idempotenztest. Keine zweite Produktverdrahtung eingeführt.
- Abgleich gegen Roadmap: natives Touch-/Accessibility-Szenario ist in allen bestehenden Today-
  Lanes ausgewählt und prüft produktiven Fokus plus Activity-Neuerstellung. Der tatsächliche
  native Lauf, vollständiger lokaler Gate, PR- und Main-Abschluss bleiben bis zu ihren Ergebnissen
  offen. Kein Release erforderlich: ausschließlich Tests und Ausführungsprotokoll geändert.

### P1 – Integration und Abschluss

- PR 364, geprüfter Head `29ef4c5ba77da95b57cbae7906f330cec71db304`, Lauf 34698017126:
  Contracts, Goldens, Build und alle sechs normalen/animierten API-26/35/37-Lanes grün.
- Vollständiger lokaler `check-all.sh`: Exit 0, 788 Tests, 0 Fehler, 1 optionaler Benchmark
  übersprungen; Gradle-Gesamtdauer 7m51s einschließlich Tests, Lint und Builds. Identität und
  Größenlimits bestanden. Keine Gegenprobe im Produktcode verblieben.
- Squash-Main `5425bb51aeb1f5b0c2b397a95c13227b04f583d7`; exakter Main-Lauf 34699171018
  ebenfalls vollständig grün. Kein Produktrelease. Main und PR besitzen identische Git-Inhalte.
- PR-Jobzeiten: Contracts 2m34s, Goldens 1m56s, Build 6m14s; native normale Lanes
  4m18s/5m24s/4m54s, Animations-Lanes 4m42s/15m05s/8m02s (API 26/35/37).
  Der API-35-Animationslauf führte 36 Tests erfolgreich aus; kein Retry. Parallelzeiten sind
  nicht addierbar. Die bestehende Wiederverwendung greift nur bei Produktänderungen, weshalb
  Main für die Teständerung trotz identischer Inhalte nochmals dieselbe Matrix ausführte.
- Abschließender Abgleich gegen P1-Plan und kanonische Roadmap: alle P1-Nachweise erfüllt.
  Die weiterhin fehlende Pixel-Verbindung gehört zur separaten P0/P5-Geräteabnahme.

## P2 – Phasenplan vor Implementierung

Ausgangsstand: `5425bb51aeb1f5b0c2b397a95c13227b04f583d7`.
Branch: `codex/risk-based-today-verification`. P1 und dessen exakter Main-Lauf sind abgeschlossen.
Die Nutzerbeauftragung nimmt die in der Roadmap ausdrücklich beschriebene Vertragsänderung an.

1. `change_scope.py` bekommt vier versionierte Profile mit sichtbaren Gründen. Git-Status,
   neue Dateien, Umbenennungen/Löschungen, gemischte/unklare Diffs und fehlende Basis werden
   konservativ behandelt. Die Today-Positivliste umfasst nur die zwei benannten Dispatcher-
   Dateien plus unmittelbar zugehörige Hosttests; keine allgemeine Modulliste.
2. Vor Aufnahme zusätzlich die Handler-/Payload-Zuordnung des TodayCommandDispatcher durch
   gültige Befehle prüfen. Der bisherige Architekturtest sucht dort nur Quelltext. P1 belegt
   bereits den produktiven Titel-/Später-Weg und sämtliche Coordinator-Befehlsarten.
3. Lokalen Einstieg und `verify.yml` auf denselben Profilvertrag ausrichten. Docs bekommen
   schnelle Dokument-/Vertragsprüfungen; Hosts die Contracts; Today zusätzlich Build und einen
   API-35-Animationslauf der Today-Suite; Full behält die vollständigen bestehenden Lanes.
   Stabile Aggregat-Gates lehnen unpassende, fehlende, rote und fälschlich übersprungene Jobs ab.
4. PR-/Main-Wiederverwendung benötigt identischen Inhalt, identisches Profil samt Policy-Version
   und jeden ausgewählten Einzeljob. Ein profil-/policyabhängiger Jobname macht diesen Nachweis
   über die Jobs-API prüfbar. Reine Teständerungen dürfen künftig denselben strikten Nachweis nutzen.
5. Aktuelle signierte Quelle direkt bei Releaseplanung durch Tag, Commit, Version, Paket,
   Signatur, APK-/Metadatenhash und Quellschema binden. Die Metadatenformatversion ist nicht die
   Datenbankschemaversion; diese wird am unveränderlichen Quellcommit gelesen. Quelle und
   aktuelles Smoke-Fixture werden mit dem einmal gebauten Kandidaten archiviert, ohne spätere
   Latest-Auflösung. Fehlende oder widersprüchliche Quelle verhindert Veröffentlichung.
6. Eigener expliziter Current-Smoke-Vertrag mit Aufgaben, Today-Platzierung und Historie erlaubt
   Schema 27→27 bei höherer App-Version. Historische Fixtures bleiben beim strengeren
   Migrationsvertrag. Generiertes Smoke-Asset separat vom strikt manifestierten historischen
   Corpus einbinden. Aktueller Smoke für jeden Produktrelease, fünf historische Lanes zusätzlich
   für Full; alle auf denselben signierten Kandidaten, Publish abhängig von anwendbaren Erfolgen.
7. ADR-005/035, README und aktuelle Teststrategie gemeinsam aktualisieren. Historische Berichte
   und die kanonische Roadmap bleiben historische Belege; keine rückwirkende Umdeutung.
8. Gezielte Python-/Hostverträge, historische Diffs PR359/360/361/362, negative Freigabe- und
   Source-Pin-Fälle prüfen; P1-Gegenproben müssen im Today-Contracts-Scope enthalten sein.
   Danach vollständiger lokaler Gate und vollständiger P2-PR-Gate. P2 stuft seine eigenen
   Policy-/CI-/Build-/Probe-Änderungen nicht herab. Erwarteter Releasebedarf: ja; aktueller
   Smoke und alle historischen signierten Lanes sowie exakter Main-/Publish-Nachweis erforderlich.

Vor Produktänderungen in P3 bleiben Bootstrap-/Ursachenbefunde vorbereitend. Die offene
Pixel-Abnahme ist kein Ersatz für die automatischen Freigaben und wird nicht als erledigt geführt.

### P2 – Zwischenvalidierung und Korrekturrunde 1

- Statusbewusste Klassifikation: 14 gezielte Tests grün. Dispatcher-Verhaltensvertrag für alle
  29 Befehlsarten inklusive Payload, Einzelaufruf und regulärer Rückkehr grün (Hostlauf 12s).
- Wiederverwendung prüft jetzt ausgewählte Einzeljobs, eindeutigen Profil-/Policy-Zeugen und
  identischen Git-Inhalt; auch Teständerungen sind zulässig. Ein neuerer roter/unvollständiger
  Lauf wird nicht durch einen älteren grünen verdeckt. Elf Tests einschließlich negativer Fälle
  für alle vier Profile grün. Fünf weitere Tests prüfen die tatsächlich ausgeführte Aggregatlogik.
- Lokale Profile und CI-Auswahl sind verdrahtet. Gesamte Python-CI-Suite: 41 Tests grün.
  Hostprofil führt auch Goldens aus, damit reine Änderungen an Golden-Tests nicht ausgespart werden.
- Aktueller Smoke: expliziter Vertrag Version 2 erlaubt gleiche Datenbankschemata; historischer
  Vertrag Version 1 bleibt strikt auf höhere Zielschema-Versionen begrenzt. Sechs Python-Tests
  prüfen Quellenbindung, negative Identitäten und vollständige synthetische Zeilen in SQLite.
- Quellenauflösung tatsächlich gegen GitHub ausgeführt: veröffentlichte 0.2.172, Tag
  `forest-android-1017201`, Commit `6ad6b6c6019a69eb75529479644df793074c4618`, Schema 27 aus
  genau dessen `DatabaseContract.java`. APK-Hash
  `84ce792395f217bc53ba984503bca40344f522389b159f6e545255cc90f1ea2b`; Metadatenhash
  `fe9ac31eeb0dce076332ac38c4932bf27a47df74ffde8f67c556137b9c88ee16`.
  Lokale Pin-Dateien: `/tmp/p2-current-source/`. Kein produktiver Installationsvorgang.
- Upgrade-Probe mit separat generiertem Current-Smoke-Asset gebaut: Exit 0, 55s. Dieser Build
  belegt Kompilierung und Asset-Einbindung, noch keinen signierten Android-Update-Lauf.
- Die Release-Python-Suite hat vier erwartete Vertragsabweichungen gefunden: bestehende
  Quelltextprüfungen verlangen noch Inline-Matrizen, pauschale Qualitätsflags und die bisherige
  eingebettete Shell-Gatelogik. Korrekturplan vor Änderung: auf die neue tatsächliche Verdrahtung
  zur versionierten Policy und getesteten Aggregatfunktion umstellen; vollständige API-Auswahl,
  stabile PR-Gates, Signierung und Kandidatenbindung weiterhin prüfen. Die neuen negativen
  Verhaltensprüfungen bleiben maßgeblich; keine rote Auswahl durch gelockerte Assertion akzeptieren.

Noch offen: aktuelles Fixture im produktiven Room-/Today-Ablauf, signierte Smoke-Ausführung,
Dokumentangleichung, historischer Diffvergleich, vollständiger lokaler Gate, P2-PR und Main/Release.

### P2 – Korrekturrunde 2: Room-Fixture-Testaufbau

Der erste native Build und die Quellenbindung sind grün; der zusätzliche Room-Hosttest scheitert
bereits beim Kompilieren, weil `AppDatabase` kein `AutoCloseable` implementiert. Plan: dieselbe
explizite `try/finally`-Schließung wie im bestehenden Produktions-Fixture-Test verwenden, danach
API-26/35-Lauf erneut ausführen. Das ist kein fehlgeschlagener Persistenznachweis und kein Produktfehler.
ADR-005 fordert für Vertragsänderungen eine ergänzende ADR. Der bereits angenommene P2-Vertrag
wird deshalb zusätzlich als neue Entscheidung dokumentiert und von ADR-005/035 referenziert.

### P2 – Korrekturrunde 3: fachlich vollständiges Smoke-Fixture

Nach korrigiertem Schließen startet der Room-Test auf API 26/35 und scheitert im echten
`MaterializeDueOccurrences`: `Active task has no schedule: current-smoke-task`. Das Fixture
war SQL-/FK-gültig, aber fachlich unvollständig. Plan: den erforderlichen regulären Zeitplan
mit sämtlichen Spalten und unverändertem fernen Fälligkeitsdatum ergänzen; alle Seedwerte nach
Materialisierung, Today-Projektion und Neuöffnung erneut prüfen. Weder den Startpfad umgehen
noch den Test auf bloßes Lesen reduzieren. Der historische Korpus bleibt unverändert.
Historische Git-Diffs frisch klassifiziert: PR 359/360/361 jeweils `full`, PR 362 `today`;
Rohbeleg `/tmp/p2-historical-diff-profiles.json`.

### P2 – Abgleich vor vollständiger Validierung

- Korrigiertes Current-Smoke-Fixture: produktive Materialisierung und Today-Projektion ergeben
  genau die offene Aufgabe im gespeicherten Mittagsabschnitt. Alle Seedspalten inklusive
  Zeitplan und abgeschlossenem Verlauf bleiben über Neuöffnung erhalten; API 26/35 grün (47s).
  Das Fixture-Verzeichnis ist als Gradle-Testeingabe registriert, damit Änderungen Tests neu auslösen.
- Release-Verträge auf tatsächliche neue Verdrahtung umgestellt: 38 Python-Release-Tests grün.
  42 Python-CI-Tests grün; die neue ausführbare Publish-Prüfung lehnt fehlenden/roten aktuellen
  Smoke und fehlende/rote/fälschlich übersprungene historische Vollprüfungen ab. Anschließend
  zusätzlicher Negativtest gegen abgeschnittene GitHub-Jobantworten ergänzt.
- Workflow mit `actionlint` v1.7.12 geprüft, Exit 0; Werkzeugarchiv gegen veröffentlichten
  Prüfsummenbeleg geprüft. YAML-Struktur und `git diff --check` ebenfalls fehlerfrei.
- Abgleich gegen P2-Phasenplan: vier Profile und lokale/CI-Übereinstimmung umgesetzt; Host-Goldens
  enthalten; Dispatcher-Verhalten geprüft; Einzeljob-/Profil-/Policy-/Inhaltsnachweis für Main;
  aktuelle Quelle bei Planung gebunden; eigener Same-Schema-Smoke und separate Assets;
  vollständige historische Matrix bei `full`; aktueller und anwendbarer historischer Gate vor
  Publish; ADR-037 ergänzt die aktualisierten ADR-005/035, README, Releaseguide und Teststrategie.
- Abgleich gegen kanonische Roadmap: keine Pauschalabschaffung historischer oder Android-Lanes,
  keine erweiterten Today-Produktdateien, P1-Tests im Contracts-Scope, historische Diffs passend.
  P2-Arbeitsstand klassifiziert sich selbst als `full` mit Releasebedarf. Originalroadmap und
  Frontend-Checkout bleiben unverändert. Kein P3-Produktfix in dieser Phase.
- Noch unbewiesen und vor Phasenabschluss erforderlich: kompletter lokaler Check, vollständige
  P2-PR-Läufe, Squash und exaktes Main, aktueller plus fünf historische signierte Upgrades und
  bytegeprüfte Veröffentlichung. Die Pixel-Abnahme bleibt unabhängig davon offen.

### P2 – Korrekturrunde 4: native Wartegrenze und tatsächlicher PR-Testinhalt

Der vollständige lokale Gate bestand auf `e4e66733` (Exit 0, 13m58s; 791 Tests, keine Fehler,
1 optionaler Benchmark übersprungen; Lint/Build/Identität/Größe grün). PR 365, Lauf 34702118064,
bestand Quality und fünf Android-Lanes. API-35-Animation scheiterte am 20-Minuten-Schrittlimit.
32/36 Tests waren fertig. Das fortlaufende geräteseitige Protokoll identifiziert den neuen
`titleTouchAndAccessibleLaterChangeActualFocusAndPersistOnRecreation`: Start 15:38:43Z,
MainActivity resumed 15:38:43Z, erste Neuerstellung erst 15:44:53Z, laufende Frames bis zum
Abbruch 15:49:06Z. Keine beobachtete App-Exception in diesem Bedienweg. Rohbelege:
`/tmp/p2-api35-failure/` und `/tmp/p2-pr-failed.log`.

Der tatsächlich eingebundene AndroidX-Test-Core 1.7.0 ruft in `ActivityScenario.launch`,
`onActivity`, `recreate` und seinen Zustandsabfragen jeweils `Instrumentation.waitForIdleSync`
auf (Bytecodebeleg `/tmp/p2-activity-scenario.txt`). Diese unbeschränkten Leerqueue-Wartepunkte
liegen außerhalb des signalgebundenen `PresentationAwaiter`-Timeouts. Die Logs stützen eine
Blockade an diesen globalen Wartepunkten; ein lokaler nativer Reproduktions-/Stacknachweis wird
vor der endgültigen Ursachenbehauptung ergänzt.

Plan: isolierten API-35-Emulator bereitstellen und den unveränderten fehlgeschlagenen Stand
gezielt untersuchen. Für den animierenden Produktbildschirm die globale Queue-Ruhe durch
beobachtete echte Lifecycle-Ereignisse mit harten Zeitgrenzen ersetzen, sofern die Reproduktion
diese Ursache bestätigt. Titel-Touch, Accessibility-Später, Fokus, Speicherung und echte
Neuerstellung beibehalten. Keine Animation abschalten, Assertion abschwächen oder identischen
roten Lauf automatisch wiederholen. Der Produktcode und TouchGestureDriver bleiben außerhalb
solcher Harness-Korrekturen, solange kein eigener Defekt nachgewiesen ist.

Zusätzlicher P2-Auditbefund bei Artefaktzuordnung: GitHub testet im PR den synthetischen Merge
(`GITHUB_SHA`), während die Runs-API `head_sha` des Themenbranches meldet. Nur dessen Baum gegen
Main zu vergleichen ist als allgemeiner Inhaltsnachweis zu schwach, etwa wenn Basisänderungen
zwischen PR-Test und Merge hinzukommen und wieder zurückgenommen werden. Plan: zusätzlich den
wirklich ausgecheckten Git-Baum als erfolgreichen, eindeutigen Jobzeugen binden und bei Main-
Wiederverwendung exakt prüfen. Gegenproben für fremden/fehlenden/doppelten Baumzeugen ergänzen.
Danach passende lokale Prüfungen und vollständiger PR-Gate auf dem korrigierten Head; kein Merge
mit dem gegenwärtig roten Nachweis.

Die erste Prüfung des Baumzeugen besteht 44 CI-Vertragstests und Actionlint. Zwei bestehende
Release-Verdrahtungsassertionen erwarten noch die alten Abhängigkeitslisten; sie werden vor
der nächsten Prüfung um den jetzt verbindlichen Inhaltszeugen ergänzt. Negative ausführbare
Gatetests verlangen dessen Erfolg bereits in Quality, Android und PR.

Lokale native Vorbereitung: API-35-Systemabbild installiert, eigenes AVD unter
`/tmp/p2-avd-home` mit 2-GB-Testdatenpartition angelegt. Der vorhandene Emulator 36.4.9
segfaultet vor abgeschlossenem Boot sowohl mit swiftshader_indirect als auch swiftshader
ohne Vulkan. Damit ist ein Host-/Emulatorfehler belegt, seine genaue interne Ursache nicht.
Kein App-Test wurde dabei ausgeführt. Für die Reproduktion wird das offizielle stabile
37.1.11-Binary aus Googles SDK-Metadaten getrennt vom bestehenden SDK entpackt und dessen
veröffentlichte Prüfsumme geprüft; CI und Produkt bleiben dadurch unverändert.

Der Host-Coredump grenzt den ersten Emulatorabsturz auf einen SwiftShader-Worker
(`libGLESv2.so`) ein; Rohbeleg `/tmp/p2-emulator-host-backtrace.txt`. Auch das aktuelle
37.1.11-Binary scheitert mit SwiftShader vor Boot. Der nächste lokale Versuch verwendet
daher die vorhandene Intel-GPU ohne Vulkan; Animationseinstellungen bleiben 1.0. Dies ist
keine Wiederholung eines unveränderten App-/CI-Tests.

API35 mit Intel-GPU bootet. Der erste Instrumentierungsstart wurde vom Android-System
nach 30 Sekunden noch beim DEX-Klassenladen abgebrochen; kein JUnit-Test gestartet. Gleichzeitig
ANRs mehrerer Google-Systemapps, CPU-Load 29.52 bei zwei Kernen. Belege:
`/tmp/p2-native-before-all-logcat.txt`, `/tmp/p2-emulator-anr/`. Dies reproduziert nicht den
CI-Wartefehler. Vor erneutem diagnostischem Teststart wird der Erstboot-/Paketladezustand
beendet und dessen Last geprüft; keine Änderung von Animationen oder Testassertionen.

Die tatsächliche App-Testreproduktion bleibt getrennt von den Host-/Erstbootfehlern offen.
Der Bytecode belegt die unbeschränkte Synchronisierung unabhängig davon. Die begrenzte
Lifecycle-Hilfe wird daher jetzt vorbereitet, während das eigene AVD mit vier Kernen/4 GB
und vorhandenem Bootbestand startet. Vor Integration müssen der native Bedienweg und echte
Neuerstellungen bestehen. Eine nicht erfasste CI-Stackposition wird nicht nachträglich als
bewiesen ausgegeben; der unveränderte Test-APK bleibt für die Ursachenprobe erhalten.

Die Lifecycle-Hilfe kompiliert (1m04s). Die gemeinsame `@After`-Methode enthält für
den Produktfall ohne Harness-Activity noch einen unnötigen unbeschränkten Idle-Wait. Plan:
Diesen Wartepunkt nur zusammen mit der tatsächlich vorhandenen alten Harness-Activity ausführen;
der Produktfall beendet und beobachtet seine Activities bereits in der begrenzten Session.

### P2 – Direktnachweis und Prüfung der Wartekorrektur

Nach beendetem Erstboot und gezielter DEX-Vorkompilierung ausschließlich der beiden isolierten
Testpakete startet der unveränderte Test. Bei aktiven Animationen bleibt er nach Titel-Touch,
Fokus- und Queue-Assertion in der ersten Neuerstellung stehen. SIGQUIT-Stack vom 18:20:50Z:
`Instrumentation$Idler.waitForIdle` → `Instrumentation.waitForIdleSync` →
`ActivityScenario.getCurrentActivityState` → `ActivityScenario.recreate` →
`TodayProductInteractionScenario.execute:68`. Rohbeleg `/tmp/p2-native-before-wait-stack.txt`,
Zeilen 206–222, plus `/tmp/p2-native-before-compiled-result.txt`. Damit ist der zuvor nur aus
CI-Verlauf/Bytecode abgeleitete konkrete Wartepunkt lokal reproduziert. Die exakte Stackposition
des früheren CI-Laufs selbst wurde dort nicht erfasst.

Die Korrektur ersetzt ausschließlich die Lifecycle-Synchronisierung des echten Produktfalls
und seinen unnötigen gemeinsamen Nachlauf durch beobachtete, zeitbegrenzte Ereignisse. Touch,
Accessibility, Fokus-/Reihenfolge-/Erhaltungsassertionen und zwei echte Neuerstellungen bleiben.
Vollständiger lokaler Check auf korrigiertem Code: Exit 0, 34s (vorhandene unveränderte Host-
Ergebnisse wiederverwendet; 44 CI- und 38 Release-Python-Tests frisch, native APKs neu gebaut).
Actionlint und Diffprüfung grün. Native Prüfung des korrigierten APK steht als nächster Schritt an.

Korrigierter nativer Produktfall auf demselben API-35-AVD erfolgreich: 1 Test, 0 Fehler,
10.778s. Alle drei Animationsskalen unmittelbar vorher als 1.0 bestätigt. Lifecycle-Protokoll
zeigt drei unterschiedliche MainActivity-Instanzen mit zwei vollständigen Zerstörungs-/Start-
Übergängen und abschließender Zerstörung. Belege `/tmp/p2-native-fixed-result.txt` und
`/tmp/p2-native-fixed-logcat.txt`. Kein neuer Touch-Treiber, keine Koordinatenwiederholung,
kein Abschalten der Animationen und keine abgeschwächte fachliche Assertion.

Abgleich Korrekturrunde 4 gegen Plan: konkreter Wartepunkt mit unverändertem APK reproduziert,
begrenzte Lifecycle-Synchronisierung implementiert und nativ bestätigt; tatsächlicher PR-Testbaum
als eigener erfolgreicher Zeuge gebunden und Gegenproben bestanden. Abgleich gegen Roadmap:
P1-Bediennachweis erhalten; P2-Inhalts-/Profil-/Policy-Nachweis geschlossen; vollständiger lokaler
Gate erfolgreich. Gegenwärtig offen bleiben vollständiger PR-Gate auf diesem neuen Head,
Squash/exaktes Main, signierte aktuelle/historische Upgrades und Veröffentlichung. P3 beginnt
weiterhin erst nach P2-Abschluss. Pixel-Abnahme bleibt separat offen.

## P2 – Main-Integration und Korrekturrunde 5: aktuelles Kalender-Fixture

PR 365 bestand auf `34992250` den vollständigen Lauf 34705066507 und wurde als `074a3ad2`
nach Main gesquasht. Exakter Main-Baum `87865ef77a1ad6f072756878e46d307865d59ef2` stimmt mit
dem erfolgreichen PR-Inhaltszeugen überein; Wiederverwendung und Main-Aggregate bestanden.
Kandidat 0.2.173 / 1017301 ist regulär signiert und an diesen Main-Commit gebunden. Main-Lauf
34706313825 bestand alle fünf historischen signierten Upgrades, scheiterte aber im aktuellen
27→27-Smoke mit `occurrences.state differs`. Veröffentlichung wurde übersprungen; 0.2.173
ist damit noch kein freigegebener Release. Belege `/tmp/p2-main-failed.log`,
`/tmp/p2-main-candidate/`, `/tmp/p2-candidate-proof.json`.

Korrekturbranch: `codex/current-upgrade-smoke-fix` vom integrierten Main `074a3ad2`.
Vorläufige Ursache anhand Code/Fixture: Die Platzierung wird auf den Planungstag gebunden,
der offene Datensatz bleibt jedoch auf 2000-01-02 datiert. `OccurrenceCarryForward.collect`
markiert alte offene Einträge im regulären COLLAPSE-Modus als MISSED. Der vorhandene Room-
Test verwendet genau 2000-01-02 als Uhr und verdeckt deshalb diese Datumsabweichung.

Plan vor Änderungen: Unveränderten signierten Übergang ausschließlich im eigenen Emulator
nachvollziehen und die tatsächlich geänderte Zeile/Spalte abfragen. Den Room-Test zunächst
mit einem späteren Datum und der bisherigen Generatorbindung reproduzierend rot machen.
Dann den Planungstag im Template explizit durch einen gemeinsamen Tagesplatzhalter für
scheduledOn und displayOn ausdrücken und diesen in Generator sowie Testaufbau binden.
COLLAPSE und vollständige Erhaltungsassertionen bleiben erhalten; keine Produktschreiblogik
ändern oder die erwartete Zustandsänderung pauschal akzeptieren. Generatorprüfungen müssen
alle gebundenen Datumsfelder in Seed und Erwartung kontrollieren; Room muss mehrere Daten
und echte Neuöffnung prüfen. Fehlermeldungen der Probe sollen die konkrete synthetische Zeile
und Soll/Ist-Werte nennen. Danach vollständiger lokaler Check, eigener geprüfter PR/Squash und
neuer exakter Main-/Release-Gate; kein Wiederholen des unveränderten roten Main-Laufs.

Reproduktion bestätigt: Der unveränderte signierte Source→Kandidat-Übergang scheitert
auch lokal. Anschließendes lesendes SQL zeigt den alten offenen Datensatz als MISSED, einen
neuen OPEN-Datensatz vom 2026-09-12 und keine alte Platzierung. Die abgeschlossene Historie
bleibt unverändert. Rohbelege `/tmp/p2-native-calendar-repro.log` und
`/tmp/p2-native-calendar-rows.txt`. Der Room-Test mit späterem Datum und bisheriger Generator-
bindung scheitert auf API 26/35 (2 Tests, 2 Fehler, 55s); `/tmp/p2-calendar-repro.xml`.
Der erste Host-Aufruf nannte eine nicht vorhandene Gradle-Aufgabe und führte keine Tests aus;
der tatsächliche Reproduktionslauf nutzte die vorhandene Aufgabe `testInstrumentationUnitTest`.

Korrigierter Generator: 39 Release-Python-Tests grün, einschließlich beider Tagesbindungen
in Seed/Erwartung und unveränderter Historie/COLLAPSE. Room-Aufbau auf zwei Kalendertagen
(2026-09-12, 2031-06-18), jeweils API 26/35 und Neuöffnung: 4 Tests grün, 54s.
Zusätzliche lokale Prüfung der real generierten korrigierten Daten auf denselben regulär
signierten APKs: Quelle 0.2.172 und Kandidat 0.2.173 zeigen die Aufgabe im echten MainActivity,
alle Seedspalten und die Platzierung bleiben nach Start erhalten, In-place-Installation
erhält firstInstallTime. Beleg `/tmp/p2-native-fixed-calendar.log`. Setup/SQL-Abgleich dieses
Zusatznachweises erfolgte extern im eigenen Emulator; er ersetzt nicht den noch ausstehenden
CI-Lauf des neu gebauten signierten Probe-APK. Kein Zugriff auf produktive Pixel-Daten.

Abgleich der Kalenderkorrektur gegen Plan: unveränderten Fehler signiert und im echten Room-
Ablauf reproduziert; Ursache auf die widersprüchlichen Fixture-Daten begrenzt; gemeinsame
explizite Tagesbindung und aussagekräftige Spaltenfehler umgesetzt; keine Produktschreiblogik
oder COLLAPSE-Assertion geändert. Abgleich gegen P2-Roadmap: Aufgaben, Today-Platzierung und
Verlauf bleiben im aktuellen Smoke gefordert; Quelle und Artefakte bleiben unveränderlich gebunden;
Veröffentlichung bleibt bei rotem Smoke blockiert. Der eigene Klassifikator verlangt für diesen
Korrekturstand `full` einschließlich Release und aller historischen Lanes. Vollständiger lokaler
Check läuft; sein Ergebnis wird vor Merge geprüft. PR-/Main-/Publish-Nachweise sind noch offen.


## P2 – geprüfter Abschluss

Die Kalenderkorrektur bestand den vollständigen lokalen Check (Exit 0, 8m): 793 Hosttests,
0 Fehler, 0 Errors, 1 optionaler Benchmark-Skip; 44 CI- und 39 Release-Python-Tests,
Lint sowie Release-/Instrumentierungsbuilds. PR 366 bestand auf `35d2b3ad` den vollständigen
Lauf 34707845004 einschließlich aller sechs Android-Lanes. Der API-35-Animationsjob dauerte
11m28s; dies ist der vollständige Animationskorpus, keine gemessene Today-Profillaufzeit.
Abgleich gegen Korrekturplan und Roadmap ohne offene Abweichung.

Squash-Main `d0099a63448d79d0d937ace67497ee7f88e218b3` hat denselben geprüften Baum
`3d28156e6c228f6c815e09b42f62b846f5b981a2`. Exakter Main-Lauf 34708748445 bestand die
Inhalts-/Profil-Wiederverwendung, Packaging, aktuellen signierten 27→27-Smoke, alle fünf
historischen signierten Upgrade-Lanes und Publish. Release 0.2.173 / 1017301 wurde am
2026-09-12 um 17:44:40Z veröffentlicht; Tag `forest-android-1017301` zeigt auf diesen Main.
Heruntergeladene öffentliche APK und Metadaten sind bytegleich zum geprüften Kandidaten.
APK-SHA256 `1ced1d33234fa2e418030bdf622c103dc6bed94f697b53b1728b6d5fa4055aca`,
Metadaten-SHA256 `9929b316c56a2e9d7de63850bb219041ab3f657f0518ae9b2cf246579bb12aa6`.
App und Helper tragen die reguläre Signatur `de45d94c9724beeaa2e0dff31f69f53bb0f4c9ba79a5aa419d1f29d18f4d91da`.
Belege: `/tmp/p2-calendar-{pr-completed,main-completed,candidate-proof,publish-proof}.json`.
P2 ist damit abgeschlossen; der vorherige fehlgeschlagene Main-Lauf bleibt als Historie erhalten.
Pixel ist bei der letzten frischen Abfrage nicht angeschlossen; sichtbare Geräteabnahme bleibt offen.

## P3 – konkreter Phasenplan vor Produktänderungen

Branch `codex/diagnostic-lifecycle-contract` von Main `d0099a63`. Profil `full`, Release erforderlich.
Die kanonische Roadmap bleibt unverändert. Ziel ist Schutz von Schema und sämtlichen Nutzdaten
während eines ausdrücklich unterstützten Diagnosezugangs, mit sicherem anschließenden Normalstart.
Android-/WorkManager-Verwaltungsdaten und Framework-Caches sind keine zugesicherte Dateisystemruhe.

Belegter Einstieg: Auf API 26 und Android 16 erzeugt `Instrumentation.newApplication` die App
vor Providern; `Instrumentation.onCreate(arguments)` kommt erst nach Providern. Der bestehende
Argumentcheck ist deshalb zu spät. Der neue, getrennte Diagnose-Runner kennt seinen Modus durch
seine Komponente und aktiviert vor `super.newApplication` einen kleinen, versionierten, beim
Shrinking erhaltenen, nur prozesslokalen Bootstrapvertrag. Fehlt der Vertrag, wird die Kombination
vor App-/Provider-Erzeugung ausdrücklich abgewiesen. Der alte Runner bleibt für historische
Seed-/Verify-Läufe zuständig; sein bisheriger Diagnosezugang wird ausdrücklich ersetzt.

Die tatsächlich aufgelöste WorkManager-Version 2.11.2 benötigt ihre Provider-Initialisierung:
`SystemJobService.onCreate` wirft sonst bei dieser Application eine Ausnahme. Deshalb bleiben
WorkManager und die übrigen geprüften Framework-Provider initialisiert. Der Schutz verhindert
AppContainer-/Room-Erzeugung sowie fachliche Receiver-, Widget-, Timer- und Workerarbeit.
Er beansprucht nicht, WorkManagers eigene Verwaltungsdatenbank schreibfrei zu halten.

Implementierungsfolge:

1. Bootstrapvertrag und eigener Diagnose-Runner, explizite Helper-Manifestkomponente mit korrektem
   Zielpaket, Erhaltungsregel für die reflektierte ABI und eindeutige Versions-/Fehlermeldung.
   Keine späte Umschaltung anhand Diagnoseargumenten, keine persistenten Flags/Komponentenänderungen.
2. Application vor Containeraufbau sperren; registrierte Boot-/Timer-/Task-Receiver vor `goAsync`
   und WidgetProvider vor Framework-Callbackdispatch abfangen. FlowWakeWorker gibt vor Containerzugriff
   `retry` zurück. MainActivity, FlowRunsActivity und FlowSetupActivity beenden einen während
   Diagnose erfolgenden Start vor fachlichen ViewModels sicher. Zentraler Room-Einstieg darf im
   Diagnoseprozess keine Datenbank erzeugen. Normale Bedien- und Timerpfade bleiben erhalten.
3. Frische native Prozesse mit getrennten isolierten Fixture-/Diagnose-/Normalphasen prüfen.
   Fixture-Schreiben ausschließlich für das verifizierte Emulator-Testpaket. Die produktive
   Diagnosekomponente bekommt keinen Seed-Modus. Echte Schema-24-Daten belegen fehlende Migration;
   Schema-27-Daten umfassen zusätzlich Archiv, Historie, Platzierung, Belohnungen und Timer.
   Vollständige typisierte Nutzdaten-/Schema-Snapshots vor und nach Diagnose vergleichen.
4. Während einer begrenzten Diagnose echte Manifest-Broadcasts für Boot, Paketwechsel, passende
   Timer, Widget-Lifecycle und gültige Widget-Aktionen zustellen; zusätzlich Activity-Start und
   echte WorkManager-Ausführung prüfen. SystemJobService muss trotz Diagnose initialisieren können.
   Direkte Callbackaufrufe oder ein gezähltes Application.onCreate ersetzen diese Nachweise nicht.
5. Normales Diagnoseende und kontrollierten Prozessabbruch getrennt prüfen. Ohne `--no-restart`
   beendet Android den Instrumentierungsprozess; der unterstützte Aufruf prüft dessen Ende und
   startet danach normal. Fällige Timer und zurückgestellte Systemarbeit werden abgeglichen,
   ohne ignorierte Benutzeraktionen nachzuholen oder Historie/Belohnungen doppelt zu buchen.
6. Den neuen Runner mit regulär signiertem Kandidaten prüfen, einschließlich ausdrücklicher
   Ablehnung des vorherigen APK ohne ABI. Die alten signierten Seed-/Verify-Pfade bleiben grün.
   Eine mögliche Migration durch Paketwechsel vor dem Diagnoseaufruf wird nicht als Nachweis
   einer schreibfreien Diagnose alter Schemata ausgegeben; dafür existiert die isolierte Phase 3.
7. Ausführbare Skript-/Identitätsprüfungen und Dokumentation auf diese Zugänge ausrichten.
   Vollständiger lokaler Gate, native Plattformmatrix, geprüfter PR/Squash, exaktes Main und
   erforderlicher Release. Danach getrennte unterstützte lesende Pixel-Diagnose, Helper entfernen
   und Normalstart prüfen, sobald das Gerät verfügbar ist.

Abnahme gegen Plan und Roadmap separat: früher Schutz tatsächlich nativ belegt; keine Migration
oder fachliche Schreibarbeit in den Diagnoseintervallen; alle Nutzdatenkategorien erhalten;
Ereignisbehandlung ohne Absturz oder nachträgliche Benutzeraktion; normaler Neustart/Timerabgleich
nach Ende und Abbruch; klare alte/neue APK-Grenze; historische Upgrades weiterhin funktionsfähig.
Jede festgestellte Abweichung erhält vor Änderungen einen begrenzten Korrekturplan.

### P3 – erste Buildprüfung und Manifestkorrektur

Der erste Instrumentierungs-/Helper-Build bestand (58s). Die anschließende Prüfung des tatsächlichen
APK-Manifests zeigte jedoch nur AndroidJUnitRunner, Fixture- und Normal-Runner: AGP hatte den zuerst
declarierten DiagnosticProbeInstrumentation durch den ausgewählten Standardrunner ersetzt. Ein
grüner Build allein beweist daher den neuen Einstieg nicht. Korrekturplan vor Änderung: den primären
Gradle-Runner als eigenes erstes Instrumentierungselement mit gebundenem Namen ausdrücken, die drei
zusätzlichen Komponenten danach belassen. Erneut bauen und alle Runner/Zielpaket-Paare prüfen;
die spätere ausführbare Identitätsprüfung muss eine solche fehlende Komponente erkennen.

### P3 – native Kontrolle und Korrekturrunde 2: geschützte Testzustellung

Manifestkorrektur bestanden: alle vier Komponenten mit isoliertem Ziel im tatsächlichen APK;
weiterer Build 16s. Vier neue ausführbare Manifestprüfungen erkennen fehlende, doppelte und falsch
zugeordnete Komponenten. Der erste native Normalkontrolllauf mit Schema27-Daten, unverändertem
Verlauf/Platzierung/Archiv/Belohnungen und fälligem Timer besteht (`/tmp/p3-native-first/normal-control`).
Der erste gehaltene Schema24-Diagnoseprozess meldet den frühen Vertrag; der echte nachgelagerte
Provider bestätigt Diagnosemodus, fehlenden Container und initialisierten WorkManager.

Der Ereignislauf scheitert im ausschließlich debugseitigen Testsender: Android verbietet dessen
App-UID den Versand des geschützten `APPWIDGET_UPDATE_OPTIONS`-Broadcasts. Konkreter nativer Stack
in `/tmp/p3-native-first-runtime.log`, `DiagnosticWitnessProvider:48`; noch kein bestandenes
P3-Ereignis-/Erhaltungsurteil. Die Diagnose wurde dabei beendet, während der erste Treiber weitere
Testereignisse zustellte; dies darf nicht als ununterbrochenes Diagnoseintervall gelten.

Korrekturplan vor Änderungen: das Optionsereignis wie die anderen geschützten Ereignisse über die
bereits rootberechtigte, zuvor als Emulator geprüfte Shell zustellen. Android15 `AppWidgetProvider`
Zeilen80–87 prüft die Anwesenheit beider Extra-Schlüssel und ruft den Callback auch mit null-Options
auf; der geprüfte Produktcallback verwendet diesen Parameter nicht. Daher `--ei appWidgetId 901`
und `--esn appWidgetOptions` verwenden und diese konkrete Testeingabe benennen, statt eine App-UID
als Systemsender auszugeben. App-seitigen geschützten Testsender entfernen, echte WorkManager-
Ausführung und Bindung des registrierten SystemJobService behalten. Der Treiber prüft vor und nach
jeder Zustellung die originale Diagnose-PID und bricht bei Prozessende sofort ab. Danach neue,
eigene Artefaktablage und erneuter nativer Lauf, keine unveränderte Wiederholung des Fehlers.
Quelle lokal `/tmp/p3-AppWidgetProvider-android15.java`, offizieller AOSP-Tag android-15.0.0_r1.

### P3 – native Ereignisabnahme API35 und Korrekturrunde 3: erkennbare ABI-Ablehnung

Der korrigierte native Lauf `/tmp/p3-native-sender-fixed/result.json` besteht vollständig:
Normalkontrolle sowie Schema24/27 mit jeweils regulärem Ende und absichtlichem Prozessabbruch.
Alle Schema-/typisierten Datensnapshots sind in den Diagnoseintervallen unverändert. Der echte
Provider belegt frühe Diagnose, fehlenden Container und initialisierten WorkManager; der tatsächlich
registrierte SystemJobService wird über Android gebunden; der echte FlowWakeWorker wird durch
WorkManager ausgeführt und als ENQUEUED nach mindestens einem Versuch zurückgestellt. Echte
Manifest- und Activity-Zustellungen bestehen mit derselben PID ohne Produktfehler. Der folgende
Normalprozess erreicht Schema27, gleicht den fälligen Timer ab und führt den zurückgestellten Worker
erfolgreich aus; vorhandene Daten einschließlich Buchungen bleiben bis auf den erwarteten Timerstand
unverändert. Dies ist API35; weitere Plattformen, signierte APKs und vollständige Gates bleiben offen.

Zusätzliche native alte-APK-Probe verwendet ausschließlich das erhaltene P2-Test-APK ohne Bootstrap
(`/tmp/p2-native-before/app-instrumentation.apk`, SHA256 b8b50ec80af018147d80feaff662b9aa0ff8c8851f3cfbb55e84fe00861c1a1e)
und den neuen Helper im eigenen Emulator. Kein Produktions-APK wurde ersetzt. Der frühe Hook lehnt
vor App-Erzeugung ab; Datenbank, WAL und SHM sind bytegleich vorher/nachher. Die regulär signierte
Kombination bleibt separat im Release-Gate erforderlich. Abweichung: der Instrumentierungsclient
bekommt nur `shortMsg=Process crashed`, obwohl die Exception den ABI-Grund nennt; damit ist die
geforderte erkennbare Ablehnung noch nicht erfüllt. Beleg `/tmp/p3-legacy-early-rejection.json`.

Korrekturplan vor Änderung: der bereits initialisierte Instrumentierungsrunner sendet im frühen
Ablehnungspfad ein ausdrückliches Unsupported-Ergebnis per `finish`, bevor die weiterhin nötige
Exception eine App-Erzeugung verhindert. ActivityThread.finishInstrumentation ruft auf API26/16
direkt den schon vorhandenen Systemdienst auf und benötigt keine erzeugte Application. Den alten
APK-Fall erneut nativ mit Datenbank-Bytevergleich prüfen. Der unterstützte lesende CLI-Zugang und
der aktuelle signierte Upgrade-Lauf müssen dieses Ergebnis von einer tatsächlichen Diagnose und
von einem unbekannten Prozessabsturz unterscheiden; ein Kandidat muss erfolgreich unterstützt sein.

Korrekturrunde 3 nativ bestanden: derselbe ältere Teststand wird über den unterstützten CLI mit
Status `unsupported`, angefordertem Protokoll1, bestätigtem Prozessende und Exit4 abgewiesen;
DB/WAL/SHM sind bytegleich. Die aktuelle isolierte APK liefert anschließend `supported`, Protokoll1,
Schema27 und bestätigtes Prozessende (Exit0). Belege `/tmp/p3-legacy-explicit-rejection.json`,
`/tmp/p3-legacy-explicit-cli.json`, `/tmp/p3-current-supported-cli.json`. Drei Parser-/Zugangsprüfungen
und sechs Upgrade-Wrapper-Prüfungen sind grün; unbekannte Quellenabstürze sowie nicht unterstützte
Kandidaten sperren den Gate. Historische Lanes behalten ihren bisherigen Seed-/Verify-Zugang.

Die Helper-Veröffentlichung bekommt abschließend ein eigenes Manifest: nur historischer Upgrade-
Runner und unterstützter Diagnose-Runner werden am Produktionsziel deklariert. Fixture- und Normal-
Kontrollrunner bleiben ausschließlich im Manifest des isolierten Testziels. Die vollständige Paar-
prüfung muss diese beiden zulässigen Manifestmengen getrennt erzwingen. Dadurch wird insbesondere
kein Normal-Kontrollrunner angeboten, der vor seiner späten Isolationsprüfung eine produktive
Application normal starten könnte. Diese Grenze ist Teil der geplanten Helper-Identität.

### P3 – abschließende Prüfverdrahtung

Die erste vollständige schnelle Skriptprüfung bestand 52 CI-Tests; ein bestehender Release-
Quelltextvertrag erwartete den Runnernamen noch direkt im Shellskript statt im neuen ausführbaren
Paarprüfer. Der zusätzliche Room-Zugriffsgegencheck benutzte zunächst `try`-with-resources, obwohl
RoomDatabase hier kein AutoCloseable ist. Beides ist auf die Testverdrahtung begrenzt.
Korrektur vor erneuter Prüfung: den bestehenden Wiring-Vertrag auf den aufgerufenen Paarprüfer
richten (dessen positive/negative Verhaltensfälle bleiben), die Room-Probe explizit in `finally`
schließen. Erfolgreiche native CI-Lanes müssen ihre Diagnosebelege ebenfalls hochladen; so bleiben
API-/Schema-/Ereignisnachweise nach dem vergänglichen Emulator zugänglich, nicht nur der Exitcode.

Die Produktions-Helper-Kompilierung besteht nach der expliziten Room-Schließung. Die erneute
Release-Skriptprüfung findet zwei weitere veraltete Textprüfungen im selben bestehenden
Workflowtest: alte Fehlermeldungen der ersetzten Runnerprüfung. Begrenzte Fortsetzung der
Prüfverdrahtungskorrektur: den tatsächlichen Aufruf des vollständigen Manifest-Paarprüfers im
Packaging prüfen; dessen ausführbare Negativfälle tragen weiterhin den Verhaltensnachweis.

### P3 – abschließende lokale Vorprüfungen

Produktions-Helper-Build grün (7s); tatsächliches APK-Manifest mit genau Upgrade- und Diagnose-
Runner am Produktionsziel bestanden (`/tmp/p3-production-helper-proof/`). Anschließender regulärer
App-/Helper-Build grün (10s), vollständige isolierte Identitätsprüfung ebenfalls. Alle 42 Release-
Skriptprüfungen bestehen. Actionlint findet ausschließlich den unverändert auch im Main-Vorgänger
vorhandenen Shellcheck-Stilhinweis SC2129 im Packaging; keine neue Workflowdiagnose.

Zusätzlicher nativer Schema24-Gegencheck mit dem final kompilierten Testcode besteht:
DatabaseFactory verweigert den Zugriff mit der vorgesehenen frühen Schutzmeldung, vollständiger
Schema-/Datensnapshot bleibt unverändert (`/tmp/p3-native-database-guard/`). Der bisherige gesamte
API35-Ereignisnachweis bleibt gültig; der letzte Zusatz prüft gezielt den neuen direkten Room-Zugriff.
Der eigene Emulator wird für den vollständigen Hostcheck beendet. Kein physisches Gerät verbunden.

### P3 – Abgleich vor PR (noch kein Freigabeabschluss)

Abgleich gegen den konkreten Phasenplan: früher reflektierter Vertrag, zentrale Room-Sperre,
alle registrierten fachlichen Receiver/Activities und FlowWakeWorker sind erfasst. Die echten
AndroidX-Provider bleiben erhalten; der nachgelagerte native Providerzeuge bestätigt deren
Reihenfolge. Die zwei Schemata, Ende/Abbruch, vollständige Nutzdatensnapshots, reale Worker-
Zurückstellung und folgender Normalstart sind im API35-Ergebnis vorhanden. Alte Test-APK ohne ABI
wird vor App-Erzeugung explizit abgewiesen; Produktionshelper exponiert ausschließlich zwei Zugänge.

Gesonderter Abgleich gegen Roadmap P3: Schreibruhe bezieht sich auf die fachliche Datenbank;
WorkManager-Verwaltung ist ausdrücklich ausgenommen. Die SystemJobService-Bindung beweist
Serviceerzeugung, keinen JobScheduler-Dispatch. Ereignisse werden innerhalb derselben Diagnose-PID
zugestellt, Benutzeraktionen nicht nachgeholt; Timer und Worker werden danach regulär abgeglichen.
Keine persistenten Diagnoseflags, deaktivierten Produktkomponenten oder geänderten Geräteeinstellungen.
Der unterstützte CLI prüft Komponentenidentität, ABI-Ergebnis und Prozessende. Historische
Seed-/Verify-Lanes bleiben erhalten. Vollständiges Profil ist zwingend; P3 kann sich nicht selbst
auf das Today-Profil verkürzen.

Offene Freigabenachweise bleiben ausdrücklich: laufender vollständiger lokaler Check, frische
API26/35/37-PR-Läufe, regulär signierte vorherige/neue APK im aktuellen Upgrade-Lane sowie fünf
historische Lanes, geprüfter Squash/Main/Publikation. Unterstützte Pixel-Diagnose und anschließende
Normal-/Bedienabnahme bleiben bei fehlendem Gerät separat offen. Remote-Main aktuell d0099a63;
noch kein P3-PR vorhanden. Kein weiterer Produktumfang aus P4 in diesem Branch.

### P3 – PR367 Korrekturrunde 4: native Activity- und Diagnoseintervallgrenzen

PR-Head e04b0c1b, Lauf 34712253576: normale API35/37- und animierte API26/37-Lanes bestehen;
API35-Animation noch aktiv. Normaler API26-Gradle-Korpus besteht (60 gemeldete Tests, zwei
vorgesehene Upgrade-Skips), aber der anschließende Diagnose-Treiber läuft beim ersten
`am start -W MainActivity` in seinen 30s-Timeout. Die Diagnose selbst meldet danach regulär
Schema24 ohne FK-Fehler. Job103603663949 und `/tmp/p3-api26-first-artifacts/` belegen die Grenze.

Zusätzliche beobachtete Grenze: API26 startet direkt nach dem Ende der Instrumentierung einen
normalen SystemJobService-Prozess; der zurückgestellte Worker läuft dort erfolgreich. Daher
würde ein erst in einem weiteren Diagnoseprozess aufgenommener Snapshot erlaubte normale
Wiederherstellung mit unerlaubter Arbeit innerhalb der Diagnose verwechseln. Der bisherige
API35-Erfolg widerlegt diese native API26-Reihenfolge nicht.

Begrenzter Korrekturplan vor Änderung:
1. Der ausschließlich debugseitige Provider registriert ActivityLifecycleCallbacks. Für jede
   reale Produkt-Activity werden Erzeugung und Zerstörung, finishing, Diagnosemodus und PID
   festgehalten. Der Treiber sendet den Start einmal ohne `-W` und wartet begrenzt auf diese
   tatsächlichen Callbackbelege sowie dieselbe PID; keine schwächere reine Shell-Erfolgskontrolle.
2. Die echte DiagnosticProbeInstrumentation erhält einen explizit auf Emulator-Testpaket
   begrenzten lesenden Prüfmodus. Ein zufälliger Token bindet das vom Treiber nach allen Ereignissen
   geschriebene Bereitschaftssignal an den gehaltenen Prozess. Noch vor Diagnoseende werden
   vollständiger ursprünglicher Schema-/Datensnapshot, direkte Room-Sperre und native Ereignis-
   belege geprüft und bestätigt. Der Zugang enthält weiterhin keinen Seed-Modus.
3. Im Abbruchfall bestätigt der Runner dieselben Prüfungen und hält danach begrenzt bis zur
   gezielten Prozessbeendigung. Der Treiber muss diese Bestätigung vor dem Abbruch sehen.
   Nach Ende/Abbruch ist normales Hintergrundanlaufen erlaubt; der frische Normal-Kontrolllauf
   prüft weiterhin Timer, denselben zurückgestellten Worker und sämtliche erhaltenen Fachzeilen.
   Der normale Produktionsdiagnoseaufruf behält seinen bisherigen lesenden Weg ohne Testsynchronisation.
4. Dokumentation und fokussierte Treiber-Gegenprüfungen auf tatsächliche Activitycallbacks,
   Diagnose-PID und Intervallbestätigung ausrichten. API26 nativ und die betroffenen API35/37-
   Diagnosefälle neu prüfen. Der vorhandene vollständige lokale Lauf wird regulär beendet und
   seinem ursprünglichen Quellstand zugeordnet; Änderungen erst nach dessen Buildabschluss.

Die gleiche belegte Normalprozess-Rennbedingung gilt für den CLI: nach Ende der Diagnose darf
ein neuer normaler Prozess bereits laufen. Die Beendigungsprüfung muss daher die originale
Diagnose-PID verfolgen, statt jede neue Paket-PID als weiterlaufende Diagnose einzustufen.
Ergänzung zum Korrekturplan: PID im unterstützten und frühen Unsupported-Ergebnis eindeutig
berichten und ausschließlich deren Ende prüfen; einen bereits gestarteten Normalprozess separat
benennen. Keine Zusicherung einer dauerhaft gestoppten App oder von Schreibruhe nach Diagnoseende.

Vollständiger lokaler Check für e04b0c1b regulär mit Exit0 abgeschlossen: 22m49s, 793 Hosttests,
0 Fehler, ein optionaler Benchmark-Skip, 52 CI-/42 Release-Skriptprüfungen, Lint, alle Builds,
Identität und Größenlimits bestanden. APKs unter `/tmp/p3-initial-full-proof/` erhalten.
Dieser Befund gilt für den ursprünglichen P3-Stand und schließt die API26-Korrekturrunde nicht.

Korrekturrunde 4 nativ auf API26 bestanden (`/tmp/p3-round4-api26/result.json`, 169.08s):
Normalkontrolle und alle vier Schema24/27-Ende-/Abbruchfälle. Für MainActivity, FlowRunsActivity
und FlowSetupActivity liegen tatsächliche created/destroyed/finishing/diagnosing-Belege der
originalen PID vor. Der echte Diagnose-Runner bestätigt vor seinem Ende den vollständigen
Datensnapshot, die Room-Sperre und die Worker-/Servicebelege mit gebundenem Token. Nach Ende
und Abbruch bestehen Timer-/Workerabgleich und vollständiger Erhaltungscheck im Normalbetrieb.
55 CI- und 43 Release-Skriptprüfungen bestehen; Gegenfälle erkennen fremde PID, alten Token,
fehlende Activitycallbacks und verweigern physische Geräte vor Root/Installation/Fixture-Arbeit.
Die Synchronisation ist ausschließlich am isolierten Emulator-Testpaket erlaubt; feste reflektierte
lesende Assertions vermeiden eine Room-/Fixture-ABI-Verknüpfung im Produktionsdiagnosepfad.

Der geänderte CLI besteht zusätzlich nativ auf API26: ältere isolierte APK ohne ABI wird vor
App-Erzeugung mit explizitem Unsupported, originaler PID und Exit4 abgewiesen; DB/WAL/SHM bleiben
bytegleich. Die aktuelle APK liefert danach einen erfolgreichen Diagnosebericht und bestätigt
das Ende ihrer PID (Exit0). Belege `/tmp/p3-round4-api26-legacy-rejection.json` und
`/tmp/p3-round4-api26-supported-cli.json`; frühere API35-Belege werden nicht überschrieben.
Abgleich der Korrektur: echte Activity-Zustellung und vollständiger Datenumfang bleiben erhalten;
Zeitgrenze jetzt innerhalb der Diagnose, anschließender Normalprozess ausdrücklich zulässig.
Der Emulator wird nach diesen Nachweisen beendet. Produktionshelper-Build und vollständiger
lokaler Abschluss werden für den korrigierten Stand neu geprüft; neuer PR-Head bleibt bis dahin
und bis zum passenden vollständigen CI-Ergebnis ungemergt.

## P3 – geprüfter Main-/Releaseabschluss

PR367 wurde auf Head edb349a44de53f0235cc906c695a9e4edba8701e im Lauf34713738417 vollständig
geprüft: Qualitäts-, Instrumentierungs- und PR-Gate sowie alle sechs API26/35/37-Lanes grün.
Alle zwölf heruntergeladenen Diagnosefälle binden Provider, Service, Worker, echte Activity-
Callbacks und vollständige Daten-/Room-Assertions an die ursprüngliche Diagnose-PID; Ende/Abbruch
und folgender Normalbetrieb bestehen. Zusätzliche Diagnosefolge je CI-Plattform: 58.78s/53.96s/
55.59s (API26/35/37), nicht mit dem gesamten Android-Job gleichzusetzen.

Finaler lokaler check-all.sh auf dem korrigierten Stand: Exit0, 15m09s; 793 Hosttests, keine Fehler,
ein optionaler Benchmark-Skip; 55 CI- und 43 Release-Skriptprüfungen, Lint, alle Builds, Identität
und Größenlimits bestanden. Native ältere/neue isolierte APK-Prüfung auch auf API26 bestanden.

Squash-Main 3c0a873efa0eb553bcf670b778443d17659422fa ist inhaltsgleich zum PR-Baum
c7a9d533b83281eaee76b371b2d4b521222aec4a. Exakter Main-Lauf34714576280 erfolgreich: gültige
Wiederverwendung von PR367/Lauf34713738417 mit Profil full und Policy1, Packaging, aktueller
signierter 27->27-Smoke und alle fünf historischen Upgrades, anschließend Veröffentlichung.
Die Quelle0.2.173 wird vom neuen signierten Helper vor App-Erzeugung explizit als unsupported
abgewiesen; Kandidat0.2.174 liefert supported, Schema27 ohne FK-/Zählerfehler und bestätigtes
Ende der Diagnose-PID. Historische Seed-/Verify-Lanes bleiben funktionsfähig.

Release forest-android-1017401 / 0.2.174 /1017401 veröffentlicht am2026-09-12T19:47:41Z;
Tag zeigt auf denselben Main-Commit. Erneut heruntergeladene APK und Metadaten sind bytegleich
mit dem getesteten Kandidaten. APK SHA256 80c2a04bdf085e5d447f8140e22baf8b6d99c3b3e97986f746864fe7d64827de;
Metadaten359f8789dd318f17ba168368efc43f4227e8244d41cda54fe5afb285cb257cd9;
Helper860502b4c9c9c7cfa9115f123687e56755eab3139512aad54e999ce6a8303435.
APK und Helper tragen reguläres Zertifikatde45d94c9724beeaa2e0dff31f69f53bb0f4c9ba79a5aa419d1f29d18f4d91da.
Belege `/tmp/p3-publication-proof.json`, `/tmp/p3-candidate-proof.json`,
`/tmp/p3-main-reuse-proof.json`, `/tmp/p3-current-upgrade-job.log`, `/tmp/p3-round4-ci-diagnostics/`.

Abgleich gegen Phasenplan und Roadmap: implementierter Diagnosevertrag und sämtliche automatischen
Freigabenachweise erfüllt. Der erste fehlgeschlagene API26-Testlauf bleibt als Historie erhalten;
Korrektur erhält Datenumfang und tatsächliche Android-Lifecycle-Zustellung. Keine Produktionsdaten
verändert. Softwareseitiger P3-Abschluss belegt; mangels verbundenem Pixel bleiben dessen unterstützte
lesende Diagnose, Helper-Entfernung und sichtbarer Normal-/Bediennachweis ausdrücklich bei P5 offen.
P4-Arbeit an isolierten Datenbanken ist dadurch nicht blockiert.

## P4 – Phasenplan vor nativer Ursachenuntersuchung

Ausgangspunkt Main3c0a873e / veröffentlichte0.2.174 /Schema27.
Branch codex/recovery-origin-investigation. Erwartetes Profil full wegen neuer nativer Tests;
kein Produktrelease bei ausschließlich Untersuchungsdokumentation und isolierten Testfixtures.
Keine Produktmigration oder Fachlogik wird in dieser Untersuchungsphase geändert.

1. Den vorhandenen hostseitigen SQL-Befund am echten Room-Öffnungspfad prüfen. Synthetische
   gesunde Quellbestände mit clean/started/waiting-Runs, nichtleeren Kindtabellen und vorhandenem
   Verlauf verwenden. Fremdschlüsselmodus innerhalb der echten Migration22->23 messen, nicht
   im Test auf einen gewünschten Wert umschalten. Reale Migrationen delegieren; vor und nach
   22->23 die Beziehungen festhalten, bevor24->25 mögliche Waisen archiviert.
2. Zwei tatsächliche Schemahistorien prüfen: exportiertes Schema22 sowie aus Schema20 durch
   die realen Zwischenmigrationen entstandene physische Spaltenordnung. Die ursprünglichen
   Fachwerte und drei beobachteten Waisenkategorien differenzieren. Bei aktuellem Öffnen bis27
   zusätzlich exakte Archiv-IDs/Quelle und erhaltene gestartete/wartende Abläufe, Kandidaten und
   Verlauf prüfen. Datenbanken liegen ausschließlich im isolierten Emulator-Testpaket.
3. Einführungsversion anhand Historie/Release belegen: PR335/f6e72e51 führt die Umwandlung ein;
   früheste veröffentlichte enthaltene Version0.2.158 (Tag1015801, Commita25add10). Der getrennte
   frühere Spaltenpermutationsfehler bleibt von der Waisenerzeugung unterscheidbar.
4. Sequenzzähler als eigene Frage abschließen: bereits betrachtete Offer-/Carry-forward-, Rewind-,
   Abbruch-/Lösch- und Snapshot-/Mapper-Pfade mit ihren Transaktionsgrenzen dokumentieren.
   Der ausgeführte originale Schema20-Modellcode besteht32 Übergänge ohne Zählerrücksetzung;
   dies beweist weder sämtliche Versionen noch die persönliche Historie. Fehlende Belege konkret
   nennen; keinen Ursachenfix ohne erreichbaren Reproduktionspfad erfinden.
5. Zuerst gezielte native API26-Prüfung, anschließend vollständiger lokaler Check und geltende
   PR-Matrix. Ergebnis pro Anomalie mit reproduziertem Vorgang oder begrenztem Negativbefund
   dokumentieren. Gesonderter Abgleich gegen diesen Plan und P4 der Roadmap. Geprüfter PR,
   Squash und passender Main-Abschluss vor einer eventuellen Folgephase.
6. Nur wenn ein weiterhin vorhandener Erzeuger nachgewiesen ist, P4b separat mit kleinstem
   Ursachenfix, Regressions- und Erhaltungsnachweisen abgrenzen. Keine stillschweigende neue
   Daten-/Produktentscheidung und keine pauschale Freigabe eines Architekturumbaus.

### P4 – Fixture-Abgleich vor dem nativen Lauf

Der erste Test-Build besteht. Quellenabgleich mit MigrateLinearFlowExecution zeigt vor Ausführung:
WAITING_TIME setzt einen abgeschlossenen Vorgänger voraus; Position0 wäre fachlich ungültig.
Begrenzte Fixture-Korrektur: wartender Ablauf mit zwei Schritten und Cursor1, erwartete Wartezeit
am Vorgänger. Ressourcenenden liegen innerhalb der vorhandenen Snapshotpositionen. Der Test
bleibt eine gesunde Ausgangsdatenbank; keine Akzeptanz eines ungültigen Bestands als Reproduktion.

### P4 – Nativer Nachweis und Audit vor vollständiger Freigabe

RecoveryOriginInstrumentationTest besteht nativ auf eigenem API26-Emulator: zwei Tests,4.256s,
keine Fehler. Tatsächlicher Room-Migrationsmodus FK=0, innerhalb Upgrade-Transaktion. Gesunde
Schemata20/22 erzeugen direkt durch22→23 jeweils eine flow_run_steps-, flow_run_resources- und
occurrence_steps-Verletzung. Physische Vorgeschichten unterscheiden sich nachweislich; alle
semantischen Spaltenwerte bleiben beim Umbau korrekt. Nach Öffnung auf27 sind die drei Zeilen
vollständig samt SQLite-Typ archiviert; gültige Mischabläufe, Verlauf und Belohnungen erhalten.
Logs `/tmp/p4-api26-native-results.txt`, `/tmp/p4-api26-origin-logcat.txt`; native Builds17s/13s.

Untersuchungsbericht: [recovery-origin-investigation.md](recovery-origin-investigation.md).
Einführung PR335/erste reguläre Version0.2.158 separat belegt. Der Sequenzzähler hat nach Prüfung
der direkten historischen Aufrufer, Transaktionen, Mapper und32 Modellübergänge weiterhin keinen
reproduzierten Erzeuger. Persönliche Herkunft wird weder aus dem Archivschema noch aus diesem
begrenzten Negativbefund behauptet.

Abgleich gegen Phasenplan: tatsächlicher Room-Pfad, beide physischen Historien, Zwischenprüfung,
vollständige Archivspalten und erhaltener nichtleerer Verlauf belegt. Abgleich gegen RoadmapP4:
drei Kategorien haben gemeinsamen reproduzierten Erzeuger, Sequenz bleibt abgegrenzt unbekannt.
Der Erzeuger ist noch im aktuellen Migrationspfad enthalten; deshalb P4b erforderlich. Keine
Produktänderung in P4. Vollständiger lokaler Check, frischer PR/API26/35/37 und exakter Main-Abschluss
stehen bis zu ihren tatsächlichen Ergebnissen offen; keine Wiederholung abgeschlossener P3-Belege.

Vollständiger lokaler check-all.sh anschließend mit Exit0 abgeschlossen: 55 CI- und43 Release-
Skriptprüfungen frisch erfolgreich; Gradle9s mit172 Tasks, davon169 UP-TO-DATE. Unveränderte Host-
Tests, Lint und Produktbuilds wurden gültig wiederverwendet, nicht als neue793 Testausführungen
gezählt. Native neue Testklasse zuvor frisch gebaut und ausgeführt. Instrumentierungsidentität
und Größenlimits bestanden. Klassifikation bestätigt full, release_required=false; PR-Matrix
bleibt vollständig. Die folgenden noch offenen Nachweise sind PR und exakter Main.

### P4 – Korrekturrunde 1: historisch zulässige Zustände

Nach PR368/Headb5788c9f zeigt der direkte Quellabgleich eine Fixture-Grenze: PENDING_START wird
erst mit PR325/c5f062d1 unter Schema22 eingeführt. Der erste erfolgreiche native Lauf bewies
FK-Modus und Waisenerzeugung, aber seine als20 bezeichnete Datenvorgeschichte enthielt bereits
diesen späteren Zustand. Die grüne Ausführung ist kein Nachweis seiner historischen Zulässigkeit.

Korrekturplan vor Änderung: gesunde gestartete/wartende Bestände mit damals vorhandenen
Zuständen OFFERED/WAITING_TIME unter20 anlegen; durch die echten Migrationen20→21→22 führen.
Erst dann ein neues synthetisches PENDING_START-Angebot in dieser real entstandenen Datenbank
ergänzen. Der exportierte22-Fall erhält dasselbe Angebot. Anschließend wieder echter Room-
Upgrade22→27, unverändert umfassende Archiv-/Erhaltungsprüfung. Keine historischen Runtime-
Aufrufe vortäuschen: das spätere Angebot ist expliziter Fixture-Aufbau unter22. Den Bericht
entsprechend präzisieren und die geänderten nativen Fälle erneut aufAPI26 prüfen. Der ursprüngliche
PR-Lauf bleibt seinem ursprünglichen Head zugeordnet; neuer Prüfstand nach dem korrigierten Build.

Ergänzung desselben Fixture-Abgleichs: Reward-Originalwerte bereits vor20→22 aufnehmen;
ungehandelte Schritte haben noch keinen gewählten Delay. Statische Spalten aller erhaltenen
Ablauf-Snapshots zusätzlich vollständig gegen den Zustand vor22→23 vergleichen, damit die
Archivprüfung nicht der einzige umfassende Spaltennachweis bleibt.

Korrigierter nativer Lauf auf eigenemAPI26: beide Fälle erfolgreich,4.73s. Build9s/8s.
Originale Reward-Zeilen über20→22→27 unverändert; vollständige ursprüngliche Spalten aller vier
erhaltenen Ablauf-Schritte bis27 verglichen. Ursprüngliches Angebot wird ausschließlich unter22
angelegt. Originale und korrigierte Protokolle bleiben getrennt (`/tmp/p4-round1-api26-*`).
Gesondertes Audit gegen Korrekturplan und Roadmap: historisch unmöglicher Quellzustand entfernt,
reale physische Vorgeschichte und beobachteter Room-Modus beibehalten, Erhaltungsnachweis verstärkt.
Der bekannte Erzeuger ist unverändert reproduziert. Neuer vollständiger lokaler Check läuft;
PR368 erhält erst danach den korrigierten Head und passende neue PR-Prüfungen.

Finaler lokaler check-all.sh der Korrekturrunde mit Exit0: 55+43 Skriptprüfungen erfolgreich,
Gradle8s mit169 von172 unveränderten Tasks wiederverwendet; Identität und Größenlimits bestanden.
Keine neue Ausführung der unveränderten Hosttests behauptet. Nur frische PR-/Main-Nachweise offen.

## P4 – Integration und Abschluss

PR368 auf korrigiertem Headf4f519ccf8204e3850df86e5275c0a2ada4d9efa besteht Lauf34716431030:
alle Quality-/Policy-/Inhaltsnachweise, sechs normale/animierte API26/35/37-Lanes und beide
abschließenden Gates grün. Normale JUnit-Läufe entdecken60/60/39 Tests auf26/35/37, jeweils mit
genau den zwei vorgesehenen UpgradePersistenceTest-Skips. Die neue Klasse ist ohne SDK- oder
Klassenfilter im regulären Korpus enthalten. Logs `/tmp/p4-pr-api26.log`, `-api35.log`, `-api37.log`.
Der ursprüngliche Lauf34716089627 wurde nach dem korrigierten Head als überholt abgebrochen;
sein Headb5788c9f wird nicht als finaler Nachweis verwendet.

Squash-Maind779ed38ec2915717a710a1811b74a049bf58f06, gemergt2026-09-12T20:31:50Z,
hat denselben Baume42106fc7f1d5bf425548cf99adf0aef6f5c78ed. Exakter Main-Lauf34717366394 grün.
Tatsächliche Policy-Job-Eingaben bestätigen PR368/Headf4f519cc/Lauf34716431030, full/Policy1,
identical_tree_and_green_pr und release_required=false. Keine zweite Android-Matrix und kein
Produktrelease. Belege `/tmp/p4-main-reuse-proof.json`, `/tmp/p4-main-policy.log`,
`/tmp/p4-pr-final-results.json`, `/tmp/p4-main-final-results.json`.

Abgleich gegen P4-Plan und Roadmap: reproduzierter gemeinsamer Erzeuger, historische Einführung,
beide physische Vorgeschichten, erhaltene Daten und begrenzter Zähler-Negativbefund dokumentiert.
Die korrigierte Fixture respektiert die Einführung von PENDING_START unter22. P4 abgeschlossen;
P4b ist erforderlich. Keine Aussage über die persönliche Entstehungsgeschichte oder ausgeschlossene
zukünftige Zählerfehler. Pixel weiterhin nicht verbunden; dessen Abnahme verbleibt bei P5.

Präzisierung eines früheren P3-Zahlenvermerks: `/tmp/p3-api26-first-job.log` meldet Starting58
einschließlich zwei Upgrade-Skips, nicht60 zusätzliche ausgeführte Tests. P4 meldet60 aufAPI26/35
und39 aufAPI37; die SDK-Auswahl unterscheidet deren Korpora. Skips werden nicht doppelt gezählt.

## P4b – Phasenplan vor Ursachenfix

Ausgangspunkt geprüfter Maind779ed38 / veröffentlichte0.2.174 /Schema27.
Branch codex/recovery-candidate-cleanup. Umfang: Migration22→23, unmittelbar zugehörige native/
Room-Regressionen, ein gezieltes signiertes Upgradefixture und Dokumentation. Erwartetes Profil
full mit Produktrelease, aktuellem signiertem Smoke und dem historischen Korpus.

Bindende vorhandene Entscheidung: ADR-033, Abschnitt Schema-23-Migration, entfernt ungenutzte
Steps/Ressourcen/Einzelblätter unberührter PENDING_START-Runs beim Übergang zu Kandidaten.
Gestartete oder inkonsistent als PENDING_START markierte Runs mit Bearbeitungs-/Ressourcenbelegen
müssen erhalten bleiben. Der Fix setzt diesen bestehenden Vertrag um; keine neue Löschpolitik,
kein neues Schema, keine Rekonstruktion fehlender Abläufe und keine Abschwächung des Archivs.

1. Die nativen P4-Reproduktionen zu Regressionen erweitern: vor24→25 keine neu erzeugten Waisen,
   keine unnötigen Archiveinträge für gesunde unbenutzte Angebote, Kandidat korrekt übernommen,
   unverändert erhaltene gestartete/wartende Abläufe, Snapshotspalten, Historie und Rewards.
   Die bisherigen Gegenbelege mit produktivem Fehler bleiben im P4-Commit erhalten; neue
   Erwartungen zunächst gegen den noch unveränderten Erzeuger tatsächlich ausführen.
2. Abhängigkeiten vor explizitem Löschen prüfen. Gegenfälle mit PENDING_START plus Teilergebnissen,
   Timer, abgeschlossenem Verlauf oder quer zugeordneter Reward-Buchung dürfen nicht als
   unberührt entfernt werden. Falls diese Reproduktion die heutige Kandidatenauswahl widerlegt,
   deren Prädikat begrenzt gemäß vorhandener ADR-033-Erhaltungsregel ergänzen. Offene, rein
   terminbedingte Combo-Verpflichtungen nicht mit abgeschlossenem Fortschritt verwechseln.
3. Nur für die eingefrorene Menge wirklich unberührter Angebote deren abhängige Zeilen vor den
   Eltern entfernen. Beziehungen aus Schema22 vollständig berücksichtigen; keine alleinige
   Reparatur der drei obersten Tabellen bei zurückbleibenden Nachfahren. Kein globales Umschalten
   von FK innerhalb der Upgrade-Transaktion und keine neue globale Vorprüfung, die vorhandene
   schema24-Recovery für bereits beschädigte historische Bestände vorzeitig blockiert.
4. Gezielter Fehler nach Kindbehandlung und vor Elternlöschung: gesamtes Upgrade muss zurückrollen.
   Originalschema und ursprüngliche Datensätze vollständig vergleichen. Bestehende schema24-
   Archiv-/Rollback-/Fremdfehler-Tests sowie gesunde25/26-Pfade bleiben unverändert erforderlich.
5. Signierter direkter Regressionspfad22→27 aufAPI26 zusätzlich zum bestehenden Korpus. Quelle
   0.2.157/1015701, Tagforest-android-1015701, Commit324205e914a73aea0a406b718509e1508afb6165
   wurde veröffentlicht2026-09-07T12:03:57Z; heruntergeladene Bytes, Metadaten, Tag und reguläre
   Signatur frisch geprüft. APK865db75b1f9305e84d4b9239bf17a112fac2e947f52ab5d98feb3d797bb63ed1,
   Metadatene798d44361d197e91b74f1a6fa05e6df8a83abde488fc07085ebb30eff602138.
   Der vorhandene Korpus8/20/23 enthält kein solches unberührtes Schema22-Angebot; die zusätzliche
   Lane prüft genau den jetzt reparierten Auslöser am signierten Kandidaten. Bestehende fünf
   historische Lanes bleiben erhalten. Synthetische Daten ausschließlich im eigenen Emulator.
6. Zuerst gezielte nativeAPI26-/Room- und Fixtureprüfungen, dann vollständiger lokaler Check,
   voller PR-Gate und Squash. Gesonderter Abgleich gegen diesen Plan und RoadmapP4b. Exakter Main,
   regulär signierter höher versionierter Kandidat, aktueller und sechs historische Upgradefälle,
   Veröffentlichung und Hash-/Signaturabgleich. Pixel-In-place-Installation und sichtbare
   Abnahme unter P5; fehlender Gerätezugang bleibt separat offen.

Abnahme: derselbe bislang schädigende Vorgang erzeugt keine Waisen; Bearbeitungsnachweise werden
nicht zu unbenutzten Kandidaten zurückgestuft; Rollback erhält den vollständigen Quellbestand;
das signierte Update besteht den direkten Schema22-Auslöser und die vorhandenen Schutzpfade.
Die unbekannte ursprüngliche Zählerabweichung bleibt ausdrücklich außerhalb eines erfundenen Fixes.

### P4b – Reproduktion vor Produktänderung

Sieben erweiterte native Regressionen auf eigenemAPI26 scheitern gegen die unveränderte produktive
Migration am erwarteten Zwischenbefund (Build9s, `/tmp/p4b-api26-red-results.txt`). Neben den drei
bekannten Kategorien bleibt eine offene Combo-Verpflichtung zurück; bei quer zugewiesener Buchung
zusätzlich reward_assignments. Teilergebnisse, Timer, abgeschlossenes Vorkommen und bereits
aufgelöste Verpflichtung verhindern die bisherige Entfernung ebenfalls nicht. Damit ist auch die
zu weite Auswahl als „unberührt“ konkret widerlegt, nicht nur aus einer hypothetischen Löschgefahr
abgeleitet. Planpräzisierung: diese vorhandenen Nachweise und beide Reward-FK-Bezüge ausschließen,
offene rein terminbedingte Verpflichtung dagegen mit dem wirklich unbenutzten Angebot entfernen.
Zusätzlicher Gegenfall für Buchung mit fremdem Ursprung, aber Bezug auf den betroffenen Schritt,
und Fehlerzeugung nach Kindbehandlung sichern die vollständige Abhängigkeits-/Rollbackgrenze.

Auch diese zwei zusätzlichen nativen Gegenproben scheitern gezielt vor Produktänderung:
fremde Buchung mit betroffenem occurrenceStepId lässt den Run verschwinden; der Fehlertrigger
meldet children-not-cleaned statt injected-after-cleanup. Damit sind alle neun neuen Erwartungen
gegen den alten Code tatsächlich rot (`/tmp/p4b-api26-red-extra-results.txt`). Kein Build- oder
Fixture-Setupfehler wird als erwartetes Regressionsversagen gezählt. Nun begrenzte Umsetzung:
Bearbeitungsbelege aus der Kandidatenmenge ausschließen; offene Verpflichtungen und ungenutzte
Snapshotkinder vor Eltern löschen. Timer-/Ergebnis-/Reward-Zeilen selbst werden nicht gelöscht,
weil ihre vorhandenen Bezüge die betreffenden Runs erhalten.

### P4b – Korrekturrunde 1: gültige alte Ressourcenintervalle

Erster Lauf nach dem lokalen Fix: unbenutzte20/22-Fälle und vollständiger Rollback bestehen;
sechs erhaltene Pending-Fälle erreichen jetzt24→25, scheitern dort aber an der Fixture-Ressource.
FlowRunResourceSnapshot verlangt releasePosition > acquirePosition. Der bisherige Clean-Run
hatte nur einen Schritt und Intervall0→0; P4 erreichte dessen Domain-Konvertierung wegen der
Elternentfernung nicht. Die ursprüngliche FK-Reproduktion war real, ihr „gesunder“ Ressourcen-
Begriff aber zu schwach. Kein Produktfehler wird durch Abschwächen dieser Validierung versteckt.

Korrekturplan: zweiter unbenutzter Folgeschritt und gültiges Intervall0→1; diese Form schon beim
Fixture-Aufbau prüfen. Erhaltene Snapshotzählung und vollständiger Spaltenvergleich entsprechend
erweitern. Mit dieser gültigen Form die alte Migration nochmals isoliert als Gegenprobe ausführen
(vorübergehende, gesicherte Quellvariante ausschließlich im eigenen Worktree/Emulator), danach
den unveränderten Fix wiederherstellen und alle neun Fälle erneut prüfen. Frühere Logs bleiben
unverändert (`/tmp/p4b-api26-green-results.txt`, drei bestanden/sechs Fixturefehler).

Gültiger korrigierter Bestand: alle neun Gegenproben scheitern am alten Code am erwarteten
Verhalten (acht Waisenbefunde und children-not-cleaned). Der gesicherte Fix wurde anschließend
bytegleich wiederhergestellt (SHA2568b86bd3fbcaedab9c6bd7da6ee2fb19d1eee4a838f996b99c0e482da58bac8e4).
Mit Fix bestehen alle neun nativen Fälle in14.121s, einschließlich vollständigem Schema-/Zeilen-
Rollback. Belege `/tmp/p4b-round1-api26-counter-results.txt` und
`/tmp/p4b-round1-api26-fixed-results.txt`. Abschließend wird der vorhandene Spaltenvergleich auch
für das erhaltene Pending-Vorkommen, dessen Schritt und die umbenannten Ressourcenbindungen bis27
vollständig ausgeführt; Umfang weiterhin derselbe Erhaltungsvertrag.

### P4b – Präzisierung des signierten Regressionstests

Der vorhandene Fixture-Vertrag kann nur die Anwesenheit einzelner Zielzeilen prüfen. Das genügt
hier nicht: der alte Fehler mit späterer Archivierung würde dieselben Kandidaten/gesunden Zeilen
liefern. Für den direkten Schema22-Nachweis muss ausdrücklich geprüft werden, dass für die
unbenutzten Snapshot-IDs keine Recovery-Zeilen entstanden sind. Daher vor Fixture-Implementierung
begrenzte Erweiterung des bestehenden Erwartungsformats: pro table/where entweder nichtleere
values (genau eine Zeile mit diesen Werten) oder absent:true (keine Zeile). Keine freien SQL-
Ausdrücke, keine leeren Selektoren und keine mehrdeutige Kombination. Bestehende Fixtures und
aktueller Smoke behalten unverändert ihre bisherige Bedeutung. Python-Vertragsprüfung,
Room-Fixturetest und produktionskompatibler UpgradePersistenceProbe müssen dieselbe Form prüfen;
negative Vertragsfälle und tatsächlich vorhandene verbotene Zeile müssen den Nachweis stoppen.
Danach gezieltes Schema22-Fixture mit Kandidat, gültiger Historie und negativen Archiv-Assertions;
alte Migration muss dessen Abwesenheitsnachweis verfehlen. Diese Testvertragsänderung gehört
zum bestehenden full/Release-Umfang und ändert keine fachliche Datenentscheidung.

### P4b – Korrekturrunde 2: Zielspalten vollständig berücksichtigen

Die zusätzlich verschärfte Erhaltungsprüfung meldet sechs Fehler im Lauf
`/tmp/p4b-round1-api26-final-results.txt`. Der verglichene Schritt enthält nach24→25
korrekt die neue Spalte flowRunStepId=clean-run-step; die Erwartung enthielt nur die
Schema22-Spalten. FlowGraphMigration25 ergänzt und befüllt diese Zuordnung ausdrücklich.
Korrekturplan: die vollständige erwartete Zielzeile um genau diese geprüfte Zuordnung ergänzen,
alle Originalspalten weiter unverändert vergleichen. Keine Produktänderung und keine Entfernung
der Erhaltungsprüfung. Danach alle neun nativen Fälle erneut ausführen; die spätere Ressourcen-
Assertion wird erst durch diesen Lauf vollständig erreicht und gilt vorher nicht als bestanden.

Korrekturrunde2 bestanden: alle neun nativen API26-Fälle erfolgreich in14.356s
(`/tmp/p4b-round2-api26-results.txt`), einschließlich der vollständigen Zielzeilen für
Vorkommen, Schritt und Ressource. Ursprüngliche Werte und neue Schrittzuordnungen geprüft.

Das signierte22-Fixture fokussiert auf den zuvor durch Recovery verdeckten Erzeuger: zwei
unbenutzte Schritte, geplante Ressource und Angebot; daneben gültiger wartender Ablauf,
aktive Ressource, abgeschlossener Verlauf und HEAD-/VESSEL-Buchungen samt Zuordnungen.
Offene Combo-Verpflichtungen bleiben in der nativen Regression abgedeckt; sie werden hier
bewusst nicht zusätzlich gesät, damit der alte Code bis zur Archivierung gelangt und gerade
am neuen Abwesenheitsnachweis scheitern muss. Der signierte Quellpfad bleibt22→27 aufAPI26.

### P4b – Fixture-Korrekturrunde 3: vorhandene Reward-Projektion

Erster fokussierter Fixture-Lauf: sieben Vertragsfälle bestehen, beide realen Room-Upgrades
API26/35 erreichen die Zielprüfung und melden alreadyPaidTau=4 statt erwarteter0. Die neue
Fixture enthält im Unterschied zur übernommenen Schema20-Basis eine VESSEL-Buchung4 und eine
HEAD-Buchung im selben abgeschlossenen Vorkommen. Schema24FlowMigrationInput zählt diesen
bezahlten Hauptwert ausdrücklich als4. Korrekturplan: diese bestehende Projektion als4 am Run
und earnedTau=4 am zugeordneten Schritt prüfen; Buchungen und Zuordnungen unverändert vollständig
vergleichen. Zusätzlich die schon bestehende COALESCE-Normalisierung eines schlussständigen
Schritts ohne Übergang (delayMode FIXED/defaultDelayMillis0) im erweiterten Spaltenvergleich
berücksichtigen. Keine Buchung oder Produktlogik ändern. Wiederholung der fokussierten Contracts
und echten26/35-Migrationen; ursprünglicher Fehler bleibt im ersten Log dokumentiert.

### P4b – gezielte Nachweise und Abgleich vor Vollprüfung

Fixture-Korrekturrunde3: neun Host-Vertragsfälle einschließlich echter Room-Upgrades auf26/35
bestanden, 42.22s Testzeit (`/tmp/p4b-fixture-round3-build.log`, Build54s). Neuer nativer Lauf
auf eigenemAPI26: elf Fälle bestanden,20.44s (`/tmp/p4b-native-final-api26.txt`). Dies umfasst
neun Erzeuger-/Erhaltungs-/Rollbackfälle sowie zwei Tests des tatsächlichen Release-Zeilenprüfers:
vorhandene verbotene Zeilen, Null-Selektor, fehlende/mehrfache/falsche Wertzeilen und mehrdeutige
Erwartungen werden abgewiesen. In-memory-Datenbank und isoliertes Testpaket; kein Pixel betroffen.

Die reale Room-Gegenprobe mit unveränderter alter Migration scheitert auf26/35 exakt an
migration_recovery(sourceTable=flow_run_steps,sourceId=upgrade-clean-step-0): erwartet0,
gefunden1. Alle vorherigen positiven Zielerwartungen bestanden bis dahin. Damit ist das
Maskierungsproblem konkret bewiesen, nicht bloß vermutet. Originalfix danach bytegleich
wiederhergestellt (SHA2568b86bd3fbcaedab9c6bd7da6ee2fb19d1eee4a838f996b99c0e482da58bac8e4).
Belege `/tmp/p4b-fixture-old-counter.log` und `-results.xml`; erwartete Gegenproben zählen nicht
als Freigabeerfolg. Eigener Emulator anschließend regulär beendet.

Abgleich gegen P4b-Plan: bestehender ADR-033-Vertrag umgesetzt, keine neue Löschpolitik oder
Schemaänderung, echte Room-FK-Beobachtung, sofortige Integrität, vollständiger Rollback und
separate Bearbeitungsbelege geprüft. Fixture-Vertrag und Python/Room/Release-Runner unterstützen
explizite Abwesenheit; bestehende Quellen unverändert. Quelle0.2.157 erneut per Bytehash und
regulärem Zertifikat geprüft. 45 Release-Werkzeugtests grün. Dokumentation benennt sechs
historische Lanes plus aktuellen Smoke und die tatsächlichen Grenzen der früheren Fixture.

Abgleich gegen kanonische Roadmap: bedingter P4b-Ursachenfix ist durch P4-Reproduktion ausgelöst;
kein Umbau von Room/Domain/Bedienung, kein erfundener Zählerfix, keine Änderung am bestehenden
Archiv. Unbekannte persönliche Entstehungsgeschichte und P5-Geräteabnahme bleiben offen.
Klassifikation frisch full/Policy1/release_required=true. Noch offene Software-Gates:
vollständiger lokaler Check, vollständiger PR, Squash, exakter Main, signierte Upgrades und
veröffentlichte Kandidatenbytes. Alle gezielten Ergebnisse gehören zum uncommitteten Stand.

Produktionskompatibler Helper separat mit upgradeProbeRunner=true gebaut (11s) und sein Manifest
gegen den Upgrade-Vertrag geprüft: ausschließlich die vorgesehenen Runner. Dies ist ein lokaler
Build-/Identitätsnachweis ohne Produktionssignatur. Der vollständige lokale check-all.sh läuft
anschließend mit regulärer Instrumentierungsvariante; Logs `/tmp/p4b-full-local-check.log`.
Aktuell veröffentlichte Version frisch über GitHub geprüft:0.2.174/tag1017401. P4b-PR noch nicht
angelegt; regulär signierter höher versionierter Release wird erst nach dessen geprüftem Merge gebaut.

### P4b – Präzisierung der Prüfungsreihenfolge vor PR-Eröffnung

Der vollständige lokale Check läuft nach den grünen gezielten Nachweisen weiterhin nachweislich
in seiner ursprünglichen Sitzung. Statt bis zu dessen Ende auch mit dem Start unabhängiger
PR-Prüfungen zu warten, wird ein Draft-PR parallel eröffnet. Das ändert ausschließlich die
zeitliche Überlappung: lokaler Vollcheck und vollständiger PR-Gate bleiben beide vor Ready/Merge
erforderlich, kein grünes Ergebnis wird vorweggenommen. Bei einem lokalen Fehler wird zuerst
dessen Ursache geklärt; ein überholter CI-Stand wird nicht als Nachweis übernommen. Keine
Wiederholung des laufenden lokalen Checks. Produkt-/Testdateien sind seit Commit01f1dd64 unverändert;
der folgende Commit ergänzt nur diesen Ausführungsvermerk. Das vorgeschaltete Warten bis vor
PR-Eröffnung wird damit ausdrücklich ersetzt, der Freigabevertrag bleibt unverändert.

Frischer lesender Vorabgleich: PR364/366/367/368 sind gemergt, ihre Main-Läufe34699171018/
34708748445/34714576280/34717366394 erfolgreich und alle vier Merge-Commits Vorfahren des
aktuellen Branches. Remote-Main unverändertd779ed38; Pixel weiterhin nicht verbunden.
Beleg `/tmp/p4b-crossphase-remote-proof.json`. Keine neue Ausführung früherer Phasenprüfungen.
